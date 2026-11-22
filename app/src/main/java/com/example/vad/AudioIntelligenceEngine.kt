package com.example.vad

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

enum class TurnPhase {
    SILENCE,
    SPEECH_ACTIVE,
    END_OF_TURN,
    BARGE_IN
}

data class AudioIntelligenceState(
    val isSpeechDetected: Boolean = false,
    val turnPhase: TurnPhase = TurnPhase.SILENCE,
    val speechProbability: Float = 0f,
    val silenceDurationMs: Long = 0L,
    val addresseeScore: Float = 0f,
    val estimatedSpeakers: Int = 1,
    val acousticMoodLabel: String = "Neutral",
    val vadEngineLabel: String = "Hybrid Energy + ZCR VAD (Silero-compatible slot ready)"
)

/**
 * Lightweight frame-by-frame Voice Activity Detection (VAD) and audio intelligence
 * tuned for 4 GB RAM devices. Tracks speech start/end, end-of-turn hangover,
 * barge-in, silence duration, ZCR-based speaker shift heuristics, and burst emotion cues.
 */
class AudioIntelligenceEngine {
    private val _state = MutableStateFlow(AudioIntelligenceState())
    val state: StateFlow<AudioIntelligenceState> = _state.asStateFlow()

    private var adaptiveFloorDb = -48f
    private var lastSpeechTime = 0L
    private var speechFramesConsecutive = 0
    private var burstTransitions = 0
    private var lastZcr = 0f
    private var speakerChanges = 1

    fun processFrame(
        frame: ShortArray,
        length: Int,
        rms: Double,
        dbfs: Float,
        assistantSpeaking: Boolean
    ) {
        val now = System.currentTimeMillis()
        var zeroCrossings = 0
        for (i in 1 until length) {
            if ((frame[i - 1] >= 0 && frame[i] < 0) || (frame[i - 1] < 0 && frame[i] >= 0)) {
                zeroCrossings++
            }
        }
        val zcr = zeroCrossings.toFloat() / length.coerceAtLeast(1)

        if (dbfs < adaptiveFloorDb + 6f) {
            adaptiveFloorDb = adaptiveFloorDb * 0.96f + dbfs * 0.04f
        }

        val snrDb = (dbfs - adaptiveFloorDb).coerceAtLeast(0f)
        val speechProb = (snrDb / 22f).coerceIn(0f, 1f)
        // Typical voiced speech ZCR sits in 0.03..0.38
        val isVoiceBand = zcr in 0.02f..0.45f
        val isSpeech = speechProb > 0.42f && isVoiceBand && !assistantSpeaking

        val bargeIn = assistantSpeaking && snrDb > 24f && isVoiceBand

        if (isSpeech || bargeIn) {
            speechFramesConsecutive++
            if (abs(zcr - lastZcr) > 0.14f && speechFramesConsecutive > 6) {
                speakerChanges = (speakerChanges + 1).coerceAtMost(3)
            }
            if (speechFramesConsecutive % 4 == 0 && dbfs > -22f) {
                burstTransitions++
            }
            lastSpeechTime = now
        } else {
            speechFramesConsecutive = 0
            if (now - lastSpeechTime > 4000L) {
                speakerChanges = 1
                burstTransitions = 0
            }
        }
        lastZcr = zcr

        val silenceMs = if (lastSpeechTime == 0L) 0L else (now - lastSpeechTime).coerceAtLeast(0L)
        val turnPhase = when {
            bargeIn -> TurnPhase.BARGE_IN
            isSpeech -> TurnPhase.SPEECH_ACTIVE
            silenceMs in 650L..1400L -> TurnPhase.END_OF_TURN
            else -> TurnPhase.SILENCE
        }

        val moodLabel = when {
            burstTransitions >= 5 -> "Laughter / High Energy"
            speechProb > 0.7f && zcr < 0.08f -> "Calm / Low Pitch"
            speechProb > 0.65f -> "Active Speech"
            else -> "Neutral"
        }

        val addresseeScore = if (isSpeech) {
            (0.55f + (speechProb * 0.45f)).coerceIn(0f, 1f)
        } else 0f

        _state.value = AudioIntelligenceState(
            isSpeechDetected = isSpeech,
            turnPhase = turnPhase,
            speechProbability = speechProb,
            silenceDurationMs = silenceMs,
            addresseeScore = addresseeScore,
            estimatedSpeakers = speakerChanges,
            acousticMoodLabel = moodLabel
        )
    }

    fun reset() {
        lastSpeechTime = 0L
        speechFramesConsecutive = 0
        burstTransitions = 0
        speakerChanges = 1
        _state.value = AudioIntelligenceState()
    }
}
