package com.example.roomcall.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/** Renewable bounded lock; ownership is strictly tied to the foreground service lifetime. */
internal class ServiceWakeLock(context: Context, tag: String) {
    private val handler = Handler(Looper.getMainLooper())
    private val lock = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag).apply { setReferenceCounted(false) }
    private val renew = object : Runnable {
        override fun run() {
            lock.acquire(10 * 60 * 1000L)
            handler.postDelayed(this, 5 * 60 * 1000L)
        }
    }
    fun start() { handler.removeCallbacks(renew); renew.run() }
    fun close() { handler.removeCallbacks(renew); if (lock.isHeld) lock.release() }
}