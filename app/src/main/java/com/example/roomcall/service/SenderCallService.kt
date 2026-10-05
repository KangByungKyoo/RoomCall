package com.example.roomcall.service

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.roomcall.MainActivity
import com.example.roomcall.R
import com.example.roomcall.call.CallClient
import com.example.roomcall.call.CallStates
import com.example.roomcall.call.CallStatus

/** Owns Sender audio across screen-off and Activity recreation. Started only by visible UI. */
class SenderCallService : Service() {
    private var client: CallClient? = null
    private var wakeLock: ServiceWakeLock? = null
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "RoomCall 음성통화", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == END) { client?.close(); if (client == null) stopSelf(); return START_NOT_STICKY }
        if (client != null) return START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            CallStates.senderMutable.value = CallStatus(detail = "마이크 권한이 필요합니다.")
            stopSelf(); return START_NOT_STICKY
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val end = PendingIntent.getService(this, 1, Intent(this, SenderCallService::class.java).setAction(END), PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle("RoomCall 음성통화")
            .setContentText("통화 연결 중 / 통화 중").setContentIntent(open).setOngoing(true)
            .addAction(0, "통화 종료", end).build()
        try {
            ServiceCompat.startForeground(this, 1002, notification,
                if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
            wakeLock = ServiceWakeLock(this, "RoomCall:SenderCall").apply { start() }
            client = CallClient(this) { android.os.Handler(mainLooper).post { stopSelf() } }
            client!!.connect(intent?.getStringExtra(IP).orEmpty())
        } catch (e: Exception) {
            CallStates.senderMutable.value = CallStatus(detail = e.message ?: "통화 서비스 시작 실패")
            stopSelf()
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() {
        client?.close(); client = null
        wakeLock?.close(); wakeLock = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val IP = "receiver_ip"
        const val END = "com.example.roomcall.END_CALL"
        private const val CHANNEL = "roomcall_sender_call"
    }
}