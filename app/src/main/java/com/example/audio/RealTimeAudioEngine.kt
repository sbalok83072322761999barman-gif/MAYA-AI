package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import com.example.vad.AudioIntelligenceEngine
import com.example.vad.AudioIntelligenceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

enum class MicState {
    IDLE,
    LISTENING,
    MUTED_ECHO_GUARD,
    ERROR
}

data class AudioDiagnostics(
    val sampleRateHz: Int = 16000,
    val hardwareAecSupported: Boolean = AcousticEchoCanceler.isAvailable(),
    val hardwareNsSupported: Boolean = NoiseSuppressor.isAvailable(),
    val hardwareAgcSupported: Boolean = AutomaticGainControl.isAvailable(),
    val aecActive: Boolean = false,
    val nsActive: Boolean = false,
    val agcActive: Boolean = false,
    val dtlnFilterActive: Boolean = false,
    val rmsDbfs: Float = -60f,
    val latencyMs: Long = 0L,
    val framesProcessed: Long = 0L,
    val lastError: String? = null
)

class RealTimeAudioEngine(
    private val intelligenceEngine: AudioIntelligenceEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    private val _micState = MutableStateFlow(MicState.IDLE)
    val micState: StateFlow<MicState> = _micState.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _spectrumBars = MutableStateFlow(List(24) { 0.05f })
    val spectrumBars: StateFlow<List<Float>> = _spectrumBars.asStateFlow()

    private val _diagnostics = MutableStateFlow(AudioDiagnostics())
    val diagnostics: StateFlow<AudioDiagnostics> = _diagnostics.asStateFlow()

    val intelligenceState: StateFlow<AudioIntelligenceState> = intelligenceEngine.state

    @Volatile
    var assistantSpeaking: Boolean = false

    @Volatile
    private var dtlnEnabled: Boolean = false

    @Volatile
    private var bargeInEnabled: Boolean = true

    private var onBargeInCallback: (() -> Unit)? = null
    private var onFrameListeners = mutableListOf<(ShortArray, Int) -> Unit>()

    // Ring buffer for last ~6 seconds of 16kHz PCM16 audio (192 KB) — very RAM friendly for 4GB devices
    private val pcmRingBuffer = ByteArrayOutputStream()
    private val maxRingBytes = 16000 * 2 * 6

    fun addFrameListener(listener: (ShortArray, Int) -> Unit) {
        synchronized(onFrameListeners) {
            onFrameListeners.add(listener)
        }
    }

    fun removeFrameListener(listener: (ShortArray, Int) -> Unit) {
        synchronized(onFrameListeners) {
            onFrameListeners.remove(listener)
        }
    }

    fun setOnBargeIn(callback: (() -> Unit)?) {
        onBargeInCallback = callback
    }

    fun configureProcessing(
        dtlnFilter: Boolean,
        bargeIn: Boolean
    ) {
        dtlnEnabled = dtlnFilter
        bargeInEnabled = bargeIn
        _diagnostics.value = _diagnostics.value.copy(dtlnFilterActive = dtlnFilter)
    }

    @SuppressLint("MissingPermission")
    fun startCapture(
        useAec: Boolean = true,
        useNs: Boolean = true,
        useAgc: Boolean = true,
        dtlnFilter: Boolean = false,
        bargeIn: Boolean = true
    ): Boolean {
        if (captureJob?.isActive == true) return true
        dtlnEnabled = dtlnFilter
        bargeInEnabled = bargeIn

        val sampleRate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            _micState.value = MicState.ERROR
            _diagnostics.value = _diagnostics.value.copy(lastError = "AudioRecord unsupported on this hardware.")
            return false
        }

        val bufferSize = maxOf(minBuf, 2048)
        return try {
            val source = if (useAec) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.MIC
            val record = AudioRecord(
                source,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                _micState.value = MicState.ERROR
                _diagnostics.value = _diagnostics.value.copy(lastError = "AudioRecord failed to initialize (check RECORD_AUDIO permission).")
                return false
            }

            val sessionId = record.audioSessionId
            var aecActive = false
            var nsActive = false
            var agcActive = false

            if (useAec && AcousticEchoCanceler.isAvailable()) {
                aec = AcousticEchoCanceler.create(sessionId)?.apply {
                    enabled = true
                    aecActive = enabled
                }
            }
            if (useNs && NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(sessionId)?.apply {
                    enabled = true
                    nsActive = enabled
                }
            }
            if (useAgc && AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(sessionId)?.apply {
                    enabled = true
                    agcActive = enabled
                }
            }

            audioRecord = record
            record.startRecording()
            _micState.value = MicState.LISTENING
            _diagnostics.value = _diagnostics.value.copy(
                aecActive = aecActive,
                nsActive = nsActive,
                agcActive = agcActive,
                dtlnFilterActive = dtlnEnabled,
                lastError = null
            )

            captureJob = scope.launch {
                val frameSize = 512 // 32ms @ 16kHz
                val frame = ShortArray(frameSize)
                var frames = 0L
                val startTime = System.currentTimeMillis()
                var noiseFloor = 120.0

                while (isActive) {
                    val read = record.read(frame, 0, frameSize)
                    if (read > 0) {
                        frames++
                        // Optional DTLN-inspired lightweight soft-gate noise suppression
                        if (dtlnEnabled) {
                            noiseFloor = applySoftSpectralGate(frame, read, noiseFloor)
                        }

                        // Compute RMS & dBFS
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = frame[i].toDouble()
                            sumSq += s * s
                        }
                        val rms = sqrt(sumSq / read)
                        val dbfs = if (rms > 1.0) (20.0 * log10(rms / 32768.0)).toFloat() else -90f
                        val normalized = ((dbfs + 60f) / 55f).coerceIn(0f, 1f)

                        // Self-echo guard while Maya speaks
                        if (assistantSpeaking) {
                            _micState.value = MicState.MUTED_ECHO_GUARD
                            if (bargeInEnabled && dbfs > -18f) {
                                // Sustained loud user speech triggers barge-in interruption
                                onBargeInCallback?.invoke()
                            }
                        } else {
                            _micState.value = MicState.LISTENING
                        }

                        _rmsLevel.value = normalized
                        _spectrumBars.value = computeSpectrumBars(frame, read)

                        intelligenceEngine.processFrame(frame, read, rms, dbfs, assistantSpeaking)

                        synchronized(pcmRingBuffer) {
                            val byteBuf = ByteArray(read * 2)
                            for (i in 0 until read) {
                                val sample = frame[i].toInt()
                                byteBuf[i * 2] = (sample and 0xFF).toByte()
                                byteBuf[i * 2 + 1] = ((sample ushr 8) and 0xFF).toByte()
                            }
                            if (pcmRingBuffer.size() > maxRingBytes) {
                                val current = pcmRingBuffer.toByteArray()
                                pcmRingBuffer.reset()
                                pcmRingBuffer.write(current, current.size / 2, current.size - current.size / 2)
                            }
                            pcmRingBuffer.write(byteBuf)
                        }

                        synchronized(onFrameListeners) {
                            for (listener in onFrameListeners) {
                                listener(frame, read)
                            }
                        }

                        if (frames % 10L == 0L) {
                            _diagnostics.value = _diagnostics.value.copy(
                                rmsDbfs = dbfs,
                                latencyMs = ((System.currentTimeMillis() - startTime) / frames.coerceAtLeast(1)),
                                framesProcessed = frames
                            )
                        }
                    } else if (read < 0) {
                        Log.w(TAG, "AudioRecord read error code: $read")
                        _diagnostics.value = _diagnostics.value.copy(lastError = "AudioRecord read error: $read")
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
            _micState.value = MicState.ERROR
            _diagnostics.value = _diagnostics.value.copy(lastError = e.localizedMessage ?: "Audio capture error")
            stopCapture()
            false
        }
    }

    fun getRecentWavBytes(): ByteArray {
        val pcm = synchronized(pcmRingBuffer) { pcmRingBuffer.toByteArray() }
        return wrapPcmAsWav(pcm, 16000, 1, 16)
    }

    fun clearBuffer() {
        synchronized(pcmRingBuffer) { pcmRingBuffer.reset() }
    }

    fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
        try {
            audioRecord?.stop()
        } catch (_: Exception) {}
        try {
            aec?.release()
            ns?.release()
            agc?.release()
            audioRecord?.release()
        } catch (_: Exception) {}
        aec = null
        ns = null
        agc = null
        audioRecord = null
        _micState.value = MicState.IDLE
        _rmsLevel.value = 0f
        _spectrumBars.value = List(24) { 0.05f }
    }

    /**
     * Low-latency AudioTrack test chime / PCM playback for audio diagnostics.
     */
    fun playDiagnosticTone(freqHz: Double = 528.0, durationMs: Int = 280) {
        scope.launch {
            val sampleRate = 16000
            val count = (sampleRate * durationMs) / 1000
            val samples = ShortArray(count)
            for (i in 0 until count) {
                val env = minOf(1.0, minOf(i / 200.0, (count - i) / 400.0))
                samples[i] = (sin(2.0 * PI * i * freqHz / sampleRate) * Short.MAX_VALUE * 0.25 * env).toInt().toShort()
            }
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, samples.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                track.write(samples, 0, samples.size)
                track.play()
                kotlinx.coroutines.delay(durationMs.toLong() + 60L)
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            }
        }
    }

    private fun applySoftSpectralGate(frame: ShortArray, len: Int, prevNoiseFloor: Double): Double {
        var meanAbs = 0.0
        for (i in 0 until len) meanAbs += abs(frame[i].toDouble())
        meanAbs /= len.coerceAtLeast(1)
        val updatedFloor = if (meanAbs < prevNoiseFloor * 1.4) {
            prevNoiseFloor * 0.92 + meanAbs * 0.08
        } else {
            prevNoiseFloor
        }
        val threshold = updatedFloor * 1.65
        for (i in 0 until len) {
            val v = frame[i].toDouble()
            if (abs(v) < threshold) {
                frame[i] = (v * 0.2).toInt().toShort()
            }
        }
        return updatedFloor
    }

    private fun computeSpectrumBars(frame: ShortArray, len: Int): List<Float> {
        val bins = 24
        val step = (len / bins).coerceAtLeast(1)
        return List(bins) { b ->
            var sum = 0f
            val start = b * step
            val end = minOf(len, start + step)
            for (i in start until end) {
                sum += abs(frame[i].toFloat()) / 32768f
            }
            ((sum / (end - start).coerceAtLeast(1)) * 4.5f).coerceIn(0.05f, 1f)
        }
    }

    fun release() {
        stopCapture()
        scope.cancel()
    }

    companion object {
        private const val TAG = "RealTimeAudioEngine"

        fun wrapPcmAsWav(pcm: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
            val totalDataLen = pcm.size + 36
            val byteRate = sampleRate * channels * bitsPerSample / 8
            val out = ByteArray(44 + pcm.size)
            out[0] = 'R'.code.toByte(); out[1] = 'I'.code.toByte(); out[2] = 'F'.code.toByte(); out[3] = 'F'.code.toByte()
            out[4] = (totalDataLen and 0xff).toByte()
            out[5] = ((totalDataLen shr 8) and 0xff).toByte()
            out[6] = ((totalDataLen shr 16) and 0xff).toByte()
            out[7] = ((totalDataLen shr 24) and 0xff).toByte()
            out[8] = 'W'.code.toByte(); out[9] = 'A'.code.toByte(); out[10] = 'V'.code.toByte(); out[11] = 'E'.code.toByte()
            out[12] = 'f'.code.toByte(); out[13] = 'm'.code.toByte(); out[14] = 't'.code.toByte(); out[15] = ' '.code.toByte()
            out[16] = 16; out[17] = 0; out[18] = 0; out[19] = 0
            out[20] = 1; out[21] = 0
            out[22] = channels.toByte(); out[23] = 0
            out[24] = (sampleRate and 0xff).toByte()
            out[25] = ((sampleRate shr 8) and 0xff).toByte()
            out[26] = ((sampleRate shr 16) and 0xff).toByte()
            out[27] = ((sampleRate shr 24) and 0xff).toByte()
            out[28] = (byteRate and 0xff).toByte()
            out[29] = ((byteRate shr 8) and 0xff).toByte()
            out[30] = ((byteRate shr 16) and 0xff).toByte()
            out[31] = ((byteRate shr 24) and 0xff).toByte()
            out[32] = (channels * bitsPerSample / 8).toByte(); out[33] = 0
            out[34] = bitsPerSample.toByte(); out[35] = 0
            out[36] = 'd'.code.toByte(); out[37] = 'a'.code.toByte(); out[38] = 't'.code.toByte(); out[39] = 'a'.code.toByte()
            out[40] = (pcm.size and 0xff).toByte()
            out[41] = ((pcm.size shr 8) and 0xff).toByte()
            out[42] = ((pcm.size shr 16) and 0xff).toByte()
            out[43] = ((pcm.size shr 24) and 0xff).toByte()
            System.arraycopy(pcm, 0, out, 44, pcm.size)
            return out
        }
    }
}
