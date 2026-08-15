package com.example.roomcall.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.roomcall.R
import com.example.roomcall.audio.VoicePlayer
import com.example.roomcall.model.defaultMessages
import com.example.roomcall.network.RoomCallNsdRegistrar
import com.example.roomcall.network.TcpServer

class RoomCallReceiverService : Service() {
    private var tcpServer: TcpServer? = null
    private var nsdRegistrar: RoomCallNsdRegistrar? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private lateinit var voicePlayer: VoicePlayer
    private val allowedMessages = defaultMessages.map { it.speechText }.toSet()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForegroundService()
        acquireWifiLock()
        voicePlayer = VoicePlayer(this)

        tcpServer = TcpServer { message ->
            if (message !in allowedMessages) return@TcpServer
            voicePlayer.playMessage(message)
            sendBroadcast(Intent(ACTION_MESSAGE_RECEIVED).apply {
                setPackage(packageName)
                putExtra(EXTRA_MESSAGE, message)
            })
        }
        tcpServer?.start()
        nsdRegistrar = RoomCallNsdRegistrar(applicationContext)
        nsdRegistrar?.register()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        nsdRegistrar?.unregister()
        nsdRegistrar = null
        tcpServer?.stop()
        tcpServer = null
        releaseWifiLock()
        voicePlayer.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
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
            } else 0
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
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireWifiLock() {
        if (wifiLock?.isHeld == true) return
        wifiLock = applicationContext.getSystemService(WifiManager::class.java)
            .createWifiLock("RoomCall:ReceiverWifiLock")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWifiLock() {
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    companion object {
        const val ACTION_MESSAGE_RECEIVED = "com.example.roomcall.ACTION_MESSAGE_RECEIVED"
        const val EXTRA_MESSAGE = "extra_message"
        private const val CHANNEL_ID = "roomcall_receiver_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
