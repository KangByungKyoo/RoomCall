package com.example.roomcall.call

import java.io.EOFException
import java.net.Socket

/** Separate port preserves the existing newline message protocol on port 5050. */
object CallProtocol {
    const val PORT = 5051
    const val VERSION = "ROOMCALL_V1"
    const val TIMEOUT_MS = 6000
    fun read(socket: Socket): String {
        val input = socket.getInputStream()
        val bytes = java.io.ByteArrayOutputStream()
        while (bytes.size() < 256) {
            val b = input.read()
            if (b < 0) throw EOFException("통화 연결이 종료되었습니다.")
            if (b == 10) return bytes.toString("UTF-8")
            if (b != 13) bytes.write(b)
        }
        error("Invalid call control frame")
    }
    fun write(socket: Socket, line: String) = synchronized(socket) {
        socket.getOutputStream().apply {
            write((line + "\n").toByteArray(Charsets.UTF_8)); flush()
        }
    }
}