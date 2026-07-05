package com.example.roomcall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.roomcall.model.defaultMessages
import com.example.roomcall.tts.TtsManager
import com.example.roomcall.ui.RoomCallScreen
import com.example.roomcall.ui.theme.RoomCallTheme

class MainActivity : ComponentActivity() {

    private var ttsManager: TtsManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ttsManager = TtsManager(this)

        setContent {
            RoomCallTheme {
                RoomCallScreen(
                    messages = defaultMessages,
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