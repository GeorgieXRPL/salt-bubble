package com.easton.saltbubble.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class WavRecorder(private val context: Context) {
    private val recording = AtomicBoolean(false)
    private var worker: Thread? = null
    private var output: File? = null

    val isRecording: Boolean get() = recording.get()

    @SuppressLint("MissingPermission")
    fun start(): File {
        if (recording.get()) {
            throw IllegalStateException("Already recording")
        }
        val dir = File(context.cacheDir, "recordings").apply { mkdirs() }
        val file = File(dir, "salt_${System.currentTimeMillis()}.wav")
        output = file

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            throw IllegalStateException("AudioRecord is not available on this device")
        }
        val bufferSize = (minBuf * 2).coerceAtLeast(SAMPLE_RATE)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL,
            ENCODING,
            bufferSize,
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Microphone failed to start")
        }

        recording.set(true)
        worker = thread(name = "salt-wav-recorder", priority = Thread.MAX_PRIORITY) {
            val pcm = ByteArray(bufferSize)
            RandomAccessFile(file, "rw").use { raf ->
                writeHeader(raf, 0)
                recorder.startRecording()
                try {
                    while (recording.get()) {
                        val read = recorder.read(pcm, 0, pcm.size)
                        if (read > 0) {
                            raf.write(pcm, 0, read)
                        }
                    }
                } finally {
                    runCatching { recorder.stop() }
                    recorder.release()
                    val dataBytes = (raf.length() - 44).coerceAtLeast(0)
                    writeHeader(raf, dataBytes.toInt())
                }
            }
        }
        return file
    }

    fun stop(): File? {
        recording.set(false)
        worker?.join(4_000)
        worker = null
        return output
    }

    fun cancel() {
        recording.set(false)
        worker?.join(2_000)
        worker = null
        output?.delete()
        output = null
    }

    private fun writeHeader(raf: RandomAccessFile, dataBytes: Int) {
        val byteRate = SAMPLE_RATE * 2
        raf.seek(0)
        raf.writeBytes("RIFF")
        raf.writeIntLE(36 + dataBytes)
        raf.writeBytes("WAVE")
        raf.writeBytes("fmt ")
        raf.writeIntLE(16)
        raf.writeShortLE(1)
        raf.writeShortLE(1)
        raf.writeIntLE(SAMPLE_RATE)
        raf.writeIntLE(byteRate)
        raf.writeShortLE(2)
        raf.writeShortLE(16)
        raf.writeBytes("data")
        raf.writeIntLE(dataBytes)
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xff)
        write(value shr 8 and 0xff)
        write(value shr 16 and 0xff)
        write(value shr 24 and 0xff)
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xff)
        write(value shr 8 and 0xff)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
