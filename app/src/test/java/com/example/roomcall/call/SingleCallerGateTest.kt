package com.example.roomcall.call

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SingleCallerGateTest {
    @Test fun simultaneousRequestsAdmitExactlyOneUntilOwnerReleases() {
        val gate = SingleCallerGate<Any>()
        val candidates = List(24) { Any() }
        val pool = Executors.newFixedThreadPool(24)
        val ready = CountDownLatch(24)
        val start = CountDownLatch(1)
        val finished = CountDownLatch(24)
        val winners = AtomicInteger()
        try {
            candidates.forEach { candidate -> pool.execute {
                ready.countDown(); start.await()
                if (gate.tryAcquire(candidate)) winners.incrementAndGet()
                finished.countDown()
            } }
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertEquals(1, winners.get())
            val winner = gate.owner!!
            val loser = candidates.first { it !== winner }
            assertFalse(gate.release(loser))
            assertFalse(gate.tryAcquire(loser))
            assertTrue(gate.release(winner))
            assertTrue(gate.tryAcquire(loser))
            assertFalse(gate.release(winner))
            assertSame(loser, gate.owner)
        } finally { start.countDown(); pool.shutdownNow() }
    }
}