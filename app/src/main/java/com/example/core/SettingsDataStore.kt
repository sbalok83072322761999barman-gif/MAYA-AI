package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mayaDataStore: DataStore<Preferences> by preferencesDataStore(name = "maya_settings")

enum class AiProviderId(val displayName: String, val baseUrl: String?, val requiresApiKey: Boolean) {
    GEMINI_FIREBASE("Google Gemini (Firebase AI Logic)", null, false),
    GEMINI_USER_KEY("Google Gemini (Personal API Key)", "https://generativelanguage.googleapis.com/v1beta/", true),
    OPENAI("OpenAI-Compatible", "https://api.openai.com/v1/", true),
    GROQ("Groq", "https://api.groq.com/openai/v1/", true),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1/", true),
    TOGETHER("Together AI", "https://api.together.xyz/v1/", true),
    NVIDIA_NIM("NVIDIA NIM", "https://integrate.api.nvidia.com/v1/", true),
    ANTHROPIC("Anthropic Claude", "https://api.anthropic.com/v1/", true),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1/", true);

    companion object {
        fun fromId(raw: String): AiProviderId =
            entries.firstOrNull { it.name == raw } ?: GEMINI_FIREBASE
    }
}

enum class TargetFramework(
    val title: String,
    val subtitle: String,
    val systemGuidance: String,
    val supportsLocalPreview: Boolean
) {
    ANDROID_COMPOSE(
        title = "Android (Kotlin + Jetpack Compose)",
        subtitle = "Native Android 16 / Material 3 / MVVM target",
        systemGuidance = "Generate idiomatic Kotlin, Jetpack Compose Material 3, Coroutines/Flow, and Android SDK solutions.",
        supportsLocalPreview = false
    ),
    WEB_STATIC(
        title = "Web (HTML5 + CSS3 + JavaScript)",
        subtitle = "Responsive web bundle with built-in WebView live preview",
        systemGuidance = "Generate self-contained, responsive HTML5, modern CSS3, and vanilla ES6+ JavaScript.",
        supportsLocalPreview = true
    ),
    PYTHON_TERMUX(
        title = "Python (Termux CLI / FastAPI)",
        subtitle = "Executable via Termux RUN_COMMAND bridge when Termux is installed",
        systemGuidance = "Generate clean Python 3.11+ scripts compatible with Termux non-root Android execution.",
        supportsLocalPreview = false
    ),
    NODEJS_TERMUX(
        title = "Node.js (Termux / Express)",
        subtitle = "Executable via Termux RUN_COMMAND bridge when Termux is installed",
        systemGuidance = "Generate Node.js LTS modules compatible with Termux Android non-root execution.",
        supportsLocalPreview = false
    );

    companion object {
        fun fromId(raw: String): TargetFramework =
            entries.firstOrNull { it.name == raw } ?: ANDROID_COMPOSE
    }
}

enum class AssistantPersona(val displayName: String, val tagline: String) {
    MAYA("Maya", "Warm, natural, expressive Hindi/Hinglish/English companion"),
    FRIDAY("Friday", "Crisp, executive, analytical & high-precision assistant"),
    VENOM("Venom", "Bold, sharp-witted, energetic & unfiltered strategist");

    companion object {
        fun fromId(raw: String): AssistantPersona =
            entries.firstOrNull { it.name == raw } ?: MAYA
    }
}

