package com.example.roomcall.network

import android.util.Log
import java.net.ServerSocket

class TcpServer {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return

        Thread {
            try {
                serverSocket = ServerSocket(NetworkConstants.PORT)
                isRunning = true

                Log.d("RoomCall", "TCP Server started on port ${NetworkConstants.PORT}")

                while (isRunning) {
                    val clientSocket = serverSocket?.accept()

                    Log.d("RoomCall",
                        "Client connected: ${clientSocket?.inetAddress}"
                    )

                    val reader = clientSocket
                        ?.getInputStream()
                        ?.bufferedReader()

                    val message = reader?.readLine()

                    Log.d("RoomCall", "Received message: $message")

                    reader?.close()
                    clientSocket?.close()
                }
            } catch (e: Exception) {
                Log.e("RoomCall", "TCP Server error", e)
            }
        }.start()
    }

    fun stop() {
        isRunning = false

        try {
            serverSocket?.close()
            serverSocket = null

            Log.d("RoomCall", "TCP Server stopped")

        } catch (e: Exception) {
            Log.e("RoomCall", "TCP Server stop error", e)
        }
    }
}