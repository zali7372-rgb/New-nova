package com.nova.assistant.system.bridge

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Literal "phone as microphone" mode: captures raw PCM16 mono audio and
 * streams it to NOVA Desktop, which runs speech-to-text itself (see the
 * desktop app's audio/receiver.py). This is the experimental, higher-
 * bandwidth alternative to [LaptopBridgeManager.sendTranscript] - it needs
 * the laptop to have its own internet access for speech recognition, unlike
 * sending already-recognized text. See NOVA Desktop's README for the
 * detailed trade-off explanation shown to the user in Settings.
 *
 * Uses the same 16 kHz mono PCM16 format the desktop's AudioSessionBuffer
 * expects (network/protocol.py's audio_start declares the sample rate
 * explicitly, so this is negotiated rather than hardcoded on both ends).
 */
class AudioStreamer(
    private val context: Context,
    private val bridge: LaptopBridgeManager
) {
    private var recordJob: Job? = null
    private var audioRecord: AudioRecord? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHUNK_MILLIS = 250L
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // caller must check hasPermission() first
    fun start(scope: CoroutineScope) {
        if (!hasPermission() || !bridge.isConnected()) return
        stop()

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) return

        val chunkSizeBytes = (SAMPLE_RATE * 2 /* bytes per sample */ * CHUNK_MILLIS / 1000).toInt()
        val bufferSize = maxOf(minBufferSize, chunkSizeBytes * 2)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }
        audioRecord = record

        bridge.sendAudioStart(SAMPLE_RATE)
        record.startRecording()

        recordJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(chunkSizeBytes)
            while (isActive()) {
                val bytesRead = record.read(buffer, 0, buffer.size)
                if (bytesRead > 0) {
                    val encoded = Base64.encodeToString(buffer, 0, bytesRead, Base64.NO_WRAP)
                    bridge.sendAudioChunk(encoded)
                }
            }
        }
    }

    fun stop() {
        recordJob?.cancel()
        recordJob = null
        val wasRecording = audioRecord != null
        audioRecord?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                // Already stopped/never started recording - not a real error.
            }
            it.release()
        }
        audioRecord = null
        if (wasRecording) bridge.sendAudioEnd()
    }

    private fun isActive(): Boolean = audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING
}
