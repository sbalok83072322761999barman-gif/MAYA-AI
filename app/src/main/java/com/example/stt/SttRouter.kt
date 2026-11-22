package com.example.stt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.example.audio.RealTimeAudioEngine
import com.example.security.KeystoreVault
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

enum class SttProvider(val label: String) {
    ANDROID("Android SpeechRecognizer"),
    DEEPGRAM_NOVA3("Deepgram Nova-3 (Cloud)"),
    GEMINI_AUDIO("Gemini Multimodal Audio"),
    LOCAL_WHISPER("Local Whisper Asset")
}

data class SttLanguageOption(val code: String, val label: String)

data class SttState(
    val isListening: Boolean = false,
    val activeProvider: SttProvider = SttProvider.ANDROID,
    val languageCode: String = "en-IN",
    val interimText: String = "",
    val finalText: String = "",
    val confidence: Float = 0f,
    val offlineModelInstalled: Boolean = false,
    val errorMessage: String? = null
)

class SttRouter(
    private val context: Context,
    private val audioEngine: RealTimeAudioEngine,
    private val vault: KeystoreVault
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(
        SttState(offlineModelInstalled = hasLocalWhisperModel())
    )
    val state: StateFlow<SttState> = _state.asStateFlow()

    val supportedLanguages: List<SttLanguageOption> = listOf(
        SttLanguageOption("en-IN", "English (India)"),
        SttLanguageOption("hi-IN", "Hindi (हिन्दी)"),
        SttLanguageOption("bn-IN", "Bengali (বাংলা)"),
        SttLanguageOption("as-IN", "Assamese (অসমীয়া)"),
        SttLanguageOption("gu-IN", "Gujarati (ગુજરાતી)"),
        SttLanguageOption("kn-IN", "Kannada (ಕನ್ನಡ)"),
        SttLanguageOption("mr-IN", "Marathi (मराठी)"),
        SttLanguageOption("ne-NP", "Nepali (नेपाली)"),
        SttLanguageOption("pa-IN", "Punjabi (ਪੰਜਾਬੀ)"),
        SttLanguageOption("ta-IN", "Tamil (தமிழ்)"),
        SttLanguageOption("te-IN", "Telugu (తెలుగు)"),
        SttLanguageOption("ur-IN", "Urdu (اردو)"),
        SttLanguageOption("en-US", "English (US)")
    )

    fun setLanguage(code: String) {
        _state.value = _state.value.copy(languageCode = code)
    }

    fun setProvider(provider: SttProvider) {
        _state.value = _state.value.copy(
            activeProvider = provider,
            offlineModelInstalled = hasLocalWhisperModel(),
            errorMessage = null
        )
    }

    fun startAndroidRecognition(
        languageCode: String = _state.value.languageCode,
        preferOffline: Boolean = false,
        onFinalResult: (String, Float) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _state.value = _state.value.copy(
                errorMessage = "Android SpeechRecognizer is unavailable on this device. Try Gemini Audio or Deepgram."
            )
            return
        }
        mainHandler.post {
            try {
                recognizer?.destroy()
                val sr = SpeechRecognizer.createSpeechRecognizer(appContext)
                recognizer = sr
                _state.value = _state.value.copy(
                    isListening = true,
                    interimText = "",
                    errorMessage = null,
                    languageCode = languageCode
                )
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
                        val msg = mapSpeechError(error)
                        _state.value = _state.value.copy(isListening = false, errorMessage = msg)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                        if (partial.isNotBlank()) {
                            _state.value = _state.value.copy(interimText = partial)
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        val scores = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)
                        val text = postProcessPunctuation(list.firstOrNull().orEmpty())
                        val conf = scores?.firstOrNull()?.coerceIn(0f, 1f) ?: 0.85f
                        _state.value = _state.value.copy(
                            isListening = false,
                            finalText = text,
                            interimText = "",
                            confidence = conf,
                            errorMessage = null
                        )
                        if (text.isNotBlank()) onFinalResult(text, conf)
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
                }
                sr.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "SpeechRecognizer error", e)
                _state.value = _state.value.copy(
                    isListening = false,
                    errorMessage = e.localizedMessage ?: "SpeechRecognizer failed"
                )
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                recognizer?.stopListening()
                recognizer?.destroy()
            } catch (_: Exception) {}
            recognizer = null
            _state.value = _state.value.copy(isListening = false)
        }
    }

    suspend fun transcribeRecordedAudioWithCloud(provider: SttProvider): Result<String> {
        val wavBytes = audioEngine.getRecentWavBytes()
        if (wavBytes.size <= 44) {
            return Result.failure(IllegalStateException("No recorded audio in buffer. Start microphone capture first."))
        }
        return when (provider) {
            SttProvider.DEEPGRAM_NOVA3 -> transcribeWithDeepgram(wavBytes)
            SttProvider.GEMINI_AUDIO -> transcribeWithGemini(wavBytes)
            SttProvider.LOCAL_WHISPER -> {
                if (hasLocalWhisperModel()) {
                    Result.failure(IllegalStateException("Whisper model file detected, but native whisper.cpp JNI library is not bundled. Use Android SpeechRecognizer or Gemini Audio."))
                } else {
                    Result.failure(IllegalStateException("No local Whisper model installed in app files/models/whisper."))
                }
            }
            SttProvider.ANDROID -> Result.failure(IllegalStateException("Use live microphone mode for Android SpeechRecognizer."))
        }
    }

    private suspend fun transcribeWithDeepgram(wavBytes: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = vault.getSecret(KeystoreVault.KEY_DEEPGRAM)
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Deepgram API key not configured. Add it in Settings > API Vault."))
        }
        try {
            val req = Request.Builder()
                .url("https://api.deepgram.com/v1/listen?model=nova-3&smart_format=true&detect_language=true")
                .addHeader("Authorization", "Token $apiKey")
                .post(wavBytes.toRequestBody("audio/wav".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Deepgram HTTP ${resp.code}: ${body.take(200)}"))
                }
                val json = JSONObject(body)
                val transcript = json.optJSONObject("results")
                    ?.optJSONArray("channels")
                    ?.optJSONObject(0)
                    ?.optJSONArray("alternatives")
                    ?.optJSONObject(0)
                    ?.optString("transcript")
                    .orEmpty()
                val conf = json.optJSONObject("results")
                    ?.optJSONArray("channels")
                    ?.optJSONObject(0)
                    ?.optJSONArray("alternatives")
                    ?.optJSONObject(0)
                    ?.optDouble("confidence", 0.0)?.toFloat() ?: 0f
                _state.value = _state.value.copy(finalText = transcript, confidence = conf, errorMessage = null)
                Result.success(transcript)
            }
        } catch (e: Exception) {
            _state.value = _state.value.copy(errorMessage = e.localizedMessage)
            Result.failure(e)
        }
    }

    private suspend fun transcribeWithGemini(wavBytes: ByteArray): Result<String> {
        return try {
            val model = Firebase.ai.generativeModel("gemini-3.8-flash")
            val response = withTimeout(45_000L) {
                model.generateContent(
                    content {
                        inlineData(wavBytes, "audio/wav")
                        text("Transcribe the spoken audio accurately with proper punctuation. Preserve Hindi, Hinglish, English, or regional Indian language words naturally. Output only the transcription.")
                    }
                )
            }
            val text = response.text?.trim().orEmpty()
            _state.value = _state.value.copy(finalText = text, confidence = 0.92f, errorMessage = null)
            Result.success(text)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini audio transcription failed", e)
            _state.value = _state.value.copy(errorMessage = e.localizedMessage)
            Result.failure(e)
        }
    }

    private fun postProcessPunctuation(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun mapSpeechError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network error during recognition"
        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected before timeout"
        else -> "Speech recognizer error ($code)"
    }

    private fun hasLocalWhisperModel(): Boolean {
        val dir = File(appContext.filesDir, "models/whisper")
        return dir.exists() && dir.listFiles()?.isNotEmpty() == true
    }

    companion object {
        private const val TAG = "SttRouter"
    }
}
