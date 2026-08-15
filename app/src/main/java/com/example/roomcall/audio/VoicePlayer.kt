package com.example.roomcall.audio

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.example.roomcall.R

class VoicePlayer(context: Context) {
    private val appContext = context.applicationContext
    private var mediaPlayer: MediaPlayer? = null

    fun playMessage(message: String) {
        val resourceId = when (message.trim()) {
            "응, 괜찮아" -> R.raw.all_right
            "주희야, 조용히 좀 해" -> R.raw.be_quiet
            "주희야, 밥 먹자" -> R.raw.have_a_meal
            else -> return
        }
        play(resourceId)
    }

    private fun play(@RawRes resourceId: Int) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer.create(appContext, resourceId)?.apply {
            setOnCompletionListener {
                release()
                mediaPlayer = null
            }
            start()
        }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
