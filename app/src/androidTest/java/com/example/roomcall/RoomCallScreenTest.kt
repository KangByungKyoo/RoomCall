package com.example.roomcall

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.roomcall.call.CallState
import com.example.roomcall.call.CallStatus
import com.example.roomcall.model.AppMode
import com.example.roomcall.ui.RoomCallScreen
import com.example.roomcall.ui.theme.RoomCallTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RoomCallScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = File(context.getExternalFilesDir("ui-preview"), name)
        destination.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun senderSupportsCallEndRefreshAndHasNoMessageButtons() {
        val status = mutableStateOf(CallStatus(detail = "통화가 종료되었습니다."))
        var calls = 0; var ends = 0; var searches = 0
        compose.setContent { RoomCallTheme {
            RoomCallScreen(AppMode.SENDER, "192.168.45.10", "192.168.45.173", {}, {},
                callStatus = status.value, remoteCallStatus = CallStatus(ready = true),
                onCall = { calls++ }, onEndCall = { ends++ }, onFindReceiver = { searches++ })
        } }
        compose.onNodeWithText("아이 방 스마트폰 발견").assertIsDisplayed()
        compose.onNodeWithText("응, 괜찮아").assertDoesNotExist()
        compose.onNodeWithText("주희야, 조용히 좀 해").assertDoesNotExist()
        compose.onNodeWithText("주희야, 밥 먹자").assertDoesNotExist()
        capture("roomcall-sender-preview.png")
        compose.onNodeWithText("음성통화 연결").performClick()
        assertEquals(1, calls)
        compose.runOnIdle { status.value = CallStatus(CallState.BUSY, "통화 중") }
        compose.onNodeWithText("통화 종료").assertIsDisplayed().performClick()
        assertEquals(1, ends)
        compose.onNodeWithText("기기 다시 찾기").assertIsNotEnabled()
        compose.runOnIdle { status.value = CallStatus() }
        compose.onNodeWithText("기기 다시 찾기").performClick()
        assertEquals(1, searches)
    }
    @Test fun ipCardAllowsManualEntryAndModeMenuSwitchesRole() {
        val mode = mutableStateOf(AppMode.SENDER)
        val ip = mutableStateOf("192.168.45.173")
        compose.setContent { RoomCallTheme {
            RoomCallScreen(mode.value, "192.168.45.10", ip.value,
                onModeChange = { mode.value = it }, onReceiverIpChange = { ip.value = it }, callStatus = CallStatus(ready = true))
        } }
        compose.onNodeWithContentDescription("수신기 IP 주소 입력").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("192.168.45.99")
        compose.onNodeWithText("적용").performClick()
        compose.onNodeWithText("192.168.45.99").assertIsDisplayed()
        compose.onNodeWithContentDescription("모드 변경").performClick()
        compose.onNodeWithText("수신 모드").performClick()
        compose.onNodeWithText("음성통화 대기 중").assertIsDisplayed()
    }
    @Test fun receiverShowsAutomaticWaitingWithoutMessageUi() {
        compose.setContent { RoomCallTheme {
            RoomCallScreen(AppMode.RECEIVER, "192.168.45.173", "", {}, {}, callStatus = CallStatus(ready = true))
        } }
        compose.onNodeWithText("수신 대기 중").assertIsDisplayed()
        compose.onNodeWithText("Receiver 상태: IDLE").assertIsDisplayed()
        compose.onNodeWithText("받은 메시지").assertDoesNotExist()
        compose.onNodeWithText("음성통화 대기 중").assertIsNotEnabled()
        capture("roomcall-receiver-preview.png")
    }
}