package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MayaApplication
import com.example.R
import com.example.agent.PendingToolConfirmation
import com.example.agent.ToolExecutionResult
import com.example.audio.MicState
import com.example.core.AiProviderId
import com.example.core.AssistantPersona
import com.example.core.DeviceHealthSnapshot
import com.example.core.MayaSettingsState
import com.example.core.TargetFramework
import com.example.data.local.ChatMessageEntity
import com.example.data.local.DiagnosticLogEntity
import com.example.data.local.MemoryEntity
import com.example.data.remote.CloudMemoryRecord
import com.example.llm.ChatTurnInput
import com.example.llm.DiscoveredModel
import com.example.llm.ProviderHealth
import com.example.llm.StreamChunk
import com.example.security.KeystoreVault
import com.example.services.MayaAssistantService
import com.example.tts.SpeechEmotion
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AuthUiState(
    val user: FirebaseUser? = null,
    val isSigningIn: Boolean = false,
    val authError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MayaMainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MayaApplication
    val container = app.container

    private val auth: FirebaseAuth = Firebase.auth
    private val credentialManager by lazy { CredentialManager.create(app) }

    val settings: StateFlow<MayaSettingsState> = container.settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, MayaSettingsState())

    private val _authState = MutableStateFlow(AuthUiState(user = auth.currentUser))
    val authState: StateFlow<AuthUiState> = _authState.asStateFlow()

    val discoveredModels: StateFlow<List<DiscoveredModel>> = container.llmGateway.discoveredModels
    val isRefreshingModels: StateFlow<Boolean> = container.llmGateway.isRefreshingModels
    val providerHealth: StateFlow<ProviderHealth?> = container.llmGateway.providerHealth

    val chatMessages: StateFlow<List<ChatMessageEntity>> = container.database.chatDao()
        .observeSession("default")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryEntity>> = container.memoryManager.allMemories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cloudMemories: StateFlow<List<CloudMemoryRecord>> = observeAuthUidFlow()
        .flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else container.firestoreSync.observeCloudMemories()
                .catch { e ->
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Cloud memories listener error", e)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val usageRecords = container.database.diagnosticsDao().observeUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val diagnosticLogs = container.database.diagnosticsDao().observeLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val micState: StateFlow<MicState> = container.audioEngine.micState
    val rmsLevel: StateFlow<Float> = container.audioEngine.rmsLevel
    val spectrumBars: StateFlow<List<Float>> = container.audioEngine.spectrumBars
    val audioDiagnostics = container.audioEngine.diagnostics
    val audioIntelligence = container.audioEngine.intelligenceState
    val wakeWordState = container.wakeWordEngine.state
    val sttState = container.sttRouter.state
    val ttsState = container.ttsRouter.state
    val assistantServiceRunning = MayaAssistantService.isRunning
    val pendingConfirmation: StateFlow<PendingToolConfirmation?> = container.agentOrchestrator.pendingConfirmation
    val touchGuardEnabled = container.agentOrchestrator.touchGuardEnabled

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _streamingReply = MutableStateFlow("")
    val streamingReply: StateFlow<String> = _streamingReply.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _deviceHealth = MutableStateFlow(container.telemetry.captureSnapshot())
    val deviceHealth: StateFlow<DeviceHealthSnapshot> = _deviceHealth.asStateFlow()

    private val _memorySearchQuery = MutableStateFlow("")
    val memorySearchQuery: StateFlow<String> = _memorySearchQuery.asStateFlow()

    private val _recalledMemories = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val recalledMemories: StateFlow<List<MemoryEntity>> = _recalledMemories.asStateFlow()

    private val _vaultRefreshTick = MutableStateFlow(0)
    val vaultRefreshTick: StateFlow<Int> = _vaultRefreshTick.asStateFlow()

    private var chatJob: Job? = null
    private var autoSignInAttempted = false

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        _authState.value = _authState.value.copy(user = firebaseAuth.currentUser)
    }

    init {
        auth.addAuthStateListener(authListener)
        viewModelScope.launch {
            container.agentOrchestrator.seedBuiltInRulesIfEmpty()
        }
        container.audioEngine.setOnBargeIn {
            container.ttsRouter.stop()
        }
        container.wakeWordEngine.setOnWakeDetected { phrase ->
            _statusBanner.value = "Wake phrase detected: \"$phrase\""
            viewModelScope.launch {
                val cfg = settings.value
                container.ttsRouter.speak("Yes, I am listening.", SpeechEmotion.HAPPY, cfg.sttLanguageCode)
            }
        }
    }

    private fun observeAuthUidFlow(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            trySend(fa.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        trySend(auth.currentUser?.uid)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun showBanner(msg: String) {
        _statusBanner.value = msg
    }

    fun refreshTelemetry() {
        _deviceHealth.value = container.telemetry.captureSnapshot()
    }

    // --- Authentication (Google Sign-In via Credential Manager + Local Mode) ---

    fun attemptAutoSignIn(activityContext: Context) {
        if (autoSignInAttempted || auth.currentUser != null) return
        autoSignInAttempted = true
        viewModelScope.launch {
            try {
                val serverClientId = app.getString(R.string.default_web_client_id)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(serverClientId)
                    .setAutoSelectEnabled(true)
                    .build()
                val req = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val res = credentialManager.getCredential(activityContext, req)
                handleGoogleCredential(res.credential)
            } catch (_: NoCredentialException) {
                // Silent check: no saved authorized account
            } catch (_: GetCredentialCancellationException) {
                // Silent check cancelled
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d(TAG, "Silent Google sign-in skipped: ${e.localizedMessage}")
            }
        }
    }

    fun signInWithGoogle(activityContext: Context) {
        if (_authState.value.isSigningIn) return
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isSigningIn = true, authError = null)
            try {
                val serverClientId = app.getString(R.string.default_web_client_id)
                val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
                val req = GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build()
                val result = credentialManager.getCredential(activityContext, req)
                handleGoogleCredential(result.credential)
                container.settingsStore.updateLocalOfflineMode(false)
                container.settingsStore.updateOnboardingCompleted(true)
            } catch (_: GetCredentialCancellationException) {
                _authState.value = _authState.value.copy(isSigningIn = false, authError = null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Google Sign-In failed", e)
                _authState.value = _authState.value.copy(
                    isSigningIn = false,
                    authError = e.localizedMessage ?: "Google Sign-In failed. Check network or device Google account."
                )
            }
        }
    }

    private suspend fun handleGoogleCredential(credential: androidx.credentials.Credential) {
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
            val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCred).await()
            val user = authResult.user
            _authState.value = AuthUiState(user = user, isSigningIn = false, authError = null)
            if (user != null) {
                syncProfileToFirestore()
                _statusBanner.value = "Signed in as ${user.displayName ?: user.email}"
            }
        } else {
            _authState.value = _authState.value.copy(isSigningIn = false, authError = "Unsupported credential type returned.")
        }
    }

    fun signOut() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                auth.signOut()
                runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            }
            _authState.value = AuthUiState(user = null, isSigningIn = false, authError = null)
            _statusBanner.value = "Signed out safely."
        }
    }

    fun continueInLocalOfflineMode() {
        viewModelScope.launch {
            container.settingsStore.updateLocalOfflineMode(true)
            container.settingsStore.updateOnboardingCompleted(true)
            _statusBanner.value = "Local device mode active. Cloud Firestore sync stays disabled until you sign in."
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            container.settingsStore.updateOnboardingCompleted(true)
        }
    }

    private suspend fun syncProfileToFirestore() {
        val user = auth.currentUser ?: return
        val s = settings.value
        container.firestoreSync.upsertUserProfile(
            displayName = user.displayName ?: "Maya User",
            email = user.email,
            activeProvider = s.activeProvider.name,
            activeModel = s.selectedModelId,
            activeFramework = s.activeFramework.name,
            activePersona = s.persona.name,
            customInstructions = s.customInstructions,
            customInstructionsEnabled = s.customInstructionsEnabled
        )
    }

    // --- Advanced Settings: Model Discovery, Framework, Custom Instructions, Vault ---

    fun selectProvider(provider: AiProviderId) {
        viewModelScope.launch {
            val currentModels = if (provider == AiProviderId.GEMINI_FIREBASE) {
                container.llmGateway.defaultFirebaseGeminiModels()
            } else {
                emptyList()
            }
            val nextModel = currentModels.firstOrNull()?.id ?: settings.value.selectedModelId
            container.settingsStore.updateProviderAndModel(provider, nextModel)
            refreshDiscoveredModels(provider)
        }
    }

    fun selectModel(modelId: String) {
        viewModelScope.launch {
            container.settingsStore.updateSelectedModel(modelId)
            syncProfileToFirestore()
            _statusBanner.value = "Active model set to $modelId"
        }
    }

    fun refreshDiscoveredModels(provider: AiProviderId = settings.value.activeProvider) {
        viewModelScope.launch {
            val res = container.llmGateway.discoverModels(provider, settings.value.openaiBaseUrlOverride)
            res.onSuccess { list ->
                if (list.isNotEmpty() && list.none { it.id == settings.value.selectedModelId }) {
                    container.settingsStore.updateSelectedModel(list.first().id)
                }
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Model discovery failed"
            }
        }
    }

    fun testProviderHealth() {
        viewModelScope.launch {
            val health = container.llmGateway.testProviderConnection(settings.value)
            _statusBanner.value = health.message
        }
    }

    fun selectFramework(framework: TargetFramework) {
        viewModelScope.launch {
            container.settingsStore.updateFramework(framework)
            syncProfileToFirestore()
            _statusBanner.value = "Target framework: ${framework.title}"
        }
    }

    fun saveCustomInstructions(text: String, enabled: Boolean) {
        viewModelScope.launch {
            container.settingsStore.updateCustomInstructions(text)
            container.settingsStore.updateCustomInstructionsEnabled(enabled)
            syncProfileToFirestore()
            _statusBanner.value = if (enabled) "Custom instructions saved and active." else "Custom instructions saved (disabled)."
        }
    }

    fun toggleCustomInstructions(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsStore.updateCustomInstructionsEnabled(enabled)
            syncProfileToFirestore()
        }
    }

    fun resetCustomInstructions() {
        viewModelScope.launch {
            container.settingsStore.resetCustomInstructions()
            syncProfileToFirestore()
            _statusBanner.value = "Reset to default system instructions."
        }
    }

    fun deleteCustomInstructions() {
        viewModelScope.launch {
            container.settingsStore.clearCustomInstructions()
            syncProfileToFirestore()
            _statusBanner.value = "Custom instructions cleared."
        }
    }

    fun updateOpenAiBaseUrl(url: String) {
        viewModelScope.launch {
            container.settingsStore.updateOpenAiBaseUrl(url.trim())
        }
    }

    fun saveVaultSecret(key: String, value: String) {
        if (value.isBlank()) {
            container.vault.removeSecret(key)
            _statusBanner.value = "Removed secret entry."
        } else {
            container.vault.putSecret(key, value.trim())
            _statusBanner.value = "Encrypted & saved in AndroidKeyStore vault."
        }
        _vaultRefreshTick.value++
    }

    fun hasVaultSecret(key: String): Boolean = container.vault.hasSecret(key)

    fun updateAudioProcessing(aec: Boolean, dtln: Boolean, ns: Boolean, agc: Boolean, bargeIn: Boolean) {
        viewModelScope.launch {
            container.settingsStore.updateAudioProcessing(aec, dtln, ns, agc, bargeIn)
            container.audioEngine.configureProcessing(dtln, bargeIn)
        }
    }

    fun updateLowPowerMode(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.updateLowPowerMode(enabled) }
    }

    fun updateAutoMemorySave(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.updateAutoMemorySave(enabled) }
    }

    fun updateCloudSyncEnabled(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.updateCloudSyncEnabled(enabled) }
    }

    fun updatePersonaAndVoice(
        persona: AssistantPersona,
        customName: String,
        language: String,
        genderTone: String,
        rate: Float,
        pitch: Float,
        voiceName: String
    ) {
        viewModelScope.launch {
            container.settingsStore.updatePersona(persona, customName)
            container.settingsStore.updateLanguageAndTone(language, genderTone)
            container.settingsStore.updateVoiceParams(rate, pitch, voiceName)
            container.ttsRouter.configureVoice(rate, pitch, voiceName)
            syncProfileToFirestore()
            _statusBanner.value = "Persona '$customName' updated."
        }
    }

    fun updateAvatarSettings(style: String, speedScale: Float) {
        viewModelScope.launch {
            container.settingsStore.updateAvatarConfig(style, speedScale)
        }
    }

    fun updateGuardianMode(mode: String) {
        viewModelScope.launch {
            container.settingsStore.updateGuardianMode(mode)
        }
    }

    fun updateFaceWatchman(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsStore.updateFaceWatchman(enabled)
        }
    }

    // --- Real-Time Audio, Wake Word, STT & TTS ---

    fun toggleLiveMic() {
        val s = settings.value
        if (micState.value == MicState.IDLE || micState.value == MicState.ERROR) {
            val started = container.audioEngine.startCapture(
                useAec = s.aecEnabled,
                useNs = s.noiseSuppressionEnabled,
                useAgc = s.agcEnabled,
                dtlnFilter = s.dtlnFilterEnabled,
                bargeIn = s.bargeInEnabled
            )
            if (!started) {
                _statusBanner.value = audioDiagnostics.value.lastError ?: "Grant RECORD_AUDIO permission to enable microphone."
            }
        } else {
            container.audioEngine.stopCapture()
        }
    }

    fun toggleWakeWord(enabled: Boolean, sensitivity: Float = settings.value.wakeWordSensitivity) {
        viewModelScope.launch {
            container.settingsStore.updateWakeWord(enabled, sensitivity)
            if (enabled) {
                container.wakeWordEngine.start(sensitivity)
            } else {
                container.wakeWordEngine.stop()
            }
        }
    }

    fun startForegroundAssistant(drivingMode: Boolean) {
        viewModelScope.launch {
            container.settingsStore.updateDrivingMode(drivingMode)
            val intent = Intent(app, MayaAssistantService::class.java).apply {
                action = MayaAssistantService.ACTION_START
                putExtra(MayaAssistantService.EXTRA_DRIVING_MODE, drivingMode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        }
    }

    fun stopForegroundAssistant() {
        viewModelScope.launch {
            container.settingsStore.updateDrivingMode(false)
            val intent = Intent(app, MayaAssistantService::class.java).setAction(MayaAssistantService.ACTION_STOP)
            app.startService(intent)
        }
    }

    fun setSttConfig(languageCode: String, provider: com.example.stt.SttProvider) {
        viewModelScope.launch {
            container.settingsStore.updateSttConfig(languageCode, provider.name)
            container.sttRouter.setLanguage(languageCode)
            container.sttRouter.setProvider(provider)
        }
    }

    fun startVoiceTurn(speakReply: Boolean = true) {
        val s = settings.value
        val sttProv = runCatching { com.example.stt.SttProvider.valueOf(s.sttProvider) }
            .getOrDefault(com.example.stt.SttProvider.ANDROID)

        if (sttProv == com.example.stt.SttProvider.ANDROID) {
            container.sttRouter.startAndroidRecognition(s.sttLanguageCode) { transcript, _ ->
                if (transcript.isNotBlank()) {
                    sendChatMessage(transcript, speakOutput = speakReply)
                }
            }
        } else {
            viewModelScope.launch {
                val res = container.sttRouter.transcribeRecordedAudioWithCloud(sttProv)
                res.onSuccess { transcript ->
                    if (transcript.isNotBlank()) {
                        sendChatMessage(transcript, speakOutput = speakReply)
                    } else {
                        _statusBanner.value = "Transcription returned empty speech."
                    }
                }.onFailure { err ->
                    _statusBanner.value = err.localizedMessage ?: "STT failed"
                }
            }
        }
    }

    fun stopVoiceTurn() {
        container.sttRouter.stopListening()
    }

    fun stopSpeaking() {
        container.ttsRouter.stop()
    }

    fun speakText(text: String, emotion: SpeechEmotion = SpeechEmotion.NEUTRAL) {
        val s = settings.value
        container.ttsRouter.configureVoice(s.voiceRate, s.voicePitch, s.selectedVoiceName)
        container.ttsRouter.speak(text, emotion, s.sttLanguageCode)
    }

    fun playDiagnosticTone() {
        container.audioEngine.playDiagnosticTone()
    }

    // --- Streaming Chat + Yaadein Recall + Agent Tool Calling ---

    fun sendChatMessage(
        rawText: String,
        attachedImageUri: Uri? = null,
        attachedBitmap: Bitmap? = null,
        speakOutput: Boolean = false
    ) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty() && attachedImageUri == null && attachedBitmap == null) return
        val prompt = trimmed.ifEmpty { "Analyze this attached image thoroughly." }

        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            val s = settings.value
            val chatDao = container.database.chatDao()

            chatDao.insert(
                ChatMessageEntity(
                    role = "user",
                    text = prompt,
                    imageUri = attachedImageUri?.toString()
                )
            )
            if (auth.currentUser != null && s.cloudSyncEnabled) {
                container.firestoreSync.backupChatMessage("default", "user", prompt, null, null)
            }

            // Recall relevant Yaadein context
            val recalled = container.memoryManager.recallMemory(prompt, limit = 5)
            val recalledLines = recalled.map { "[${it.category}] ${it.content}" }

            // Auto-save explicit "remember that..." intents to Yaadein safely
            if (s.autoMemorySave && prompt.startsWith("remember ", ignoreCase = true)) {
                val fact = prompt.removePrefix("remember ").removePrefix("Remember ").trim()
                val memRes = container.memoryManager.rememberFact(fact, category = "auto", importance = 4, source = "chat")
                memRes.onFailure { err ->
                    _statusBanner.value = err.localizedMessage
                }
            }

            val systemPrompt = container.personaManager.buildSystemPrompt(
                settings = s,
                recalledMemories = recalledLines,
                availableToolsSummary = container.toolRegistry.compactPromptSummary()
            )
            val recentHistory = chatDao.getRecent(14).reversed().map {
                ChatTurnInput(role = if (it.role == "model") "model" else "user", text = it.text)
            }

            _isStreaming.value = true
            _streamingReply.value = ""

            try {
                var finalCompleted: StreamChunk.Completed? = null
                container.llmGateway.streamChat(
                    settings = s,
                    systemPrompt = systemPrompt,
                    history = recentHistory.dropLast(1),
                    userMessage = prompt,
                    attachedImageUri = attachedImageUri,
                    attachedBitmap = attachedBitmap
                ).collect { chunk ->
                    when (chunk) {
                        is StreamChunk.Delta -> _streamingReply.value += chunk.text
                        is StreamChunk.Completed -> finalCompleted = chunk
                    }
                }

                val completed = finalCompleted
                if (completed != null) {
                    chatDao.insert(
                        ChatMessageEntity(
                            role = "model",
                            text = completed.fullText,
                            modelUsed = completed.modelUsed,
                            providerUsed = completed.providerUsed.displayName,
                            tokensUsed = completed.promptTokens + completed.completionTokens
                        )
                    )
                    if (auth.currentUser != null && s.cloudSyncEnabled) {
                        container.firestoreSync.backupChatMessage(
                            "default", "model", completed.fullText, completed.modelUsed, completed.providerUsed.name
                        )
                    }

                    // Check for ```tool ... ``` invocation block from LLM
                    val toolOutcome = maybeExecuteEmbeddedTool(completed.fullText)
                    if (toolOutcome != null) {
                        chatDao.insert(
                            ChatMessageEntity(
                                role = "tool",
                                text = "[Tool: ${toolOutcome.toolName} | ${if (toolOutcome.success) "OK" else "INFO"}]\n${toolOutcome.output}"
                            )
                        )
                    }

                    if (speakOutput) {
                        val emotion = inferSpeechEmotion(completed.fullText)
                        val spoken = completed.fullText.replace(Regex("```[\\s\\S]*?```"), "").trim()
                        if (spoken.isNotBlank()) speakText(spoken.take(900), emotion)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "AI request failed"
                chatDao.insert(
                    ChatMessageEntity(
                        role = "model",
                        text = "Error: $msg",
                        modelUsed = s.selectedModelId,
                        providerUsed = s.activeProvider.displayName
                    )
                )
                container.database.diagnosticsDao().insertLog(
                    DiagnosticLogEntity(level = "ERROR", tag = "Chat", message = msg)
                )
                _statusBanner.value = msg
            } finally {
                _isStreaming.value = false
                _streamingReply.value = ""
            }
        }
    }

    private suspend fun maybeExecuteEmbeddedTool(responseText: String): ToolExecutionResult? {
        val regex = Regex("```tool\\s*(\\{[\\s\\S]*?\\})\\s*```", RegexOption.IGNORE_CASE)
        val match = regex.find(responseText) ?: return null
        return try {
            val json = JSONObject(match.groupValues[1])
            val name = json.optString("tool")
            val args = json.optJSONObject("args") ?: JSONObject()
            if (name.isBlank()) null
            else container.agentOrchestrator.executeTool(
                toolName = name,
                args = args,
                confirmedByUser = false,
                replayNonce = "${System.currentTimeMillis()}_$name"
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun inferSpeechEmotion(text: String): SpeechEmotion {
        val lower = text.lowercase()
        return when {
            "congrats" in lower || "great!" in lower || "awesome" in lower -> SpeechEmotion.EXCITED
            "sorry" in lower || "understand" in lower -> SpeechEmotion.EMPATHETIC
            "?" in lower -> SpeechEmotion.CURIOUS
            "warning" in lower || "error" in lower -> SpeechEmotion.SERIOUS
            else -> SpeechEmotion.NEUTRAL
        }
    }

    fun confirmPendingTool() {
        viewModelScope.launch {
            val res = container.agentOrchestrator.confirmPendingAction() ?: return@launch
            container.database.chatDao().insert(
                ChatMessageEntity(
                    role = "tool",
                    text = "[Confirmed Tool: ${res.toolName}]\n${res.output}"
                )
            )
            _statusBanner.value = res.output
        }
    }

    fun dismissPendingTool() {
        container.agentOrchestrator.dismissPendingConfirmation()
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            container.database.chatDao().clearSession("default")
        }
    }

    // --- Yaadein Memory Operations ---

    fun addMemory(content: String, category: String, importance: Int) {
        viewModelScope.launch {
            val res = container.memoryManager.rememberFact(
                content = content,
                category = category,
                importance = importance,
                source = "manual",
                syncToCloud = settings.value.cloudSyncEnabled
            )
            res.onSuccess {
                _statusBanner.value = "Saved to Yaadein memory."
                searchMemories(_memorySearchQuery.value)
            }.onFailure { err ->
                _statusBanner.value = err.localizedMessage ?: "Unable to save memory"
            }
        }
    }

    fun searchMemories(query: String) {
        _memorySearchQuery.value = query
        viewModelScope.launch {
            _recalledMemories.value = container.memoryManager.recallMemory(query, limit = 25)
        }
    }

    fun forgetMemory(id: Long) {
        viewModelScope.launch {
            container.memoryManager.forgetMemory(id)
            searchMemories(_memorySearchQuery.value)
        }
    }

    suspend fun exportMemoriesJson(): String = container.memoryManager.exportBackupJson()

    fun importMemoriesJson(json: String) {
        viewModelScope.launch {
            container.memoryManager.importBackupJson(json).onSuccess { count ->
                _statusBanner.value = "Restored $count Yaadein memories."
            }.onFailure { err ->
                _statusBanner.value = "Backup restore failed: ${err.localizedMessage}"
            }
        }
    }

    fun clearDiagnosticsAndUsage() {
        viewModelScope.launch {
            container.database.diagnosticsDao().clearLogs()
            container.database.diagnosticsDao().clearUsage()
            _statusBanner.value = "Usage and diagnostic logs cleared."
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    companion object {
        private const val TAG = "MayaMainViewModel"
    }
}
