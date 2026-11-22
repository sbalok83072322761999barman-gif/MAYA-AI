package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.MayaMainViewModel
import com.example.ui.MayaModulesViewModel
import com.example.ui.components.ToolConfirmationDialog
import com.example.ui.screens.AdvancedSettingsScreen
import com.example.ui.screens.AvatarScreen
import com.example.ui.screens.BrowserScreen
import com.example.ui.screens.CameraVisionScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.CodingScreen
import com.example.ui.screens.ContactsScreen
import com.example.ui.screens.DiagnosticsScreen
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.FaceLockScreen
import com.example.ui.screens.FeatureHubScreen
import com.example.ui.screens.GuardianScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.MarketingScreen
import com.example.ui.screens.MissionsScreen
import com.example.ui.screens.MusicScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.PersonaScreen
import com.example.ui.screens.PlansScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.ScreenScannerScreen
import com.example.ui.screens.SkillsScreen
import com.example.ui.screens.TerminalScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.VoiceScreen
import com.example.ui.screens.WeatherScreen
import com.example.ui.screens.WebsitesScreen
import com.example.ui.screens.YaadeinScreen

object MayaRoutes {
    const val HOME = "home"
    const val VOICE = "voice"
    const val CHAT = "chat"
    const val TOOLS = "tools"
    const val HUB = "hub"
    const val SETTINGS = "settings"
    const val YAADEIN = "yaadein"
    const val GUARDIAN = "guardian"
    const val FACE_LOCK = "face_lock"
    const val VISION = "vision"
    const val SCREEN = "screen"
    const val NOTIFICATIONS = "notifications"
    const val MISSIONS = "missions"
    const val SKILLS = "skills"
    const val REMINDERS = "reminders"
    const val TERMINAL = "terminal"
    const val BROWSER = "browser"
    const val CODING = "coding"
    const val WEBSITES = "websites"
    const val MARKETING = "marketing"
    const val PERSONA = "persona"
    const val AVATAR = "avatar"
    const val LOGIN = "login"
    const val PLANS = "plans"
    const val CONTACTS = "contacts"
    const val DOCUMENTS = "documents"
    const val WEATHER = "weather"
    const val MUSIC = "music"
    const val DIAGNOSTICS = "diagnostics"
}

private data class TopLevelTab(val route: String, val label: String, val icon: ImageVector)

@Composable
fun MayaAppShell(
    mainVm: MayaMainViewModel,
    modVm: MayaModulesViewModel,
    initialRoute: String,
    onRequestMediaProjection: () -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: MayaRoutes.HOME

    val snackbarHostState = remember { SnackbarHostState() }
    val banner by mainVm.statusBanner.collectAsStateWithLifecycle()
    val modBanner by modVm.moduleMessage.collectAsStateWithLifecycle()
    val pendingConfirm by mainVm.pendingConfirmation.collectAsStateWithLifecycle()

    LaunchedEffect(banner) {
        banner?.let {
            snackbarHostState.showSnackbar(it)
            mainVm.clearBanner()
        }
    }
    LaunchedEffect(modBanner) {
        modBanner?.let {
            snackbarHostState.showSnackbar(it)
            modVm.clearMessage()
        }
    }

    val topTabs = remember {
        listOf(
            TopLevelTab(MayaRoutes.HOME, "Home", Icons.Default.Home),
            TopLevelTab(MayaRoutes.VOICE, "Voice", Icons.Default.Mic),
            TopLevelTab(MayaRoutes.CHAT, "Chat", Icons.Default.Chat),
            TopLevelTab(MayaRoutes.TOOLS, "Tools", Icons.Default.Build),
            TopLevelTab(MayaRoutes.HUB, "Hub", Icons.Default.Apps)
        )
    }

    val isTopLevel = currentRoute in topTabs.map { it.route }
    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 720

    pendingConfirm?.let { pending ->
        ToolConfirmationDialog(
            pending = pending,
            onConfirm = { mainVm.confirmPendingTool() },
            onDismiss = { mainVm.dismissPendingTool() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isTopLevel && !isWideScreen) {
                NavigationBar {
                    topTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(MayaRoutes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("nav_${tab.route}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isTopLevel && isWideScreen) {
                NavigationRail {
                    topTabs.forEach { tab ->
                        NavigationRailItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(MayaRoutes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag("rail_${tab.route}")
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = initialRoute
                ) {
                    composable(MayaRoutes.HOME) {
                        HomeScreen(mainVm, onNavigate = { navController.navigate(it) })
                    }
                    composable(MayaRoutes.VOICE) { VoiceScreen(mainVm) }
                    composable(MayaRoutes.CHAT) { ChatScreen(mainVm) }
                    composable(MayaRoutes.HUB) { FeatureHubScreen(onNavigate = { navController.navigate(it) }) }
                    composable(MayaRoutes.SETTINGS) { AdvancedSettingsScreen(mainVm) { navController.popBackStack() } }
                    composable(MayaRoutes.YAADEIN) { YaadeinScreen(mainVm) { navController.popBackStack() } }
                    composable(MayaRoutes.GUARDIAN) { GuardianScreen(mainVm, modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.FACE_LOCK) { FaceLockScreen(mainVm, modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.VISION) { CameraVisionScreen(modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.SCREEN) {
                        ScreenScannerScreen(modVm, onRequestMediaProjection) { navController.popBackStack() }
                    }
                    composable(MayaRoutes.NOTIFICATIONS) { NotificationsScreen(modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.TOOLS) {
                        val canPop = navController.previousBackStackEntry != null
                        ToolsScreen(modVm, if (canPop) ({ navController.popBackStack() }) else null)
                    }
                    composable(MayaRoutes.MISSIONS) { MissionsScreen(modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.SKILLS) { SkillsScreen(modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.REMINDERS) { RemindersScreen(modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.TERMINAL) { TerminalScreen(modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.BROWSER) { BrowserScreen(modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.CODING) { CodingScreen(mainVm, modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.WEBSITES) { WebsitesScreen(mainVm, modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.MARKETING) { MarketingScreen(mainVm, modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.PERSONA) { PersonaScreen(mainVm) { navController.popBackStack() } }
                    composable(MayaRoutes.AVATAR) { AvatarScreen(mainVm) { navController.popBackStack() } }
                    composable(MayaRoutes.LOGIN) { LoginScreen(mainVm) { navController.popBackStack() } }
                    composable(MayaRoutes.PLANS) { PlansScreen(mainVm) { navController.popBackStack() } }
                    composable(MayaRoutes.CONTACTS) { ContactsScreen(modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.DOCUMENTS) { DocumentsScreen(mainVm, modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.WEATHER) { WeatherScreen(modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.MUSIC) { MusicScreen(modVm) { navController.popBackStack() } }
                    composable(MayaRoutes.DIAGNOSTICS) { DiagnosticsScreen(mainVm) { navController.popBackStack() } }
                }
            }
        }
    }
}
