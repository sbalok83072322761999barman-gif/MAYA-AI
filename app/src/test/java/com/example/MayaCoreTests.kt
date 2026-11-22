package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.agent.ToolRegistry
import com.example.core.AssistantPersona
import com.example.core.MayaSettingsState
import com.example.core.TargetFramework
import com.example.data.local.MayaDatabase
import com.example.data.remote.FirestoreSyncRepository
import com.example.integrations.TermuxBridge
import com.example.llm.PersonaManager
import com.example.memory.YaadeinMemoryManager
import com.example.security.KeystoreVault
import com.example.vad.AudioIntelligenceEngine
import com.example.vad.TurnPhase
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MayaCoreTests {

    private lateinit var context: Context
    private lateinit var db: MayaDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        db = Room.inMemoryDatabaseBuilder(context, MayaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun `credential sanitizer rejects secrets and permits normal facts`() {
        assertTrue(KeystoreVault.containsSensitiveCredential("My OTP is 492811"))
        assertTrue(KeystoreVault.containsSensitiveCredential("password: supersecret123"))
        assertTrue(KeystoreVault.containsSensitiveCredential("api_key=sk-1234567890abcdef1234"))
        assertFalse(KeystoreVault.containsSensitiveCredential("I prefer Jetpack Compose Material 3 dark mode"))
    }

    @Test
    fun `persona manager injects active framework and toggled custom instructions`() {
        val pm = PersonaManager()
        val enabledSettings = MayaSettingsState(
            persona = AssistantPersona.FRIDAY,
            activeFramework = TargetFramework.PYTHON_TERMUX,
            customInstructions = "Always return concise JSON when asked for schemas.",
            customInstructionsEnabled = true
        )
        val promptEnabled = pm.buildSystemPrompt(enabledSettings, listOf("Likes Kotlin"), "tools")
        assertTrue(promptEnabled.contains("Python (Termux"))
        assertTrue(promptEnabled.contains("Always return concise JSON"))
        assertTrue(promptEnabled.contains("Likes Kotlin"))

        val disabledSettings = enabledSettings.copy(customInstructionsEnabled = false)
        val promptDisabled = pm.buildSystemPrompt(disabledSettings)
        assertFalse(promptDisabled.contains("Always return concise JSON"))
    }

    @Test
    fun `tool registry validates bounds and discovers tools`() {
        val reg = ToolRegistry()
        assertTrue(reg.tools.size >= 40)
        assertTrue(reg.findTool("weather").any { it.name == "get_weather" })
        val spec = reg.getSpec("search_web")
        assertNotNull(spec)
        val oversized = JSONObject().put("query", "a".repeat(8001))
        assertNotNull(reg.validateArgs(spec!!, oversized))
    }

    @Test
    fun `termux bridge blocks destructive and root commands`() {
        val bridge = TermuxBridge(context)
        assertFalse(bridge.isSafeCommand("rm -rf /").first)
        assertFalse(bridge.isSafeCommand("sudo apt update").first)
        assertTrue(bridge.isSafeCommand("python3 --version").first)
    }

    @Test
    fun `vad engine transitions from silence to active speech`() {
        val vad = AudioIntelligenceEngine()
        val silent = ShortArray(512) { 0 }
        vad.processFrame(silent, 512, 0.0, -70f, assistantSpeaking = false)
        assertFalse(vad.state.value.isSpeechDetected)

        val voiced = ShortArray(512) { i -> if (i % 16 < 8) 9000 else -9000 }
        vad.processFrame(voiced, 512, 9000.0, -11f, assistantSpeaking = false)
        assertTrue(vad.state.value.isSpeechDetected)
        assertEquals(TurnPhase.SPEECH_ACTIVE, vad.state.value.turnPhase)
    }

    @Test
    fun `yaadein memory stores, recalls, sanitizes, and exports backup`() = runBlocking {
        val syncRepo = FirestoreSyncRepository(context)
        val mgr = YaadeinMemoryManager(db.memoryDao(), syncRepo)

        val blocked = mgr.rememberFact("My ATM PIN is 8844", syncToCloud = false)
        assertTrue(blocked.isFailure)

        val ok = mgr.rememberFact("Target device is Vivo Y21 5G with 4GB RAM", "device", 5, syncToCloud = false)
        assertTrue(ok.isSuccess)

        val recalled = mgr.recallMemory("Vivo Y21 RAM")
        assertTrue(recalled.any { it.content.contains("Vivo Y21 5G") })

        val backupJson = mgr.exportBackupJson()
        assertTrue(backupJson.contains("Vivo Y21 5G"))
    }
}
