package com.example.roomcall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
    localIpAddress: String,
    receiverIpAddress: String,
    receivedMessage: String,
    sendStatus: String?,
    messages: List<RoomMessage>,
    onModeChange: (AppMode) -> Unit,
    onReceiverIpChange: (String) -> Unit,
    onSend: (RoomMessage) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "RoomCall",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))
        Text("현재 모드: ${mode.label}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = { onModeChange(AppMode.SENDER) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("송신 모드") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onModeChange(AppMode.RECEIVER) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("수신 모드") }
        Spacer(Modifier.height(32.dp))

        if (mode == AppMode.SENDER) {
            OutlinedTextField(
                value = receiverIpAddress,
                onValueChange = onReceiverIpChange,
                label = { Text("수신기 IP 주소") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            sendStatus?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(16.dp))
            messages.forEach { message ->
                Button(
                    onClick = { onSend(message) },
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text(message.title) }
                Spacer(Modifier.height(16.dp))
            }
        } else {
            Text("수신 대기 중입니다.", style = MaterialTheme.typography.bodyLarge)
            Text("수신기 IP: $localIpAddress", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text("받은 메시지", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(receivedMessage, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RoomCallScreenPreview() {
    RoomCallTheme {
        RoomCallScreen(
            mode = AppMode.SENDER,
            localIpAddress = "192.168.0.15",
            receiverIpAddress = "",
            receivedMessage = "아직 받은 메시지가 없습니다.",
            sendStatus = null,
            messages = defaultMessages,
            onModeChange = {},
            onReceiverIpChange = {},
            onSend = {}
        )
    }
}
