package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.automation.NotificationIntelligenceService
import com.example.ui.MayaMainViewModel
import com.example.ui.MayaModulesViewModel
import com.example.ui.components.MayaCard
import com.example.ui.components.MayaScreenTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import kotlinx.coroutines.launch

@Composable
fun YaadeinScreen(
    vm: MayaMainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allMemories by vm.memories.collectAsStateWithLifecycle()
    val recalled by vm.recalledMemories.collectAsStateWithLifecycle()
    val cloudMemories by vm.cloudMemories.collectAsStateWithLifecycle()
    val authState by vm.authState.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("preference") }
    var importance by remember { mutableIntStateOf(3) }

    val displayed = if (searchQuery.isBlank()) allMemories else recalled

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = vm.exportMemoriesJson()
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
                }.onSuccess {
                    vm.showBanner("Yaadein JSON backup saved.")
                }.onFailure {
                    vm.showBanner("Export failed: ${it.localizedMessage}")
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                if (!json.isNullOrBlank()) vm.importMemoriesJson(json)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Yaadein Long-Term Memory", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MayaCard(highlightBorder = true) {
                    SectionHeader(
                        title = "Semantic Memory Vault",
                        subtitle = "Local Room + 64-dim vector recall • Credential/OTP/PIN sanitizer active",
                        icon = Icons.Default.Psychology
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = if (authState.user != null) "Firestore Cloud: ${cloudMemories.size} synced" else "Local-Only (Sign in for Cloud)",
                            color = if (authState.user != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                        )
                        Switch(
                            checked = settings.cloudSyncEnabled,
                            onCheckedChange = { vm.updateCloudSyncEnabled(it) }
                        )
                    }

                    OutlinedTextField(
                        value = newContent,
                        onValueChange = { newContent = it },
                        label = { Text("Remember a fact, preference, or project note") },
                        minLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("yaadein_content_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("preference", "project", "contact", "general").forEach { cat ->
                            FilterChip(
                                selected = newCategory == cat,
                                onClick = { newCategory = cat },
                                label = { Text(cat) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Importance: $importance/5", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            (1..5).forEach { lvl ->
                                FilterChip(
                                    selected = importance == lvl,
                                    onClick = { importance = lvl },
                                    label = { Text("$lvl") }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = {
                            vm.addMemory(newContent, newCategory, importance)
                            newContent = ""
                        },
                        enabled = newContent.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("save_memory_button")
                    ) {
                        Text("Save to Yaadein")
                    }
                }

                // Backup / Restore & Semantic Search
                MayaCard {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            vm.searchMemories(it)
                        },
                        label = { Text("Semantic & Keyword Recall Search") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("yaadein_search_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { exportLauncher.launch("maya_yaadein_backup.json") },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("export_yaadein_button")
                        ) {
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = { importLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("import_yaadein_button")
                        ) {
                            Text("Restore JSON")
                        }
                    }
                }

                displayed.forEach { mem ->
                    MayaCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                StatusPill("${mem.category} ★${mem.importance}", MaterialTheme.colorScheme.primary)
                                if (mem.syncedToCloud) {
                                    Icon(Icons.Default.CloudDone, contentDescription = "Synced to Firestore", tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = { vm.forgetMemory(mem.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete memory", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(mem.content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun GuardianScreen(
    mainVm: MayaMainViewModel,
    modVm: MayaModulesViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val settings by mainVm.settings.collectAsStateWithLifecycle()
    val profiles by modVm.voiceProfiles.collectAsStateWithLifecycle()
    val lastVerif by modVm.lastVoiceVerification.collectAsStateWithLifecycle()
    val alerts by modVm.intrusionAlerts.collectAsStateWithLifecycle()
    val micState by mainVm.micState.collectAsStateWithLifecycle()

    var speakerName by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("BOSS") }
    var biometricVerified by remember { mutableStateOf(false) }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) mainVm.toggleLiveMic()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Voice Guardian & Access Control", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MayaCard(highlightBorder = true) {
                    SectionHeader(
                        title = "Guardian Mode & Biometric Gate",
                        subtitle = "Active mode: ${settings.guardianMode} • Mic: ${micState.name}",
                        icon = Icons.Default.Security
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("EVERYONE", "BOSS_ONLY", "GUARD", "AWAY").forEach { mode ->
                            FilterChip(
                                selected = settings.guardianMode == mode,
                                onClick = { mainVm.updateGuardianMode(mode) },
                                label = { Text(mode) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    mainVm.toggleLiveMic()
                                } else {
                                    micPerm.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (micState.name == "LISTENING") "Stop Mic" else "Record Buffer")
                        }

                        Button(
                            onClick = {
                                if (activity != null) {
                                    modVm.authenticateBiometric(activity) { biometricVerified = true }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("biometric_verify_button")
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (biometricVerified) "Owner Verified" else "Biometric Lock")
                        }
                    }
                }

                MayaCard {
                    SectionHeader("Enroll & Verify Speaker", "Speak 2–3 seconds while mic is live, then enroll or verify")
                    OutlinedTextField(
                        value = speakerName,
                        onValueChange = { speakerName = it },
                        label = { Text("Speaker Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("BOSS", "FAMILY", "FRIEND").forEach { r ->
                            FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                modVm.enrollVoice(speakerName, role)
                                speakerName = ""
                            },
                            enabled = speakerName.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("enroll_voice_button")
                        ) {
                            Text("Enroll Voice")
                        }
                        OutlinedButton(
                            onClick = { modVm.verifySpeaker(settings.guardianMode) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("verify_voice_button")
                        ) {
                            Text("Verify Speaker")
                        }
                    }
                    lastVerif?.let { v ->
                        Text(
                            text = v.status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (v.intrusionTriggered) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                if (profiles.isNotEmpty()) {
                    MayaCard {
                        SectionHeader("Enrolled Voice Profiles (${profiles.size})")
                        profiles.forEach { p ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${p.name} (${p.role})", style = MaterialTheme.typography.bodyLarge)
                                IconButton(onClick = { modVm.deleteVoiceProfile(p.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete voice profile", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }

                if (alerts.isNotEmpty()) {
                    MayaCard(highlightBorder = true) {
                        SectionHeader("Intrusion Alerts")
                        alerts.forEach { alert ->
                            Text(alert, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FaceLockScreen(
    mainVm: MayaMainViewModel,
    modVm: MayaModulesViewModel,
    onBack: () -> Unit
) {
    val settings by mainVm.settings.collectAsStateWithLifecycle()
    val faces by modVm.faceProfiles.collectAsStateWithLifecycle()
    val matchResult by modVm.faceMatchResult.collectAsStateWithLifecycle()

    var personName by remember { mutableStateOf("") }
    var relation by remember { mutableStateOf("Family") }
    var consentChecked by remember { mutableStateOf(false) }

    val enrollPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            modVm.enrollFaceFromUri(uri, personName, relation, consentChecked)
            personName = ""
        }
    }

    val verifyPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            modVm.recognizeFaceFromUri(uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Face Lock & Recognition", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MayaCard(highlightBorder = true) {
                    SectionHeader(
                        title = "Consent-Gated ML Kit Face Gallery",
                        subtitle = "On-device landmark geometry — zero cloud face uploads",
                        icon = Icons.Default.Face
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Watchman Alert Mode", style = MaterialTheme.typography.titleMedium)
                        Switch(checked = settings.faceWatchmanEnabled, onCheckedChange = { mainVm.updateFaceWatchman(it) })
                    }

                    OutlinedTextField(
                        value = personName,
                        onValueChange = { personName = it },
                        label = { Text("Person Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Owner", "Family", "Friend", "Guest").forEach { rel ->
                            FilterChip(selected = relation == rel, onClick = { relation = rel }, label = { Text(rel) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = consentChecked, onCheckedChange = { consentChecked = it })
                        Text("Explicit consent obtained to store facial landmark descriptor locally", style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { enrollPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            enabled = consentChecked && personName.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("enroll_face_button")
                        ) {
                            Text("Pick Photo & Enroll")
                        }
                        OutlinedButton(
                            onClick = { verifyPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("recognize_face_button")
                        ) {
                            Text("Verify Photo")
                        }
                    }

                    matchResult?.let { res ->
                        Text(res.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                MayaCard {
                    SectionHeader("Local Face Gallery (${faces.size})")
                    faces.forEach { f ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${f.name} (${f.relationship})", style = MaterialTheme.typography.bodyLarge)
                            IconButton(onClick = { modVm.deleteFaceProfile(f) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete face profile", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (faces.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { modVm.wipeAllFaces() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Wipe All Stored Face Data")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationsScreen(
    modVm: MayaModulesViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val connected by modVm.notificationListenerConnected.collectAsStateWithLifecycle()
    val notifications by modVm.capturedNotifications.collectAsStateWithLifecycle()
    val summary by modVm.notificationSummary.collectAsStateWithLifecycle()

    var replyTargetKey by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Notification Intelligence", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 680.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MayaCard(highlightBorder = true) {
                    SectionHeader(
                        title = "NotificationListenerService",
                        subtitle = if (connected) "Active — capturing & deduplicating notifications" else "Disabled in Android Settings",
                        icon = Icons.Default.NotificationsActive
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Grant Access")
                        }
                        Button(
                            onClick = { modVm.summarizeRecentNotifications() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("summarize_notifications_button")
                        ) {
                            Text("AI Summarize")
                        }
                    }
                    if (summary.isNotBlank()) {
                        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (notifications.isNotEmpty()) {
                    OutlinedButton(onClick = { modVm.clearNotifications() }) { Text("Clear Feed") }
                }

                notifications.forEach { n ->
                    MayaCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${n.appName} — ${n.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (n.isDuplicate) StatusPill("Duplicate", MaterialTheme.colorScheme.secondary)
                        }
                        Text(n.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (NotificationIntelligenceService.hasReplyAction(n.notificationKey)) {
                            if (replyTargetKey == n.notificationKey) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = replyText,
                                        onValueChange = { replyText = it },
                                        label = { Text("Inline reply") },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(onClick = {
                                        modVm.sendNotificationInlineReply(n.notificationKey, replyText)
                                        replyText = ""
                                        replyTargetKey = null
                                    }) { Text("Send") }
                                }
                            } else {
                                OutlinedButton(onClick = { replyTargetKey = n.notificationKey }) {
                                    Text("Quick Inline Reply")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
