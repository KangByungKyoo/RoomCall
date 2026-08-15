package com.example.roomcall.network

import android.util.Log
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread
import java.io.PrintWriter


object TcpClient {

    fun send(
        ipAddress: String,
        port: Int = NetworkConstants.PORT,
        message: String,
        onResult: (Boolean) -> Unit = {}
    ) {

        if (ipAddress.isBlank() || port !in 1..65535) {
            onResult(false)
            return
        }

        thread {
            try {
                Socket().use { socket ->
                    socket.connect(
                        InetSocketAddress(ipAddress.trim(), port),
                        NetworkConstants.CONNECT_TIMEOUT_MS
                    )

                    PrintWriter(socket.getOutputStream(), true).use { writer ->
                        writer.println(message)
                        if (writer.checkError()) {
                            throw IOException("Could not write the message")
                        }
                    }
                }

                Log.d("TcpClient", "Message sent to $ipAddress:$port")
                onResult(true)
            } catch (e: IOException) {
                Log.w("TcpClient", "Connection failed: $ipAddress:$port", e)
                onResult(false)
            } catch (e: IllegalArgumentException) {
                Log.w("TcpClient", "Invalid receiver address: $ipAddress:$port", e)
                onResult(false)
            }
        }
    }
}

