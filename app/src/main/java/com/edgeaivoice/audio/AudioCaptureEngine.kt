package com.edgeaivoice.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
class AudioCaptureEngine(context: Context) {
    private val tag = "AudioCaptureEngine"

    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

    private val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioEncoding)
    private val recordBufferSize = (minBufferSize.coerceAtLeast(4096)) * 2

    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private var captureBuffer = ByteArrayOutputStream()
    private val recording = AtomicBoolean(false)

    init {
        require(minBufferSize > 0) {
            "AudioRecord min buffer invalid: $minBufferSize"
        }
    }

    @Synchronized
    fun startCapture(): Boolean {
        if (recording.get()) return true

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioEncoding,
            recordBufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(tag, "AudioRecord init failed")
            recorder.release()
            return false
        }

        audioRecord = recorder
        captureBuffer = ByteArrayOutputStream()
        recording.set(true)

        recorder.startRecording()

        captureThread = Thread {
            val chunk = ByteArray(2048)
            while (recording.get()) {
                val n = recorder.read(chunk, 0, chunk.size)
                if (n > 0) {
                    captureBuffer.write(chunk, 0, n)
                }
            }
        }.apply {
            name = "audio-capture-thread"
            start()
        }

        return true
    }

    @Synchronized
    fun stopCapture(): ByteArray {
        if (!recording.get()) return ByteArray(0)

        recording.set(false)

        val recorder = audioRecord
        runCatching { recorder?.stop() }
        captureThread?.join(500)
        captureThread = null

        runCatching { recorder?.release() }
        audioRecord = null

        return captureBuffer.toByteArray()
    }
}
