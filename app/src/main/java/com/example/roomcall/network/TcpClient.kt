package com.example.roomcall.network

import android.util.Log
import java.io.IOException
import java.net.Socket
import kotlin.concurrent.thread
import java.io.PrintWriter


object TcpClient {

    fun send(
        ipAddress: String,
        message: String
    ){

        thread {

            try {
                val socket = Socket(ipAddress, 5050)

                Log.d("TcpClient", "Connected!")

                val writer = PrintWriter(
                    socket.getOutputStream(),
                    true
                )

                writer.println(message)

                Log.d("TcpClient", "Message sent: $message")
                writer.close()
                socket.close()

            } catch (e: IOException) {
                Log.d("TcpClient", "Connection failed", e)
            }
        }
    }
}

