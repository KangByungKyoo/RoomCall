package com.example.roomcall.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.roomcall.R
import com.example.roomcall.network.TcpServer

import com.example.roomcall.network.RoomCallNsdRegistrar
import com.example.roomcall.audio.VoicePlayer


class RoomCallReceiverService : Service() {

    private var tcpServer: TcpServer? = null

    private var nsdRegistrar: RoomCallNsdRegistrar? = null
    private lateinit var voicePlayer: VoicePlayer

    override fun onCreate() {
        super.onCreate()

        voicePlayer = VoicePlayer(this)


        createNotificationChannel()

        val notification = NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("RoomCall")
            .setContentText("메시지 수신 대기 중입니다.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(
            NOTIFICATION_ID,
            notification
        )



        tcpServer = TcpServer { message ->



            // MainActivity에 받은 메시지 전달
            val broadcastIntent = Intent(ACTION_MESSAGE_RECEIVED).apply {
                setPackage(packageName)
                putExtra(EXTRA_MESSAGE, message)
            }

            sendBroadcast(broadcastIntent)
        }

        tcpServer?.start()

        nsdRegistrar = RoomCallNsdRegistrar(applicationContext)
        nsdRegistrar?.register()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        nsdRegistrar?.unregister()
        nsdRegistrar = null

        tcpServer?.stop()
        tcpServer = null




        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "RoomCall 수신 서비스",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "RoomCall 메시지를 계속 수신합니다."
        }

        val notificationManager =
            getSystemService(NotificationManager::class.java)

        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "roomcall_receiver_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_MESSAGE_RECEIVED =
            "com.example.roomcall.ACTION_MESSAGE_RECEIVED"

        const val EXTRA_MESSAGE = "extra_message"
    }
}