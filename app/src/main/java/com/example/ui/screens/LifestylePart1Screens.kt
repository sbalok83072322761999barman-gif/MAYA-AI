package com.example.ui.screens

import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.AssistantPersona
import com.example.services.MayaWallpaperService
import com.example.ui.MayaMainViewModel
import com.example.ui.MayaModulesViewModel
import com.example.ui.components.MayaCard
import com.example.ui.components.MayaOrb
import com.example.ui.components.MayaScreenTopBar
import com.example.ui.components.OrbVisualState
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaScreen(vm: MayaMainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ttsState by vm.ttsState.collectAsStateWithLifecycle()

    var selectedPersona by remember(settings.persona) { mutableStateOf(settings.persona) }
    var customName by remember(settings.customAssistantName) { mutableStateOf(settings.customAssistantName) }
    var language by remember(settings.languagePreference) { mutableStateOf(settings.languagePreference) }
    var genderTone by remember(settings.genderTone) { mutableStateOf(settings.genderTone) }
    var rate by remember(settings.voiceRate) { mutableFloatStateOf(settings.voiceRate) }
    var pitch by remember(settings.voicePitch) { mutableFloatStateOf(settings.voicePitch) }
    var voiceName by remember(settings.selectedVoiceName) { mutableStateOf(settings.selectedVoiceName) }
    var voiceMenuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Personas & Voice Style", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Assistant Identity", "Customize name, persona temperament & Hinglish/Hindi grammar", Icons.Default.RecordVoiceOver)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistantPersona.entries.forEach { p ->
                            FilterChip(
                                selected = selectedPersona == p,
                                onClick = {
                                    selectedPersona = p
                                    if (customName in listOf("Maya", "Friday", "Venom")) customName = p.displayName
                                },
                                label = { Text(p.displayName) }
                            )
                        }
                    }
                    Text(selectedPersona.tagline, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = customName, onValueChange = { customName = it }, label = { Text("Assistant Name") }, modifier = Modifier.fillMaxWidth())

                    Text("Language Mode", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Hinglish", "Hindi", "English").forEach { lang ->
                            FilterChip(selected = language == lang, onClick = { language = lang }, label = { Text(lang) })
                        }
                    }

                    Text("Grammar Tone", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Feminine", "Masculine", "Neutral").forEach { g ->
                            FilterChip(selected = genderTone == g, onClick = { genderTone = g }, label = { Text(g) })
                        }
                    }
                }

                MayaCard {
                    SectionHeader("TTS Voice Controls", ttsState.statusDetail)
                    ExposedDropdownMenuBox(expanded = voiceMenuOpen, onExpandedChange = { voiceMenuOpen = !voiceMenuOpen }) {
                        OutlinedTextField(
                            value = voiceName.ifBlank { "System Default Locale Voice" },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Android TTS Voice") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(voiceMenuOpen) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = voiceMenuOpen, onDismissRequest = { voiceMenuOpen = false }) {
                            DropdownMenuItem(text = { Text("System Default") }, onClick = { voiceName = ""; voiceMenuOpen = false })
                            ttsState.availableVoices.take(20).forEach { v ->
                                DropdownMenuItem(
                                    text = { Text("${v.localeTag} • ${v.name}") },
                                    onClick = { voiceName = v.name; voiceMenuOpen = false }
                                )
                            }
                        }
                    }

                    Text("Speech Rate: ${String.format("%.2f", rate)}x", style = MaterialTheme.typography.labelMedium)
                    Slider(value = rate, onValueChange = { rate = it }, valueRange = 0.6f..1.8f)

                    Text("Pitch: ${String.format("%.2f", pitch)}x", style = MaterialTheme.typography.labelMedium)
                    Slider(value = pitch, onValueChange = { pitch = it }, valueRange = 0.6f..1.6f)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                vm.updatePersonaAndVoice(selectedPersona, customName, language, genderTone, rate, pitch, voiceName)
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("save_persona_button")
                        ) { Text("Save Persona") }
                        OutlinedButton(
                            onClick = { vm.container.ttsRouter.testVoice() },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Test Voice") }
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarScreen(vm: MayaMainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val rms by vm.rmsLevel.collectAsStateWithLifecycle()
    var speed by remember(settings.orbSpeedScale) { mutableFloatStateOf(settings.orbSpeedScale) }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Avatar & Live Wallpaper", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Interactive Neural Avatar", "Lightweight Canvas rendering for 4 GB RAM", Icons.Default.FaceRetouchingNatural)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.img_maya_avatar_core),
                            contentDescription = "Maya AI avatar core artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(110.dp).clip(RoundedCornerShape(55.dp))
                        )
                        MayaOrb(
                            state = OrbVisualState.SPEAKING,
                            rmsLevel = rms,
                            avatarStyle = settings.avatarStyle,
                            speedScale = speed,
                            lowPowerMode = settings.lowPowerMode,
                            sizeDp = 128.dp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("NEURAL_CORE", "SOLAR_PULSE", "CYBER_RING").forEach { style ->
                            FilterChip(
                                selected = settings.avatarStyle == style,
                                onClick = { vm.updateAvatarSettings(style, speed) },
                                label = { Text(style) }
                            )
                        }
                    }

                    Text("Orb Animation Speed: ${String.format("%.1f", speed)}x", style = MaterialTheme.typography.labelMedium)
                    Slider(value = speed, onValueChange = { speed = it }, onValueChangeFinished = { vm.updateAvatarSettings(settings.avatarStyle, speed) }, valueRange = 0.5f..2.0f)

                    Button(
                        onClick = {
                            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                putExtra(
                                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                    ComponentName(context, MayaWallpaperService::class.java)
                                )
                            }
                            runCatching { context.startActivity(intent) }.onFailure {
                                context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("set_wallpaper_button")
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Apply Maya Neural Live Wallpaper")
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(vm: MayaMainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val authState by vm.authState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Google Account & Sync", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Identity & Cloud Backup", "Credential Manager Google Sign-In", Icons.Default.AccountCircle)
                    val user = authState.user
                    if (user != null) {
                        StatusPill("Signed In", MaterialTheme.colorScheme.tertiary)
                        Text("Name: ${user.displayName ?: "Maya User"}", style = MaterialTheme.typography.titleMedium)
                        Text("Email: ${user.email ?: "Hidden"}", style = MaterialTheme.typography.bodyMedium)
                        Text("UID: ${user.uid}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Sync Yaadein & Profile to Firestore", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = settings.cloudSyncEnabled, onCheckedChange = { vm.updateCloudSyncEnabled(it) })
                        }

                        Button(
                            onClick = { vm.signOut() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sign_out_button")
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sign Out")
                        }
                    } else {
                        StatusPill("Local Mode Only", MaterialTheme.colorScheme.secondary)
                        Text("Sign in with Google to enable encrypted Firestore profile, chat backup, and Yaadein cross-device sync.", style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { vm.signInWithGoogle(context) },
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("login_screen_google_btn")
                        ) { Text("Sign in with Google") }
                    }
                }
            }
        }
    }
}

