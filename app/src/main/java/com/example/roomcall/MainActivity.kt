package com.example.roomcall

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.roomcall.call.CallClient
import com.example.roomcall.call.CallStates
import com.example.roomcall.call.CallStatus
import com.example.roomcall.model.AppMode
import com.example.roomcall.network.NetworkUtils
import com.example.roomcall.network.RoomCallNsdDiscovery
import com.example.roomcall.service.RoomCallReceiverService
import com.example.roomcall.service.SenderCallService
import com.example.roomcall.ui.RoomCallScreen
import com.example.roomcall.ui.theme.RoomCallTheme
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    private var appMode by mutableStateOf(AppMode.SENDER)
    private var localIpAddress by mutableStateOf("IP 확인 중")
    private var receiverIpAddress by mutableStateOf("")
    private var remoteCallStatus by mutableStateOf<CallStatus?>(null)
    private var notice by mutableStateOf<String?>(null)
    private var pendingSenderCall = false
    private var requestedNotificationPermission = false
    private var requestedReceiverMic = false
    private var nsdDiscovery: RoomCallNsdDiscovery? = null
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val microphonePermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            notice = null
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (appMode == AppMode.RECEIVER) startReceiverService()
                else if (pendingSenderCall) startSenderCall()
            }
        } else {
            pendingSenderCall = false
            notice = "음성통화를 사용하려면 마이크 권한을 허용해 주세요."
        }
        requestNotificationPermissionIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.WHITE, android.graphics.Color.WHITE))
        appMode = savedMode()
        localIpAddress = NetworkUtils.getLocalIpAddress(this)
        nsdDiscovery = RoomCallNsdDiscovery(this,
            onReceiverFound = { ip, _ -> runOnUiThread {
                if (appMode == AppMode.SENDER) { receiverIpAddress = ip; notice = null }
            } },
            onReceiverLost = { runOnUiThread {
                receiverIpAddress = ""; remoteCallStatus = null
                notice = "수신기 연결이 해제되었습니다. 기기를 다시 찾아 주세요."
            } })
        if (appMode == AppMode.SENDER) nsdDiscovery?.startDiscovery()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val ip = receiverIpAddress
                    if (appMode == AppMode.SENDER && ip.isNotBlank()) {
                        val status = withContext(Dispatchers.IO) { runCatching { CallClient.query(this@MainActivity, ip) }.getOrNull() }
                        if (appMode == AppMode.SENDER && receiverIpAddress == ip) remoteCallStatus = status
                    } else remoteCallStatus = null
                    localIpAddress = NetworkUtils.getLocalIpAddress(this@MainActivity)
                    delay(2000)
                }
            }
        }
        setContent {
            val senderCall by CallStates.sender.collectAsState()
            val receiverCall by CallStates.receiver.collectAsState()
            RoomCallTheme {
                RoomCallScreen(mode = appMode, localIpAddress = localIpAddress, receiverIpAddress = receiverIpAddress,
                    callStatus = if (appMode == AppMode.SENDER) senderCall else receiverCall,
                    remoteCallStatus = remoteCallStatus, notice = notice,
                    onCall = {
                        notice = null
                        if (hasMicrophonePermission()) startSenderCall()
                        else { pendingSenderCall = true; microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    },
                    onEndCall = { stopSenderCall() },
                    onEnableVoice = {
                        notice = null
                        if (hasMicrophonePermission()) startReceiverService()
                        else microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    },
                    onReceiverIpChange = { receiverIpAddress = it; remoteCallStatus = null; notice = null },
                    onModeChange = { selected ->
                        if (selected != appMode) {
                            stopSenderCall(); pendingSenderCall = false
                            appMode = selected; saveMode(selected); notice = null
                            if (selected == AppMode.RECEIVER) { nsdDiscovery?.stopDiscovery(); prepareReceiver() }
                            else { stopReceiverService(); nsdDiscovery?.startDiscovery() }
                        }
                    },
                    onFindReceiver = {
                        receiverIpAddress = ""; remoteCallStatus = null; notice = null
                        nsdDiscovery?.restartDiscovery()
                    })
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if (appMode == AppMode.RECEIVER) prepareReceiver()
        else if (pendingSenderCall && hasMicrophonePermission()) startSenderCall()
    }
    override fun onDestroy() { nsdDiscovery?.stopDiscovery(); super.onDestroy() }
    private fun hasMicrophonePermission() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun prepareReceiver() {
        startReceiverService()
        if (!hasMicrophonePermission() && !requestedReceiverMic) {
            requestedReceiverMic = true
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else if (hasMicrophonePermission()) requestNotificationPermissionIfNeeded()
    }
    private fun startSenderCall() {
        if (appMode != AppMode.SENDER || receiverIpAddress.isBlank() || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        pendingSenderCall = false
        requestNotificationPermissionIfNeeded()
        ContextCompat.startForegroundService(this, Intent(this, SenderCallService::class.java).putExtra(SenderCallService.IP, receiverIpAddress))
    }
    private fun stopSenderCall() { stopService(Intent(this, SenderCallService::class.java)) }
    private fun startReceiverService() {
        ContextCompat.startForegroundService(this, Intent(this, RoomCallReceiverService::class.java)
            .putExtra(RoomCallReceiverService.EXTRA_ARM_VOICE, hasMicrophonePermission() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)))
    }
    private fun stopReceiverService() { stopService(Intent(this, RoomCallReceiverService::class.java)) }
    private fun requestNotificationPermissionIfNeeded() {
        if (!requestedNotificationPermission && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    private fun savedMode(): AppMode = runCatching {
        AppMode.valueOf(getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).getString(PREFERENCE_MODE, AppMode.SENDER.name).orEmpty())
    }.getOrDefault(AppMode.SENDER)
    private fun saveMode(mode: AppMode) {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit().putString(PREFERENCE_MODE, mode.name).apply()
    }
    companion object {
        private const val PREFERENCES_NAME = "room_call_preferences"
        private const val PREFERENCE_MODE = "app_mode"
    }
}