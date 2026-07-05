package com.example.roomcall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.roomcall.model.AppMode
import com.example.roomcall.model.RoomMessage
import com.example.roomcall.model.defaultMessages
import com.example.roomcall.ui.theme.RoomCallTheme

@Composable
fun RoomCallScreen(
    mode: AppMode,
    messages: List<RoomMessage>,
    onModeChange: (AppMode) -> Unit,
    onSpeak: (RoomMessage) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "RoomCall",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "현재 모드: ${mode.label}",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = { onModeChange(AppMode.SENDER) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("송신 모드")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { onModeChange(AppMode.RECEIVER) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("수신 모드")
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (mode == AppMode.SENDER) {
            messages.forEach { message ->
                Button(
                    onClick = { onSpeak(message) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(text = message.title)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            Text(
                text = "수신 대기 중입니다.",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RoomCallScreenPreview() {
    RoomCallTheme {
        RoomCallScreen(
            mode = AppMode.SENDER,
            messages = defaultMessages,
            onModeChange = {},
            onSpeak = {}
        )
    }
}