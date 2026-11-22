package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.MayaApplication
import com.example.agent.ToolExecutionResult
import com.example.automation.MayaAccessibilityService
import com.example.automation.NotificationIntelligenceService
import com.example.automation.ScreenCaptureService
import com.example.core.MayaSettingsState
import com.example.data.local.BusinessProfileEntity
import com.example.data.local.FaceProfileEntity
import com.example.data.local.GeneratedImageEntity
import com.example.data.local.MacroRuleEntity
import com.example.data.local.MarketingLeadEntity
import com.example.data.local.MediaHistoryEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.SmartDeviceEntity
import com.example.data.local.WebsiteProjectEntity
import com.example.faces.FaceMatchResult
import com.example.integrations.ContactEntry
import com.example.integrations.ExtractedDocumentResult
import com.example.integrations.SeoAuditReport
import com.example.integrations.WeatherReport
import com.example.integrations.WebSearchResult
import com.example.integrations.WebpageContent
import com.example.llm.StreamChunk
import com.example.security.KeystoreVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject

class MayaModulesViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MayaApplication
    private val c = app.container

    // Room Observables
    val reminders = c.database.reminderDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val macroRules = c.database.macroRuleDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val missions = c.database.missionDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val businesses = c.database.marketingDao().observeBusinesses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val leads = c.database.marketingDao().observeLeads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val websites = c.database.websiteDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val smartDevices = c.database.smartHomeDao().observeDevices()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val capturedNotifications = c.database.notificationDao().observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val voiceProfiles = c.voiceGuardian.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val faceProfiles = c.faceEngine.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val mediaHistory = c.database.mediaDao().observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val generatedImages = c.database.mediaDao().observeGeneratedImages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // System & Service Observables
    val accessibilityConnected = MayaAccessibilityService.isConnected
    val activePackage = MayaAccessibilityService.activePackage
    val isScreenSharing = ScreenCaptureService.isSharing
    val lastScreenBitmap = ScreenCaptureService.lastCapturedBitmap
    val notificationListenerConnected = NotificationIntelligenceService.isConnected
    val lastVoiceVerification = c.voiceGuardian.lastVerification
    val intrusionAlerts = c.voiceGuardian.intrusionAlerts
    val termuxLogs = c.termuxBridge.logs
    val callStateLabel = c.commsIntegration.callStateLabel
    val allTools = c.toolRegistry.tools

    // Feature Working States
    private val _moduleMessage = MutableStateFlow<String?>(null)
    val moduleMessage: StateFlow<String?> = _moduleMessage.asStateFlow()

    private val _visionOutput = MutableStateFlow("")
    val visionOutput: StateFlow<String> = _visionOutput.asStateFlow()

    private val _visionLoading = MutableStateFlow(false)
    val visionLoading: StateFlow<Boolean> = _visionLoading.asStateFlow()

    private val _screenAnalysisOutput = MutableStateFlow("")
    val screenAnalysisOutput: StateFlow<String> = _screenAnalysisOutput.asStateFlow()

    private val _accessibilityDump = MutableStateFlow("")
    val accessibilityDump: StateFlow<String> = _accessibilityDump.asStateFlow()

    private val _faceMatchResult = MutableStateFlow<FaceMatchResult?>(null)
    val faceMatchResult: StateFlow<FaceMatchResult?> = _faceMatchResult.asStateFlow()

    private val _toolResult = MutableStateFlow<ToolExecutionResult?>(null)
    val toolResult: StateFlow<ToolExecutionResult?> = _toolResult.asStateFlow()

    private val _searchResults = MutableStateFlow<List<WebSearchResult>>(emptyList())
    val searchResults: StateFlow<List<WebSearchResult>> = _searchResults.asStateFlow()

    private val _webpageContent = MutableStateFlow<WebpageContent?>(null)
    val webpageContent: StateFlow<WebpageContent?> = _webpageContent.asStateFlow()

    private val _seoReport = MutableStateFlow<SeoAuditReport?>(null)
    val seoReport: StateFlow<SeoAuditReport?> = _seoReport.asStateFlow()

    private val _marketingAiOutput = MutableStateFlow("")
    val marketingAiOutput: StateFlow<String> = _marketingAiOutput.asStateFlow()

    private val _codingOutput = MutableStateFlow("")
    val codingOutput: StateFlow<String> = _codingOutput.asStateFlow()

    private val _codingLoading = MutableStateFlow(false)
    val codingLoading: StateFlow<Boolean> = _codingLoading.asStateFlow()

    private val _weatherReport = MutableStateFlow<WeatherReport?>(null)
    val weatherReport: StateFlow<WeatherReport?> = _weatherReport.asStateFlow()

    private val _weatherLoading = MutableStateFlow(false)
    val weatherLoading: StateFlow<Boolean> = _weatherLoading.asStateFlow()

    private val _contacts = MutableStateFlow<List<ContactEntry>>(emptyList())
    val contacts: StateFlow<List<ContactEntry>> = _contacts.asStateFlow()

    private val _extractedDoc = MutableStateFlow<ExtractedDocumentResult?>(null)
    val extractedDoc: StateFlow<ExtractedDocumentResult?> = _extractedDoc.asStateFlow()

    private val _docAnswer = MutableStateFlow("")
    val docAnswer: StateFlow<String> = _docAnswer.asStateFlow()

    private val _generatedBitmap = MutableStateFlow<Bitmap?>(null)
    val generatedBitmap: StateFlow<Bitmap?> = _generatedBitmap.asStateFlow()

    private val _imageGenLoading = MutableStateFlow(false)
    val imageGenLoading: StateFlow<Boolean> = _imageGenLoading.asStateFlow()

    private val _notificationSummary = MutableStateFlow("")
    val notificationSummary: StateFlow<String> = _notificationSummary.asStateFlow()

    private val _portStatus = MutableStateFlow<String?>(null)
    val portStatus: StateFlow<String?> = _portStatus.asStateFlow()

    fun clearMessage() { _moduleMessage.value = null }

    // --- Voice Guardian ---
    fun enrollVoice(name: String, role: String) {
        viewModelScope.launch {
            c.voiceGuardian.enrollCurrentAudio(name, role)
                .onSuccess { _moduleMessage.value = "Enrolled voice profile '${it.name}' (${it.role})." }
                .onFailure { _moduleMessage.value = it.localizedMessage }
        }
    }

    fun verifySpeaker(guardianMode: String) {
        viewModelScope.launch {
            val res = c.voiceGuardian.verifyCurrentSpeaker(guardianMode)
            _moduleMessage.value = res.status
        }
    }

    fun deleteVoiceProfile(id: Long) {
        viewModelScope.launch { c.voiceGuardian.deleteProfile(id) }
    }

    fun authenticateBiometric(activity: FragmentActivity, onSuccess: () -> Unit) {
        c.voiceGuardian.authenticateDeviceUser(
            activity = activity,
            title = "Maya Voice Guardian Verification",
            subtitle = "Confirm device biometric or PIN",
            onAuthenticated = {
                _moduleMessage.value = "Device owner verified."
                onSuccess()
            },
            onError = { err -> _moduleMessage.value = err }
        )
    }

    // --- Face Recognition ---
    fun enrollFaceFromUri(uri: Uri, name: String, relation: String, consent: Boolean) {
        viewModelScope.launch {
            val bmp = c.visionEngine.decodeUriToBitmap(uri)
            if (bmp == null) {
                _moduleMessage.value = "Could not decode selected photo."
                return@launch
            }
            c.faceEngine.enrollFace(bmp, name, relation, consent)
                .onSuccess { _moduleMessage.value = "Enrolled face profile for ${it.name}." }
                .onFailure { _moduleMessage.value = it.localizedMessage }
        }
    }

    fun recognizeFaceFromBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            c.faceEngine.recognizeFace(bitmap)
                .onSuccess { res ->
                    _faceMatchResult.value = res
                    _moduleMessage.value = res.message
                }
                .onFailure { _moduleMessage.value = it.localizedMessage }
        }
    }

    fun recognizeFaceFromUri(uri: Uri) {
        val bmp = c.visionEngine.decodeUriToBitmap(uri)
        if (bmp == null) {
            _moduleMessage.value = "Unable to decode photo."
            return
        }
        recognizeFaceFromBitmap(bmp)
    }

    fun deleteFaceProfile(profile: FaceProfileEntity) {
        viewModelScope.launch { c.faceEngine.deleteProfile(profile) }
    }

    fun wipeAllFaces() {
        viewModelScope.launch {
            c.faceEngine.wipeAllFaceData()
            _faceMatchResult.value = null
            _moduleMessage.value = "All local face profiles and snapshots wiped."
        }
    }

    // --- Camera & Vision ---
    fun analyzeBitmap(bitmap: Bitmap, prompt: String) {
        viewModelScope.launch {
            _visionLoading.value = true
            c.visionEngine.analyzeImageWithGemini(bitmap, prompt)
                .onSuccess { _visionOutput.value = it }
                .onFailure { _visionOutput.value = "Vision error: ${it.localizedMessage}" }
            _visionLoading.value = false
        }
    }

    fun runOcrOnBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _visionLoading.value = true
            c.visionEngine.extractOcrText(bitmap)
                .onSuccess { _visionOutput.value = "OCR Extracted Text:\n$it" }
                .onFailure { _visionOutput.value = "OCR error: ${it.localizedMessage}" }
            _visionLoading.value = false
        }
    }

    fun scanQrOnBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            _visionLoading.value = true
            c.visionEngine.scanBarcodes(bitmap)
                .onSuccess { list ->
                    _visionOutput.value = if (list.isEmpty()) "No QR code or barcode detected."
                    else "Detected Codes:\n" + list.joinToString("\n")
                }
                .onFailure { _visionOutput.value = "Barcode scan error: ${it.localizedMessage}" }
            _visionLoading.value = false
        }
    }

    fun decodeUri(uri: Uri): Bitmap? = c.visionEngine.decodeUriToBitmap(uri)

    // --- Screen Understanding & Accessibility ---
    fun captureAndExplainScreen() {
        viewModelScope.launch {
            val svc = ScreenCaptureService.instance
            val bmp = svc?.captureLatestBitmap()
            if (bmp == null) {
                val treeText = MayaAccessibilityService.instance?.dumpScreenText().orEmpty()
                if (treeText.isNotBlank()) {
                    _accessibilityDump.value = treeText
                    _screenAnalysisOutput.value = "Captured via Accessibility Tree (start Screen Share for visual pixel analysis):\n\n$treeText"
                } else {
                    _screenAnalysisOutput.value = "No active screen capture. Tap 'Start Screen Share' or enable Accessibility Service."
                }
                return@launch
            }
            _visionLoading.value = true
            val ocr = c.visionEngine.extractOcrText(bmp).getOrDefault("")
            val ai = c.visionEngine.analyzeImageWithGemini(
                bmp,
                "Summarize what is visible on this Android screen, highlight key interactive elements, and answer any visible prompt."
            ).getOrElse { "AI analysis error: ${it.localizedMessage}\n\nOCR Fallback:\n$ocr" }
            _screenAnalysisOutput.value = ai
            _visionLoading.value = false
        }
    }

    fun readAccessibilityTree() {
        val svc = MayaAccessibilityService.instance
        if (svc == null) {
            _accessibilityDump.value = "Accessibility Service is disabled. Enable 'Maya AI Screen & UI Driver' in Android Accessibility Settings."
            return
        }
        val elements = svc.dumpInteractiveElements()
        val text = svc.dumpScreenText()
        _accessibilityDump.value = "Interactive Nodes:\n$elements\n\nVisible Text:\n$text"
    }

    fun stopScreenSharing() {
        val intent = android.content.Intent(app, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP)
        app.startService(intent)
    }

    // --- Notifications ---
    fun summarizeRecentNotifications() {
        viewModelScope.launch {
            val recent = capturedNotifications.value.filter { !it.isDuplicate }.take(20)
            if (recent.isEmpty()) {
                _notificationSummary.value = "No captured notifications yet. Ensure Notification Access is enabled."
                return@launch
            }
            val joined = recent.joinToString("\n") { "[${it.appName}] ${it.title}: ${it.text}" }
            val s = c.settingsStore.settingsFlow.first()
            val acc = StringBuilder()
            runCatching {
                c.llmGateway.streamChat(
                    settings = s,
                    systemPrompt = "Summarize these Android notifications concisely by priority and highlight action items.",
                    history = emptyList(),
                    userMessage = joined
                ).collect { chunk ->
                    if (chunk is StreamChunk.Delta) acc.append(chunk.text)
                }
            }.onFailure { err ->
                acc.append("Summary failed (${err.localizedMessage}). Raw feed count: ${recent.size}")
            }
            _notificationSummary.value = acc.toString()
        }
    }

    fun sendNotificationInlineReply(notificationKey: String, reply: String) {
        val ok = NotificationIntelligenceService.sendInlineReply(app, notificationKey, reply)
        _moduleMessage.value = if (ok) "Inline reply dispatched." else "Notification inline reply unavailable or expired."
    }

    fun clearNotifications() {
        viewModelScope.launch { c.database.notificationDao().clearAll() }
    }

    // --- Tools, Missions, Reminders & Rules ---
    fun executeToolManually(toolName: String, argsJsonText: String) {
        viewModelScope.launch {
            val args = runCatching {
                if (argsJsonText.isBlank()) JSONObject() else JSONObject(argsJsonText)
            }.getOrElse {
                _toolResult.value = ToolExecutionResult(toolName, false, "Invalid JSON arguments: ${it.localizedMessage}")
                return@launch
            }
            _toolResult.value = c.agentOrchestrator.executeTool(toolName, args, confirmedByUser = false)
        }
    }

    fun launchMission(title: String, goal: String) {
        if (title.isBlank() || goal.isBlank()) {
            _moduleMessage.value = "Provide both a mission title and goal."
            return
        }
        viewModelScope.launch {
            c.agentOrchestrator.runMission(title.trim(), goal.trim())
        }
    }

    fun addReminder(title: String, delayMinutes: Long, isAlarm: Boolean, kind: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val triggerAt = System.currentTimeMillis() + delayMinutes.coerceAtLeast(1L) * 60_000L
            val id = c.database.reminderDao().insert(
                ReminderEntity(title = title.trim(), triggerAtMillis = triggerAt, isAlarm = isAlarm, kind = kind)
            )
            c.reminderScheduler.scheduleReminder(id, title.trim(), triggerAt, isAlarm)
            _moduleMessage.value = "Scheduled $kind '$title' in $delayMinutes min."
        }
    }

    fun toggleReminderCompleted(item: ReminderEntity) {
        viewModelScope.launch {
            c.database.reminderDao().update(item.copy(isCompleted = !item.isCompleted))
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            c.reminderScheduler.cancelReminder(id)
            c.database.reminderDao().deleteById(id)
        }
    }

    fun canScheduleExactAlarms(): Boolean = c.reminderScheduler.canScheduleExactAlarms()

    fun addMacroRule(name: String, trigger: String, actionJson: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            c.database.macroRuleDao().insert(
                MacroRuleEntity(name = name.trim(), triggerType = trigger.ifBlank { "MANUAL" }, actionJson = actionJson)
            )
        }
    }

    fun toggleMacroRule(rule: MacroRuleEntity) {
        viewModelScope.launch { c.database.macroRuleDao().update(rule.copy(isEnabled = !rule.isEnabled)) }
    }

    fun deleteMacroRule(id: Long) {
        viewModelScope.launch { c.database.macroRuleDao().deleteById(id) }
    }

    // --- Termux & Coding & Websites ---
    fun isTermuxInstalled() = c.termuxBridge.isTermuxInstalled()
    fun hasTermuxPermission() = c.termuxBridge.hasRunCommandPermission()

    fun runTermuxCommand(cmd: String, background: Boolean = false) {
        c.termuxBridge.executeCommand(cmd, background = background)
            .onSuccess { _moduleMessage.value = it }
            .onFailure { _moduleMessage.value = it.localizedMessage }
    }

    fun checkLocalPort(port: Int) {
        viewModelScope.launch {
            val open = c.termuxBridge.checkLocalPort(port)
            _portStatus.value = "127.0.0.1:$port -> ${if (open) "OPEN / Listening" else "Closed"}"
        }
    }

    fun generateCodeForActiveFramework(prompt: String, settings: MayaSettingsState) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _codingLoading.value = true
            _codingOutput.value = ""
            val sys = "You are a senior software engineer targeting ${settings.activeFramework.title}. ${settings.activeFramework.systemGuidance}. Provide complete, production-ready code with exact file paths."
            runCatching {
                c.llmGateway.streamChat(settings, sys, emptyList(), prompt).collect { chunk ->
                    if (chunk is StreamChunk.Delta) _codingOutput.value += chunk.text
                }
            }.onFailure { err ->
                _codingOutput.value = "Code generation failed: ${err.localizedMessage}"
            }
            _codingLoading.value = false
        }
    }

    fun fetchGitHubRepo(ownerRepo: String) {
        viewModelScope.launch {
            c.messagingIntegration.fetchGitHubRepoSummary(ownerRepo)
                .onSuccess { _codingOutput.value = "GitHub Repo Info:\n$it\n\n${_codingOutput.value}" }
                .onFailure { _moduleMessage.value = it.localizedMessage }
        }
    }

    fun createWebsiteProject(name: String, description: String, settings: MayaSettingsState) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _codingLoading.value = true
            val acc = StringBuilder()
            val prompt = "Build a complete single-file responsive HTML5+CSS+JS webpage for '$name'. Description: $description. Output raw valid HTML starting with <!DOCTYPE html> and no markdown fences."
            runCatching {
                c.llmGateway.streamChat(settings, "Output only clean valid HTML5.", emptyList(), prompt).collect { chunk ->
                    if (chunk is StreamChunk.Delta) acc.append(chunk.text)
                }
            }
            val html = acc.toString()
                .removePrefix("```html").removePrefix("```").removeSuffix("```").trim()
                .ifBlank {
                    "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>$name</title><style>body{font-family:system-ui;background:#070b12;color:#ebf4ff;padding:2rem}.box{background:#0e1525;border:1px solid #26334d;border-radius:16px;padding:1.5rem}h1{color:#00e5ff}</style></head><body><div class=\"box\"><h1>$name</h1><p>$description</p></div></body></html>"
                }
            c.database.websiteDao().insert(
                WebsiteProjectEntity(name = name.trim(), description = description, htmlContent = html)
            )
            _codingLoading.value = false
            _moduleMessage.value = "Website project '$name' saved."
        }
    }

    fun updateWebsiteHtml(project: WebsiteProjectEntity, html: String) {
        viewModelScope.launch {
            c.database.websiteDao().update(project.copy(htmlContent = html, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteWebsite(id: Long) {
        viewModelScope.launch { c.database.websiteDao().deleteById(id) }
    }

    // --- Research, Browser, SEO & Marketing ---
    fun searchWeb(query: String) {
        viewModelScope.launch {
            c.researchIntegration.searchDuckDuckGo(query)
                .onSuccess { _searchResults.value = it }
                .onFailure { _moduleMessage.value = it.localizedMessage }
        }
    }

    fun fetchWebpage(url: String) {
        viewModelScope.launch {
            c.researchIntegration.fetchWebpageText(url)
                .onSuccess { _webpageContent.value = it }
                .onFailure { _moduleMessage.value = it.localizedMessage }
        }
    }

    fun addBusinessProfile(name: String, location: String, services: String, audience: String, url: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            c.database.marketingDao().insertBusiness(
                BusinessProfileEntity(name = name.trim(), location = location, services = services, targetAudience = audience, websiteUrl = url)
            )
        }
    }

    fun deleteBusinessProfile(id: Long) {
        viewModelScope.launch { c.database.marketingDao().deleteBusiness(id) }
    }

    fun addMarketingLead(name: String, contact: String, kpi: Double) {
        if (name.isBlank()) return
        viewModelScope.launch {
            c.database.marketingDao().insertLead(
                MarketingLeadEntity(name = name.trim(), contact = contact, kpiValue = kpi)
            )
        }
    }

    fun cycleLeadStatus(lead: MarketingLeadEntity) {
        val next = when (lead.status) {
            "NEW" -> "CONTACTED"
            "CONTACTED" -> "QUALIFIED"
            "QUALIFIED" -> "WON"
            else -> "NEW"
        }
        viewModelScope.launch { c.database.marketingDao().updateLead(lead.copy(status = next)) }
    }

    fun deleteMarketingLead(id: Long) {
        viewModelScope.launch { c.database.marketingDao().deleteLead(id) }
    }

    fun runSeoAudit(url: String) {
        viewModelScope.launch {
            c.researchIntegration.runSeoAudit(url)
                .onSuccess { _seoReport.value = it }
                .onFailure { _moduleMessage.value = it.localizedMessage }
        }
    }

    fun generateMarketingCopy(business: BusinessProfileEntity?, topic: String, settings: MayaSettingsState) {
        viewModelScope.launch {
            _marketingAiOutput.value = ""
            val ctx = if (business != null) "Business: ${business.name} (${business.location}), Services: ${business.services}, Audience: ${business.targetAudience}." else ""
            val prompt = "$ctx\nTask: $topic. Create a 30-day content calendar snippet, high-intent keywords, and a ready-to-share social post."
            runCatching {
                c.llmGateway.streamChat(settings, "You are a digital marketing and SEO strategist.", emptyList(), prompt).collect { chunk ->
                    if (chunk is StreamChunk.Delta) _marketingAiOutput.value += chunk.text
                }
            }.onFailure { err ->
                _marketingAiOutput.value = "Marketing generation failed: ${err.localizedMessage}"
            }
        }
    }

    fun shareText(title: String, body: String) {
        c.mediaIntegration.shareTextContent(title, body)
    }

    // --- Smart Home, Email, Telegram, Notion ---
    fun addSmartDevice(name: String, type: String, endpointUrl: String) {
        if (name.isBlank() || endpointUrl.isBlank()) return
        viewModelScope.launch {
            c.database.smartHomeDao().insert(
                SmartDeviceEntity(name = name.trim(), type = type, endpointUrl = endpointUrl.trim())
            )
        }
    }

    fun toggleSmartDevice(device: SmartDeviceEntity) {
        val nextOn = !device.isOn
        viewModelScope.launch {
            c.messagingIntegration.toggleSmartDevice(device, nextOn)
                .onSuccess { status ->
                    c.database.smartHomeDao().update(device.copy(isOn = nextOn, lastStatus = status, lastCheckedAt = System.currentTimeMillis()))
                    _moduleMessage.value = status
                }
                .onFailure { err ->
                    c.database.smartHomeDao().update(device.copy(lastStatus = "Error: ${err.localizedMessage}", lastCheckedAt = System.currentTimeMillis()))
                    _moduleMessage.value = err.localizedMessage
                }
        }
    }

    fun deleteSmartDevice(id: Long) {
        viewModelScope.launch { c.database.smartHomeDao().deleteById(id) }
    }

    fun testTlsMail(host: String, port: Int) {
        viewModelScope.launch {
            c.messagingIntegration.testTlsMailServer(host, port)
                .onSuccess { _moduleMessage.value = it }
                .onFailure { _moduleMessage.value = it.localizedMessage }
        }
    }

    fun saveEmailSettings(smtpHost: String, smtpPort: Int, email: String, imapHost: String, imapPort: Int, password: String) {
        viewModelScope.launch {
            c.settingsStore.updateEmailConfig(smtpHost, smtpPort, email, imapHost, imapPort)
            if (password.isNotBlank()) c.vault.putSecret(KeystoreVault.KEY_SMTP_PASSWORD, password)
            _moduleMessage.value = "Email TLS configuration saved."
        }
    }

    fun sendSmtpEmail(host: String, port: Int, from: String, to: String, subject: String, body: String) {
        viewModelScope.launch {
            c.messagingIntegration.sendSmtpTlsEmail(host, port, from, to, subject, body)
                .onSuccess { _moduleMessage.value = it }
                .onFailure { _moduleMessage.value = it.localizedMessage }
        }
    }

    fun openEmailDraft(to: String, subject: String, body: String) {
        c.messagingIntegration.openEmailDraft(to, subject, body)
            .onFailure { _moduleMessage.value = it.localizedMessage }
    }

    fun sendTelegram(chatId: String, text: String) {
        viewModelScope.launch {
            if (chatId.isNotBlank()) c.settingsStore.updateTelegramChatId(chatId.trim())
            c.messagingIntegration.sendTelegramMessage(text)
                .onSuccess { _moduleMessage.value = it }
                .onFailure { _moduleMessage.value = it.localizedMessage }
        }
    }

    fun searchNotion(query: String) {
        viewModelScope.launch {
            c.messagingIntegration.searchNotionWorkspace(query)
                .onSuccess { _moduleMessage.value = it }
                .onFailure { _moduleMessage.value = it.localizedMessage }
        }
    }

    // --- Contacts, Calls, SMS, WhatsApp ---
    fun searchContacts(query: String) {
        _contacts.value = c.commsIntegration.searchContacts(query)
    }

    fun startCallObserver() = c.commsIntegration.startCallStateObserver()

    fun placeCall(number: String, direct: Boolean) {
        c.commsIntegration.placeCall(number, direct)
            .onSuccess { _moduleMessage.value = it }
            .onFailure { _moduleMessage.value = it.localizedMessage }
    }

    fun sendSms(number: String, body: String, direct: Boolean) {
        val res = if (direct) c.commsIntegration.sendSmsDirect(number, body) else c.commsIntegration.openSmsComposer(number, body)
        res.onSuccess { _moduleMessage.value = it }.onFailure { _moduleMessage.value = it.localizedMessage }
    }

    fun sendWhatsApp(target: String, msg: String) {
        c.commsIntegration.openWhatsAppMessage(target, msg)
            .onSuccess { _moduleMessage.value = it }
            .onFailure { _moduleMessage.value = it.localizedMessage }
    }

    // --- Weather & Maps ---
    fun fetchWeatherCity(city: String) {
        viewModelScope.launch {
            _weatherLoading.value = true
            c.researchIntegration.fetchWeatherForCity(city)
                .onSuccess { _weatherReport.value = it }
                .onFailure { _moduleMessage.value = it.localizedMessage }
            _weatherLoading.value = false
        }
    }

    fun fetchWeatherCurrentLocation() {
        viewModelScope.launch {
            _weatherLoading.value = true
            c.researchIntegration.fetchWeatherForDeviceLocation()
                .onSuccess { _weatherReport.value = it }
                .onFailure { _moduleMessage.value = it.localizedMessage }
            _weatherLoading.value = false
        }
    }

    fun openMaps(query: String) {
        c.researchIntegration.openMapsSearch(query)
            .onFailure { _moduleMessage.value = it.localizedMessage }
    }

    // --- Documents ---
    fun extractDocument(uri: Uri) {
        viewModelScope.launch {
            c.mediaIntegration.extractDocumentText(uri)
                .onSuccess { _extractedDoc.value = it }
                .onFailure { _moduleMessage.value = it.localizedMessage }
        }
    }

    fun askDocumentQuestion(question: String, settings: MayaSettingsState) {
        val doc = _extractedDoc.value ?: return
        viewModelScope.launch {
            _docAnswer.value = ""
            val prompt = "Document (${doc.fileName}):\n${doc.text.take(9000)}\n\nQuestion: $question"
            runCatching {
                c.llmGateway.streamChat(settings, "Answer strictly from the document text provided.", emptyList(), prompt).collect { chunk ->
                    if (chunk is StreamChunk.Delta) _docAnswer.value += chunk.text
                }
            }.onFailure { err ->
                _docAnswer.value = "Document Q&A failed: ${err.localizedMessage}"
            }
        }
    }

    // --- Music, Media & Runtime Image Generation ---
    fun launchYouTube(query: String) {
        if (query.isBlank()) return
        c.mediaIntegration.openYouTubeSearch(query)
            .onSuccess {
                viewModelScope.launch {
                    c.database.mediaDao().insertHistory(
                        MediaHistoryEntity(query = query, title = "YouTube: $query", uriOrUrl = "youtube://$query", source = "YOUTUBE")
                    )
                }
                _moduleMessage.value = it
            }
            .onFailure { _moduleMessage.value = it.localizedMessage }
    }

    fun launchSpotify(query: String) {
        if (query.isBlank()) return
        c.mediaIntegration.openSpotifySearch(query)
            .onSuccess {
                viewModelScope.launch {
                    c.database.mediaDao().insertHistory(
                        MediaHistoryEntity(query = query, title = "Spotify: $query", uriOrUrl = "spotify://$query", source = "SPOTIFY")
                    )
                }
                _moduleMessage.value = it
            }
            .onFailure { _moduleMessage.value = it.localizedMessage }
    }

    fun toggleFavoriteMedia(item: MediaHistoryEntity) {
        viewModelScope.launch { c.database.mediaDao().updateHistory(item.copy(isFavorite = !item.isFavorite)) }
    }

    fun generateRuntimeImage(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            _imageGenLoading.value = true
            c.llmGateway.generateRuntimeImage(prompt)
                .onSuccess { bmp ->
                    _generatedBitmap.value = bmp
                    val path = c.mediaIntegration.saveBitmapToGallery(bmp).getOrDefault("")
                    c.database.mediaDao().insertGeneratedImage(
                        GeneratedImageEntity(prompt = prompt, model = "gemini-nano-banana-2.1", filePath = path)
                    )
                    _moduleMessage.value = if (path.isNotBlank()) "Image generated & saved to Pictures/MayaAI." else "Image generated."
                }
                .onFailure { err ->
                    _moduleMessage.value = err.localizedMessage ?: "Image generation failed (Nano Banana 2.1 requires Blaze plan)."
                }
            _imageGenLoading.value = false
        }
    }
}
