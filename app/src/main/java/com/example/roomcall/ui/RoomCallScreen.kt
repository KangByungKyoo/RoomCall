package com.example.roomcall.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.roomcall.R
import com.example.roomcall.call.CallState
import com.example.roomcall.call.CallStatus
import com.example.roomcall.model.AppMode
import com.example.roomcall.ui.theme.RoomCallTheme

private val Blue = Color(0xFF0866FF)
private val Ink = Color(0xFF071331)
private val Muted = Color(0xFF647084)
private val CanvasColor = Color(0xFFFBFDFF)

@Composable
fun RoomCallScreen(
    mode: AppMode,
    localIpAddress: String,
    receiverIpAddress: String,
    onModeChange: (AppMode) -> Unit,
    onReceiverIpChange: (String) -> Unit,
    callStatus: CallStatus = CallStatus(),
    remoteCallStatus: CallStatus? = null,
    notice: String? = null,
    onCall: () -> Unit = {},
    onEndCall: () -> Unit = {},
    onEnableVoice: () -> Unit = {},
    onFindReceiver: () -> Unit = {}
) {
    var showModes by remember { mutableStateOf(false) }
    var editIp by remember { mutableStateOf(false) }
    var ipDraft by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val sender = mode == AppMode.SENDER
    val active = callStatus.state != CallState.IDLE
    val found = receiverIpAddress.isNotBlank()
    val ip = if (sender) receiverIpAddress else localIpAddress
    val shownState = if (sender) remoteCallStatus?.state else callStatus.state
    val statusColor = when {
        shownState == CallState.BUSY -> Color(0xFFFFA32B)
        shownState == CallState.CONNECTING -> Blue
        shownState == CallState.IDLE && (sender || callStatus.ready) -> Color(0xFF23CF46)
        else -> Color(0xFF9BA8BA)
    }
    val heading = when {
        callStatus.state == CallState.BUSY -> "현재 통화 중"
        callStatus.state == CallState.CONNECTING -> "통화 연결 중"
        !sender -> "수신 대기 중"
        found -> "아이 방 스마트폰 발견"
        else -> "아이 방 스마트폰 찾는 중"
    }
    val detail = notice?.takeIf { it.isNotBlank() }
        ?: callStatus.detail.takeIf { it.isNotBlank() }
        ?: when {
            active -> "스피커로 편하게 대화하세요."
            !sender && !callStatus.ready -> "마이크 권한을 허용하고 수신 대기를 켜 주세요."
            !sender -> "가족이 연결하면 자동으로 통화가 시작돼요."
            !found -> "같은 Wi-Fi의 수신 모드 스마트폰을 찾고 있어요."
            remoteCallStatus?.ready == false -> "아이 방 스마트폰에서 수신 대기를 켜 주세요."
            else -> "연결 버튼을 누르면 바로 대화할 수 있어요."
        }

    BoxWithConstraints(Modifier.fillMaxSize().background(CanvasColor)) {
        val compact = maxWidth < 380.dp
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(top = 30.dp, bottom = 32.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = Blue)) { append("RoomCall") }
                        withStyle(SpanStyle(color = Ink)) { append(if (sender) " Sender" else " Receiver") }
                    },
                    modifier = Modifier.weight(1f), fontSize = if (compact) 25.sp else 29.sp,
                    fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.1).sp, maxLines = 1
                )
                Box {
                    Surface(
                        shape = CircleShape, color = Color(0xFFE5F0FF),
                        modifier = Modifier.clip(CircleShape).clickable(enabled = !active) { showModes = true }
                            .semantics { contentDescription = "모드 변경" }
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(if (sender) R.drawable.ic_intercom_send else R.drawable.ic_intercom_phone), null,
                                Modifier.size(16.dp), tint = Blue)
                            Spacer(Modifier.width(5.dp))
                            Text(mode.label, color = Blue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    DropdownMenu(expanded = showModes, onDismissRequest = { showModes = false }) {
                        AppMode.entries.forEach { choice ->
                            DropdownMenuItem(text = { Text(choice.label) }, onClick = { showModes = false; onModeChange(choice) })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(if (sender) "같은 Wi-Fi의 우리 아이 방으로 바로 통화해요." else "아이 방에서 가족의 통화를 자동으로 받아요.",
                color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(28.dp))

            Surface(shape = RoundedCornerShape(22.dp), shadowElevation = 3.dp,
                color = Color(0xFFF0F7FF), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(if (compact) 76.dp else 84.dp).background(Color(0xFFDCEEFF), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_intercom_phone), null, Modifier.size(48.dp), tint = Blue)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(heading, color = Ink, fontSize = if (compact) 17.sp else 19.sp,
                            fontWeight = FontWeight.Bold, lineHeight = 25.sp)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).background(statusColor, CircleShape))
                            Spacer(Modifier.width(7.dp))
                            Text("Receiver 상태: ${shownState?.name ?: "확인 중"}", color = Muted, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = Color(0xFFDCE5EF))
                        Spacer(Modifier.height(10.dp))
                        Text(detail, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 3.dp, color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFEDF1F6)), modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)).clickable(enabled = sender && !active) { ipDraft = receiverIpAddress; editIp = true }
                    .semantics { if (sender) contentDescription = "수신기 IP 주소 입력" }) {
                Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_intercom_link), null, Modifier.size(19.dp), tint = Muted)
                            Spacer(Modifier.width(10.dp))
                            Text(if (sender) "수신기 IP 주소" else "이 스마트폰 IP 주소", color = Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(ip.ifBlank { "자동 검색 중" }, color = if (ip.isBlank()) Muted else Ink,
                            fontSize = if (ip.isBlank()) 20.sp else 25.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
                        if (copied) Text("IP 주소를 복사했어요.", color = Blue, fontSize = 11.sp)
                    }
                    IconButton(onClick = { clipboard.setText(AnnotatedString(ip)); copied = true }, enabled = ip.isNotBlank(),
                        modifier = Modifier.size(42.dp).background(Color(0xFFF0F3F8), CircleShape)) {
                        Icon(painterResource(R.drawable.ic_intercom_copy), "IP 주소 복사", Modifier.size(22.dp), tint = Muted)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            val primaryEnabled = if (sender) (found || active) else (!callStatus.ready && !active)
            val primaryLabel = when {
                sender && active -> "통화 종료"
                sender -> "음성통화 연결"
                callStatus.state == CallState.BUSY -> "현재 통화 중"
                callStatus.state == CallState.CONNECTING -> "통화 연결 중"
                callStatus.ready -> "음성통화 대기 중"
                else -> "음성통화 대기 활성화"
            }
            val primaryBrush = when {
                sender && active -> Brush.horizontalGradient(listOf(Color(0xFFEE5363), Color(0xFFD7354D)))
                primaryEnabled || !sender -> Brush.horizontalGradient(listOf(Color(0xFF3288FF), Color(0xFF2675FA)))
                else -> Brush.horizontalGradient(listOf(Color(0xFF92BFFF), Color(0xFF81AFF0)))
            }
            Button(onClick = { if (sender) { if (active) onEndCall() else onCall() } else onEnableVoice() },
                enabled = primaryEnabled, shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
                    contentColor = Color.White, disabledContentColor = Color.White),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.fillMaxWidth().height(76.dp).clip(CircleShape).background(primaryBrush)) {
                Icon(painterResource(R.drawable.ic_intercom_call), null, Modifier.size(34.dp), tint = Color.White)
                Spacer(Modifier.width(20.dp))
                Box(Modifier.width(1.dp).height(26.dp).background(Color.White.copy(alpha = 0.35f)))
                Spacer(Modifier.width(20.dp))
                Text(primaryLabel, fontSize = if (compact) 21.sp else 23.sp, fontWeight = FontWeight.Bold)
            }
            if (sender) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onFindReceiver, enabled = !active, shape = CircleShape,
                    border = BorderStroke(1.dp, Color(0xFFD3DDE9)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue),
                    modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Icon(painterResource(R.drawable.ic_intercom_refresh), null, Modifier.size(25.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("기기 다시 찾기", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (editIp) {
        AlertDialog(onDismissRequest = { editIp = false }, title = { Text("수신기 IP 주소") },
            text = {
                OutlinedTextField(value = ipDraft, onValueChange = { ipDraft = it }, singleLine = true,
                    label = { Text("IP 주소 직접 입력") }, placeholder = { Text("192.168.0.10") })
            },
            confirmButton = { TextButton(onClick = { onReceiverIpChange(ipDraft.trim()); copied = false; editIp = false }) { Text("적용") } },
            dismissButton = { TextButton(onClick = { editIp = false }) { Text("취소") } })
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 740)
@Composable
fun RoomCallScreenPreview() {
    RoomCallTheme {
        RoomCallScreen(mode = AppMode.SENDER, localIpAddress = "192.168.45.10", receiverIpAddress = "192.168.45.173",
            onModeChange = {}, onReceiverIpChange = {}, callStatus = CallStatus(detail = "통화가 종료되었습니다."),
            remoteCallStatus = CallStatus(ready = true))
    }
}