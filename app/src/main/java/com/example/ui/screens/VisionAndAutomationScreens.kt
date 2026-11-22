package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MayaModulesViewModel
import com.example.ui.components.MayaCard
import com.example.ui.components.MayaScreenTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill
import java.nio.ByteBuffer

@Composable
fun CameraVisionScreen(
    modVm: MayaModulesViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val visionOutput by modVm.visionOutput.collectAsStateWithLifecycle()
    val loading by modVm.visionLoading.collectAsStateWithLifecycle()

    var hasCameraPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var prompt by remember { mutableStateOf("Describe this scene, read any important text, and answer key details.") }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    val camPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPerm = it
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            capturedBitmap = modVm.decodeUri(uri)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Camera & Multimodal Vision", onBack = onBack)

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
                    SectionHeader("CameraX + ML Kit + Gemini Vision", "Live capture or zero-permission Photo Picker", Icons.Default.CameraAlt)

                    if (hasCameraPerm) {
                        val previewView = remember { PreviewView(context) }
                        DisposableEffect(lifecycleOwner) {
                            val providerFuture = ProcessCameraProvider.getInstance(context)
                            val executor = ContextCompat.getMainExecutor(context)
                            providerFuture.addListener({
                                runCatching {
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .build()
                                    imageCapture = capture
                                    provider.unbindAll()
                                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                                }
                            }, executor)
                            onDispose {
                                runCatching { providerFuture.get().unbindAll() }
                            }
                        }
                        AndroidView(
                            factory = { previewView },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(14.dp))
                        )
                    } else {
                        Button(
                            onClick = { camPermLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Grant Camera Permission for Live Preview")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val cap = imageCapture ?: return@Button
                                cap.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            capturedBitmap = imageProxyToBitmap(image)
                                            image.close()
                                        }
                                        override fun onError(exception: ImageCaptureException) {}
                                    }
                                )
                            },
                            enabled = hasCameraPerm && imageCapture != null,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("capture_photo_button")
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Capture")
                        }

                        OutlinedButton(
                            onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("pick_vision_photo_button")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Photo Picker")
                        }
                    }

                    capturedBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Selected vision frame",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Vision Question") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { capturedBitmap?.let { modVm.analyzeBitmap(it, prompt) } },
                            enabled = capturedBitmap != null && !loading,
                            modifier = Modifier.weight(1f).height(48.dp).testTag("ask_gemini_vision_button")
                        ) {
                            Text("Ask Gemini")
                        }
                        OutlinedButton(
                            onClick = { capturedBitmap?.let { modVm.runOcrOnBitmap(it) } },
                            enabled = capturedBitmap != null && !loading,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("OCR Text")
                        }
                        OutlinedButton(
                            onClick = { capturedBitmap?.let { modVm.scanQrOnBitmap(it) } },
                            enabled = capturedBitmap != null && !loading,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan QR/Barcode")
                        }
                    }

                    if (loading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    if (visionOutput.isNotBlank()) {
                        Text(visionOutput, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    val buffer: ByteBuffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val rot = image.imageInfo.rotationDegrees
    if (rot == 0) return raw
    val m = Matrix().apply { postRotate(rot.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
}

@Composable
fun ScreenScannerScreen(
    modVm: MayaModulesViewModel,
    onRequestMediaProjection: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val isSharing by modVm.isScreenSharing.collectAsStateWithLifecycle()
    val a11yConnected by modVm.accessibilityConnected.collectAsStateWithLifecycle()
    val activePkg by modVm.activePackage.collectAsStateWithLifecycle()
    val analysis by modVm.screenAnalysisOutput.collectAsStateWithLifecycle()
    val treeDump by modVm.accessibilityDump.collectAsStateWithLifecycle()
    val lastScreen by modVm.lastScreenBitmap.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Screen Understanding & Driver", onBack = onBack)

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
                        title = "MediaProjection & Accessibility Driver",
                        subtitle = "Active Package: ${activePkg.ifBlank { "None" }}",
                        icon = Icons.Default.ScreenshotMonitor
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            if (isSharing) "Screen Share ON" else "Screen Share OFF",
                            if (isSharing) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                        )
                        StatusPill(
                            if (a11yConnected) "Accessibility ON" else "Accessibility OFF",
                            if (a11yConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { if (isSharing) modVm.stopScreenSharing() else onRequestMediaProjection() },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("toggle_screen_share_button"),
                            colors = if (isSharing) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                        ) {
                            Text(if (isSharing) "Stop Sharing" else "Start Screen Share")
                        }
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Accessibility Settings")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { modVm.captureAndExplainScreen() },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("explain_screen_button")
                        ) {
                            Text("Capture & Explain")
                        }
                        OutlinedButton(
                            onClick = { modVm.readAccessibilityTree() },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Read UI Nodes")
                        }
                    }

                    lastScreen?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Captured screen preview",
                            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }

                    if (analysis.isNotBlank()) Text(analysis, style = MaterialTheme.typography.bodyLarge)
                    if (treeDump.isNotBlank()) Text(treeDump, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ToolsScreen(
    modVm: MayaModulesViewModel,
    onBack: (() -> Unit)? = null
) {
    var query by remember { mutableStateOf("") }
    var selectedTool by remember { mutableStateOf("agent_status") }
    var argsJson by remember { mutableStateOf("{}") }
    val result by modVm.toolResult.collectAsStateWithLifecycle()

    val filtered = remember(query) {
        if (query.isBlank()) modVm.allTools
        else modVm.allTools.filter {
            it.name.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Agentic Tool Registry (${modVm.allTools.size})", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Direct Tool Runner", "Selected: $selectedTool", Icons.Default.Build)
                    OutlinedTextField(
                        value = argsJson,
                        onValueChange = { argsJson = it },
                        label = { Text("JSON Arguments") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { modVm.executeToolManually(selectedTool, argsJson) },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("run_tool_button")
                    ) {
                        Text("Execute '$selectedTool'")
                    }
                    result?.let { r ->
                        Text(
                            text = "[${r.toolName} | ${if (r.success) "SUCCESS" else "INFO"}] ${r.output}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (r.success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Filter tools by name or category") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                filtered.forEach { spec ->
                    MayaCard(highlightBorder = spec.name == selectedTool) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(spec.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Text("${spec.category} • ${spec.description}", style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = {
                                selectedTool = spec.name
                                val template = if (spec.parametersSchema.isEmpty()) "{}"
                                else spec.parametersSchema.keys.joinToString(",", "{", "}") { "\"$it\":\"\"" }
                                argsJson = template
                            }) { Text("Select") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MissionsScreen(modVm: MayaModulesViewModel, onBack: () -> Unit) {
    val missions by modVm.missions.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Autonomous Missions", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Launch Multi-Step Mission", "Runs Yaadein recall, telemetry check & contextual action", Icons.Default.Flag)
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Mission Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = goal, onValueChange = { goal = it }, label = { Text("Mission Goal (e.g. Research Android 16 voice APIs)") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            modVm.launchMission(title, goal)
                            title = ""
                            goal = ""
                        },
                        enabled = title.isNotBlank() && goal.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("launch_mission_button")
                    ) { Text("Run Mission") }
                }

                missions.forEach { m ->
                    MayaCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(m.title, style = MaterialTheme.typography.titleMedium)
                            StatusPill(m.status, if (m.status == "COMPLETED") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary)
                        }
                        Text(m.goal, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (m.resultSummary.isNotBlank()) Text(m.resultSummary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun SkillsScreen(modVm: MayaModulesViewModel, onBack: () -> Unit) {
    val rules by modVm.macroRules.collectAsStateWithLifecycle()
    var ruleName by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("VOICE_OR_MANUAL") }
    var actionJson by remember { mutableStateOf("{\"tools\":[\"get_weather\"]}") }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Skills & Automation Rules", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Create Macro Rule", "Persistent trigger-action workflows", Icons.Default.AutoFixHigh)
                    OutlinedTextField(value = ruleName, onValueChange = { ruleName = it }, label = { Text("Rule Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = trigger, onValueChange = { trigger = it }, label = { Text("Trigger Type") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = actionJson, onValueChange = { actionJson = it }, label = { Text("Action JSON") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            modVm.addMacroRule(ruleName, trigger, actionJson)
                            ruleName = ""
                        },
                        enabled = ruleName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Save Rule") }
                }

                rules.forEach { rule ->
                    MayaCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                                Text("${rule.triggerType} • ${rule.actionJson}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = rule.isEnabled, onCheckedChange = { modVm.toggleMacroRule(rule) })
                            if (!rule.isBuiltIn) {
                                IconButton(onClick = { modVm.deleteMacroRule(rule.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete rule", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemindersScreen(modVm: MayaModulesViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val reminders by modVm.reminders.collectAsStateWithLifecycle()
    val exactOk = remember { modVm.canScheduleExactAlarms() }

    var title by remember { mutableStateOf("") }
    var delayText by remember { mutableStateOf("15") }
    var kind by remember { mutableStateOf("REMINDER") }
    var isAlarm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Reminders, Alarms & Scheduling", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader(
                        title = "WorkManager & Alarm Scheduler",
                        subtitle = if (exactOk) "Exact alarm permission granted" else "Standard WorkManager scheduling active",
                        icon = Icons.Default.Alarm
                    )
                    if (!exactOk) {
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }) { Text("Allow Exact Alarms in Settings") }
                    }
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Reminder / Alarm Title") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = delayText, onValueChange = { delayText = it.filter { c -> c.isDigit() } }, label = { Text("Delay (minutes)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("REMINDER", "ALARM", "NUDGE").forEach { k ->
                            FilterChip(
                                selected = kind == k,
                                onClick = {
                                    kind = k
                                    isAlarm = (k == "ALARM")
                                },
                                label = { Text(k) }
                            )
                        }
                    }
                    Button(
                        onClick = {
                            modVm.addReminder(title, delayText.toLongOrNull() ?: 15L, isAlarm, kind)
                            title = ""
                        },
                        enabled = title.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("add_reminder_button")
                    ) { Text("Schedule Notification") }
                }

                reminders.forEach { item ->
                    MayaCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Checkbox(checked = item.isCompleted, onCheckedChange = { modVm.toggleReminderCompleted(item) })
                                Column {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Text("${item.kind} • ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(item.triggerAtMillis))}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { modVm.deleteReminder(item.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete reminder", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
