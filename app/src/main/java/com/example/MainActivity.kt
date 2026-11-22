package com.example

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.automation.ScreenCaptureService
import com.example.core.configureAppCheck
import com.example.ui.MayaMainViewModel
import com.example.ui.MayaModulesViewModel
import com.example.ui.components.AuthGateScreen
import com.example.ui.navigation.MayaAppShell
import com.example.ui.navigation.MayaRoutes
import com.example.ui.theme.MayaTheme

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // CRITICAL: App Check must be configured BEFORE any Firebase AI / Firestore / ViewModel access.
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        val initialRoute = when (intent?.getStringExtra("start_route")) {
            "voice" -> MayaRoutes.VOICE
            else -> MayaRoutes.HOME
        }

        setContent {
            MayaTheme {
                val mainVm: MayaMainViewModel = viewModel()
                val modVm: MayaModulesViewModel = viewModel()

                val authState by mainVm.authState.collectAsStateWithLifecycle()
                val settings by mainVm.settings.collectAsStateWithLifecycle()

                val projectionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val data = result.data
                    if (result.resultCode == RESULT_OK && data != null) {
                        val svcIntent = Intent(this, ScreenCaptureService::class.java).apply {
                            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                            putExtra(ScreenCaptureService.EXTRA_DATA, data)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(svcIntent)
                        } else {
                            startService(svcIntent)
                        }
                    } else {
                        mainVm.showBanner("Screen capture permission declined.")
                    }
                }

                LaunchedEffect(Unit) {
                    if (authState.user == null && !settings.localOfflineMode) {
                        mainVm.attemptAutoSignIn(this@MainActivity)
                    }
                }

                if (!settings.onboardingCompleted && authState.user == null) {
                    AuthGateScreen(
                        authState = authState,
                        onSignInWithGoogle = { mainVm.signInWithGoogle(this@MainActivity) },
                        onContinueOfflineLocal = { mainVm.continueInLocalOfflineMode() }
                    )
                } else {
                    key(authState.user?.uid ?: "local_device_mode") {
                        MayaAppShell(
                            mainVm = mainVm,
                            modVm = modVm,
                            initialRoute = initialRoute,
                            onRequestMediaProjection = {
                                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                projectionLauncher.launch(mgr.createScreenCaptureIntent())
                            }
                        )
                    }
                }
            }
        }
    }
}
