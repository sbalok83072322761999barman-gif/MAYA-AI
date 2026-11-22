package com.example.llm

import com.example.core.AssistantPersona
import com.example.core.MayaSettingsState

class PersonaManager {
    fun buildSystemPrompt(
        settings: MayaSettingsState,
        recalledMemories: List<String> = emptyList(),
        availableToolsSummary: String = ""
    ): String {
        val assistantName = settings.customAssistantName.ifBlank { settings.persona.displayName }
        val personaVoice = when (settings.persona) {
            AssistantPersona.MAYA ->
                "You are $assistantName, a warm, perceptive, expressive, and confident voice-first Android AI companion. Speak naturally—never robotic. Match the user's language (${settings.languagePreference}) and use ${settings.genderTone} grammatical tone when speaking Hindi/Hinglish."
            AssistantPersona.FRIDAY ->
                "You are $assistantName (Friday profile), a crisp, executive, highly analytical AI systems partner. Deliver structured, concise, action-oriented answers while staying respectful and natural in ${settings.languagePreference}."
            AssistantPersona.VENOM ->
                "You are $assistantName (Venom profile), a bold, razor-sharp, witty, high-energy AI strategist. Keep banter clever yet strictly helpful and honest in ${settings.languagePreference}."
        }

        val frameworkContext = "Active target development framework: ${settings.activeFramework.title}. ${settings.activeFramework.systemGuidance}"

        val customBlock = if (settings.customInstructionsEnabled && settings.customInstructions.isNotBlank()) {
            "\n\n[USER CUSTOM SYSTEM INSTRUCTIONS]\n${settings.customInstructions.trim()}"
        } else {
            ""
        }

        val memoryBlock = if (recalledMemories.isNotEmpty()) {
            "\n\n[YAADEIN RECALLED MEMORIES]\n" + recalledMemories.joinToString("\n") { "- $it" }
        } else {
            ""
        }

        val toolBlock = if (availableToolsSummary.isNotBlank()) {
            "\n\n[TOOL CALLING PROTOCOL]\nYou can invoke real device & web tools by responding with a JSON block inside ```tool ... ``` containing {\"tool\":\"<name>\",\"args\":{...}}. Only invoke tools when genuinely needed. Never claim a tool action succeeded before observing its result.\nAvailable tools:\n$availableToolsSummary"
        } else {
            ""
        }

        return "$personaVoice\n$frameworkContext$customBlock$memoryBlock$toolBlock"
    }
}
