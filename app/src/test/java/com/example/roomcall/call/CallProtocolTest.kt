package com.example.roomcall.call

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.net.Socket

class CallProtocolTest {
    private class MemorySocket(data: String) : Socket() {
        private val input = ByteArrayInputStream(data.toByteArray(Charsets.UTF_8))
        val output = ByteArrayOutputStream()
        override fun getInputStream() = input
        override fun getOutputStream() = output
    }
    @Test fun readsOneFrameWithoutConsumingNextFrame() {
        val socket = MemorySocket("ROOMCALL_V1 CALL 12000 42\r\nREADY\nPING\nEND\n")
        assertEquals("ROOMCALL_V1 CALL 12000 42", CallProtocol.read(socket))
        assertEquals("READY", CallProtocol.read(socket))
        assertEquals("PING", CallProtocol.read(socket))
        assertEquals("END", CallProtocol.read(socket))
    }
    @Test(expected = EOFException::class) fun disconnectDuringHandshakeFailsRatherThanHanging() {
        CallProtocol.read(MemorySocket("ROOMCALL_V1 CALL"))
    }
    @Test(expected = IllegalStateException::class) fun oversizedRequestIsRejected() {
        CallProtocol.read(MemorySocket("x".repeat(256) + "\n"))
    }
    @Test fun writesNewlineTerminatedUtf8() {
        val socket = MemorySocket("")
        CallProtocol.write(socket, "현재 다른 사용자가 통화 중입니다.")
        assertEquals("현재 다른 사용자가 통화 중입니다.\n", socket.output.toString("UTF-8"))
    }
    @Test fun rejectsWanOtherSubnetMulticastBroadcastAndSelf() {
        val local = byteArrayOf(192.toByte(), 168.toByte(), 1, 10)
        fun peer(a: Int, b: Int, c: Int, d: Int) = byteArrayOf(a.toByte(), b.toByte(), c.toByte(), d.toByte())
        assertTrue(LanNetwork.sameSubnet(local, peer(192,168,1,11), 24))
        assertFalse(LanNetwork.sameSubnet(local, peer(192,168,2,11), 24))
        assertFalse(LanNetwork.sameSubnet(local, peer(8,8,8,8), 24))
        assertFalse(LanNetwork.sameSubnet(local, peer(224,0,0,1), 24))
        assertFalse(LanNetwork.sameSubnet(local, peer(192,168,1,255), 24))
        assertFalse(LanNetwork.sameSubnet(local, local, 24))
    }
}