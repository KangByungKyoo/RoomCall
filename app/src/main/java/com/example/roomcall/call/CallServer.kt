package com.example.roomcall.call

import android.content.Context
import android.util.Log
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class CallServer(private val context: Context, private val ready: () -> Boolean) {
    private val lock = Any()
    @Volatile private var running = false
    @Volatile private var listener: ServerSocket? = null
    private val gate = SingleCallerGate<Socket>()
    private val owner: Socket? get() = gate.owner
    private var session: CallSession? = null
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val workers = ThreadPoolExecutor(4, 4, 0L, TimeUnit.SECONDS, ArrayBlockingQueue(8))
    fun start() {
        running = true
        thread(name = "RoomCall-CallServer") {
            while (running) {
                try {
                    val lan = LanNetwork.find(context)
                    if (lan == null) { Thread.sleep(1000); continue }
                    ServerSocket().use { server ->
                        server.reuseAddress = true
                        server.bind(InetSocketAddress(lan.address, CallProtocol.PORT))
                        server.soTimeout = 1000
                        listener = server
                        while (running && LanNetwork.find(context) == lan) {
                            val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
                            socket.soTimeout = CallProtocol.TIMEOUT_MS
                            socket.tcpNoDelay = true
                            clients.add(socket)
                            try { workers.execute { handle(socket, lan) } }
                            catch (_: java.util.concurrent.RejectedExecutionException) {
                                clients.remove(socket); socket.close()
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (running) { Log.w("RoomCall", "Voice listener retry", e); Thread.sleep(1000) }
                } finally {
                    listener = null
                    synchronized(lock) {
                        session?.close("Wi-Fi 연결이 변경되었습니다.")
                        owner?.let { runCatching { it.close() } }
                    }
                }
            }
        }
    }
    private fun handle(socket: Socket, lan: LanNetwork) {
        var transferred = false
        try {
            if (!lan.contains(socket.inetAddress)) return
            val parts = CallProtocol.read(socket).split(' ')
            if (parts == listOf(CallProtocol.VERSION, "STATUS")) {
                synchronized(lock) {
                    CallProtocol.write(socket, "STATE ${CallStates.receiver.value.state.name} ${if (ready()) "READY" else "NOT_READY"}")
                }
                return
            }
            require(parts.size == 4 && parts[0] == CallProtocol.VERSION && parts[1] == "CALL")
            val peerPort = parts[2].toInt(); require(peerPort in 1..65535)
            val token = parts[3].toLong()
            synchronized(lock) {
                if (owner != null) { CallProtocol.write(socket, "BUSY"); return }
                if (!ready()) { CallProtocol.write(socket, "NOT_READY"); return }
                if (!running) return
                check(gate.tryAcquire(socket))
                CallStates.receiverMutable.value = CallStatus(CallState.CONNECTING, "통화 연결 중", true)
            }
            val call = CallSession(context, socket, lan, token) { reason ->
                synchronized(lock) {
                    if (owner === socket) {
                        gate.release(socket); session = null
                        if (running) CallStates.receiverMutable.value = CallStatus(detail = reason, ready = ready())
                    }
                }
                clients.remove(socket)
            }
            synchronized(lock) {
                if (!running || owner !== socket) { call.close(); return }
                session = call
            }
            transferred = true
            try {
                CallProtocol.write(socket, "ACCEPT ${call.audioPort}")
                check(CallProtocol.read(socket) == "READY")
                call.startAudio(peerPort)
                CallProtocol.write(socket, "ACTIVE")
                call.activate {
                    synchronized(lock) {
                        check(running && owner === socket)
                        CallStates.receiverMutable.value = CallStatus(CallState.BUSY, "현재 통화 중", true)
                    }
                }
            } catch (e: Exception) { call.close(e.message ?: "통화 연결 실패") }
        } catch (e: Exception) {
            Log.w("RoomCall", "Call request failed", e)
        } finally {
            if (!transferred) {
                runCatching { socket.close() }; clients.remove(socket)
                synchronized(lock) {
                    if (owner === socket) {
                        gate.release(socket)
                        if (running) CallStates.receiverMutable.value = CallStatus(detail = "통화 연결 실패", ready = ready())
                    }
                }
            }
        }
    }
    fun stop() {
        running = false
        runCatching { listener?.close() }
        synchronized(lock) { session?.close("수신 서비스가 종료되었습니다.") }
        clients.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }
}