package com.example.roomcall

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

class MainActivity : ComponentActivity() {

    private var ttsManager: TtsManager? = null
    private var appMode by mutableStateOf(AppMode.SENDER)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ttsManager = TtsManager(this)

        setContent {
            RoomCallTheme {
                RoomCallScreen(
                    mode = appMode,
                    messages = defaultMessages,
                    onModeChange = { selectedMode ->
                        appMode = selectedMode
                    },
                    onSpeak = { message ->
                        ttsManager?.speak(message.speechText)
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        ttsManager?.shutdown()
        ttsManager = null
        super.onDestroy()
    }
}