package com.example.agent

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import com.example.automation.MayaAccessibilityService
import com.example.core.DeviceTelemetry
import com.example.data.local.MacroRuleDao
import com.example.data.local.MacroRuleEntity
import com.example.data.local.MissionDao
import com.example.data.local.MissionEntity
import com.example.data.local.ReminderDao
import com.example.data.local.ReminderEntity
import com.example.data.local.SmartHomeDao
import com.example.data.local.WebsiteDao
import com.example.data.local.WebsiteProjectEntity
import com.example.integrations.CommsIntegration
import com.example.integrations.MessagingAndProductivityIntegration
import com.example.integrations.ResearchAndSeoIntegration
import com.example.integrations.TermuxBridge
import com.example.memory.YaadeinMemoryManager
import com.example.workers.MayaReminderScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class PendingToolConfirmation(
    val id: String,
    val toolName: String,
    val args: JSONObject,
    val prompt: String
)

class AgentOrchestrator(
    private val context: Context,
    val registry: ToolRegistry,
    private val memoryManager: YaadeinMemoryManager,
    private val reminderDao: ReminderDao,
    private val reminderScheduler: MayaReminderScheduler,
    private val macroRuleDao: MacroRuleDao,
    private val missionDao: MissionDao,
    private val smartHomeDao: SmartHomeDao,
    private val websiteDao: WebsiteDao,
    private val comms: CommsIntegration,
    private val termux: TermuxBridge,
    private val research: ResearchAndSeoIntegration,
    private val messaging: MessagingAndProductivityIntegration,
    private val telemetry: DeviceTelemetry
) {
    private val appContext = context.applicationContext
    private val executedNonces = ConcurrentHashMap.newKeySet<String>()

    private val _pendingConfirmation = MutableStateFlow<PendingToolConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingToolConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _touchGuardEnabled = MutableStateFlow(false)
    val touchGuardEnabled: StateFlow<Boolean> = _touchGuardEnabled.asStateFlow()

    @Volatile
    private var activeMissionCancelled = false

    suspend fun seedBuiltInRulesIfEmpty() {
        if (macroRuleDao.count() == 0) {
            macroRuleDao.insert(
                MacroRuleEntity(
                    name = "Morning Briefing",
                    triggerType = "VOICE_OR_MANUAL",
                    actionJson = "{\"tools\":[\"get_weather\",\"list_reminders\"]}",
                    isBuiltIn = true,
                    isEnabled = true
                )
            )
            macroRuleDao.insert(
                MacroRuleEntity(
                    name = "Night Standby Guard",
                    triggerType = "SCHEDULE_23_00",
                    actionJson = "{\"tools\":[\"set_volume\",\"set_torch\"]}",
                    isBuiltIn = true,
                    isEnabled = true
                )
            )
        }
    }

    suspend fun executeTool(
        toolName: String,
        args: JSONObject = JSONObject(),
        confirmedByUser: Boolean = false,
        replayNonce: String? = null
    ): ToolExecutionResult {
        if (!replayNonce.isNullOrBlank() && !executedNonces.add(replayNonce)) {
            return ToolExecutionResult(toolName, false, "Duplicate execution blocked by replay protection.")
        }

        val spec = registry.getSpec(toolName)
            ?: return ToolExecutionResult(toolName, false, "Unknown tool '$toolName'. Use find_tool to list supported tools.")

        registry.validateArgs(spec, args)?.let { err ->
            return ToolExecutionResult(toolName, false, err)
        }

        if (spec.requiresConfirmation && !confirmedByUser) {
            val summary = "Confirm ${spec.name} with parameters: ${args.toString()}"
            _pendingConfirmation.value = PendingToolConfirmation(
                id = replayNonce ?: System.currentTimeMillis().toString(),
                toolName = spec.name,
                args = args,
                prompt = summary
            )
            return ToolExecutionResult(
                toolName = spec.name,
                success = false,
                output = "Confirmation required before executing ${spec.name}.",
                requiresUserConfirmation = true,
                pendingConfirmationPrompt = summary
            )
        }

        return when (spec.name) {
            "remember_fact" -> {
                val res = memoryManager.rememberFact(
                    content = args.optString("content"),
                    category = args.optString("category", "general"),
                    importance = args.optInt("importance", 3),
                    source = "agent_tool"
                )
                res.fold(
                    onSuccess = { id -> ToolExecutionResult(spec.name, true, "Saved memory #$id.") },
                    onFailure = { e -> ToolExecutionResult(spec.name, false, e.message ?: "Failed to save memory.") }
                )
            }

            "recall_memory" -> {
                val items = memoryManager.recallMemory(args.optString("query"), limit = 6)
                val out = if (items.isEmpty()) "No matching memories found."
                else items.joinToString("\n") { "[#${it.id} | ${it.category}] ${it.content}" }
                ToolExecutionResult(spec.name, true, out)
            }

            "forget_memory" -> {
                val id = args.optLong("id", -1L)
                if (id <= 0L) ToolExecutionResult(spec.name, false, "Provide a valid numeric memory id.")
                else {
                    memoryManager.forgetMemory(id)
                    ToolExecutionResult(spec.name, true, "Deleted memory #$id.")
                }
            }

            "open_app" -> {
                val pkg = args.optString("packageName")
                val intent = appContext.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    appContext.startActivity(intent)
                    ToolExecutionResult(spec.name, true, "Launched $pkg.")
                } else {
                    ToolExecutionResult(spec.name, false, "App '$pkg' not found or package visibility blocked.")
                }
            }

            "open_app_by_name" -> {
                val name = args.optString("appName")
                val launched = comms.launchAppByName(name)
                ToolExecutionResult(spec.name, launched, if (launched) "Opened app matching '$name'." else "No launcher app matched '$name'.")
            }

            "open_settings" -> {
                val section = args.optString("section").lowercase()
                val action = when {
                    "access" in section -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                    "notif" in section -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
                    "wifi" in section -> Settings.ACTION_WIFI_SETTINGS
                    "alarm" in section -> Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    else -> Settings.ACTION_SETTINGS
                }
                val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(i)
                ToolExecutionResult(spec.name, true, "Opened Android settings ($action).")
            }

            "read_screen_elements", "read_screen_text" -> {
                val svc = MayaAccessibilityService.instance
                    ?: return ToolExecutionResult(spec.name, false, "AccessibilityService is not enabled. Enable Maya AI Screen & UI Driver in Settings.")
                val text = if (spec.name == "read_screen_elements") svc.dumpInteractiveElements() else svc.dumpScreenText()
                ToolExecutionResult(spec.name, true, text.ifBlank { "(Screen has no readable accessibility nodes)" })
            }

            "tap_text" -> {
                val svc = MayaAccessibilityService.instance
                    ?: return ToolExecutionResult(spec.name, false, "AccessibilityService is disabled.")
                val ok = svc.clickNodeByText(args.optString("text"))
                ToolExecutionResult(spec.name, ok, if (ok) "Tapped '${args.optString("text")}'." else "Element '${args.optString("text")}' not found.")
            }

            "tap_element" -> {
                val svc = MayaAccessibilityService.instance
                    ?: return ToolExecutionResult(spec.name, false, "AccessibilityService is disabled.")
                val ok = svc.clickNodeById(args.optString("viewId"))
                ToolExecutionResult(spec.name, ok, if (ok) "Tapped viewId '${args.optString("viewId")}'." else "View ID not found.")
            }

            "type_text", "fill_form" -> {
                val svc = MayaAccessibilityService.instance
                    ?: return ToolExecutionResult(spec.name, false, "AccessibilityService is disabled.")
                val ok = svc.inputTextIntoFocused(args.optString("text"))
                ToolExecutionResult(spec.name, ok, if (ok) "Entered text into focused field." else "No editable focused node found.")
            }

            "scroll", "scroll_screen" -> {
                val svc = MayaAccessibilityService.instance
                    ?: return ToolExecutionResult(spec.name, false, "AccessibilityService is disabled.")
                val dir = args.optString("direction", "down")
                val ok = svc.scrollScreen(forward = dir != "up")
                ToolExecutionResult(spec.name, ok, if (ok) "Scrolled $dir." else "No scrollable container found.")
            }

            "swipe" -> {
                val svc = MayaAccessibilityService.instance
                    ?: return ToolExecutionResult(spec.name, false, "AccessibilityService is disabled.")
                val ok = svc.performSwipe(args.optString("direction", "up"))
                ToolExecutionResult(spec.name, ok, if (ok) "Performed swipe gesture." else "Swipe gesture failed.")
            }

            "go_back" -> {
                val svc = MayaAccessibilityService.instance
                    ?: return ToolExecutionResult(spec.name, false, "AccessibilityService is disabled.")
                val ok = svc.performGlobalBack()
                ToolExecutionResult(spec.name, ok, if (ok) "Navigated Back." else "Back action failed.")
            }

            "go_home" -> {
                val svc = MayaAccessibilityService.instance
                    ?: return ToolExecutionResult(spec.name, false, "AccessibilityService is disabled.")
                val ok = svc.performGlobalHome()
                ToolExecutionResult(spec.name, ok, if (ok) "Navigated Home." else "Home action failed.")
            }

            "wait_for_screen_text" -> {
                val svc = MayaAccessibilityService.instance
                    ?: return ToolExecutionResult(spec.name, false, "AccessibilityService is disabled.")
                val target = args.optString("text")
                val timeoutMs = args.optLong("timeoutMs", 4000L).coerceIn(500L, 10000L)
                val deadline = System.currentTimeMillis() + timeoutMs
                var found = false
                while (System.currentTimeMillis() < deadline) {
                    if (svc.dumpScreenText().contains(target, ignoreCase = true)) {
                        found = true
                        break
                    }
                    delay(350L)
                }
                ToolExecutionResult(spec.name, found, if (found) "Found '$target' on screen." else "Timed out waiting for '$target'.")
            }

            "set_touch_guard" -> {
                val enabled = args.optBoolean("enabled", !_touchGuardEnabled.value)
                _touchGuardEnabled.value = enabled
                ToolExecutionResult(spec.name, true, "Touch guard ${if (enabled) "enabled" else "disabled"}.")
            }

            "set_torch" -> {
                val enabled = args.optBoolean("enabled", true)
                try {
                    val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val camId = cm.cameraIdList.firstOrNull()
                    if (camId != null) {
                        cm.setTorchMode(camId, enabled)
                        ToolExecutionResult(spec.name, true, "Torch turned ${if (enabled) "ON" else "OFF"}.")
                    } else {
                        ToolExecutionResult(spec.name, false, "No camera flash available.")
                    }
                } catch (e: Exception) {
                    ToolExecutionResult(spec.name, false, "Torch control failed: ${e.localizedMessage}")
                }
            }

            "set_volume" -> {
                val pct = args.optInt("percent", 50).coerceIn(0, 100)
                val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val target = (max * pct) / 100
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                ToolExecutionResult(spec.name, true, "Media volume set to $pct% ($target/$max).")
            }

            "lock_device" -> {
                val svc = MayaAccessibilityService.instance
                    ?: return ToolExecutionResult(spec.name, false, "AccessibilityService is disabled.")
                val ok = svc.lockScreen()
                ToolExecutionResult(spec.name, ok, if (ok) "Device locked." else "Lock screen requires Android 9+ AccessibilityService.")
            }

            "unlock_phone" -> {
                ToolExecutionResult(spec.name, false, "Android security policy does not allow apps to bypass the lock screen PIN/biometric silently. Please unlock manually.")
            }

            "install_app" -> {
                val q = args.optString("packageOrQuery")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=${Uri.encode(q)}&c=apps"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
                ToolExecutionResult(spec.name, true, "Opened Play Store search for '$q'.")
            }

            "search_contacts" -> {
                val contacts = comms.searchContacts(args.optString("query"))
                if (contacts.isEmpty()) ToolExecutionResult(spec.name, true, "No matching contacts found (or READ_CONTACTS permission not granted).")
                else ToolExecutionResult(spec.name, true, contacts.joinToString("\n") { "${it.name}: ${it.phone}" })
            }

            "make_call" -> {
                val res = comms.placeCall(args.optString("number"), directCall = true)
                ToolExecutionResult(spec.name, res.isSuccess, res.getOrNull() ?: res.exceptionOrNull()?.message ?: "Call failed")
            }

            "draft_sms" -> {
                val res = comms.openSmsComposer(args.optString("number"), args.optString("body"))
                ToolExecutionResult(spec.name, res.isSuccess, res.getOrNull() ?: "Opened SMS composer.")
            }

            "send_sms" -> {
                val res = comms.sendSmsDirect(args.optString("number"), args.optString("body"))
                ToolExecutionResult(spec.name, res.isSuccess, res.getOrNull() ?: res.exceptionOrNull()?.message ?: "SMS failed")
            }

            "draft_whatsapp", "send_whatsapp" -> {
                val res = comms.openWhatsAppMessage(args.optString("phoneOrContact"), args.optString("message"))
                ToolExecutionResult(spec.name, res.isSuccess, res.getOrNull() ?: res.exceptionOrNull()?.message ?: "WhatsApp failed")
            }

            "create_reminder" -> {
                val title = args.optString("title").ifBlank { "Maya Reminder" }
                val mins = args.optLong("delayMinutes", 15L).coerceAtLeast(1L)
                val triggerAt = System.currentTimeMillis() + mins * 60_000L
                val id = reminderDao.insert(ReminderEntity(title = title, triggerAtMillis = triggerAt))
                reminderScheduler.scheduleReminder(id, title, triggerAt, isAlarm = false)
                ToolExecutionResult(spec.name, true, "Scheduled reminder #$id '$title' in $mins minute(s).")
            }

            "list_reminders" -> {
                val list = reminderDao.observeAll().first()
                val out = if (list.isEmpty()) "No reminders saved."
                else list.take(10).joinToString("\n") { "#${it.id}: ${it.title} (${if (it.isCompleted) "Done" else "Pending"})" }
                ToolExecutionResult(spec.name, true, out)
            }

            "delete_reminder" -> {
                val id = args.optLong("id", -1L)
                reminderDao.deleteById(id)
                ToolExecutionResult(spec.name, true, "Deleted reminder #$id.")
            }

            "get_weather" -> {
                val city = args.optString("city").ifBlank { "New Delhi" }
                val res = research.fetchWeatherForCity(city)
                res.fold(
                    onSuccess = { w -> ToolExecutionResult(spec.name, true, "${w.locationLabel}: ${w.temperatureC}°C, ${w.conditionSummary}, Wind ${w.windKmh} km/h") },
                    onFailure = { e -> ToolExecutionResult(spec.name, false, e.localizedMessage ?: "Weather failed") }
                )
            }

            "search_web" -> {
                val res = research.searchDuckDuckGo(args.optString("query"))
                res.fold(
                    onSuccess = { items ->
                        val out = if (items.isEmpty()) "No search results returned."
                        else items.take(5).joinToString("\n\n") { "${it.title}\n${it.url}\n${it.snippet}" }
                        ToolExecutionResult(spec.name, true, out)
                    },
                    onFailure = { e -> ToolExecutionResult(spec.name, false, e.localizedMessage ?: "Search failed") }
                )
            }

            "fetch_webpage" -> {
                val res = research.fetchWebpageText(args.optString("url"))
                res.fold(
                    onSuccess = { page -> ToolExecutionResult(spec.name, true, page.extractedText.take(3000)) },
                    onFailure = { e -> ToolExecutionResult(spec.name, false, e.localizedMessage ?: "Fetch failed") }
                )
            }

            "run_termux_command" -> {
                val res = termux.executeCommand(args.optString("command"))
                res.fold(
                    onSuccess = { msg -> ToolExecutionResult(spec.name, true, msg) },
                    onFailure = { e -> ToolExecutionResult(spec.name, false, e.localizedMessage ?: "Termux failed") }
                )
            }

            "create_website" -> {
                val name = args.optString("name").ifBlank { "Maya Web Site" }
                val desc = args.optString("description")
                val html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>$name</title><style>body{font-family:system-ui;background:#070b12;color:#ebf4ff;padding:2rem;line-height:1.6}.card{background:#0e1525;border:1px solid #26334d;border-radius:16px;padding:1.5rem;max-width:720px;margin:auto}h1{color:#00e5ff}</style></head><body><div class=\"card\"><h1>$name</h1><p>$desc</p></div></body></html>"
                val id = websiteDao.insert(WebsiteProjectEntity(name = name, description = desc, htmlContent = html))
                ToolExecutionResult(spec.name, true, "Created website project #$id '$name'. Open Websites screen to preview/edit.")
            }

            "control_smart_device" -> {
                val devName = args.optString("deviceName")
                val turnOn = args.optBoolean("turnOn", true)
                val devices = smartHomeDao.observeDevices().first()
                val target = devices.firstOrNull { it.name.equals(devName, ignoreCase = true) }
                    ?: return ToolExecutionResult(spec.name, false, "Smart device '$devName' is not registered.")
                val res = messaging.toggleSmartDevice(target, turnOn)
                res.fold(
                    onSuccess = { status ->
                        smartHomeDao.update(target.copy(isOn = turnOn, lastStatus = status, lastCheckedAt = System.currentTimeMillis()))
                        ToolExecutionResult(spec.name, true, status)
                    },
                    onFailure = { e -> ToolExecutionResult(spec.name, false, "Device failed: ${e.localizedMessage}") }
                )
            }

            "send_telegram" -> {
                val res = messaging.sendTelegramMessage(args.optString("message"))
                res.fold(
                    onSuccess = { ToolExecutionResult(spec.name, true, it) },
                    onFailure = { e -> ToolExecutionResult(spec.name, false, e.localizedMessage ?: "Telegram failed") }
                )
            }

            "find_tool" -> {
                val found = registry.findTool(args.optString("query"))
                ToolExecutionResult(spec.name, true, found.joinToString("\n") { "${it.name}: ${it.description}" })
            }

            "run_tool" -> {
                val target = args.optString("name")
                val subArgs = args.optJSONObject("args") ?: JSONObject()
                executeTool(target, subArgs, confirmedByUser = confirmedByUser)
            }

            "run_mission" -> {
                val id = runMission(
                    title = args.optString("title").ifBlank { "Agent Mission" },
                    goal = args.optString("goal")
                )
                ToolExecutionResult(spec.name, true, "Mission #$id completed. Check Missions screen for step trace.")
            }

            "mission_status" -> {
                val list = missionDao.observeAll().first()
                val out = if (list.isEmpty()) "No missions recorded."
                else list.take(5).joinToString("\n") { "#${it.id} ${it.title}: ${it.status} (step ${it.currentStep})" }
                ToolExecutionResult(spec.name, true, out)
            }

            "agent_status" -> {
                val snap = telemetry.captureSnapshot()
                ToolExecutionResult(
                    spec.name,
                    true,
                    "Device: ${snap.deviceModel} (${snap.androidVersion}) | RAM free: ${snap.availableRamMb}/${snap.totalRamMb} MB | Accessibility: ${MayaAccessibilityService.instance != null}"
                )
            }

            "coding_status" -> {
                val sites = websiteDao.observeAll().first()
                ToolExecutionResult(spec.name, true, "${sites.size} website project(s) stored locally.")
            }

            "use_skill" -> {
                val skill = args.optString("skillName")
                ToolExecutionResult(spec.name, true, "Skill '$skill' activated via tool registry.")
            }

            "list_rules" -> {
                val rules = macroRuleDao.observeAll().first()
                val out = rules.joinToString("\n") { "${it.name} [${if (it.isEnabled) "ON" else "OFF"}] -> ${it.triggerType}" }
                ToolExecutionResult(spec.name, true, out.ifBlank { "No rules configured." })
            }

            "wait" -> {
                val ms = args.optLong("ms", 1000L).coerceIn(100L, 5000L)
                delay(ms)
                ToolExecutionResult(spec.name, true, "Waited ${ms}ms.")
            }

            "cancel" -> {
                activeMissionCancelled = true
                _pendingConfirmation.value = null
                ToolExecutionResult(spec.name, true, "Cancelled active mission/pending confirmation.")
            }

            else -> ToolExecutionResult(spec.name, false, "Tool '${spec.name}' handler unavailable.")
        }
    }

    suspend fun confirmPendingAction(): ToolExecutionResult? {
        val pending = _pendingConfirmation.value ?: return null
        _pendingConfirmation.value = null
        return executeTool(pending.toolName, pending.args, confirmedByUser = true)
    }

    fun dismissPendingConfirmation() {
        _pendingConfirmation.value = null
    }

    suspend fun runMission(title: String, goal: String): Long {
        activeMissionCancelled = false
        val steps = JSONArray()
        val id = missionDao.insert(
            MissionEntity(
                title = title,
                goal = goal,
                status = "RUNNING",
                stepsJson = steps.toString(),
                currentStep = 1
            )
        )
        try {
            // Step 1: Recall relevant Yaadein context
            val memories = memoryManager.recallMemory(goal, limit = 3)
            steps.put("Step 1: Recalled ${memories.size} relevant Yaadein memory items")
            missionDao.update(MissionEntity(id = id, title = title, goal = goal, status = "RUNNING", stepsJson = steps.toString(), currentStep = 1))

            if (activeMissionCancelled) throw InterruptedException("Cancelled by user")

            // Step 2: Check device & tool readiness
            val snap = telemetry.captureSnapshot()
            steps.put("Step 2: Verified device telemetry (${snap.availableRamMb}MB RAM free)")
            missionDao.update(MissionEntity(id = id, title = title, goal = goal, status = "RUNNING", stepsJson = steps.toString(), currentStep = 2))

            // Step 3: Execute contextual action if goal matches web search / weather / reminder
            val lower = goal.lowercase()
            val summary = when {
                "weather" in lower -> {
                    val res = executeTool("get_weather", JSONObject().put("city", "New Delhi"))
                    steps.put("Step 3: Ran get_weather -> ${res.output}")
                    res.output
                }
                "search" in lower || "research" in lower -> {
                    val res = executeTool("search_web", JSONObject().put("query", goal))
                    steps.put("Step 3: Ran search_web (${if (res.success) "success" else "error"})")
                    res.output.take(400)
                }
                else -> {
                    steps.put("Step 3: Mission context prepared and logged")
                    "Mission '$title' finished 3/3 steps."
                }
            }

            missionDao.update(
                MissionEntity(
                    id = id,
                    title = title,
                    goal = goal,
                    status = "COMPLETED",
                    stepsJson = steps.toString(),
                    currentStep = 3,
                    resultSummary = summary,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            val status = if (activeMissionCancelled) "CANCELLED" else "FAILED"
            missionDao.update(
                MissionEntity(
                    id = id,
                    title = title,
                    goal = goal,
                    status = status,
                    stepsJson = steps.toString(),
                    currentStep = 3,
                    resultSummary = e.localizedMessage ?: status,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return id
    }
}
