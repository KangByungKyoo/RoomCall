package com.example.roomcall.network

import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.SocketException
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets

class TcpServer(
    private val onMessageReceived: (String) -> Unit
) {

    @Volatile
    private var serverSocket: ServerSocket? = null

    @Volatile
    private var isRunning = false

    @Synchronized
    fun start() {
        if (isRunning) return

        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(NetworkConstants.PORT)

                Log.d("RoomCall", "TCP Server started on port ${NetworkConstants.PORT}")

                while (isRunning) {
                    val clientSocket = serverSocket?.accept() ?: break
                    clientSocket.use { socket ->
                        socket.soTimeout = NetworkConstants.READ_TIMEOUT_MS
                        val message = readMessage(socket)
                        if (message != null) {
                            Log.d("RoomCall", "Message received from ${socket.inetAddress}")
                            onMessageReceived(message)
                        }
                    }
                }
            } catch (e: SocketException) {
                if (isRunning) Log.e("RoomCall", "TCP Server socket error", e)
            } catch (e: Exception) {
                if (isRunning) Log.e("RoomCall", "TCP Server error", e)
            } finally {
                isRunning = false
                closeServerSocket()
            }
        }.apply {
            name = "RoomCall-TcpServer"
            start()
        }
    }

    @Synchronized
    fun stop() {
        isRunning = false
        closeServerSocket()
        Log.d("RoomCall", "TCP Server stopped")
    }

    private fun closeServerSocket() {
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e("RoomCall", "TCP Server stop error", e)
        }
    }

    private fun readMessage(socket: Socket): String? {
        val input = socket.getInputStream()
        val output = ByteArrayOutputStream()

        while (output.size() <= NetworkConstants.MAX_MESSAGE_BYTES) {
            val value = input.read()
            if (value == -1 || value == '\n'.code) break
            if (value != '\r'.code) output.write(value)
        }

        if (output.size() > NetworkConstants.MAX_MESSAGE_BYTES) {
            Log.w("RoomCall", "Rejected an oversized message")
            return null
        }

        return output.toString(StandardCharsets.UTF_8.name())
            .takeIf { it.isNotBlank() }
    }
}
