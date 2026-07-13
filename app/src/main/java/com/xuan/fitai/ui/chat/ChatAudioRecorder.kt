package com.xuan.fitai.ui.chat

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Records the 16 kHz mono PCM/WAV format expected by LiteRT-LM audio inputs. */
class ChatAudioRecorder {
    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_COUNT = 1
        private const val BITS_PER_SAMPLE = 16

        fun hasAudioSignal(wavBytes: ByteArray): Boolean {
            return wavBytes.size > 44 && wavBytes.copyOfRange(44, wavBytes.size)
                .any { it != 0.toByte() }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pcmBuffer = ByteArrayOutputStream()
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    @Volatile private var recording = false

    fun start() {
        if (recording) return

        val minimumBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        require(minimumBufferSize > 0) { "此裝置不支援麥克風錄音" }

        val bufferSize = maxOf(minimumBufferSize, SAMPLE_RATE / 2)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "麥克風初始化失敗" }

        pcmBuffer.reset()
        audioRecord = recorder
        recording = true
        recorder.startRecording()
        recordingJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            while (recording) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) pcmBuffer.write(buffer, 0, read)
            }
        }
    }

    suspend fun stop(): ByteArray = withContext(Dispatchers.IO) {
        recording = false
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
        }
        recordingJob?.join()
        recordingJob = null
        audioRecord?.release()
        audioRecord = null

        val pcmData = pcmBuffer.toByteArray()
        Log.d(
            "FitAI_Audio",
            "recorded pcmBytes=${pcmData.size}, " +
                "durationMs=${pcmData.size * 1000L / (SAMPLE_RATE * 2)}, " +
                "nonZeroBytes=${pcmData.count { it != 0.toByte() }}"
        )
        createWav(pcmData)
    }

    fun cancel() {
        recording = false
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
        }
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.release()
        audioRecord = null
        pcmBuffer.reset()
    }

    fun release() {
        cancel()
        scope.cancel()
    }

    private fun createWav(pcmData: ByteArray): ByteArray {
        val byteRate = SAMPLE_RATE * CHANNEL_COUNT * BITS_PER_SAMPLE / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        // Match the WAV packaging used by Google AI Edge Gallery for audio clips.
        header.putInt(44 + pcmData.size)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(CHANNEL_COUNT.toShort())
        header.putInt(SAMPLE_RATE)
        header.putInt(byteRate)
        header.putShort((CHANNEL_COUNT * BITS_PER_SAMPLE / 8).toShort())
        header.putShort(BITS_PER_SAMPLE.toShort())
        header.put("data".toByteArray())
        header.putInt(pcmData.size)

        return ByteArrayOutputStream(header.capacity() + pcmData.size).apply {
            write(header.array())
            write(pcmData)
        }.toByteArray()
    }
}
