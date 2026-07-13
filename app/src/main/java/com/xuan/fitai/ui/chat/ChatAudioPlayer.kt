package com.xuan.fitai.ui.chat

import android.content.Context
import android.media.MediaPlayer
import java.io.File

class ChatAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var audioFile: File? = null
    private var playingId: Int? = null

    fun toggle(
        id: Int,
        audioBytes: ByteArray,
        onPlayingChanged: (Int?) -> Unit,
    ) {
        if (playingId == id) {
            stop(onPlayingChanged)
            return
        }

        stop(onPlayingChanged = null)
        val file = File.createTempFile("fitai-chat-audio-", ".wav", context.cacheDir)
        file.writeBytes(audioBytes)
        val player = MediaPlayer()
        mediaPlayer = player
        audioFile = file
        playingId = id
        onPlayingChanged(id)

        try {
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { it.start() }
            player.setOnCompletionListener {
                stop(onPlayingChanged)
            }
            player.setOnErrorListener { _, _, _ ->
                stop(onPlayingChanged)
                true
            }
            player.prepareAsync()
        } catch (_: Exception) {
            stop(onPlayingChanged)
        }
    }

    fun stop(onPlayingChanged: ((Int?) -> Unit)? = null) {
        try {
            mediaPlayer?.stop()
        } catch (_: IllegalStateException) {
        }
        mediaPlayer?.release()
        mediaPlayer = null
        audioFile?.delete()
        audioFile = null
        playingId = null
        onPlayingChanged?.invoke(null)
    }

    fun release() {
        stop()
    }
}
