package com.example.roomcall.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import android.net.wifi.WifiManager
import android.os.Build
import java.net.Inet4Address

class RoomCallNsdDiscovery(
    private val context: Context,
    private val onReceiverFound: (ipAddress: String, port: Int) -> Unit,
    private val onReceiverLost: () -> Unit = {}
) {

    companion object {
        private const val TAG = "RoomCall-NSD"

        private const val SERVICE_NAME = "RoomCall Receiver"
        private const val SERVICE_TYPE = "_roomcall._tcp."
    }

    private val nsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false
    private var isResolving = false

    fun startDiscovery() {
        if (discoveryListener != null) {
            Log.d(TAG, "Discovery is already running")
            return
        }

        multicastLock = context.applicationContext.getSystemService(WifiManager::class.java)
            .createMulticastLock("RoomCall:NsdDiscovery").apply { setReferenceCounted(false); acquire() }
        val listener = object : NsdManager.DiscoveryListener {

            override fun onDiscoveryStarted(serviceType: String) {
                isDiscovering = true

                Log.d(TAG, "NSD discovery started: $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {

                Log.d(
                    TAG,
                    "Service found: " +
                            "name=${serviceInfo.serviceName}, " +
                            "type=${serviceInfo.serviceType}"
                )

                if (serviceInfo.serviceType != SERVICE_TYPE) {
                    Log.d(TAG, "Ignored: different service type")
                    return
                }

                if (!serviceInfo.serviceName.startsWith(SERVICE_NAME)) {
                    Log.d(TAG, "Ignored: different service name")
                    return
                }

                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(
                    TAG, "Service lost: ${serviceInfo.serviceName}"
                )

                if (serviceInfo.serviceName.startsWith(SERVICE_NAME)) {
                    onReceiverLost()
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                isDiscovering = false
                Log.d(TAG, "NSD discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(
                serviceType: String, errorCode: Int
            ) {
                isDiscovering = false

                Log.e(
                    TAG, "NSD discovery start failed: errorCode=$errorCode"
                )

                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(
                serviceType: String, errorCode: Int
            ) {
                isDiscovering = false

                Log.e(
                    TAG, "NSD discovery stop failed: errorCode=$errorCode"
                )
            }
        }

        discoveryListener = listener

        try {
            nsdManager.discoverServices(
                SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener
            )
        } catch (e: Exception) {
            discoveryListener = null
            isDiscovering = false

            multicastLock?.let { if (it.isHeld) it.release() }; multicastLock = null
            Log.e(TAG, "Could not start NSD discovery", e)
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        if (isResolving) {
            Log.d(TAG, "Another service is already being resolved")
            return
        }

        isResolving = true

        nsdManager.resolveService(
            serviceInfo, object : NsdManager.ResolveListener {

                override fun onServiceResolved(
                    resolvedServiceInfo: NsdServiceInfo
                ) {
                    isResolving = false

                    if (discoveryListener == null) return
                    val host = if (Build.VERSION.SDK_INT >= 34) {
                        resolvedServiceInfo.hostAddresses.firstOrNull { it is Inet4Address }
                    } else resolvedServiceInfo.host
                    val port = resolvedServiceInfo.port

                    if (host == null || port <= 0) {
                        Log.e(
                            TAG, "Resolved service has invalid address or port"
                        )
                        return
                    }

                    val ipAddress = host.hostAddress

                    if (ipAddress.isNullOrBlank()) {
                        Log.e(TAG, "Resolved IP address is empty")
                        return
                    }

                    Log.d(
                        TAG,
                        "Receiver resolved: " + "name=${resolvedServiceInfo.serviceName}, " + "ip=$ipAddress, " + "port=$port"
                    )

                    onReceiverFound(ipAddress, port)
                }

                override fun onResolveFailed(
                    serviceInfo: NsdServiceInfo, errorCode: Int
                ) {
                    isResolving = false

                    Log.e(
                        TAG,
                        "Service resolve failed: " + "name=${serviceInfo.serviceName}, " + "errorCode=$errorCode"
                    )
                }
            })
    }

    fun stopDiscovery() {
        val listener = discoveryListener ?: return

        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Discovery was already stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Could not stop NSD discovery", e)
        } finally {
            discoveryListener = null
            isDiscovering = false
            isResolving = false
            multicastLock?.let { if (it.isHeld) it.release() }; multicastLock = null
        }
    }
}

