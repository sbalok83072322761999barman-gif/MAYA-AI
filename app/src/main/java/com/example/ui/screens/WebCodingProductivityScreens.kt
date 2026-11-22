package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.WebsiteProjectEntity
import com.example.ui.MayaMainViewModel
import com.example.ui.MayaModulesViewModel
import com.example.ui.components.MayaCard
import com.example.ui.components.MayaScreenTopBar
import com.example.ui.components.SectionHeader
import com.example.ui.components.StatusPill

@Composable
fun TerminalScreen(modVm: MayaModulesViewModel, onBack: () -> Unit) {
    val installed = remember { modVm.isTermuxInstalled() }
    val permGranted = remember { modVm.hasTermuxPermission() }
    val logs by modVm.termuxLogs.collectAsStateWithLifecycle()
    val portStatus by modVm.portStatus.collectAsStateWithLifecycle()

    var command by remember { mutableStateOf("python3 --version") }
    var portInput by remember { mutableStateOf("8080") }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Termux RUN_COMMAND Bridge", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Termux Non-Root Execution", "Safe command validation + local port checker", Icons.Default.Terminal)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(
                            if (installed) "Termux Installed" else "Termux Not Installed",
                            if (installed) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                        )
                        StatusPill(
                            if (permGranted) "RUN_COMMAND Permitted" else "Permission Needed",
                            if (permGranted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                        )
                    }
                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        label = { Text("Command (non-root)") },
                        modifier = Modifier.fillMaxWidth().testTag("termux_command_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { modVm.runTermuxCommand(command, background = false) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("run_termux_button")
                        ) { Text("Run in Termux") }
                        OutlinedButton(
                            onClick = { modVm.runTermuxCommand(command, background = true) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Run Background") }
                    }
                }

                MayaCard {
                    SectionHeader("Localhost Port Check", "Inspect servers started in Termux")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it.filter { c -> c.isDigit() } },
                            label = { Text("Port") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { modVm.checkLocalPort(portInput.toIntOrNull() ?: 8080) }) { Text("Check") }
                    }
                    portStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                }

                logs.forEach { log ->
                    MayaCard {
                        Text("$ ${log.command}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(log.status, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(modVm: MayaModulesViewModel, onBack: () -> Unit) {
    val searchResults by modVm.searchResults.collectAsStateWithLifecycle()
    val webpage by modVm.webpageContent.collectAsStateWithLifecycle()

    var queryOrUrl by remember { mutableStateOf("https://developer.android.com") }
    var loadedUrl by remember { mutableStateOf("https://developer.android.com") }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Research & WebView Browser", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("DuckDuckGo Search & Built-in Browser", "Real web search & HTML text extractor", Icons.Default.Public)
                    OutlinedTextField(
                        value = queryOrUrl,
                        onValueChange = { queryOrUrl = it },
                        label = { Text("Search query or URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("browser_query_input")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (queryOrUrl.contains(".") && !queryOrUrl.contains(" ")) {
                                    loadedUrl = if (queryOrUrl.startsWith("http")) queryOrUrl else "https://$queryOrUrl"
                                    modVm.fetchWebpage(loadedUrl)
                                } else {
                                    modVm.searchWeb(queryOrUrl)
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("browser_search_button")
                        ) { Text("Search / Go") }
                        OutlinedButton(
                            onClick = { modVm.fetchWebpage(loadedUrl) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Extract Text") }
                    }
                }

                MayaCard {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                webViewClient = WebViewClient()
                                loadUrl(loadedUrl)
                            }
                        },
                        update = { view ->
                            if (view.url != loadedUrl) view.loadUrl(loadedUrl)
                        },
                        modifier = Modifier.fillMaxWidth().height(260.dp)
                    )
                }

                webpage?.let { p ->
                    MayaCard {
                        Text(p.title.ifBlank { p.url }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Links: ${p.linkCount}", style = MaterialTheme.typography.labelSmall)
                        Text(p.extractedText.take(1500), style = MaterialTheme.typography.bodySmall)
                    }
                }

                searchResults.forEach { res ->
                    MayaCard {
                        Text(res.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(res.url, style = MaterialTheme.typography.labelSmall)
                        Text(res.snippet, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            loadedUrl = res.url
                            modVm.fetchWebpage(res.url)
                        }) { Text("Open in Browser") }
                    }
                }
            }
        }
    }
}

