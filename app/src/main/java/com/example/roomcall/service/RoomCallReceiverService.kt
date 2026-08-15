package com.example.roomcall.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.roomcall.R
import com.example.roomcall.audio.VoicePlayer
import com.example.roomcall.network.RoomCallNsdRegistrar
import com.example.roomcall.network.TcpServer

class RoomCallReceiverService : Service() {

    private var tcpServer: TcpServer? = null
    private var nsdRegistrar: RoomCallNsdRegistrar? = null
    private lateinit var voicePlayer: VoicePlayer

    override fun onCreate() {
        super.onCreate()

        // Foreground Service로 전환
        createNotificationChannel()
        startAsForegroundService()

        // 화면이 꺼져도 음성을 재생할 수 있도록 서비스에서 관리
        voicePlayer = VoicePlayer(this)

        tcpServer = TcpServer { message ->
            voicePlayer.playMessage(message)                // 서비스에서 직접 음성 재생

            // 화면이 켜져 있을 때 MainActivity의 표시 내용 갱신
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

        voicePlayer.release()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startAsForegroundService() {

        val notification = NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("RoomCall 수신 대기 중")
            .setContentText("화면이 꺼져도 호출 메시지를 받을 수 있습니다.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            } else {
                0
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "RoomCall 수신 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "RoomCall 메시지를 계속 수신하기 위한 서비스입니다."
            }

            val notificationManager =
                getSystemService(NotificationManager::class.java)

            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_MESSAGE_RECEIVED =
            "com.example.roomcall.ACTION_MESSAGE_RECEIVED"

        const val EXTRA_MESSAGE = "extra_message"

        private const val CHANNEL_ID =
            "roomcall_receiver_channel"

        private const val NOTIFICATION_ID = 1001
    }
}