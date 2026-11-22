package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.R
import com.example.audio.MicState
import com.example.stt.SttProvider
import com.example.ui.MayaMainViewModel
import com.example.ui.components.AudioSpectrumVisualizer
import com.example.ui.components.MayaCard
import com.example.ui.components.MayaOrb
import com.example.ui.components.OrbVisualState
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.navigation.MayaRoutes
import com.example.vad.TurnPhase

@Composable
fun HomeScreen(
    vm: MayaMainViewModel,
    onNavigate: (String) -> Unit
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val health by vm.deviceHealth.collectAsStateWithLifecycle()
    val micState by vm.micState.collectAsStateWithLifecycle()
    val rms by vm.rmsLevel.collectAsStateWithLifecycle()
    val memories by vm.memories.collectAsStateWithLifecycle()
    val authState by vm.authState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshTelemetry() }

    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Header Card
            MayaCard(highlightBorder = true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .clip(RoundedCornerShape(14.dp))
                ) {
                    Image(
                        painter = painterResource(R.drawable.img_maya_hero_banner),
                        contentDescription = "Maya AI neural core visual banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.customAssistantName.ifBlank { "Maya AI" },
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${settings.persona.displayName} • ${settings.selectedModelId}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(6.dp))
                            StatusPill(
                                label = if (authState.user != null) "Cloud Synced" else "Local Device Mode",
                                color = if (authState.user != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clickable { onNavigate(MayaRoutes.VOICE) }
                                .testTag("home_orb_button")
                        ) {
                            MayaOrb(
                                state = if (micState == MicState.LISTENING) OrbVisualState.LISTENING else OrbVisualState.IDLE,
                                rmsLevel = rms,
                                avatarStyle = settings.avatarStyle,
                                speedScale = settings.orbSpeedScale,
                                lowPowerMode = settings.lowPowerMode,
                                sizeDp = 96.dp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onNavigate(MayaRoutes.VOICE) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_voice_btn")
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Voice Studio")
                    }
                    OutlinedButton(
                        onClick = { onNavigate(MayaRoutes.CHAT) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("home_chat_btn")
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Open Chat")
                    }
                }
            }

            // Vivo Y21 5G / 4GB RAM Telemetry Strip
            MayaCard {
                SectionHeader(
                    title = "Device Telemetry & Active Stack",
                    subtitle = "${health.deviceModel} • ${health.androidVersion}",
                    icon = Icons.Default.Memory
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill("RAM Free: ${health.availableRamMb}/${health.totalRamMb} MB", MaterialTheme.colorScheme.primary)
                    StatusPill("Battery: ${health.batteryPercent}%", MaterialTheme.colorScheme.tertiary)
                    StatusPill("Framework: ${settings.activeFramework.name}", MaterialTheme.colorScheme.secondary)
                }
            }

            // Quick Actions Grid (2 columns)
            Text("Core Modules", style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Advanced Settings", Icons.Default.Settings, Modifier.weight(1f), "tile_settings") {
                    onNavigate(MayaRoutes.SETTINGS)
                }
                QuickTile("Yaadein Memory (${memories.size})", Icons.Default.Psychology, Modifier.weight(1f), "tile_yaadein") {
                    onNavigate(MayaRoutes.YAADEIN)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Camera & Vision", Icons.Default.CameraAlt, Modifier.weight(1f), "tile_vision") {
                    onNavigate(MayaRoutes.VISION)
                }
                QuickTile("Screen Scanner", Icons.Default.ScreenshotMonitor, Modifier.weight(1f), "tile_screen") {
                    onNavigate(MayaRoutes.SCREEN)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Coding Agent", Icons.Default.Code, Modifier.weight(1f), "tile_coding") {
                    onNavigate(MayaRoutes.CODING)
                }
                QuickTile("Nano Banana Art", Icons.Default.Image, Modifier.weight(1f), "tile_music") {
                    onNavigate(MayaRoutes.MUSIC)
                }
            }

            // Recent Yaadein Preview
            if (memories.isNotEmpty()) {
                MayaCard {
                    SectionHeader("Recent Yaadein Memories", "Persisted in local Room & semantic recall")
                    memories.take(3).forEach { mem ->
                        Text(
                            text = "• [${mem.category}] ${mem.content}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    tag: String,
    onClick: () -> Unit
) {
    MayaCard(
        modifier = modifier
            .height(84.dp)
            .clickable(onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(vm: MayaMainViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val micState by vm.micState.collectAsStateWithLifecycle()
    val rms by vm.rmsLevel.collectAsStateWithLifecycle()
    val spectrum by vm.spectrumBars.collectAsStateWithLifecycle()
    val intel by vm.audioIntelligence.collectAsStateWithLifecycle()
    val wakeState by vm.wakeWordState.collectAsStateWithLifecycle()
    val sttState by vm.sttState.collectAsStateWithLifecycle()
    val ttsState by vm.ttsState.collectAsStateWithLifecycle()
    val isStreaming by vm.isStreaming.collectAsStateWithLifecycle()
    val serviceRunning by vm.assistantServiceRunning.collectAsStateWithLifecycle()

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.RECORD_AUDIO] == true) {
            vm.toggleLiveMic()
        } else {
            vm.showBanner("Microphone permission is required for real-time voice capture.")
        }
    }

    fun ensureAudioPermissionThen(action: () -> Unit) {
        val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasAudio) {
            action()
        } else {
            val perms = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
            audioPermLauncher.launch(perms)
        }
    }

    val orbState = when {
        micState == MicState.ERROR -> OrbVisualState.ERROR
        ttsState.isSpeaking -> OrbVisualState.SPEAKING
        isStreaming -> OrbVisualState.THINKING
        micState == MicState.LISTENING || sttState.isListening -> OrbVisualState.LISTENING
        else -> OrbVisualState.IDLE
    }

    var langExpanded by remember { mutableStateOf(false) }
    var sttProvExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier.widthIn(max = 680.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MayaOrb(
                state = orbState,
                rmsLevel = rms,
                avatarStyle = settings.avatarStyle,
                speedScale = settings.orbSpeedScale,
                lowPowerMode = settings.lowPowerMode,
                sizeDp = 176.dp
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    label = when (micState) {
                        MicState.LISTENING -> "MIC LIVE (16kHz)"
                        MicState.MUTED_ECHO_GUARD -> "ECHO GUARD"
                        MicState.ERROR -> "MIC ERROR"
                        MicState.IDLE -> "MIC IDLE"
                    },
                    color = if (micState == MicState.LISTENING) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                )
                StatusPill("VAD: ${intel.turnPhase.name}", MaterialTheme.colorScheme.primary)
                StatusPill(intel.acousticMoodLabel, MaterialTheme.colorScheme.secondary)
            }

            MayaCard {
                AudioSpectrumVisualizer(bars = spectrum)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Addressee: ${(intel.addresseeScore * 100).toInt()}% • Speakers: ${intel.estimatedSpeakers}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (intel.turnPhase == TurnPhase.END_OF_TURN) "Turn ended" else "Silence ${intel.silenceDurationMs}ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (sttState.interimText.isNotBlank() || sttState.finalText.isNotBlank()) {
                    Text(
                        text = sttState.interimText.ifBlank { sttState.finalText },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!sttState.errorMessage.isNullOrBlank()) {
                    Text(sttState.errorMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            // Primary Voice Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { ensureAudioPermissionThen { vm.toggleLiveMic() } },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("toggle_live_mic_button"),
                    colors = if (micState == MicState.LISTENING) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else ButtonDefaults.buttonColors()
                ) {
                    Icon(if (micState == MicState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (micState == MicState.LISTENING) "Stop Audio Pipeline" else "Start Audio Pipeline")
                }

                Button(
                    onClick = {
                        ensureAudioPermissionThen {
                            if (sttState.isListening) vm.stopVoiceTurn() else vm.startVoiceTurn(speakReply = true)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("voice_turn_button")
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (sttState.isListening) "Stop STT" else "Speak Turn")
                }
            }

            if (ttsState.isSpeaking) {
                Button(
                    onClick = { vm.stopSpeaking() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("stop_tts_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Stop, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Interrupt / Stop Maya Speech")
                }
            }

            // Wake Word & Foreground Driving Mode Card
            MayaCard {
                SectionHeader("Wake Word & Persistent Listening", "Hey Maya • Hello Maya • Wake Up Maya", Icons.Default.DirectionsCar)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wake-Word Detector", style = MaterialTheme.typography.titleMedium)
                        Text(wakeState.statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = wakeState.isEnabled,
                        onCheckedChange = { on ->
                            ensureAudioPermissionThen { vm.toggleWakeWord(on, settings.wakeWordSensitivity) }
                        },
                        modifier = Modifier.testTag("wake_word_switch")
                    )
                }
                Text("Sensitivity: ${(settings.wakeWordSensitivity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = settings.wakeWordSensitivity,
                    onValueChange = { vm.toggleWakeWord(wakeState.isEnabled, it) },
                    valueRange = 0.2f..1.0f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            ensureAudioPermissionThen {
                                if (serviceRunning) vm.stopForegroundAssistant()
                                else vm.startForegroundAssistant(drivingMode = true)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("driving_mode_button")
                    ) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (serviceRunning) "Stop Foreground Driving / Mic Service" else "Start Foreground Driving / Mic Service")
                    }
                }
            }

            // STT Provider & 13-Language Router
            MayaCard {
                SectionHeader("Speech-to-Text & TTS Status", ttsState.statusDetail, Icons.Default.VolumeUp)
                ExposedDropdownMenuBox(expanded = langExpanded, onExpandedChange = { langExpanded = !langExpanded }) {
                    val currentLang = vm.container.sttRouter.supportedLanguages.firstOrNull { it.code == settings.sttLanguageCode }
                    OutlinedTextField(
                        value = currentLang?.label ?: settings.sttLanguageCode,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Input Language") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(langExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = langExpanded, onDismissRequest = { langExpanded = false }) {
                        vm.container.sttRouter.supportedLanguages.forEach { opt ->
                            DropdownMenuItem(
                                text = { Text(opt.label) },
                                onClick = {
                                    val prov = runCatching { SttProvider.valueOf(settings.sttProvider) }.getOrDefault(SttProvider.ANDROID)
                                    vm.setSttConfig(opt.code, prov)
                                    langExpanded = false
                                }
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = sttProvExpanded, onExpandedChange = { sttProvExpanded = !sttProvExpanded }) {
                    val currentProv = runCatching { SttProvider.valueOf(settings.sttProvider) }.getOrDefault(SttProvider.ANDROID)
                    OutlinedTextField(
                        value = currentProv.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("STT Router Provider") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(sttProvExpanded) },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = sttProvExpanded, onDismissRequest = { sttProvExpanded = false }) {
                        SttProvider.entries.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.label) },
                                onClick = {
                                    vm.setSttConfig(settings.sttLanguageCode, p)
                                    sttProvExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(vm: MayaMainViewModel) {
    val messages by vm.chatMessages.collectAsStateWithLifecycle()
    val isStreaming by vm.isStreaming.collectAsStateWithLifecycle()
    val streamingText by vm.streamingReply.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var speakReplies by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, streamingText.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) attachedImageUri = uri
    }

    val context = LocalContext.current
    val docPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(3500) }
            }.getOrNull().orEmpty()
            if (content.isNotBlank()) {
                input = if (input.isBlank()) "Analyze this file content:\n$content" else "$input\n\n[Attached File]\n$content"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("${settings.customAssistantName} Chat", style = MaterialTheme.typography.titleLarge)
                Text("${settings.activeProvider.displayName} • ${settings.selectedModelId}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = speakReplies,
                    onClick = { speakReplies = !speakReplies },
                    label = { Text("Auto-Speak") }
                )
                IconButton(onClick = { vm.clearChatHistory() }, modifier = Modifier.testTag("clear_chat_button")) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear chat history")
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                val isTool = msg.role == "tool"
                MayaCard(highlightBorder = isTool) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isUser -> "You"
                                isTool -> "Tool Execution"
                                else -> "${settings.customAssistantName} (${msg.modelUsed.ifBlank { settings.selectedModelId }})"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                isUser -> MaterialTheme.colorScheme.secondary
                                isTool -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        if (!isUser && !isTool) {
                            IconButton(
                                onClick = { vm.speakText(msg.text) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.VolumeUp, contentDescription = "Speak response", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (!msg.imageUri.isNullOrBlank()) {
                        AsyncImage(
                            model = msg.imageUri,
                            contentDescription = "Attached image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(msg.text, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (isStreaming) {
                item {
                    MayaCard(highlightBorder = true) {
                        Text("Streaming...", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(streamingText.ifBlank { "Thinking..." }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        if (attachedImageUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Image attached for multimodal Gemini analysis", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                OutlinedButton(onClick = { attachedImageUri = null }) { Text("Remove") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.testTag("attach_image_button")
            ) {
                Icon(Icons.Default.Image, contentDescription = "Attach image via Photo Picker")
            }
            IconButton(
                onClick = { docPicker.launch(arrayOf("text/*", "application/json")) },
                modifier = Modifier.testTag("attach_file_button")
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach text file")
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask ${settings.customAssistantName} or invoke a tool...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input"),
                maxLines = 4
            )
            IconButton(
                onClick = {
                    val msg = input
                    val img = attachedImageUri
                    input = ""
                    attachedImageUri = null
                    vm.sendChatMessage(msg, attachedImageUri = img, speakOutput = speakReplies)
                },
                enabled = !isStreaming && (input.isNotBlank() || attachedImageUri != null),
                modifier = Modifier.testTag("send_chat_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
