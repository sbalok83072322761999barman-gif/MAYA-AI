package com.example

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.example.ui.AuthUiState
import com.example.ui.components.AuthGateScreen
import com.example.ui.theme.MayaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read app metadata strings from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        val dbId = context.getString(R.string.firestore_database_id)
        assertEquals("Maya AI", appName)
        assertTrue(dbId.isNotBlank())
    }

    @Test
    fun `all drawable images and bundled fonts decode cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val drawables = listOf(
            R.drawable.img_maya_hero_banner,
            R.drawable.img_maya_avatar_core,
            R.drawable.img_maya_icon_fg
        )
        for (resId in drawables) {
            val bitmap = BitmapFactory.decodeResource(context.resources, resId)
            assertNotNull("Expected non-null bitmap for drawable $resId", bitmap)
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
        }

        val fonts = listOf(
            R.font.space_grotesk,
            R.font.plus_jakarta_sans,
            R.font.jetbrains_mono
        )
        for (fontId in fonts) {
            val typeface = ResourcesCompat.getFont(context, fontId)
            assertNotNull("Expected non-null typeface for font $fontId", typeface)
        }
    }

    @Test
    fun `auth gate screen renders hero banner and buttons without crashing`() {
        composeTestRule.setContent {
            MayaTheme {
                AuthGateScreen(
                    authState = AuthUiState(),
                    onSignInWithGoogle = {},
                    onContinueOfflineLocal = {}
                )
            }
        }
        composeTestRule.onNodeWithTag("google_signin_button").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("local_mode_button").performScrollTo().assertIsDisplayed()
    }
}

