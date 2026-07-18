package com.example.roomcall
import com.example.roomcall.network.TcpClient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.roomcall.model.AppMode
import com.example.roomcall.model.defaultMessages
import com.example.roomcall.tts.TtsManager
import com.example.roomcall.ui.RoomCallScreen
import com.example.roomcall.ui.theme.RoomCallTheme
import com.example.roomcall.network.TcpServer
import com.example.roomcall.network.NetworkUtils


class MainActivity : ComponentActivity() {

    private var ttsManager: TtsManager? = null
    private var tcpServer: TcpServer? = null
    private var appMode by mutableStateOf(AppMode.SENDER)
    private var localIpAddress by mutableStateOf("IP 확인 중")
    private var receiverIpAddress by mutableStateOf("")
    private var receivedMessage by mutableStateOf("아직 받은 메시지가 없습니다.")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ttsManager = TtsManager(this)
        tcpServer = TcpServer { message ->
            runOnUiThread {
                receivedMessage = message
            }
        }


        localIpAddress = NetworkUtils.getLocalIpAddress()


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
                            tcpServer?.start()
                        } else {
                            tcpServer?.stop()
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
        tcpServer?.stop()
        tcpServer = null

        ttsManager?.shutdown()
        ttsManager = null

        super.onDestroy()
    }
}