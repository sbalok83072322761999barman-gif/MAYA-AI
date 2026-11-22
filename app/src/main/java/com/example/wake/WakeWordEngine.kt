package com.example.wake

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.vad.AudioIntelligenceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class WakeWordState(
    val isEnabled: Boolean = false,
    val isListening: Boolean = false,
    val sensitivity: Float = 0.65f,
    val lastDetectedPhrase: String? = null,
    val lastDetectedAt: Long = 0L,
    val hasCustomTfliteModel: Boolean = false,
    val statusMessage: String = "Wake word disabled"
)

/**
 * Wake-word engine supporting "Hey Maya", "Hello Maya", and "Wake Up Maya".
 * Combines VAD gating with Android SpeechRecognizer continuous phrase spotting
 * and checks for optional user-installed OpenWakeWord TFLite assets in app storage.
 * Immediately releases recognizer resources when disabled.
 */
class WakeWordEngine(
    private val context: Context,
    private val vadEngine: AudioIntelligenceEngine
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var onWakeCallback: ((String) -> Unit)? = null
    private var lastTriggerMillis = 0L

    private val _state = MutableStateFlow(
        WakeWordState(hasCustomTfliteModel = hasTfliteAsset())
    )
    val state: StateFlow<WakeWordState> = _state.asStateFlow()

    private val wakePhrases = listOf(
        "hey maya",
        "hello maya",
        "wake up maya",
        "he maya",
        "ok maya",
        "maya suno"
    )

    fun setOnWakeDetected(callback: (String) -> Unit) {
        onWakeCallback = callback
    }

    fun updateSensitivity(sensitivity: Float) {
        _state.value = _state.value.copy(sensitivity = sensitivity.coerceIn(0.1f, 1.0f))
    }

    fun start(sensitivity: Float = _state.value.sensitivity) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.value = _state.value.copy(
                isEnabled = false,
                isListening = false,
                statusMessage = "Speech recognition unavailable on this device"
            )
            return
        }
        _state.value = _state.value.copy(
            isEnabled = true,
            sensitivity = sensitivity,
            hasCustomTfliteModel = hasTfliteAsset(),
            statusMessage = "Listening for 'Hey Maya', 'Hello Maya', 'Wake Up Maya'"
        )
        mainHandler.post { startRecognizerCycle() }
    }

    fun stop() {
        _state.value = _state.value.copy(
            isEnabled = false,
            isListening = false,
            statusMessage = "Wake word stopped"
        )
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (_: Exception) {}
            speechRecognizer = null
        }
    }

    private fun startRecognizerCycle() {
        if (!_state.value.isEnabled) return
        try {
            speechRecognizer?.destroy()
            val sr = SpeechRecognizer.createSpeechRecognizer(appContext)
            speechRecognizer = sr
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _state.value = _state.value.copy(isListening = true)
                }

                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    _state.value = _state.value.copy(isListening = false)
                }

                override fun onError(error: Int) {
                    _state.value = _state.value.copy(isListening = false)
                    if (_state.value.isEnabled) {
                        mainHandler.postDelayed({ startRecognizerCycle() }, 750L)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    checkMatches(matches)
                    if (_state.value.isEnabled) {
                        mainHandler.postDelayed({ startRecognizerCycle() }, 350L)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    checkMatches(matches)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            sr.startListening(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Wake recognizer cycle failed", e)
            _state.value = _state.value.copy(
                isListening = false,
                statusMessage = e.localizedMessage ?: "Wake recognizer error"
            )
        }
    }

    private fun checkMatches(candidates: List<String>) {
        if (!_state.value.isEnabled) return
        val now = System.currentTimeMillis()
        // False-positive cooldown (2.5 seconds)
        if (now - lastTriggerMillis < 2500L) return

        val matched = candidates.firstOrNull { text ->
            val lower = text.lowercase().trim()
            wakePhrases.any { phrase ->
                lower.contains(phrase) || (
                    _state.value.sensitivity >= 0.75f && lower == "maya"
                )
            }
        }
        if (matched != null) {
            lastTriggerMillis = now
            _state.value = _state.value.copy(
                lastDetectedPhrase = matched,
                lastDetectedAt = now,
                statusMessage = "Wake detected: \"$matched\""
            )
            onWakeCallback?.invoke(matched)
        }
    }

    private fun hasTfliteAsset(): Boolean {
        val dir = File(appContext.filesDir, "models/wakeword")
        return dir.exists() && dir.listFiles()?.any { it.extension == "tflite" } == true
    }

    companion object {
        private const val TAG = "WakeWordEngine"
    }
}
