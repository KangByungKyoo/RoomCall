package com.example.roomcall.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.net.Inet4Address

/** All discovery lifecycle changes run on main; restart waits for stop acknowledgement. */
@Suppress("DEPRECATION")
class RoomCallNsdDiscovery(
    context: Context,
    private val onReceiverFound: (String, Int) -> Unit,
    private val onReceiverLost: () -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NsdManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var listener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var desired = false
    private var stopping = false
    private var resolving = false
    private var selectedName: String? = null
    fun startDiscovery() { handler.post { desired = true; if (listener == null) startInternal() } }
    fun stopDiscovery() { handler.post { desired = false; stopInternal() } }
    fun restartDiscovery() { handler.post { desired = true; if (listener == null) startInternal() else stopInternal() } }

    private fun startInternal() {
        if (!desired || listener != null) return
        multicastLock = appContext.getSystemService(WifiManager::class.java)
            .createMulticastLock("RoomCall:NsdDiscovery").apply { setReferenceCounted(false); acquire() }
        val current = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onServiceFound(info: NsdServiceInfo) { handler.post {
                if (listener === this && desired && !stopping &&
                    info.serviceType == RoomCallNsdRegistrar.SERVICE_TYPE &&
                    info.serviceName.startsWith(RoomCallNsdRegistrar.SERVICE_NAME)) resolve(info, this)
            } }
            override fun onServiceLost(info: NsdServiceInfo) { handler.post {
                if (listener === this && desired && !stopping && selectedName == info.serviceName) {
                    selectedName = null; onReceiverLost()
                }
            } }
            override fun onDiscoveryStopped(type: String) { handler.post {
                if (listener === this) { clear(); if (desired) startInternal() }
            } }
            override fun onStartDiscoveryFailed(type: String, code: Int) { handler.post {
                if (listener === this) {
                    Log.w("RoomCall-NSD", "Discovery failed: $code")
                    clear()
                    handler.postDelayed({ if (desired && listener == null) startInternal() }, 1500)
                }
            } }
            override fun onStopDiscoveryFailed(type: String, code: Int) { handler.post {
                if (listener === this) { clear(); if (desired) startInternal() }
            } }
        }
        listener = current
        try { manager.discoverServices(RoomCallNsdRegistrar.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, current) }
        catch (e: Exception) { Log.w("RoomCall-NSD", "Discovery error", e); clear() }
    }
    private fun stopInternal() {
        val current = listener ?: return
        if (stopping) return
        stopping = true
        try { manager.stopServiceDiscovery(current) }
        catch (_: Exception) { clear(); if (desired) startInternal() }
    }
    private fun clear() {
        listener = null; stopping = false; resolving = false; selectedName = null
        multicastLock?.let { if (it.isHeld) it.release() }; multicastLock = null
    }
    private fun resolve(info: NsdServiceInfo, source: NsdManager.DiscoveryListener) {
        if (resolving) return
        resolving = true
        try {
            manager.resolveService(info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(resolved: NsdServiceInfo) { handler.post {
                    if (listener !== source || !desired || stopping) return@post
                    resolving = false
                    val host = if (Build.VERSION.SDK_INT >= 34) resolved.hostAddresses.firstOrNull { it is Inet4Address } else resolved.host
                    val ip = host?.hostAddress ?: return@post
                    if (resolved.port <= 0) return@post
                    selectedName = resolved.serviceName
                    onReceiverFound(ip, resolved.port)
                } }
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) { handler.post {
                    if (listener === source) resolving = false
                } }
            })
        } catch (_: Exception) { resolving = false }
    }
}