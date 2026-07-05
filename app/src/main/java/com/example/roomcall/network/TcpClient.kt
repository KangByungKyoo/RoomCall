package com.example.roomcall.network

import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket

class TcpClient {

    fun connect(host: String): Boolean {
        return try {
            Thread {
                try {
                    Socket().use { socket ->
                        socket.connect(
                            InetSocketAddress(host, NetworkConstants.PORT),
                            3000
                        )

                        Log.d("RoomCall", "TCP Client connected to $host:${NetworkConstants.PORT}")
                    }
                } catch (e: Exception) {
                    Log.e("RoomCall", "TCP Client connection error", e)
                }
            }.start()

            true
        } catch (e: Exception) {
            Log.e("RoomCall", "TCP Client start error", e)
            false
        }
    }
}

