package com.charvis.app

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class AudioStreamer {
    @Volatile private var recording = false
    @Volatile private var playing = false
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    private val playQueue = LinkedBlockingQueue<ByteArray>()

    /** Mic: 16 kHz, mono, PCM16. Sends ~100 ms chunks. */
    @SuppressLint("MissingPermission")
    fun startMic(onChunk: (ByteArray) -> Unit) {
        if (recording) return
        val minBuf = AudioRecord.getMinBufferSize(
            IN_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            IN_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, CHUNK_BYTES * 2)
        )
        recorder = rec
        recording = true
        rec.startRecording()
        thread(name = "charvis-mic") {
            val buf = ByteArray(CHUNK_BYTES)
            while (recording) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) onChunk(buf.copyOf(n))
            }
        }
    }

    /** Speaker: queue 24 kHz PCM16 from Gemini and play it in order. */
    fun play(pcm: ByteArray) {
        playQueue.offer(pcm)
        if (!playing) startPlaybackThread()
    }

    /** Called on barge-in: drop everything not yet played. */
    fun flushPlayback() {
        playQueue.clear()
        try {
            player?.flush()
        } catch (_: IllegalStateException) {
            // track already released
        }
    }

    fun stopAll() {
        recording = false
        playing = false
        playQueue.clear()
        try {
            recorder?.stop()
        } catch (_: IllegalStateException) { }
        recorder?.release()
        recorder = null
    }

    private fun startPlaybackThread() {
        val track = buildTrack()
        player = track
        playing = true
        track.play()
        thread(name = "charvis-speaker") {
            while (playing) {
                val chunk = playQueue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                track.write(chunk, 0, chunk.size)
            }
            try {
                track.stop()
            } catch (_: IllegalStateException) { }
            track.release()
            player = null
        }
    }

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            OUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(OUT_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    companion object {
        const val IN_RATE = 16000
        const val OUT_RATE = 24000
        const val CHUNK_BYTES = 3200 // 100 ms of 16 kHz mono PCM16
    }
}