@Composable
fun CodingScreen(
    mainVm: MayaMainViewModel,
    modVm: MayaModulesViewModel,
    onBack: () -> Unit
) {
    val settings by mainVm.settings.collectAsStateWithLifecycle()
    val output by modVm.codingOutput.collectAsStateWithLifecycle()
    val loading by modVm.codingLoading.collectAsStateWithLifecycle()

    var taskPrompt by remember { mutableStateOf("") }
    var repoInput by remember { mutableStateOf(settings.githubOwnerRepo) }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Coding Agent", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader(
                        title = "Framework-Aware Code Generator",
                        subtitle = "Active Target: ${settings.activeFramework.title}",
                        icon = Icons.Default.Code
                    )
                    OutlinedTextField(
                        value = taskPrompt,
                        onValueChange = { taskPrompt = it },
                        label = { Text("Describe file, feature, or script to build") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth().testTag("coding_prompt_input")
                    )
                    Button(
                        onClick = { modVm.generateCodeForActiveFramework(taskPrompt, settings) },
                        enabled = !loading && taskPrompt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("generate_code_button")
                    ) { Text("Generate for ${settings.activeFramework.name}") }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            label = { Text("GitHub owner/repo") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = { modVm.fetchGitHubRepo(repoInput) }) { Text("Inspect") }
                    }

                    if (loading) CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    if (output.isNotBlank()) Text(output, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebsitesScreen(
    mainVm: MayaMainViewModel,
    modVm: MayaModulesViewModel,
    onBack: () -> Unit
) {
    val settings by mainVm.settings.collectAsStateWithLifecycle()
    val projects by modVm.websites.collectAsStateWithLifecycle()
    val loading by modVm.codingLoading.collectAsStateWithLifecycle()

    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var selectedProject by remember { mutableStateOf<WebsiteProjectEntity?>(null) }
    var htmlDraft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "Website Builder & Live Preview", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Generate Responsive Website", "Saves locally & previews in WebView", Icons.Default.Language)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Project Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Design & Sections Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            modVm.createWebsiteProject(name, desc, settings)
                            name = ""
                            desc = ""
                        },
                        enabled = !loading && name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("create_website_button")
                    ) { Text(if (loading) "Generating..." else "Build Website Project") }
                }

                selectedProject?.let { proj ->
                    MayaCard(highlightBorder = true) {
                        SectionHeader("Live Preview: ${proj.name}")
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    this.settings.javaScriptEnabled = true
                                    loadDataWithBaseURL(null, htmlDraft, "text/html", "UTF-8", null)
                                }
                            },
                            update = { view ->
                                view.loadDataWithBaseURL(null, htmlDraft, "text/html", "UTF-8", null)
                            },
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        )
                        OutlinedTextField(
                            value = htmlDraft,
                            onValueChange = { htmlDraft = it },
                            label = { Text("HTML Source") },
                            minLines = 5,
                            maxLines = 10,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = { modVm.updateWebsiteHtml(proj, htmlDraft) }) { Text("Save HTML Changes") }
                    }
                }

                projects.forEach { p ->
                    MayaCard {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(p.name, style = MaterialTheme.typography.titleMedium)
                                Text(p.description, style = MaterialTheme.typography.bodySmall)
                            }
                            Row {
                                OutlinedButton(onClick = {
                                    selectedProject = p
                                    htmlDraft = p.htmlContent
                                }) { Text("Preview") }
                                IconButton(onClick = { modVm.deleteWebsite(p.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete website", tint = MaterialTheme.colorScheme.error)
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
fun MarketingScreen(
    mainVm: MayaMainViewModel,
    modVm: MayaModulesViewModel,
    onBack: () -> Unit
) {
    val settings by mainVm.settings.collectAsStateWithLifecycle()
    val businesses by modVm.businesses.collectAsStateWithLifecycle()
    val leads by modVm.leads.collectAsStateWithLifecycle()
    val seo by modVm.seoReport.collectAsStateWithLifecycle()
    val aiOutput by modVm.marketingAiOutput.collectAsStateWithLifecycle()

    var bizName by remember { mutableStateOf("") }
    var bizCity by remember { mutableStateOf("") }
    var bizServices by remember { mutableStateOf("") }
    var auditUrl by remember { mutableStateOf("https://example.com") }
    var topic by remember { mutableStateOf("Instagram launch campaign + local SEO keywords") }
    var leadName by remember { mutableStateOf("") }
    var leadContact by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        MayaScreenTopBar(title = "SEO, Marketing & Leads", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(modifier = Modifier.widthIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MayaCard(highlightBorder = true) {
                    SectionHeader("Live Technical SEO Audit", "Checks robots.txt, sitemap.xml, meta tags, H1 & links", Icons.Default.Campaign)
                    OutlinedTextField(value = auditUrl, onValueChange = { auditUrl = it }, label = { Text("Website URL") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = { modVm.runSeoAudit(auditUrl) },
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("run_seo_audit_button")
                    ) { Text("Run SEO Audit") }
                    seo?.let { r ->
                        Text("Origin: ${r.baseUrl}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text("robots.txt: ${r.robotsTxtStatus}\nsitemap.xml: ${r.sitemapStatus}\nTitle: ${r.pageTitle}\nMeta: ${r.metaDescription}\nH1 Tags: ${r.h1Count} | Links: ${r.totalLinks}", style = MaterialTheme.typography.bodySmall)
                    }
                }

                MayaCard {
                    SectionHeader("Business Profiles & Content Calendar")
                    OutlinedTextField(value = bizName, onValueChange = { bizName = it }, label = { Text("Business Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bizCity, onValueChange = { bizCity = it }, label = { Text("Location / City") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bizServices, onValueChange = { bizServices = it }, label = { Text("Services & Target Audience") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(onClick = {
                        modVm.addBusinessProfile(bizName, bizCity, bizServices, bizCity, auditUrl)
                        bizName = ""; bizCity = ""; bizServices = ""
                    }) { Text("Save Business Profile") }

                    OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Campaign / Keyword Prompt") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { modVm.generateMarketingCopy(businesses.firstOrNull(), topic, settings) },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("Generate Strategy") }
                        if (aiOutput.isNotBlank()) {
                            IconButton(onClick = { modVm.shareText("Maya Marketing Campaign", aiOutput) }) {
                                Icon(Icons.Default.Share, contentDescription = "Share via Android Share Sheet")
                            }
                        }
                    }
                    if (aiOutput.isNotBlank()) Text(aiOutput, style = MaterialTheme.typography.bodySmall)
                }

                MayaCard {
                    SectionHeader("Lead Tracker & Funnel")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = leadName, onValueChange = { leadName = it }, label = { Text("Lead Name") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = leadContact, onValueChange = { leadContact = it }, label = { Text("Phone/Email") }, modifier = Modifier.weight(1f))
                    }
                    Button(onClick = {
                        modVm.addMarketingLead(leadName, leadContact, 0.0)
                        leadName = ""; leadContact = ""
                    }) { Text("Add Lead") }

                    leads.forEach { lead ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(lead.name, style = MaterialTheme.typography.titleMedium)
                                Text(lead.contact, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = { modVm.cycleLeadStatus(lead) }) { Text(lead.status) }
                            IconButton(onClick = { modVm.deleteMarketingLead(lead.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete lead", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}
