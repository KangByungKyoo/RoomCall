package com.example.roomcall

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.example.roomcall.model.AppMode
import com.example.roomcall.model.defaultMessages
import com.example.roomcall.network.NetworkConstants
import com.example.roomcall.network.NetworkUtils
import com.example.roomcall.network.RoomCallNsdDiscovery
import com.example.roomcall.network.TcpClient
import com.example.roomcall.service.RoomCallReceiverService
import com.example.roomcall.ui.RoomCallScreen
import com.example.roomcall.ui.theme.RoomCallTheme

class MainActivity : ComponentActivity() {
    private var appMode by mutableStateOf(AppMode.SENDER)
    private var localIpAddress by mutableStateOf("IP 확인 중")
    private var receiverIpAddress by mutableStateOf("")
    private var receiverPort = NetworkConstants.PORT
    private var receivedMessage by mutableStateOf("아직 받은 메시지가 없습니다.")
    private var sendStatus by mutableStateOf<String?>(null)
    private var nsdDiscovery: RoomCallNsdDiscovery? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Log.w("RoomCall", "Notification permission was denied")
    }

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != RoomCallReceiverService.ACTION_MESSAGE_RECEIVED) return
            receivedMessage = intent.getStringExtra(
                RoomCallReceiverService.EXTRA_MESSAGE
            ) ?: return
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appMode = savedMode()
        localIpAddress = NetworkUtils.getLocalIpAddress(this)

        nsdDiscovery = RoomCallNsdDiscovery(
            context = this,
            onReceiverFound = { ipAddress, port ->
                runOnUiThread {
                    receiverIpAddress = ipAddress
                    receiverPort = port
                    sendStatus = null
                    Log.d("RoomCall-NSD", "Receiver found: $ipAddress:$port")
                }
            },
            onReceiverLost = {
                runOnUiThread {
                    receiverIpAddress = ""
                    receiverPort = NetworkConstants.PORT
                    sendStatus = "수신기 연결이 해제되었습니다."
                    Log.d("RoomCall-NSD", "Receiver lost")
                }
            }
        )

        if (appMode == AppMode.SENDER) {
            nsdDiscovery?.startDiscovery()
        } else {
            requestNotificationPermissionIfNeeded()
            startReceiverService()
        }

        setContent {
            RoomCallTheme {
                RoomCallScreen(
                    mode = appMode,
                    localIpAddress = localIpAddress,
                    receiverIpAddress = receiverIpAddress,
                    receivedMessage = receivedMessage,
                    sendStatus = sendStatus,
                    messages = defaultMessages,
                    onReceiverIpChange = { newIpAddress ->
                        receiverIpAddress = newIpAddress
                        receiverPort = NetworkConstants.PORT
                        sendStatus = null
                    },
                    onModeChange = { selectedMode ->
                        if (selectedMode != appMode) {
                            appMode = selectedMode
                            saveMode(selectedMode)
                            sendStatus = null
                            if (selectedMode == AppMode.RECEIVER) {
                                nsdDiscovery?.stopDiscovery()
                                requestNotificationPermissionIfNeeded()
                                startReceiverService()
                            } else {
                                stopReceiverService()
                                nsdDiscovery?.startDiscovery()
                            }
                        }
                    },
                    onSend = { message ->
                        sendStatus = "전송 중"
                        TcpClient.send(
                            ipAddress = receiverIpAddress,
                            port = receiverPort,
                            message = message.speechText,
                            onResult = { success ->
                                runOnUiThread {
                                    sendStatus = if (success) {
                                        "전송했습니다."
                                    } else {
                                        "전송하지 못했습니다. IP와 네트워크를 확인하세요."
                                    }
                                }
                            }
                        )
                    }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            messageReceiver,
            IntentFilter(RoomCallReceiverService.ACTION_MESSAGE_RECEIVED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(messageReceiver)
    }

    override fun onDestroy() {
        nsdDiscovery?.stopDiscovery()
        super.onDestroy()
    }

    private fun startReceiverService() = ContextCompat.startForegroundService(
        this,
        Intent(this, RoomCallReceiverService::class.java)
    )

    private fun stopReceiverService() {
        stopService(Intent(this, RoomCallReceiverService::class.java))
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun savedMode(): AppMode {
        val value = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getString(PREFERENCE_MODE, AppMode.SENDER.name)
        return runCatching { AppMode.valueOf(value.orEmpty()) }
            .getOrDefault(AppMode.SENDER)
    }

    private fun saveMode(mode: AppMode) {
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .edit().putString(PREFERENCE_MODE, mode.name).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "room_call_preferences"
        private const val PREFERENCE_MODE = "app_mode"
    }
}
