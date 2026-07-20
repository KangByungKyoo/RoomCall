package com.example.roomcall
import com.example.roomcall.network.TcpClient

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.example.roomcall.service.RoomCallReceiverService

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import com.example.roomcall.model.AppMode
import com.example.roomcall.model.defaultMessages

import com.example.roomcall.ui.RoomCallScreen
import com.example.roomcall.ui.theme.RoomCallTheme
import com.example.roomcall.network.TcpServer
import com.example.roomcall.network.NetworkUtils
import com.example.roomcall.network.RoomCallNsdDiscovery
import android.util.Log
import android.widget.Toast
import com.example.roomcall.R
import com.example.roomcall.audio.VoicePlayer


class MainActivity : ComponentActivity() {


    private var tcpServer: TcpServer? = null
    private var appMode by mutableStateOf(AppMode.SENDER)
    private var localIpAddress by mutableStateOf("IP 확인 중")
    private var receiverIpAddress by mutableStateOf("")
    private var receivedMessage by mutableStateOf("아직 받은 메시지가 없습니다.")
    private var nsdDiscovery: RoomCallNsdDiscovery? = null
    private lateinit var voicePlayer: VoicePlayer


    private val messageReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {

            if (intent?.action !=
                RoomCallReceiverService.ACTION_MESSAGE_RECEIVED
            ) {
                return
            }

            val message = intent.getStringExtra(
                RoomCallReceiverService.EXTRA_MESSAGE
            ) ?: return

            receivedMessage = message
            voicePlayer.playMessage(message)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voicePlayer = VoicePlayer(this)

        enableEdgeToEdge()


        tcpServer = TcpServer { message ->
            runOnUiThread {
                receivedMessage = message
                voicePlayer.playMessage(message)
            }
        }


        localIpAddress = NetworkUtils.getLocalIpAddress()

        nsdDiscovery = RoomCallNsdDiscovery(
            context = this,
            onReceiverFound = { ipAddress, port ->

                runOnUiThread {

                    receiverIpAddress = ipAddress

                    Log.d(
                        "RoomCall-NSD",
                        "Receiver found : $ipAddress:$port"
                    )
                }
            },
            onReceiverLost = {

                runOnUiThread {

                    Log.d(
                        "RoomCall-NSD",
                        "Receiver lost"
                    )
                }
            }
        )

        if (appMode == AppMode.SENDER) {

            Toast.makeText(
                this,
                "Start Discovery",
                Toast.LENGTH_LONG
            ).show()

            nsdDiscovery?.startDiscovery()
        }

        setContent {
            RoomCallTheme {
                RoomCallScreen(
                    mode = appMode,
                    localIpAddress = localIpAddress,
                    receiverIpAddress = receiverIpAddress,
                    receivedMessage = receivedMessage,
                    messages = defaultMessages,
                    onReceiverIpChange = { newIpAddress ->
                        receiverIpAddress = newIpAddress
                    },
                    onModeChange = { selectedMode ->
                        appMode = selectedMode

                        if (selectedMode == AppMode.RECEIVER) {

                            nsdDiscovery?.stopDiscovery()

                            val intent = Intent(
                                this@MainActivity,
                                RoomCallReceiverService::class.java
                            )

                            ContextCompat.startForegroundService(
                                this@MainActivity,
                                intent
                            )

                        } else {

                            val intent = Intent(
                                this@MainActivity,
                                RoomCallReceiverService::class.java
                            )

                            stopService(intent)

                            nsdDiscovery?.startDiscovery()
                        }
                    },
                    onSend = { message ->
                        TcpClient.send(
                            ipAddress = receiverIpAddress,
                            message = message.speechText
                        )
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        nsdDiscovery?.stopDiscovery()

        tcpServer?.stop()
        tcpServer = null

//        ttsManager?.shutdown()
//        ttsManager = null

        voicePlayer.release()

        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()

        val filter = IntentFilter(
            RoomCallReceiverService.ACTION_MESSAGE_RECEIVED
        )

        ContextCompat.registerReceiver(
            this,
            messageReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(messageReceiver)
    }
}