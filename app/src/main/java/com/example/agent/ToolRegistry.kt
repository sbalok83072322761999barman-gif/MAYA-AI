package com.example.agent

import org.json.JSONObject

data class AgentToolSpec(
    val name: String,
    val category: String,
    val description: String,
    val parametersSchema: Map<String, String>,
    val requiresConfirmation: Boolean = false
)

data class ToolExecutionResult(
    val toolName: String,
    val success: Boolean,
    val output: String,
    val requiresUserConfirmation: Boolean = false,
    val pendingConfirmationPrompt: String? = null
)

class ToolRegistry {
    val tools: List<AgentToolSpec> = listOf(
        // Memory
        AgentToolSpec("remember_fact", "Memory", "Save a fact to Yaadein long-term memory", mapOf("content" to "string", "category" to "string", "importance" to "int(1-5)")),
        AgentToolSpec("recall_memory", "Memory", "Search Yaadein semantic memories", mapOf("query" to "string")),
        AgentToolSpec("forget_memory", "Memory", "Delete a memory by numeric ID", mapOf("id" to "long")),

        // Accessibility & Device Automation
        AgentToolSpec("open_app", "Automation", "Open an Android app by package name", mapOf("packageName" to "string")),
        AgentToolSpec("open_app_by_name", "Automation", "Search installed launcher apps and open by display name", mapOf("appName" to "string")),
        AgentToolSpec("open_settings", "Automation", "Open Android system or accessibility settings", mapOf("section" to "string")),
        AgentToolSpec("read_screen_elements", "Automation", "Read interactive UI elements via AccessibilityService", emptyMap()),
        AgentToolSpec("read_screen_text", "Automation", "Extract visible text on screen via AccessibilityService", emptyMap()),
        AgentToolSpec("tap_text", "Automation", "Tap an element matching visible text", mapOf("text" to "string")),
        AgentToolSpec("tap_element", "Automation", "Tap a node by view ID or bounds", mapOf("viewId" to "string")),
        AgentToolSpec("type_text", "Automation", "Type text into currently focused input field", mapOf("text" to "string")),
        AgentToolSpec("scroll", "Automation", "Scroll forward or backward on screen", mapOf("direction" to "up|down")),
        AgentToolSpec("scroll_screen", "Automation", "Alias to scroll screen content", mapOf("direction" to "up|down")),
        AgentToolSpec("swipe", "Automation", "Perform a swipe gesture on screen", mapOf("direction" to "left|right|up|down")),
        AgentToolSpec("go_back", "Automation", "Perform global Back navigation", emptyMap()),
        AgentToolSpec("go_home", "Automation", "Perform global Home navigation", emptyMap()),
        AgentToolSpec("fill_form", "Automation", "Fill focused field and advance", mapOf("text" to "string")),
        AgentToolSpec("wait_for_screen_text", "Automation", "Poll accessibility tree until text appears", mapOf("text" to "string", "timeoutMs" to "long")),
        AgentToolSpec("set_touch_guard", "Automation", "Toggle touch guard mode", mapOf("enabled" to "boolean")),
        AgentToolSpec("set_torch", "Automation", "Turn device flashlight on or off", mapOf("enabled" to "boolean")),
        AgentToolSpec("set_volume", "Automation", "Adjust media/assistant volume percentage", mapOf("percent" to "int")),
        AgentToolSpec("lock_device", "Automation", "Lock screen via AccessibilityService (Android 9+)", emptyMap(), requiresConfirmation = true),
        AgentToolSpec("unlock_phone", "Automation", "Prompt device keyguard/biometric unlock flow", emptyMap()),
        AgentToolSpec("install_app", "Automation", "Open Play Store / supported app install page", mapOf("packageOrQuery" to "string")),

        // Comms
        AgentToolSpec("search_contacts", "Comms", "Lookup contacts by name or number", mapOf("query" to "string")),
        AgentToolSpec("make_call", "Comms", "Dial or place phone call (requires confirmation)", mapOf("number" to "string"), requiresConfirmation = true),
        AgentToolSpec("draft_sms", "Comms", "Open SMS composer draft", mapOf("number" to "string", "body" to "string")),
        AgentToolSpec("send_sms", "Comms", "Send SMS message (requires explicit confirmation)", mapOf("number" to "string", "body" to "string"), requiresConfirmation = true),
        AgentToolSpec("draft_whatsapp", "Comms", "Open WhatsApp message draft", mapOf("phoneOrContact" to "string", "message" to "string")),
        AgentToolSpec("send_whatsapp", "Comms", "Open WhatsApp & confirm send", mapOf("phoneOrContact" to "string", "message" to "string"), requiresConfirmation = true),

        // Scheduling & Productivity
        AgentToolSpec("create_reminder", "Scheduling", "Create a persistent reminder / WorkManager job", mapOf("title" to "string", "delayMinutes" to "long")),
        AgentToolSpec("list_reminders", "Scheduling", "List active reminders & alarms", emptyMap()),
        AgentToolSpec("delete_reminder", "Scheduling", "Delete reminder by ID", mapOf("id" to "long")),
        AgentToolSpec("get_weather", "Location", "Fetch real current weather & forecast via Open-Meteo", mapOf("city" to "string")),

        // Research & Termux & Integrations
        AgentToolSpec("search_web", "Research", "Search web via DuckDuckGo / SerpAPI", mapOf("query" to "string")),
        AgentToolSpec("fetch_webpage", "Research", "Fetch and extract text from URL", mapOf("url" to "string")),
        AgentToolSpec("run_termux_command", "Termux", "Execute validated non-root Termux RUN_COMMAND", mapOf("command" to "string"), requiresConfirmation = true),
        AgentToolSpec("create_website", "Coding", "Create responsive HTML/CSS/JS project", mapOf("name" to "string", "description" to "string")),
        AgentToolSpec("control_smart_device", "SmartHome", "Toggle or query configured HTTP smart-home device", mapOf("deviceName" to "string", "turnOn" to "boolean")),
        AgentToolSpec("send_telegram", "Integrations", "Send Telegram message via configured bot", mapOf("message" to "string"), requiresConfirmation = true),

        // Agent Meta Tools
        AgentToolSpec("find_tool", "Agent", "Discover tools matching capability keyword", mapOf("query" to "string")),
        AgentToolSpec("run_tool", "Agent", "Invoke another tool by name and JSON args", mapOf("name" to "string", "args" to "json")),
        AgentToolSpec("run_mission", "Agent", "Create and run a multi-step agent mission", mapOf("title" to "string", "goal" to "string")),
        AgentToolSpec("mission_status", "Agent", "Inspect active and recent missions", emptyMap()),
        AgentToolSpec("agent_status", "Agent", "Report Maya AI engine, memory, and tool runtime status", emptyMap()),
        AgentToolSpec("coding_status", "Agent", "Report generated websites/projects status", emptyMap()),
        AgentToolSpec("use_skill", "Agent", "Run a built-in agent skill workflow", mapOf("skillName" to "string")),
        AgentToolSpec("list_rules", "Agent", "List built-in and custom macro rules", emptyMap()),
        AgentToolSpec("wait", "Agent", "Pause execution briefly (max 5000ms)", mapOf("ms" to "long")),
        AgentToolSpec("cancel", "Agent", "Cancel active mission or speech output", emptyMap())
    )

    fun findTool(query: String): List<AgentToolSpec> {
        val q = query.lowercase().trim()
        if (q.isBlank()) return tools
        return tools.filter {
            it.name.contains(q, ignoreCase = true) ||
                it.category.contains(q, ignoreCase = true) ||
                it.description.contains(q, ignoreCase = true)
        }
    }

    fun getSpec(name: String): AgentToolSpec? = tools.firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun validateArgs(spec: AgentToolSpec, args: JSONObject): String? {
        // Basic validation: check required primary args aren't maliciously huge
        val keys = args.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = args.optString(k)
            if (v.length > 8000) return "Argument '$k' exceeds 8000 character safety bound."
        }
        return null
    }

    fun compactPromptSummary(): String {
        return tools.joinToString("\n") { "- ${it.name}(${it.parametersSchema.keys.joinToString(",")}): ${it.description}" }
    }
}