data class MayaSettingsState(
    val onboardingCompleted: Boolean = false,
    val localOfflineMode: Boolean = false,
    val activeProvider: AiProviderId = AiProviderId.GEMINI_FIREBASE,
    val selectedModelId: String = "gemini-3.8-flash",
    val activeFramework: TargetFramework = TargetFramework.ANDROID_COMPOSE,
    val customInstructions: String = DEFAULT_CUSTOM_INSTRUCTIONS,
    val customInstructionsEnabled: Boolean = true,
    val persona: AssistantPersona = AssistantPersona.MAYA,
    val customAssistantName: String = "Maya",
    val languagePreference: String = "Hinglish",
    val genderTone: String = "Feminine",
    val voiceRate: Float = 1.0f,
    val voicePitch: Float = 1.0f,
    val selectedVoiceName: String = "",
    val sttLanguageCode: String = "en-IN",
    val sttProvider: String = "ANDROID",
    val ttsProvider: String = "ANDROID",
    val wakeWordEnabled: Boolean = false,
    val wakeWordSensitivity: Float = 0.65f,
    val aecEnabled: Boolean = true,
    val dtlnFilterEnabled: Boolean = false,
    val noiseSuppressionEnabled: Boolean = true,
    val agcEnabled: Boolean = true,
    val bargeInEnabled: Boolean = true,
    val autoMemorySave: Boolean = true,
    val lowPowerMode: Boolean = true, // Enabled by default for 4GB RAM Vivo Y21 5G optimization
    val drivingMode: Boolean = false,
    val guardianMode: String = "EVERYONE",
    val faceWatchmanEnabled: Boolean = false,
    val avatarStyle: String = "NEURAL_CORE",
    val orbSpeedScale: Float = 1.0f,
    val cloudSyncEnabled: Boolean = true,
    val openaiBaseUrlOverride: String = "https://api.openai.com/v1/",
    val smtpHost: String = "",
    val smtpPort: Int = 465,
    val smtpEmail: String = "",
    val imapHost: String = "",
    val imapPort: Int = 993,
    val telegramChatId: String = "",
    val githubOwnerRepo: String = ""
) {
    companion object {
        const val DEFAULT_CUSTOM_INSTRUCTIONS =
            "Be accurate, concise, and practical. Clearly distinguish verified tool/API outputs from estimates. Never claim an action succeeded unless its tool returned success."
    }
}

class SettingsDataStore(private val context: Context) {
    private val ds = context.applicationContext.mayaDataStore

    val settingsFlow: Flow<MayaSettingsState> = ds.data.map { prefs ->
        MayaSettingsState(
            onboardingCompleted = prefs[K_ONBOARDING_DONE] ?: false,
            localOfflineMode = prefs[K_LOCAL_MODE] ?: false,
            activeProvider = AiProviderId.fromId(prefs[K_ACTIVE_PROVIDER] ?: AiProviderId.GEMINI_FIREBASE.name),
            selectedModelId = prefs[K_SELECTED_MODEL] ?: "gemini-3.8-flash",
            activeFramework = TargetFramework.fromId(prefs[K_ACTIVE_FRAMEWORK] ?: TargetFramework.ANDROID_COMPOSE.name),
            customInstructions = prefs[K_CUSTOM_INSTRUCTIONS] ?: MayaSettingsState.DEFAULT_CUSTOM_INSTRUCTIONS,
            customInstructionsEnabled = prefs[K_CUSTOM_INSTRUCTIONS_ENABLED] ?: true,
            persona = AssistantPersona.fromId(prefs[K_PERSONA] ?: AssistantPersona.MAYA.name),
            customAssistantName = prefs[K_CUSTOM_NAME] ?: "Maya",
            languagePreference = prefs[K_LANGUAGE_PREF] ?: "Hinglish",
            genderTone = prefs[K_GENDER_TONE] ?: "Feminine",
            voiceRate = prefs[K_VOICE_RATE] ?: 1.0f,
            voicePitch = prefs[K_VOICE_PITCH] ?: 1.0f,
            selectedVoiceName = prefs[K_VOICE_NAME] ?: "",
            sttLanguageCode = prefs[K_STT_LANG] ?: "en-IN",
            sttProvider = prefs[K_STT_PROVIDER] ?: "ANDROID",
            ttsProvider = prefs[K_TTS_PROVIDER] ?: "ANDROID",
            wakeWordEnabled = prefs[K_WAKE_WORD_ENABLED] ?: false,
            wakeWordSensitivity = prefs[K_WAKE_SENSITIVITY] ?: 0.65f,
            aecEnabled = prefs[K_AEC_ENABLED] ?: true,
            dtlnFilterEnabled = prefs[K_DTLN_ENABLED] ?: false,
            noiseSuppressionEnabled = prefs[K_NS_ENABLED] ?: true,
            agcEnabled = prefs[K_AGC_ENABLED] ?: true,
            bargeInEnabled = prefs[K_BARGE_IN_ENABLED] ?: true,
            autoMemorySave = prefs[K_AUTO_MEMORY] ?: true,
            lowPowerMode = prefs[K_LOW_POWER] ?: true,
            drivingMode = prefs[K_DRIVING_MODE] ?: false,
            guardianMode = prefs[K_GUARDIAN_MODE] ?: "EVERYONE",
            faceWatchmanEnabled = prefs[K_FACE_WATCHMAN] ?: false,
            avatarStyle = prefs[K_AVATAR_STYLE] ?: "NEURAL_CORE",
            orbSpeedScale = prefs[K_ORB_SPEED] ?: 1.0f,
            cloudSyncEnabled = prefs[K_CLOUD_SYNC] ?: true,
            openaiBaseUrlOverride = prefs[K_OPENAI_BASE_URL] ?: "https://api.openai.com/v1/",
            smtpHost = prefs[K_SMTP_HOST] ?: "",
            smtpPort = prefs[K_SMTP_PORT] ?: 465,
            smtpEmail = prefs[K_SMTP_EMAIL] ?: "",
            imapHost = prefs[K_IMAP_HOST] ?: "",
            imapPort = prefs[K_IMAP_PORT] ?: 993,
            telegramChatId = prefs[K_TELEGRAM_CHAT_ID] ?: "",
            githubOwnerRepo = prefs[K_GITHUB_REPO] ?: ""
        )
    }

