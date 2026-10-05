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
import com.example.roomcall.call.CallState
import com.example.roomcall.call.CallStatus
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
    callStatus: CallStatus = CallStatus(),
    remoteCallStatus: CallStatus? = null,
    onCall: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onEnableVoice: () -> Unit = {},
    onSend: (RoomMessage) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = if (mode == AppMode.RECEIVER) "RoomCall Receiver" else "RoomCall Sender",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))
        Text("현재 모드: ${mode.label}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = { onModeChange(AppMode.SENDER) },
            enabled = callStatus.state == CallState.IDLE,
            modifier = Modifier.fillMaxWidth()
        ) { Text("송신 모드") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onModeChange(AppMode.RECEIVER) },
            enabled = callStatus.state == CallState.IDLE,
            modifier = Modifier.fillMaxWidth()
        ) { Text("수신 모드") }
        Spacer(Modifier.height(32.dp))

        if (mode == AppMode.SENDER) {
            Text(if (receiverIpAddress.isBlank()) "Receiver 검색 중" else "아이 방 스마트폰 발견")
            Text("Receiver 상태: ${remoteCallStatus?.state?.name ?: "확인 중"}")
            if (remoteCallStatus?.ready == false) Text("Receiver 마이크 대기가 활성화되지 않았습니다.")
            Text("상태: ${callStatus.state.name}")
            callStatus.detail.takeIf { it.isNotBlank() }?.let { Text(it) }
            if (callStatus.state == CallState.IDLE) {
                Button(onClick = onCall, enabled = receiverIpAddress.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("음성통화 연결")
                }
            } else {
                Text(if (callStatus.state == CallState.BUSY) "통화 중" else "통화 연결 중")
                Button(onClick = onEndCall, modifier = Modifier.fillMaxWidth()) { Text("통화 종료") }
            }
            Spacer(Modifier.height(16.dp))
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
            Text(if (callStatus.state == CallState.BUSY) "현재 통화 중" else if (callStatus.state == CallState.CONNECTING) "통화 연결 중" else "수신 대기 중",
                style = MaterialTheme.typography.bodyLarge)
            Text("상태: ${callStatus.state.name}")
            if (!callStatus.ready) {
                Text("메시지 수신 대기 · 음성통화에는 마이크 권한이 필요합니다.")
                OutlinedButton(onClick = onEnableVoice) { Text("음성통화 대기 활성화") }
            }
            callStatus.detail.takeIf { it.isNotBlank() }?.let { Text(it) }
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
