package com.example.roomcall.call

import android.content.Context
import com.example.roomcall.audio.DuplexAudio
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Socket close cancels blocked control reads. Slot is freed only after audio resources are released. */
class CallSession(context: Context, private val socket: Socket, lan: LanNetwork, token: Long,
                  private val onClosed: (String) -> Unit) {
    private val lifecycle = Any()
    private val closed = AtomicBoolean(false)
    private val audio = DuplexAudio(context, lan, token)
    val audioPort get() = audio.port
    private var heartbeat: Thread? = null
    fun startAudio(peerPort: Int) = synchronized(lifecycle) {
        check(!closed.get()) { "통화가 취소되었습니다." }
        audio.start(socket.inetAddress, peerPort) { close(it) }
        check(!closed.get()) { "오디오를 시작하지 못했습니다." }
    }
    fun activate(onActive: () -> Unit) = synchronized(lifecycle) {
        check(!closed.get()) { "통화가 종료되었습니다." }
        onActive()
        supervise()
    }
    private fun supervise() {
        if (closed.get()) return
        heartbeat = thread(name = "RoomCall-Heartbeat") {
            try {
                while (!closed.get()) {
                    CallProtocol.write(socket, "PING")
                    Thread.sleep(1000)
                }
            } catch (_: InterruptedException) {
            } catch (_: Exception) { close("통화 연결이 끊겼습니다.") }
        }
        thread(name = "RoomCall-Control") {
            try {
                while (!closed.get()) {
                    when (CallProtocol.read(socket)) {
                        "PING" -> Unit
                        "END" -> { close("통화가 종료되었습니다."); return@thread }
                        else -> error("Invalid call command")
                    }
                }
            } catch (_: Exception) { close("통화가 종료되었거나 연결이 끊겼습니다.") }
        }
    }
    fun close(reason: String = "통화가 종료되었습니다.") {
        if (!closed.compareAndSet(false, true)) return
        runCatching { socket.close() }
        heartbeat?.interrupt()
        thread(name = "RoomCall-Cleanup") {
            try { synchronized(lifecycle) { audio.close() } }
            finally { onClosed(reason) }
        }
    }
}