    suspend fun updateOnboardingCompleted(done: Boolean) = edit { it[K_ONBOARDING_DONE] = done }
    suspend fun updateLocalOfflineMode(enabled: Boolean) = edit { it[K_LOCAL_MODE] = enabled }
    suspend fun updateProviderAndModel(provider: AiProviderId, modelId: String) = edit {
        it[K_ACTIVE_PROVIDER] = provider.name
        it[K_SELECTED_MODEL] = modelId
    }
    suspend fun updateSelectedModel(modelId: String) = edit { it[K_SELECTED_MODEL] = modelId }
    suspend fun updateFramework(framework: TargetFramework) = edit { it[K_ACTIVE_FRAMEWORK] = framework.name }
    suspend fun updateCustomInstructions(text: String) = edit { it[K_CUSTOM_INSTRUCTIONS] = text }
    suspend fun updateCustomInstructionsEnabled(enabled: Boolean) = edit { it[K_CUSTOM_INSTRUCTIONS_ENABLED] = enabled }
    suspend fun resetCustomInstructions() = edit {
        it[K_CUSTOM_INSTRUCTIONS] = MayaSettingsState.DEFAULT_CUSTOM_INSTRUCTIONS
        it[K_CUSTOM_INSTRUCTIONS_ENABLED] = true
    }
    suspend fun clearCustomInstructions() = edit {
        it[K_CUSTOM_INSTRUCTIONS] = ""
        it[K_CUSTOM_INSTRUCTIONS_ENABLED] = false
    }
    suspend fun updatePersona(persona: AssistantPersona, customName: String? = null) = edit {
        it[K_PERSONA] = persona.name
        if (!customName.isNullOrBlank()) it[K_CUSTOM_NAME] = customName
    }
    suspend fun updateLanguageAndTone(lang: String, gender: String) = edit {
        it[K_LANGUAGE_PREF] = lang
        it[K_GENDER_TONE] = gender
    }
    suspend fun updateVoiceParams(rate: Float, pitch: Float, voiceName: String) = edit {
        it[K_VOICE_RATE] = rate
        it[K_VOICE_PITCH] = pitch
        it[K_VOICE_NAME] = voiceName
    }
    suspend fun updateSttConfig(langCode: String, provider: String) = edit {
        it[K_STT_LANG] = langCode
        it[K_STT_PROVIDER] = provider
    }
    suspend fun updateTtsProvider(provider: String) = edit { it[K_TTS_PROVIDER] = provider }
    suspend fun updateWakeWord(enabled: Boolean, sensitivity: Float) = edit {
        it[K_WAKE_WORD_ENABLED] = enabled
        it[K_WAKE_SENSITIVITY] = sensitivity
    }
    suspend fun updateAudioProcessing(aec: Boolean, dtln: Boolean, ns: Boolean, agc: Boolean, bargeIn: Boolean) = edit {
        it[K_AEC_ENABLED] = aec
        it[K_DTLN_ENABLED] = dtln
        it[K_NS_ENABLED] = ns
        it[K_AGC_ENABLED] = agc
        it[K_BARGE_IN_ENABLED] = bargeIn
    }
    suspend fun updateAutoMemorySave(enabled: Boolean) = edit { it[K_AUTO_MEMORY] = enabled }
    suspend fun updateLowPowerMode(enabled: Boolean) = edit { it[K_LOW_POWER] = enabled }
    suspend fun updateDrivingMode(enabled: Boolean) = edit { it[K_DRIVING_MODE] = enabled }
    suspend fun updateGuardianMode(mode: String) = edit { it[K_GUARDIAN_MODE] = mode }
    suspend fun updateFaceWatchman(enabled: Boolean) = edit { it[K_FACE_WATCHMAN] = enabled }
    suspend fun updateAvatarConfig(style: String, speedScale: Float) = edit {
        it[K_AVATAR_STYLE] = style
        it[K_ORB_SPEED] = speedScale
    }
    suspend fun updateCloudSyncEnabled(enabled: Boolean) = edit { it[K_CLOUD_SYNC] = enabled }
    suspend fun updateOpenAiBaseUrl(url: String) = edit { it[K_OPENAI_BASE_URL] = url }
    suspend fun updateEmailConfig(smtpHost: String, smtpPort: Int, email: String, imapHost: String, imapPort: Int) = edit {
        it[K_SMTP_HOST] = smtpHost
        it[K_SMTP_PORT] = smtpPort
        it[K_SMTP_EMAIL] = email
        it[K_IMAP_HOST] = imapHost
        it[K_IMAP_PORT] = imapPort
    }
    suspend fun updateTelegramChatId(chatId: String) = edit { it[K_TELEGRAM_CHAT_ID] = chatId }
    suspend fun updateGithubRepo(repo: String) = edit { it[K_GITHUB_REPO] = repo }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        ds.edit(block)
    }

    companion object {
        private val K_ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        private val K_LOCAL_MODE = booleanPreferencesKey("local_offline_mode")
        private val K_ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        private val K_SELECTED_MODEL = stringPreferencesKey("selected_model")
        private val K_ACTIVE_FRAMEWORK = stringPreferencesKey("active_framework")
        private val K_CUSTOM_INSTRUCTIONS = stringPreferencesKey("custom_instructions")
        private val K_CUSTOM_INSTRUCTIONS_ENABLED = booleanPreferencesKey("custom_instructions_enabled")
        private val K_PERSONA = stringPreferencesKey("persona")
        private val K_CUSTOM_NAME = stringPreferencesKey("custom_assistant_name")
        private val K_LANGUAGE_PREF = stringPreferencesKey("language_pref")
        private val K_GENDER_TONE = stringPreferencesKey("gender_tone")
        private val K_VOICE_RATE = floatPreferencesKey("voice_rate")
        private val K_VOICE_PITCH = floatPreferencesKey("voice_pitch")
        private val K_VOICE_NAME = stringPreferencesKey("voice_name")
        private val K_STT_LANG = stringPreferencesKey("stt_lang")
        private val K_STT_PROVIDER = stringPreferencesKey("stt_provider")
        private val K_TTS_PROVIDER = stringPreferencesKey("tts_provider")
        private val K_WAKE_WORD_ENABLED = booleanPreferencesKey("wake_word_enabled")
        private val K_WAKE_SENSITIVITY = floatPreferencesKey("wake_word_sensitivity")
        private val K_AEC_ENABLED = booleanPreferencesKey("aec_enabled")
        private val K_DTLN_ENABLED = booleanPreferencesKey("dtln_enabled")
        private val K_NS_ENABLED = booleanPreferencesKey("ns_enabled")
        private val K_AGC_ENABLED = booleanPreferencesKey("agc_enabled")
        private val K_BARGE_IN_ENABLED = booleanPreferencesKey("barge_in_enabled")
        private val K_AUTO_MEMORY = booleanPreferencesKey("auto_memory_save")
        private val K_LOW_POWER = booleanPreferencesKey("low_power_mode")
        private val K_DRIVING_MODE = booleanPreferencesKey("driving_mode")
        private val K_GUARDIAN_MODE = stringPreferencesKey("guardian_mode")
        private val K_FACE_WATCHMAN = booleanPreferencesKey("face_watchman")
        private val K_AVATAR_STYLE = stringPreferencesKey("avatar_style")
        private val K_ORB_SPEED = floatPreferencesKey("orb_speed")
        private val K_CLOUD_SYNC = booleanPreferencesKey("cloud_sync")
        private val K_OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        private val K_SMTP_HOST = stringPreferencesKey("smtp_host")
        private val K_SMTP_PORT = intPreferencesKey("smtp_port")
        private val K_SMTP_EMAIL = stringPreferencesKey("smtp_email")
        private val K_IMAP_HOST = stringPreferencesKey("imap_host")
        private val K_IMAP_PORT = intPreferencesKey("imap_port")
        private val K_TELEGRAM_CHAT_ID = stringPreferencesKey("telegram_chat_id")
        private val K_GITHUB_REPO = stringPreferencesKey("github_repo")
    }
}
