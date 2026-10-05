package com.example.roomcall.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.example.roomcall.call.LanNetwork
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** 16 kHz mono PCM, 20 ms frames, bounded 60 ms receive queue. No retransmission backlog. */
class DuplexAudio(private val context: Context, private val lan: LanNetwork, private val token: Long) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val socket = DatagramSocket(null).apply {
        reuseAddress = false
        try {
            bind(InetSocketAddress(lan.address, 0))
            lan.network.bindSocket(this)
            soTimeout = 1000
        } catch (e: Exception) { close(); throw e }
    }
    val port: Int get() = socket.localPort
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private val effects = mutableListOf<AudioEffect>()
    private val running = AtomicBoolean(false)
    private val queue = ArrayBlockingQueue<ByteArray>(3)
    private val workers = mutableListOf<Thread>()
    private var oldMode = AudioManager.MODE_NORMAL
    private var oldSpeaker = false
    private var oldDevice: AudioDeviceInfo? = null
    private var configured = false
    private var ownsAudio = false
    private var focus: AudioFocusRequest? = null
    @Volatile private var lastPacket = 0L

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    fun start(peer: InetAddress, peerPort: Int, onFailure: (String) -> Unit) {
        require(lan.contains(peer) && peerPort in 1..65535)
        check(audioOwner.tryAcquire(3, java.util.concurrent.TimeUnit.SECONDS)) { "이전 통화를 정리 중입니다. 다시 연결하세요." }
        ownsAudio = true
        socket.connect(peer, peerPort)
        oldMode = manager.mode
        oldSpeaker = manager.isSpeakerphoneOn
        if (Build.VERSION.SDK_INT >= 31) oldDevice = manager.communicationDevice
        configured = true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes())
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
                    onFailure("다른 앱이 오디오를 사용하여 통화를 종료했습니다.")
            }, android.os.Handler(android.os.Looper.getMainLooper())).build()
        focus = request
        check(manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "통화 오디오를 사용할 수 없습니다." }
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= 31) {
            val speaker = manager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            check(speaker != null && manager.setCommunicationDevice(speaker)) { "스피커폰 설정에 실패했습니다." }
        } else manager.isSpeakerphoneOn = true
        record = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
            maxOf(AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), FRAME * 4))
        check(record?.state == AudioRecord.STATE_INITIALIZED) { "마이크 초기화에 실패했습니다." }
        val session = record!!.audioSessionId
        fun effect(create: () -> AudioEffect?) {
            runCatching { create()?.also { effects.add(it); it.enabled = true } }
        }
        if (AcousticEchoCanceler.isAvailable()) effect { AcousticEchoCanceler.create(session) }
        if (NoiseSuppressor.isAvailable()) effect { NoiseSuppressor.create(session) }
        if (AutomaticGainControl.isAvailable()) effect { AutomaticGainControl.create(session) }
        track = AudioTrack.Builder().setAudioAttributes(attributes())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT), FRAME * 3))
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build()
        check(track?.state == AudioTrack.STATE_INITIALIZED) { "스피커 초기화에 실패했습니다." }
        track!!.play()
        record!!.startRecording()
        check(record!!.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "마이크를 시작할 수 없습니다." }
        running.set(true)
        lastPacket = SystemClock.elapsedRealtime()
        fun worker(name: String, block: () -> Unit) {
            workers += thread(name = name) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                try { block() } catch (e: Exception) {
                    if (running.get()) onFailure(e.message ?: "오디오 연결 오류")
                }
            }
        }
        worker("RoomCall-Microphone") {
            val pcm = ByteArray(FRAME)
            var sequence = 0L
            while (running.get()) {
                var offset = 0
                while (offset < FRAME && running.get()) {
                    val count = record!!.read(pcm, offset, FRAME - offset, AudioRecord.READ_BLOCKING)
                    check(count > 0) { "마이크 읽기 오류: $count" }
                    offset += count
                }
                if (!running.get()) break
                val frame = ByteBuffer.allocate(FRAME + HEADER).order(ByteOrder.BIG_ENDIAN)
                    .putLong(token).putLong(sequence++).put(pcm).array()
                socket.send(DatagramPacket(frame, frame.size))
            }
        }
        worker("RoomCall-UdpReceive") {
            var sequence = -1L
            val buffer = ByteArray(FRAME + HEADER + 1)
            while (running.get()) {
                val packet = DatagramPacket(buffer, buffer.size)
                try { socket.receive(packet) } catch (_: SocketTimeoutException) {
                    check(SystemClock.elapsedRealtime() - lastPacket < 6000) { "음성 패킷 수신이 끊겼습니다." }
                    continue
                }
                if (packet.length != FRAME + HEADER) continue
                val frame = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN)
                if (frame.long != token) continue
                val next = frame.long
                if (next <= sequence) continue
                sequence = next
                lastPacket = SystemClock.elapsedRealtime()
                val pcm = ByteArray(FRAME); frame.get(pcm)
                if (!queue.offer(pcm)) { queue.poll(); queue.offer(pcm) }
            }
        }
        worker("RoomCall-Speaker") {
            val silence = ByteArray(FRAME)
            while (running.get()) {
                val pcm = queue.poll() ?: silence
                var offset = 0
                while (offset < FRAME && running.get()) {
                    val count = track!!.write(pcm, offset, FRAME - offset, AudioTrack.WRITE_BLOCKING)
                    check(count > 0) { "스피커 출력 오류: $count" }
                    offset += count
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun close() {
        try {
            running.set(false)
            socket.close()
            runCatching { record?.stop() }
            runCatching { track?.pause(); track?.flush() }
            workers.forEach { if (it !== Thread.currentThread()) runCatching { it.join(1000) } }
            effects.forEach { runCatching { it.release() } }; effects.clear()
            runCatching { record?.release() }; record = null
            runCatching { track?.release() }; track = null
            queue.clear()
            if (configured) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= 31) {
                        oldDevice?.let { manager.setCommunicationDevice(it) } ?: manager.clearCommunicationDevice()
                    } else manager.isSpeakerphoneOn = oldSpeaker
                }
                runCatching { manager.mode = oldMode }
            }
            runCatching { focus?.let { manager.abandonAudioFocusRequest(it) } }; focus = null
        } finally {
            if (ownsAudio) { ownsAudio = false; audioOwner.release() }
        }
    }
    private fun attributes() = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    companion object {
        private val audioOwner = java.util.concurrent.Semaphore(1)
        private const val RATE = 16000
        private const val FRAME = 640
        private const val HEADER = 16
    }
}