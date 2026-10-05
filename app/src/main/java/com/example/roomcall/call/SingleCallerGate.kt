package com.example.roomcall.call

/** Identity-based ownership: a losing/old client can never release another client's reservation. */
internal class SingleCallerGate<T : Any> {
    var owner: T? = null
        private set
    @Synchronized fun tryAcquire(candidate: T): Boolean {
        if (owner != null) return false
        owner = candidate
        return true
    }
    @Synchronized fun release(candidate: T): Boolean {
        if (owner !== candidate) return false
        owner = null
        return true
    }
}