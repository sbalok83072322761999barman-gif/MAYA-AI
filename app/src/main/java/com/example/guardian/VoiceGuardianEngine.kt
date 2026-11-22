package com.example.guardian

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.audio.RealTimeAudioEngine
import com.example.data.local.DiagnosticLogEntity
import com.example.data.local.DiagnosticsDao
import com.example.data.local.SecurityProfileDao
import com.example.data.local.VoiceProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class SpeakerVerificationResult(
    val matchedProfile: VoiceProfileEntity?,
    val confidence: Float,
    val status: String,
    val intrusionTriggered: Boolean
)

class VoiceGuardianEngine(
    private val context: Context,
    private val audioEngine: RealTimeAudioEngine,
    private val securityDao: SecurityProfileDao,
    private val diagnosticsDao: DiagnosticsDao
) {
    private val appContext = context.applicationContext
    val profiles: Flow<List<VoiceProfileEntity>> = securityDao.observeVoiceProfiles()

    private val _lastVerification = MutableStateFlow<SpeakerVerificationResult?>(null)
    val lastVerification: StateFlow<SpeakerVerificationResult?> = _lastVerification.asStateFlow()

    private val _intrusionAlerts = MutableStateFlow<List<String>>(emptyList())
    val intrusionAlerts: StateFlow<List<String>> = _intrusionAlerts.asStateFlow()

    fun hasExternalSpeakerModel(): Boolean {
        val dir = File(appContext.filesDir, "models/speaker")
        return dir.exists() && dir.listFiles()?.any { it.extension in setOf("onnx", "tflite") } == true
    }

    suspend fun enrollCurrentAudio(name: String, role: String): Result<VoiceProfileEntity> {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return Result.failure(IllegalArgumentException("Speaker name is required."))
        val wav = audioEngine.getRecentWavBytes()
        if (wav.size <= 4096) {
            return Result.failure(IllegalStateException("Insufficient audio recorded. Start microphone and speak for 2–3 seconds before enrolling."))
        }
        val embedding = extractSpectralEmbedding(wav)
        val json = JSONArray().apply { embedding.forEach { put(it.toDouble()) } }.toString()
        val entity = VoiceProfileEntity(
            name = cleanName,
            role = role.uppercase(),
            embeddingJson = json
        )
        val id = securityDao.insertVoiceProfile(entity)
        diagnosticsDao.insertLog(DiagnosticLogEntity(level = "INFO", tag = "VoiceGuardian", message = "Enrolled voice profile '$cleanName' ($role)"))
        return Result.success(entity.copy(id = id))
    }

    suspend fun verifyCurrentSpeaker(guardianMode: String): SpeakerVerificationResult {
        val wav = audioEngine.getRecentWavBytes()
        if (wav.size <= 4096) {
            return SpeakerVerificationResult(null, 0f, "No recent audio sample in mic buffer", false)
                .also { _lastVerification.value = it }
        }
        val probe = extractSpectralEmbedding(wav)
        val enrolled = securityDao.getVoiceProfilesSnapshot()
        if (enrolled.isEmpty()) {
            return SpeakerVerificationResult(null, 0f, "No enrolled voice profiles yet", false)
                .also { _lastVerification.value = it }
        }

        var bestProfile: VoiceProfileEntity? = null
        var bestScore = -1f
        for (profile in enrolled) {
            val ref = parseEmbedding(profile.embeddingJson)
            val sim = cosine(probe, ref)
            if (sim > bestScore) {
                bestScore = sim
                bestProfile = profile
            }
        }
        val matched = if (bestScore >= 0.78f) bestProfile else null
        val unknownInStrictMode = matched == null && (guardianMode == "BOSS_ONLY" || guardianMode == "GUARD" || guardianMode == "AWAY")
        val nonBossInBossMode = matched != null && matched.role != "BOSS" && guardianMode == "BOSS_ONLY"
        val intrusion = unknownInStrictMode || nonBossInBossMode

        val status = when {
            intrusion -> "Unknown/unauthorized voice detected in $guardianMode mode (${(bestScore * 100).toInt()}%)"
            matched != null -> "Verified ${matched.name} (${matched.role}) — ${(bestScore * 100).toInt()}% match"
            else -> "Unrecognized speaker (${(bestScore * 100).toInt()}%)"
        }
        if (intrusion) {
            _intrusionAlerts.value = listOf("${java.text.DateFormat.getTimeInstance().format(java.util.Date())}: $status") + _intrusionAlerts.value.take(19)
            diagnosticsDao.insertLog(DiagnosticLogEntity(level = "WARN", tag = "VoiceGuardian", message = status))
        }
        return SpeakerVerificationResult(matched, bestScore.coerceIn(0f, 1f), status, intrusion)
            .also { _lastVerification.value = it }
    }

    suspend fun deleteProfile(id: Long) {
        securityDao.deleteVoiceProfile(id)
    }

    /**
     * Enforces Android system device authentication (Biometric / PIN / Pattern) for sensitive operations.
     */
    fun authenticateDeviceUser(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onAuthenticated: () -> Unit,
        onError: (String) -> Unit
    ) {
        val manager = BiometricManager.from(activity)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (manager.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onError("Device lock screen / biometric authentication is not configured on this device.")
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onAuthenticated()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString())
            }
            override fun onAuthenticationFailed() {
                Log.w("VoiceGuardian", "Biometric check did not match")
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(info)
    }

    private fun extractSpectralEmbedding(wavBytes: ByteArray): FloatArray {
        val dim = 16
        val bands = FloatArray(dim)
        val start = 44
        val sampleCount = ((wavBytes.size - start) / 2).coerceAtMost(32000)
        if (sampleCount <= dim) return bands
        val step = (sampleCount / dim).coerceAtLeast(1)
        for (b in 0 until dim) {
            var energy = 0.0
            var zcr = 0
            val offset = start + b * step * 2
            var prev = 0
            for (i in 0 until step) {
                val idx = offset + i * 2
                if (idx + 1 >= wavBytes.size) break
                val s = (wavBytes[idx].toInt() and 0xFF) or (wavBytes[idx + 1].toInt() shl 8)
                val signed = s.toShort().toInt()
                energy += abs(signed.toDouble())
                if ((prev >= 0 && signed < 0) || (prev < 0 && signed >= 0)) zcr++
                prev = signed
            }
            bands[b] = log10(1.0 + energy / step + zcr * 4.0).toFloat()
        }
        var norm = 0f
        for (v in bands) norm += v * v
        val mag = sqrt(norm)
        if (mag > 0f) for (i in bands.indices) bands[i] /= mag
        return bands
    }

    private fun parseEmbedding(json: String): FloatArray {
        return try {
            val arr = JSONArray(json)
            FloatArray(16) { i -> arr.optDouble(i, 0.0).toFloat() }
        } catch (_: Exception) {
            FloatArray(16)
        }
    }

    private fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in 0 until minOf(a.size, b.size)) dot += a[i] * b[i]
        return dot
    }
}
