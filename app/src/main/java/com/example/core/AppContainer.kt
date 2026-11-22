package com.example.core

import android.content.Context
import com.example.agent.AgentOrchestrator
import com.example.agent.ToolRegistry
import com.example.audio.RealTimeAudioEngine
import com.example.data.local.MayaDatabase
import com.example.data.remote.FirestoreSyncRepository
import com.example.faces.FaceRecognitionEngine
import com.example.guardian.VoiceGuardianEngine
import com.example.integrations.CommsIntegration
import com.example.integrations.MediaAndDocumentIntegration
import com.example.integrations.MessagingAndProductivityIntegration
import com.example.integrations.ResearchAndSeoIntegration
import com.example.integrations.TermuxBridge
import com.example.llm.LlmGateway
import com.example.llm.PersonaManager
import com.example.memory.YaadeinMemoryManager
import com.example.security.KeystoreVault
import com.example.stt.SttRouter
import com.example.tts.TtsRouter
import com.example.vad.AudioIntelligenceEngine
import com.example.vision.CameraVisionEngine
import com.example.wake.WakeWordEngine
import com.example.workers.MayaReminderScheduler

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val settingsStore by lazy { SettingsDataStore(appContext) }
    val vault by lazy { KeystoreVault(appContext) }
    val telemetry by lazy { DeviceTelemetry(appContext) }
    val database by lazy { MayaDatabase.getInstance(appContext) }
    val firestoreSync by lazy { FirestoreSyncRepository(appContext) }

    val audioIntelligence by lazy { AudioIntelligenceEngine() }
    val audioEngine by lazy { RealTimeAudioEngine(audioIntelligence) }
    val wakeWordEngine by lazy { WakeWordEngine(appContext, audioIntelligence) }
    val sttRouter by lazy { SttRouter(appContext, audioEngine, vault) }
    val ttsRouter by lazy { TtsRouter(appContext, audioEngine) }

    val personaManager by lazy { PersonaManager() }
    val llmGateway by lazy { LlmGateway(appContext, vault, database.diagnosticsDao()) }
    val memoryManager by lazy { YaadeinMemoryManager(database.memoryDao(), firestoreSync) }

    val commsIntegration by lazy { CommsIntegration(appContext) }
    val termuxBridge by lazy { TermuxBridge(appContext) }
    val researchIntegration by lazy { ResearchAndSeoIntegration(appContext, vault) }
    val messagingIntegration by lazy { MessagingAndProductivityIntegration(appContext, vault, settingsStore) }
    val visionEngine by lazy { CameraVisionEngine(appContext) }
    val mediaIntegration by lazy { MediaAndDocumentIntegration(appContext, visionEngine) }
    val reminderScheduler by lazy { MayaReminderScheduler(appContext) }

    val voiceGuardian by lazy {
        VoiceGuardianEngine(appContext, audioEngine, database.securityProfileDao(), database.diagnosticsDao())
    }
    val faceEngine by lazy {
        FaceRecognitionEngine(appContext, database.securityProfileDao())
    }

    val toolRegistry by lazy { ToolRegistry() }
    val agentOrchestrator by lazy {
        AgentOrchestrator(
            context = appContext,
            registry = toolRegistry,
            memoryManager = memoryManager,
            reminderDao = database.reminderDao(),
            reminderScheduler = reminderScheduler,
            macroRuleDao = database.macroRuleDao(),
            missionDao = database.missionDao(),
            smartHomeDao = database.smartHomeDao(),
            websiteDao = database.websiteDao(),
            comms = commsIntegration,
            termux = termuxBridge,
            research = researchIntegration,
            messaging = messagingIntegration,
            telemetry = telemetry
        )
    }

    fun releaseHeavyResourcesOnLowMemory() {
        audioEngine.clearBuffer()
        ttsRouter.stop()
    }
}