@Composable
fun PlansScreen(vm: MayaMainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val authState by vm.authState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Provider Tiers & Capabilities", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader(
                        title = "Active Runtime Configuration",
                        subtitle = "Transparent provider capability view (no fake in-app subscriptions)",
                        icon = Icons.Default.Verified
                    )
                    Text("• Active AI Provider: ${settings.activeProvider.displayName}", style = MaterialTheme.typography.bodyMedium)
                    Text("• Firebase Auth Status: ${if (authState.user != null) "Authenticated (Cloud Sync Enabled)" else "Local Device Mode"}", style = MaterialTheme.typography.bodyMedium)
                    Text("• On-Device Features (Free): AudioEngine AEC/NS, Wake Word, Android STT/TTS, ML Kit OCR/QR/Faces, Room Yaadein Memory, DuckDuckGo Search, Open-Meteo Weather, Accessibility Automation, Termux Bridge.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                MayaCard {
                    SectionHeader("Cloud Model Tiers")
                    Text("• Firebase AI Logic (Spark / Free): Gemini 3.8 Flash & Gemini 3.5 Flash-Lite via App Check.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Firebase Blaze / Personal Key: Gemini 4 Argon & Nano Banana 2.1 Runtime Image Generation.", style = MaterialTheme.typography.bodyMedium)
                    Text("• Bring Your Own Key (BYOK): OpenAI, Groq, DeepSeek, Together AI, NVIDIA NIM, Anthropic, OpenRouter, Deepgram, SerpAPI — billed strictly by your own provider account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ContactsScreen(modVm: MayaModulesViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val contacts by modVm.contacts.collectAsStateWithLifecycle()
    val callState by modVm.callStateLabel.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var targetNumber by remember { mutableStateOf("") }
    var messageBody by remember { mutableStateOf("") }
    var smartName by remember { mutableStateOf("") }
    var smartUrl by remember { mutableStateOf("http://192.168.1.50/toggle") }
    val smartDevices by modVm.smartDevices.collectAsStateWithLifecycle()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        modVm.searchContacts(query)
        modVm.startCallObserver()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Contacts, Calls, WhatsApp & IoT", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Contacts & Telephony", "Call State: $callState", Icons.Default.Contacts)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search contacts") }, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                modVm.searchContacts(query)
                                modVm.startCallObserver()
                            } else {
                                permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_PHONE_STATE))
                            }
                        }) { Text("Search") }
                    }
                    contacts.take(8).forEach { c ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.name, style = MaterialTheme.typography.titleMedium)
                                Text(c.phone, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { targetNumber = c.phone }) { Text("Select") }
                        }
                    }

                    OutlinedTextField(value = targetNumber, onValueChange = { targetNumber = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = messageBody, onValueChange = { messageBody = it }, label = { Text("SMS / WhatsApp Message") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { modVm.placeCall(targetNumber, direct = false) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Call")
                        }
                        OutlinedButton(onClick = { modVm.sendSms(targetNumber, messageBody, direct = false) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("SMS")
                        }
                        OutlinedButton(onClick = { modVm.sendWhatsApp(targetNumber, messageBody) }, modifier = Modifier.weight(1f)) {
                            Text("WhatsApp")
                        }
                    }
                }

                MayaCard {
                    SectionHeader("Smart Home HTTP Devices", "Register local LAN endpoints", Icons.Default.Home)
                    OutlinedTextField(value = smartName, onValueChange = { smartName = it }, label = { Text("Device Name (e.g. Desk Lamp)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = smartUrl, onValueChange = { smartUrl = it }, label = { Text("HTTP Endpoint URL") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        modVm.addSmartDevice(smartName, "LIGHT", smartUrl)
                        smartName = ""
                    }) { Text("Add Device") }

                    smartDevices.forEach { d ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.titleMedium)
                                Text(d.lastStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = d.isOn, onCheckedChange = { modVm.toggleSmartDevice(d) })
                        }
                    }
                }
            }
        }
    }
}
