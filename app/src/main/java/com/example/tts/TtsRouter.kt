package com.example.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.audio.RealTimeAudioEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Locale
import java.util.UUID

enum class SpeechEmotion(val rateMultiplier: Float, val pitchMultiplier: Float, val label: String) {
    NEUTRAL(1.0f, 1.0f, "neutral"),
    HAPPY(1.08f, 1.12f, "happy"),
    CURIOUS(1.02f, 1.10f, "curious"),
    SERIOUS(0.92f, 0.88f, "serious"),
    EXCITED(1.15f, 1.16f, "excited"),
    CALM(0.88f, 0.94f, "calm"),
    CONFUSED(0.95f, 1.06f, "confused"),
    EMPATHETIC(0.90f, 0.96f, "empathetic")
}

data class TtsVoiceInfo(
    val name: String,
    val localeTag: String,
    val requiresNetwork: Boolean
)

data class TtsStatusState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val activeProvider: String = "Android System TTS",
    val availableVoices: List<TtsVoiceInfo> = emptyList(),
    val pocketTtsInstalled: Boolean = false,
    val currentEmotion: SpeechEmotion = SpeechEmotion.NEUTRAL,
    val statusDetail: String = "Initializing Android TextToSpeech..."
)

class TtsRouter(
    private val context: Context,
    private val audioEngine: RealTimeAudioEngine
) {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = null

    private val _state = MutableStateFlow(
        TtsStatusState(pocketTtsInstalled = hasPocketTtsAssets())
    )
    val state: StateFlow<TtsStatusState> = _state.asStateFlow()

    private var baseRate = 1.0f
    private var basePitch = 1.0f
    private var preferredVoiceName: String = ""

    init {
        initAndroidTts()
    }

    private fun initAndroidTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                val voices = engine.voices?.filter { v ->
                    val lang = v.locale.language
                    lang == "en" || lang == "hi" || lang == "bn" || lang == "mr" || lang == "ta" || lang == "te"
                }?.map {
                    TtsVoiceInfo(
                        name = it.name,
                        localeTag = it.locale.toLanguageTag(),
                        requiresNetwork = it.isNetworkConnectionRequired
                    )
                }?.sortedBy { it.localeTag }.orEmpty()

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        audioEngine.assistantSpeaking = true
                        _state.value = _state.value.copy(isSpeaking = true)
                    }

                    override fun onDone(utteranceId: String?) {
                        audioEngine.assistantSpeaking = false
                        _state.value = _state.value.copy(isSpeaking = false)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        audioEngine.assistantSpeaking = false
                        _state.value = _state.value.copy(isSpeaking = false)
                    }
                })

                _state.value = _state.value.copy(
                    isReady = true,
                    availableVoices = voices,
                    statusDetail = "Android TTS ready (${voices.size} voices available)"
                )
            } else {
                Log.e(TAG, "Android TextToSpeech init failed with status=$status")
                _state.value = _state.value.copy(
                    isReady = false,
                    statusDetail = "Android TTS unavailable (status $status)"
                )
            }
        }
    }

    fun configureVoice(rate: Float, pitch: Float, voiceName: String) {
        baseRate = rate.coerceIn(0.5f, 2.0f)
        basePitch = pitch.coerceIn(0.5f, 2.0f)
        preferredVoiceName = voiceName
        val engine = tts ?: return
        if (voiceName.isNotBlank()) {
            engine.voices?.firstOrNull { it.name == voiceName }?.let { engine.voice = it }
        }
    }

    fun speak(
        text: String,
        emotion: SpeechEmotion = SpeechEmotion.NEUTRAL,
        languageTag: String = "en-IN"
    ) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val engine = tts ?: return
        if (!_state.value.isReady) return

        // Match locale dynamically for Hindi, Bengali, or English
        val targetLocale = when {
            containsDevanagari(clean) || languageTag.startsWith("hi") -> Locale("hi", "IN")
            containsBengali(clean) || languageTag.startsWith("bn") -> Locale("bn", "IN")
            else -> Locale.forLanguageTag(languageTag)
        }

        if (preferredVoiceName.isBlank()) {
            engine.language = targetLocale
        }

        engine.setSpeechRate((baseRate * emotion.rateMultiplier).coerceIn(0.5f, 2.0f))
        engine.setPitch((basePitch * emotion.pitchMultiplier).coerceIn(0.5f, 2.0f))
        _state.value = _state.value.copy(currentEmotion = emotion)

        // Chunked playback with natural sentence pauses
        val sentences = clean.split(Regex("(?<=[.!?।])\\s+")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return

        engine.stop()
        sentences.forEachIndexed { index, sentence ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine.speak(sentence, mode, null, "maya_utt_${UUID.randomUUID()}")
            if (index < sentences.lastIndex) {
                engine.playSilentUtterance(160L, TextToSpeech.QUEUE_ADD, "pause_$index")
            }
        }
    }

    fun stop() {
        audioEngine.assistantSpeaking = false
        try {
            tts?.stop()
        } catch (_: Exception) {}
        _state.value = _state.value.copy(isSpeaking = false)
    }

    fun testVoice(sampleText: String = "Namaste! Main Maya hoon, aapki voice-first AI companion.") {
        speak(sampleText, SpeechEmotion.HAPPY, "en-IN")
    }

    fun release() {
        stop()
        try {
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
    }

    private fun containsDevanagari(s: String): Boolean = s.any { it in '\u0900'..'\u097F' }
    private fun containsBengali(s: String): Boolean = s.any { it in '\u0980'..'\u09FF' }

    private fun hasPocketTtsAssets(): Boolean {
        val dir = File(appContext.filesDir, "models/pocket_tts")
        return dir.exists() && dir.listFiles()?.isNotEmpty() == true
    }

    companion object {
        private const val TAG = "TtsRouter"
    }
}
