package com.example.roomcall.call

import android.content.Context
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class CallClient(private val context: Context, private val onFinished: () -> Unit) {
    private val cancelled = AtomicBoolean(false)
    @Volatile private var socket: Socket? = null
    @Volatile private var session: CallSession? = null
    fun connect(ip: String) {
        CallStates.senderMutable.value = CallStatus(CallState.CONNECTING, "통화 연결 중")
        thread(name = "RoomCall-CallClient") {
            var transferred = false
            try {
                val lan = LanNetwork.find(context) ?: error("같은 Wi-Fi에 연결하세요.")
                val peer = InetAddress.getByName(ip.trim())
                val control = lan.socket(peer, CallProtocol.PORT)
                socket = control
                check(!cancelled.get()) { "통화가 취소되었습니다." }
                val token = SecureRandom().nextLong()
                val call = CallSession(context, control, lan, token) { reason ->
                    CallStates.senderMutable.value = CallStatus(detail = reason)
                    onFinished()
                }
                session = call
                transferred = true
                if (cancelled.get()) { call.close(); return@thread }
                try {
                    CallProtocol.write(control, "${CallProtocol.VERSION} CALL ${call.audioPort} $token")
                    val response = CallProtocol.read(control)
                    when (response) {
                        "BUSY" -> error("현재 다른 사용자가 통화 중입니다.")
                        "NOT_READY" -> error("Receiver에서 마이크 권한을 허용하고 수신 모드를 다시 열어 주세요.")
                    }
                    val parts = response.split(' ')
                    require(parts.size == 2 && parts[0] == "ACCEPT") { "Receiver가 음성통화를 지원하지 않습니다." }
                    call.startAudio(parts[1].toInt())
                    CallProtocol.write(control, "READY")
                    check(CallProtocol.read(control) == "ACTIVE")
                    if (cancelled.get()) { call.close(); return@thread }
                    call.activate { CallStates.senderMutable.value = CallStatus(CallState.BUSY, "통화 중") }
                } catch (e: Exception) { call.close(e.message ?: "통화 연결 실패") }
            } catch (e: Exception) {
                if (!transferred) {
                    runCatching { socket?.close() }
                    CallStates.senderMutable.value = CallStatus(detail = e.message ?: "통화 연결 실패")
                    onFinished()
                }
            }
        }
    }
    fun close() {
        cancelled.set(true)
        session?.close()
        runCatching { socket?.close() }
    }
    companion object {
        /** Called from a worker, never the UI thread. */
        fun query(context: Context, ip: String): CallStatus {
            val lan = LanNetwork.find(context) ?: error("Wi-Fi 연결 필요")
            return lan.socket(InetAddress.getByName(ip.trim()), CallProtocol.PORT).use {
                it.soTimeout = 1500
                CallProtocol.write(it, "${CallProtocol.VERSION} STATUS")
                val parts = CallProtocol.read(it).split(' ')
                require(parts.size == 3 && parts[0] == "STATE")
                CallStatus(CallState.valueOf(parts[1]), ready = parts[2] == "READY")
            }
        }
    }
}