package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MayaMainViewModel
import com.example.ui.MayaModulesViewModel
import com.example.ui.components.MayaCard
import com.example.ui.components.MayaScreenTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import com.example.ui.navigation.MayaRoutes

@Composable
fun DocumentsScreen(
    mainVm: MayaMainViewModel,
    modVm: MayaModulesViewModel,
    onBack: () -> Unit
) {
    val settings by mainVm.settings.collectAsStateWithLifecycle()
    val doc by modVm.extractedDoc.collectAsStateWithLifecycle()
    val answer by modVm.docAnswer.collectAsStateWithLifecycle()

    var question by remember { mutableStateOf("Summarize the key points and action items.") }
    var smtpHost by remember(settings.smtpHost) { mutableStateOf(settings.smtpHost) }
    var smtpEmail by remember(settings.smtpEmail) { mutableStateOf(settings.smtpEmail) }
    var telegramChat by remember(settings.telegramChatId) { mutableStateOf(settings.telegramChatId) }
    var telegramMsg by remember { mutableStateOf("") }
    var notionQuery by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) modVm.extractDocument(uri)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Documents, Email, Telegram & Notion", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("PDF & Document Q&A (SAF + PdfRenderer + OCR)", "Zero storage permissions required", Icons.Default.Description)
                    Button(
                        onClick = { picker.launch(arrayOf("application/pdf", "text/*")) },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pick_document_button")
                    ) { Text("Pick PDF or Text Document") }

                    doc?.let { d ->
                        Text("${d.fileName} (${d.pageCount} page(s))", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(d.text.take(800), style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Ask about this document") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { modVm.askDocumentQuestion(question, settings) }) { Text("Ask AI") }
                    }
                    if (answer.isNotBlank()) Text(answer, style = MaterialTheme.typography.bodyMedium)
                }

                MayaCard {
                    SectionHeader("Email TLS Check, Telegram & Notion", "Real TLS socket & REST endpoints", Icons.Default.Email)
                    OutlinedTextField(value = smtpHost, onValueChange = { smtpHost = it }, label = { Text("SMTP Host (e.g. smtp.gmail.com)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = smtpEmail, onValueChange = { smtpEmail = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { modVm.testTlsMail(smtpHost, 465) }, modifier = Modifier.weight(1f)) { Text("Verify TLS 465") }
                        OutlinedButton(onClick = { modVm.openEmailDraft(smtpEmail, "Maya AI Draft", "") }, modifier = Modifier.weight(1f)) { Text("Email Draft") }
                    }
                    OutlinedTextField(value = telegramChat, onValueChange = { telegramChat = it }, label = { Text("Telegram Chat ID") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = telegramMsg, onValueChange = { telegramMsg = it }, label = { Text("Telegram Message") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        modVm.sendTelegram(telegramChat, telegramMsg)
                        telegramMsg = ""
                    }, enabled = telegramMsg.isNotBlank()) { Text("Send Telegram") }

                    OutlinedTextField(value = notionQuery, onValueChange = { notionQuery = it }, label = { Text("Notion Workspace Query") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = { modVm.searchNotion(notionQuery) }, enabled = notionQuery.isNotBlank()) { Text("Search Notion") }
                }
            }
        }
    }
}

@Composable
fun WeatherScreen(modVm: MayaModulesViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val weather by modVm.weatherReport.collectAsStateWithLifecycle()
    val loading by modVm.weatherLoading.collectAsStateWithLifecycle()
    var city by remember { mutableStateOf("New Delhi") }
    var mapsQuery by remember { mutableStateOf("Coffee shops near me") }

    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) modVm.fetchWeatherCurrentLocation()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Weather & Maps", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Open-Meteo Real Weather & 7-Day Forecast", "Live meteorological API + coarse device location", Icons.Default.WbSunny)
                    OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("City Name") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { modVm.fetchWeatherCity(city) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("fetch_weather_button")
                        ) { Text("Check City") }
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    modVm.fetchWeatherCurrentLocation()
                                } else {
                                    locPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("My Location") }
                    }
                    if (loading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    weather?.let { w ->
                        Text("${w.locationLabel}: ${w.temperatureC}°C • ${w.conditionSummary}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Wind: ${w.windKmh} km/h", style = MaterialTheme.typography.bodyMedium)
                        w.forecast.forEach { d ->
                            Text("${d.date}: ${d.minTempC}°..${d.maxTempC}°C | Rain ${d.precipProb}%", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                MayaCard {
                    SectionHeader("Maps Navigation", "Launch Android geo: intent", Icons.Default.Map)
                    OutlinedTextField(value = mapsQuery, onValueChange = { mapsQuery = it }, label = { Text("Place or directions query") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { modVm.openMaps(mapsQuery) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Open in Maps")
                    }
                }
            }
        }
    }
}

@Composable
fun MusicScreen(modVm: MayaModulesViewModel, onBack: () -> Unit) {
    val history by modVm.mediaHistory.collectAsStateWithLifecycle()
    val generatedBmp by modVm.generatedBitmap.collectAsStateWithLifecycle()
    val genLoading by modVm.imageGenLoading.collectAsStateWithLifecycle()
    val savedImages by modVm.generatedImages.collectAsStateWithLifecycle()

    var mediaQuery by remember { mutableStateOf("") }
    var imagePrompt by remember { mutableStateOf("Futuristic cybernetic lotus glowing in dark neon cyan, minimalist vector art") }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Music, Media & Nano Banana Art", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Gemini Nano Banana 2.1 Image Studio", "Generates runtime images & saves to Pictures/MayaAI", Icons.Default.AutoFixHigh)
                    OutlinedTextField(value = imagePrompt, onValueChange = { imagePrompt = it }, label = { Text("Image Prompt") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { modVm.generateRuntimeImage(imagePrompt) },
                        enabled = !genLoading && imagePrompt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_image_button")
                    ) { Text(if (genLoading) "Generating Image..." else "Generate with Nano Banana 2.1") }

                    if (genLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    generatedBmp?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Generated artwork",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp))
                        )
                    }
                    if (savedImages.isNotEmpty()) {
                        Text("Saved artworks: ${savedImages.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }

                MayaCard {
                    SectionHeader("YouTube & Spotify Launcher", "Tracks playback history & favorites", Icons.Default.MusicNote)
                    OutlinedTextField(value = mediaQuery, onValueChange = { mediaQuery = it }, label = { Text("Song, artist, or podcast") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { modVm.launchYouTube(mediaQuery) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("YouTube") }
                        OutlinedButton(onClick = { modVm.launchSpotify(mediaQuery) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Spotify") }
                    }
                    history.take(10).forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(item.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { modVm.toggleFavoriteMedia(item) }) {
                                Icon(
                                    imageVector = if (item.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Toggle favorite",
                                    tint = if (item.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(vm: MayaMainViewModel, onBack: () -> Unit) {
    val health by vm.deviceHealth.collectAsStateWithLifecycle()
    val audioDiag by vm.audioDiagnostics.collectAsStateWithLifecycle()
    val logs by vm.diagnosticLogs.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Diagnostics, Benchmarks & OSS", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Vivo Y21 5G / Android Hardware Benchmark", "${health.deviceModel} • ${health.androidVersion}", Icons.Default.Speed)
                    Text("System RAM: ${health.availableRamMb} MB free / ${health.totalRamMb} MB total (Low-Mem Flag: ${health.isLowRamState})", style = MaterialTheme.typography.bodyMedium)
                    Text("App JVM Heap: ${health.appHeapUsedMb} MB / ${health.appHeapMaxMb} MB max", style = MaterialTheme.typography.bodyMedium)
                    Text("Battery: ${health.batteryPercent}% | Power Saver: ${health.isPowerSaveMode}", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { vm.refreshTelemetry() }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Refresh Telemetry")
                    }
                }

                MayaCard {
                    SectionHeader("Real-Time Audio Engine Diagnostics", "Hardware AEC / NS / AGC & AudioTrack latency", Icons.Default.GraphicEq)
                    Text("Hardware Supported -> AEC: ${audioDiag.hardwareAecSupported} | NS: ${audioDiag.hardwareNsSupported} | AGC: ${audioDiag.hardwareAgcSupported}", style = MaterialTheme.typography.bodySmall)
                    Text("Active -> AEC: ${audioDiag.aecActive} | DTLN Gate: ${audioDiag.dtlnFilterActive} | RMS: ${String.format("%.1f", audioDiag.rmsDbfs)} dBFS | Frames: ${audioDiag.framesProcessed}", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.playDiagnosticTone() }, modifier = Modifier.weight(1f).height(48.dp).testTag("audio_test_tone_button")) {
                            Text("Play 528Hz Test Tone")
                        }
                        OutlinedButton(onClick = { vm.clearDiagnosticsAndUsage() }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text("Clear Logs")
                        }
                    }
                }

                MayaCard {
                    SectionHeader("Recent Diagnostic Logs (${logs.size})", "Real runtime errors & events", Icons.Default.BugReport)
                    if (logs.isEmpty()) Text("No warnings or errors recorded.", style = MaterialTheme.typography.bodySmall)
                    logs.take(15).forEach { log ->
                        Text("[${log.level}/${log.tag}] ${log.message}", style = MaterialTheme.typography.bodySmall, color = if (log.level == "ERROR") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                MayaCard {
                    SectionHeader("Open-Source Licenses & Credits")
                    Text("Built with Kotlin, Jetpack Compose Material 3, Room, DataStore, WorkManager, CameraX, ML Kit (Barcode, Text, Face), Firebase AI Logic, Firestore, Credential Manager, OkHttp, and Coil.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class HubItem(val title: String, val subtitle: String, val route: String, val icon: ImageVector)

@Composable
fun FeatureHubScreen(onNavigate: (String) -> Unit) {
    val items = remember {
        listOf(
            HubItem("Advanced Settings", "Models, Frameworks, Custom Instructions, Usage & API Vault", MayaRoutes.SETTINGS, Icons.Default.Settings),
            HubItem("Yaadein Memory", "Semantic local & Firestore cloud memory + JSON backup", MayaRoutes.YAADEIN, Icons.Default.Psychology),
            HubItem("Voice Guardian", "Speaker enrollment, Boss/Guard modes & Biometric lock", MayaRoutes.GUARDIAN, Icons.Default.Security),
            HubItem("Face Lock", "Consent-gated ML Kit face gallery & Watchman", MayaRoutes.FACE_LOCK, Icons.Default.Face),
            HubItem("Camera & Vision", "CameraX preview, Gemini Vision, OCR & QR/Barcode", MayaRoutes.VISION, Icons.Default.CameraAlt),
            HubItem("Screen Scanner", "MediaProjection capture & Accessibility UI driver", MayaRoutes.SCREEN, Icons.Default.ScreenshotMonitor),
            HubItem("Notifications", "NotificationListener feed, AI summary & inline reply", MayaRoutes.NOTIFICATIONS, Icons.Default.NotificationsActive),
            HubItem("Agent Tools", "48 registered automation, search & device tools", MayaRoutes.TOOLS, Icons.Default.Build),
            HubItem("Missions", "Multi-step autonomous agent execution tracker", MayaRoutes.MISSIONS, Icons.Default.Flag),
            HubItem("Skills & Rules", "Macro automation triggers & rule workflows", MayaRoutes.SKILLS, Icons.Default.AutoFixHigh),
            HubItem("Reminders & Alarms", "WorkManager scheduling & exact alarm check", MayaRoutes.REMINDERS, Icons.Default.Alarm),
            HubItem("Termux Terminal", "RUN_COMMAND non-root bridge & localhost ports", MayaRoutes.TERMINAL, Icons.Default.Terminal),
            HubItem("Web Browser", "DuckDuckGo search & WebView + text extraction", MayaRoutes.BROWSER, Icons.Default.Public),
            HubItem("Coding Agent", "Framework-targeted code & GitHub inspection", MayaRoutes.CODING, Icons.Default.Code),
            HubItem("Website Builder", "Generate, edit & preview HTML/CSS/JS projects", MayaRoutes.WEBSITES, Icons.Default.Language),
            HubItem("SEO & Marketing", "robots/sitemap SEO audit, campaigns & leads", MayaRoutes.MARKETING, Icons.Default.Campaign),
            HubItem("Personas & Voice", "Maya/Friday/Venom, Hindi/Hinglish/English & TTS", MayaRoutes.PERSONA, Icons.Default.RecordVoiceOver),
            HubItem("Avatar & Wallpaper", "Neural orb styles & Live Wallpaper chooser", MayaRoutes.AVATAR, Icons.Default.FaceRetouchingNatural),
            HubItem("Contacts & IoT", "Phone calls, SMS, WhatsApp & HTTP smart home", MayaRoutes.CONTACTS, Icons.Default.Contacts),
            HubItem("Documents & Mail", "PDF/SAF OCR Q&A, SMTP TLS, Telegram & Notion", MayaRoutes.DOCUMENTS, Icons.Default.Description),
            HubItem("Weather & Maps", "Open-Meteo forecast & geo: Maps navigation", MayaRoutes.WEATHER, Icons.Default.WbSunny),
            HubItem("Music & Art Studio", "YouTube/Spotify & Nano Banana 2.1 image gen", MayaRoutes.MUSIC, Icons.Default.MusicNote),
            HubItem("Google Account", "Google Sign-In, UID & Firestore cloud sync toggle", MayaRoutes.LOGIN, Icons.Default.AccountCircle),
            HubItem("Provider Tiers", "Transparent capability & BYOK/Firebase overview", MayaRoutes.PLANS, Icons.Default.Verified),
            HubItem("Diagnostics", "Audio test tone, Vivo Y21 5G RAM telemetry & logs", MayaRoutes.DIAGNOSTICS, Icons.Default.Speed)
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeader("All Maya AI Modules", "28 integrated voice, vision, automation & developer screens")
            items.forEach { item ->
                MayaCard(
                    modifier = Modifier
                        .clickable { onNavigate(item.route) }
                        .testTag("hub_${item.route}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill("Open", MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
