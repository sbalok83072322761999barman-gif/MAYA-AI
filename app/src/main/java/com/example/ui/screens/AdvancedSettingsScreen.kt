package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.AiProviderId
import com.example.core.TargetFramework
import com.example.security.KeystoreVault
import com.example.ui.MayaMainViewModel
import com.example.ui.components.MayaCard
import com.example.ui.components.MayaScreenTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSettingsScreen(
    vm: MayaMainViewModel,
    onBack: () -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val models by vm.discoveredModels.collectAsStateWithLifecycle()
    val refreshing by vm.isRefreshingModels.collectAsStateWithLifecycle()
    val health by vm.providerHealth.collectAsStateWithLifecycle()
    val usage by vm.usageRecords.collectAsStateWithLifecycle()
    val vaultTick by vm.vaultRefreshTick.collectAsStateWithLifecycle()

    var providerMenuOpen by remember { mutableStateOf(false) }
    var modelMenuOpen by remember { mutableStateOf(false) }
    var frameworkMenuOpen by remember { mutableStateOf(false) }

    var instructionsDraft by remember(settings.customInstructions) { mutableStateOf(settings.customInstructions) }
    var openAiUrlDraft by remember(settings.openaiBaseUrlOverride) { mutableStateOf(settings.openaiBaseUrlOverride) }

    var selectedVaultKey by remember { mutableStateOf(KeystoreVault.KEY_OPENAI) }
    var vaultMenuOpen by remember { mutableStateOf(false) }
    var secretInput by remember { mutableStateOf("") }

    val vaultKeyLabels = remember {
        listOf(
            KeystoreVault.KEY_GEMINI_USER to "Personal Gemini API Key",
            KeystoreVault.KEY_OPENAI to "OpenAI API Key",
            KeystoreVault.KEY_GROQ to "Groq API Key",
            KeystoreVault.KEY_DEEPSEEK to "DeepSeek API Key",
            KeystoreVault.KEY_TOGETHER to "Together AI Key",
            KeystoreVault.KEY_NVIDIA_NIM to "NVIDIA NIM Key",
            KeystoreVault.KEY_ANTHROPIC to "Anthropic API Key",
            KeystoreVault.KEY_OPENROUTER to "OpenRouter API Key",
            KeystoreVault.KEY_DEEPGRAM to "Deepgram Nova-3 Key",
            KeystoreVault.KEY_SERPAPI to "SerpAPI Web Search Key",
            KeystoreVault.KEY_GITHUB_TOKEN to "GitHub Token",
            KeystoreVault.KEY_TELEGRAM_BOT to "Telegram Bot Token",
            KeystoreVault.KEY_NOTION_TOKEN to "Notion Integration Token"
        )
    }

    LaunchedEffect(settings.activeProvider) {
        vm.refreshDiscoveredModels(settings.activeProvider)
    }

    // Usage statistics strictly calculated from local Room records & provider-reported token counts
    val totalRequests = usage.size
    val successCount = usage.count { it.success }
    val failedCount = usage.count { !it.success }
    val rateLimitCount = usage.count { it.errorType == "QUOTA_OR_RATE_LIMIT" }
    val totalPromptTokens = usage.sumOf { it.promptTokens }
    val totalCompletionTokens = usage.sumOf { it.completionTokens }
    val avgLatency = if (successCount > 0) usage.filter { it.success }.map { it.latencyMs }.average().toLong() else 0L

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Advanced Settings", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // SECTION A: MODEL SELECTION (Live Discovery + Refresh + Capabilities + Errors)
                MayaCard(highlightBorder = true) {
                    SectionHeader(
                        title = "Model Selection",
                        subtitle = "Live provider discovery & active chat model routing",
                        icon = Icons.Default.Psychology
                    )

                    ExposedDropdownMenuBox(expanded = providerMenuOpen, onExpandedChange = { providerMenuOpen = !providerMenuOpen }) {
                        OutlinedTextField(
                            value = settings.activeProvider.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("AI Provider") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(providerMenuOpen) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("provider_dropdown")
                        )
                        ExposedDropdownMenu(expanded = providerMenuOpen, onDismissRequest = { providerMenuOpen = false }) {
                            AiProviderId.entries.forEach { prov ->
                                DropdownMenuItem(
                                    text = { Text(prov.displayName) },
                                    onClick = {
                                        providerMenuOpen = false
                                        vm.selectProvider(prov)
                                    }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = modelMenuOpen,
                            onExpandedChange = { modelMenuOpen = !modelMenuOpen },
                            modifier = Modifier.weight(1f)
                        ) {
                            val activeModelObj = models.firstOrNull { it.id == settings.selectedModelId }
                            OutlinedTextField(
                                value = activeModelObj?.let { "${it.displayName} (${it.id})" } ?: settings.selectedModelId,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Active Model") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(modelMenuOpen) },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                                    .testTag("model_dropdown")
                            )
                            ExposedDropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
                                if (models.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No models discovered — tap Refresh or configure API key") },
                                        onClick = { modelMenuOpen = false }
                                    )
                                } else {
                                    models.forEach { m ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(m.displayName, style = MaterialTheme.typography.bodyLarge)
                                                    Text(m.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                            },
                                            onClick = {
                                                modelMenuOpen = false
                                                vm.selectModel(m.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = { vm.refreshDiscoveredModels() },
                            enabled = !refreshing,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("refresh_models_button")
                        ) {
                            if (refreshing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh available models")
                            }
                        }
                    }

                    val selectedModel = models.firstOrNull { it.id == settings.selectedModelId }
                    if (selectedModel != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            selectedModel.capabilities.forEach { cap ->
                                StatusPill(cap, MaterialTheme.colorScheme.primary)
                            }
                            if (selectedModel.isDeprecated) {
                                StatusPill("Deprecated", MaterialTheme.colorScheme.error)
                            }
                        }
                        if (selectedModel.note.isNotBlank()) {
                            Text(selectedModel.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    health?.let { h ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (h.isReachable) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (h.isReachable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = h.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (h.isReachable) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { vm.testProviderHealth() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_provider_button")
                    ) {
                        Text("Verify Provider Connection & Quota")
                    }
                }

                // SECTION B: FRAMEWORK SELECTION (Connected to Coding Agent & Website Builder)
                MayaCard {
                    SectionHeader(
                        title = "Framework Selection",
                        subtitle = "Sets actual target stack for Coding Agent & Codegen prompts",
                        icon = Icons.Default.Code
                    )

                    ExposedDropdownMenuBox(expanded = frameworkMenuOpen, onExpandedChange = { frameworkMenuOpen = !frameworkMenuOpen }) {
                        OutlinedTextField(
                            value = settings.activeFramework.title,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Active Development Framework") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(frameworkMenuOpen) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("framework_dropdown")
                        )
                        ExposedDropdownMenu(expanded = frameworkMenuOpen, onDismissRequest = { frameworkMenuOpen = false }) {
                            TargetFramework.entries.forEach { fw ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(fw.title, style = MaterialTheme.typography.bodyLarge)
                                            Text(fw.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        frameworkMenuOpen = false
                                        vm.selectFramework(fw)
                                    }
                                )
                            }
                        }
                    }

                    Text(
                        text = settings.activeFramework.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Prompt Guidance: ${settings.activeFramework.systemGuidance}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // SECTION C: CUSTOM SYSTEM INSTRUCTIONS (Injected into every AI request)
                MayaCard {
                    SectionHeader(
                        title = "Custom System Instructions",
                        subtitle = "Persisted locally & injected into every LLM system prompt when enabled",
                        icon = Icons.Default.EditNote
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Custom Instructions", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = settings.customInstructionsEnabled,
                            onCheckedChange = { vm.toggleCustomInstructions(it) },
                            modifier = Modifier.testTag("custom_instructions_switch")
                        )
                    }

                    OutlinedTextField(
                        value = instructionsDraft,
                        onValueChange = { instructionsDraft = it },
                        label = { Text("System Instructions") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_instructions_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { vm.saveCustomInstructions(instructionsDraft, settings.customInstructionsEnabled) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_instructions_button")
                        ) {
                            Text("Save")
                        }
                        OutlinedButton(
                            onClick = { vm.resetCustomInstructions() },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("reset_instructions_button")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reset")
                        }
                        IconButton(
                            onClick = {
                                instructionsDraft = ""
                                vm.deleteCustomInstructions()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("delete_instructions_button")
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete instructions", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // SECTION D: USAGE AND FREE REQUESTS (Strict distinction: Provider vs Local counters)
                MayaCard {
                    SectionHeader(
                        title = "Usage & Request Telemetry",
                        subtitle = "Real local counters & provider-reported token usage (no invented quotas)",
                        icon = Icons.Default.Analytics
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UsageMetricBox("Local Requests", "$totalRequests", Modifier.weight(1f))
                        UsageMetricBox("Succeeded", "$successCount", Modifier.weight(1f))
                        UsageMetricBox("Failed", "$failedCount", Modifier.weight(1f))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UsageMetricBox("Provider Input Tok", "$totalPromptTokens", Modifier.weight(1f))
                        UsageMetricBox("Provider Output Tok", "$totalCompletionTokens", Modifier.weight(1f))
                        UsageMetricBox("429 Rate Limits", "$rateLimitCount", Modifier.weight(1f))
                    }

                    Text(
                        text = "Avg Latency: ${avgLatency}ms. Remaining free-tier request quotas are only shown when an API response explicitly supplies quota headers; Firebase AI Logic quotas are governed by your Google Cloud/Firebase billing tier.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // SECTION E: API KEY VAULT & AUDIO ENGINE PREFERENCES
                MayaCard {
                    SectionHeader("Encrypted API Key Vault", "Hardware-backed AndroidKeyStore AES-256-GCM", Icons.Default.Key)

                    ExposedDropdownMenuBox(expanded = vaultMenuOpen, onExpandedChange = { vaultMenuOpen = !vaultMenuOpen }) {
                        val label = vaultKeyLabels.firstOrNull { it.first == selectedVaultKey }?.second ?: selectedVaultKey
                        OutlinedTextField(
                            value = label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Service Credential") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(vaultMenuOpen) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = vaultMenuOpen, onDismissRequest = { vaultMenuOpen = false }) {
                            vaultKeyLabels.forEach { (k, title) ->
                                val saved = remember(vaultTick, k) { vm.hasVaultSecret(k) }
                                DropdownMenuItem(
                                    text = { Text(if (saved) "$title (Configured)" else title) },
                                    onClick = {
                                        selectedVaultKey = k
                                        vaultMenuOpen = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = secretInput,
                        onValueChange = { secretInput = it },
                        label = { Text("Enter Secret / API Key (leave blank + Save to remove)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("vault_secret_input")
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                vm.saveVaultSecret(selectedVaultKey, secretInput)
                                secretInput = ""
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_vault_key_button")
                        ) {
                            Text("Save Encrypted Key")
                        }
                    }

                    if (settings.activeProvider == AiProviderId.OPENAI) {
                        OutlinedTextField(
                            value = openAiUrlDraft,
                            onValueChange = { openAiUrlDraft = it },
                            label = { Text("OpenAI-Compatible Base URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(onClick = { vm.updateOpenAiBaseUrl(openAiUrlDraft) }) {
                            Text("Save Custom Base URL")
                        }
                    }
                }

                MayaCard {
                    SectionHeader("Audio Pipeline & Memory Limits", "Optimized for 4 GB RAM Vivo Y21 5G", Icons.Default.GraphicEq)
                    ToggleRow("Hardware Acoustic Echo Canceler (AEC)", settings.aecEnabled) {
                        vm.updateAudioProcessing(it, settings.dtlnFilterEnabled, settings.noiseSuppressionEnabled, settings.agcEnabled, settings.bargeInEnabled)
                    }
                    ToggleRow("DTLN-Style Soft Spectral Noise Gate", settings.dtlnFilterEnabled) {
                        vm.updateAudioProcessing(settings.aecEnabled, it, settings.noiseSuppressionEnabled, settings.agcEnabled, settings.bargeInEnabled)
                    }
                    ToggleRow("Hardware Noise Suppression & AGC", settings.noiseSuppressionEnabled) {
                        vm.updateAudioProcessing(settings.aecEnabled, settings.dtlnFilterEnabled, it, it, settings.bargeInEnabled)
                    }
                    ToggleRow("Voice Barge-In Interruption", settings.bargeInEnabled) {
                        vm.updateAudioProcessing(settings.aecEnabled, settings.dtlnFilterEnabled, settings.noiseSuppressionEnabled, settings.agcEnabled, it)
                    }
                    ToggleRow("4 GB RAM Low-Power Animation Mode", settings.lowPowerMode) {
                        vm.updateLowPowerMode(it)
                    }
                    ToggleRow("Auto-Save Memories from Chat", settings.autoMemorySave) {
                        vm.updateAutoMemorySave(it)
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageMetricBox(label: String, value: String, modifier: Modifier) {
    MayaCard(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}
