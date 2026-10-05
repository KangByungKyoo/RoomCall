package com.example.roomcall.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.example.roomcall.call.CallProtocol

class RoomCallNsdRegistrar(
    context: Context
) {

    companion object {
        const val SERVICE_NAME = "RoomCall Receiver"
        const val SERVICE_TYPE = "_roomcall._tcp."
    }

    private val nsdManager =
        context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var isRegistered = false

    fun register() {
        if (registrationListener != null) {
            return
        }

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            port = CallProtocol.PORT
        }

        Log.d(
            "RoomCall",
            "Registering NSD service: " +
                    "name=${serviceInfo.serviceName}, " +
                    "type=${serviceInfo.serviceType}, " +
                    "port=${serviceInfo.port}"
        )

        val listener = object : NsdManager.RegistrationListener {

            override fun onServiceRegistered(registeredServiceInfo: NsdServiceInfo) {
                isRegistered = true

                Log.d(
                    "RoomCall",
                    "NSD service registered: " +
                            "${registeredServiceInfo.serviceName}, " +
                            "port=${registeredServiceInfo.port}"
                )
            }

            override fun onRegistrationFailed(
                serviceInfo: NsdServiceInfo,
                errorCode: Int
            ) {
                isRegistered = false
                registrationListener = null

                Log.e(
                    "RoomCall",
                    "NSD registration failed: errorCode=$errorCode"
                )
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                isRegistered = false

                Log.d(
                    "RoomCall",
                    "NSD service unregistered: ${serviceInfo.serviceName}"
                )
            }

            override fun onUnregistrationFailed(
                serviceInfo: NsdServiceInfo,
                errorCode: Int
            ) {
                Log.e(
                    "RoomCall",
                    "NSD unregistration failed: errorCode=$errorCode"
                )
            }
        }

        registrationListener = listener

        try {
            nsdManager.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                listener
            )
        } catch (e: Exception) {
            registrationListener = null
            isRegistered = false

            Log.e("RoomCall", "NSD register error", e)
        }
    }

    fun unregister() {
        val listener = registrationListener ?: return

        if (!isRegistered) {
            registrationListener = null
            return
        }

        try {
            nsdManager.unregisterService(listener)
        } catch (e: Exception) {
            Log.e("RoomCall", "NSD unregister error", e)
        } finally {
            registrationListener = null
            isRegistered = false
        }
    }
}

