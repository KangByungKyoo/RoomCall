package com.example.roomcall.service

import android.Manifest
import android.content.pm.PackageManager
import android.app.PendingIntent
import com.example.roomcall.MainActivity
import com.example.roomcall.call.CallState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.update
import android.os.Handler
import com.example.roomcall.call.CallServer
import com.example.roomcall.call.CallStates
import com.example.roomcall.call.CallStatus
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
    @Volatile private var voiceReady = false
    private var callServer: CallServer? = null
    private var wakeLock: ServiceWakeLock? = null
    private val notificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var multicastLock: WifiManager.MulticastLock? = null
    private val mainHandler = Handler(android.os.Looper.getMainLooper())
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
        wakeLock = ServiceWakeLock(this, "RoomCall:ReceiverWait").apply { start() }
        callServer = CallServer(this) { voiceReady }
        callServer?.start()

        tcpServer = TcpServer { message ->
            if (message !in allowedMessages) return@TcpServer
            mainHandler.post { voicePlayer.playMessage(message) }
            sendBroadcast(Intent(ACTION_MESSAGE_RECEIVED).apply {
                setPackage(packageName)
                putExtra(EXTRA_MESSAGE, message)
            })
        }
        tcpServer?.start()
        nsdRegistrar = RoomCallNsdRegistrar(applicationContext)
        nsdRegistrar?.register()
        notificationScope.launch {
            CallStates.receiver.collect {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, receiverNotification(voiceReady))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promotion happens only on an explicit command from the visible Activity.
        // A sticky system restart retains messages, but must be re-armed from the UI for microphone access.
        if (!voiceReady && intent?.getBooleanExtra(EXTRA_ARM_VOICE, false) == true &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                startAsForegroundService(microphone = true)
                voiceReady = true
                CallStates.receiverMutable.update { it.copy(ready = true, detail = "") }
            } catch (e: Exception) {
                voiceReady = false
                CallStates.receiverMutable.value = CallStatus(detail = "수신 화면을 다시 열어 마이크 대기를 시작하세요.")
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        notificationScope.cancel()
        voiceReady = false
        callServer?.stop(); callServer = null
        CallStates.receiverMutable.value = CallStatus(detail = "수신 서비스 중지")
        mainHandler.removeCallbacksAndMessages(null)
        wakeLock?.close(); wakeLock = null
        nsdRegistrar?.unregister()
        nsdRegistrar = null
        tcpServer?.stop()
        tcpServer = null
        releaseWifiLock()
        voicePlayer.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun receiverNotification(microphone: Boolean) = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (CallStates.receiver.value.state == CallState.BUSY) "RoomCall 현재 통화 중" else "RoomCall 수신 대기 중")
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setContentText(if (microphone) "음성통화 자동 수신 대기 · 대기 중에는 녹음하지 않습니다." else "메시지 수신 대기 · 음성통화는 수신 화면에서 활성화하세요.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

    private fun startAsForegroundService(microphone: Boolean = false) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            receiverNotification(microphone),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
                    (if (microphone && Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
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
        multicastLock = applicationContext.getSystemService(WifiManager::class.java)
            .createMulticastLock("RoomCall:ReceiverNsd").apply { setReferenceCounted(false); acquire() }
        wifiLock = applicationContext.getSystemService(WifiManager::class.java)
            .createWifiLock("RoomCall:ReceiverWifiLock")
            .apply {
                setReferenceCounted(false)
                acquire()
            }
    }

    private fun releaseWifiLock() {
        multicastLock?.let { if (it.isHeld) it.release() }; multicastLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    companion object {
        const val EXTRA_ARM_VOICE = "arm_voice"
        const val ACTION_MESSAGE_RECEIVED = "com.example.roomcall.ACTION_MESSAGE_RECEIVED"
        const val EXTRA_MESSAGE = "extra_message"
        private const val CHANNEL_ID = "roomcall_receiver_channel"
        private const val NOTIFICATION_ID = 1001
    }
}
