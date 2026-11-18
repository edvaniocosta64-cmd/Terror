package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameSettings
import com.example.data.StoryPhase
import com.example.ui.MainMenuScreen
import com.example.ui.SettingsScreen
import com.example.ui.theme.ProjectDarknessTheme
import com.example.ui.viewmodel.SavesUiState
import org.junit.Assert.assertEquals
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
    fun `app_name matches Project Darkness`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Project: Darkness", appName)
    }

    @Test
    fun `main menu renders title and primary story button`() {
        composeTestRule.setContent {
            ProjectDarknessTheme {
                MainMenuScreen(
                    savesState = SavesUiState.Success(emptyList()),
                    userEmail = "jogador@teste.com",
                    onNewGame = {},
                    onContinueSave = {},
                    onOpenSaves = {},
                    onOpenSettings = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("DARKNESS").assertIsDisplayed()
        composeTestRule.onNodeWithTag("new_game_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()
    }

    @Test
    fun `settings screen shows 120 90 60 30 FPS chips`() {
        composeTestRule.setContent {
            ProjectDarknessTheme {
                SettingsScreen(
                    settings = GameSettings(),
                    deviceMaxRefreshHz = 120,
                    onSelectFps = {},
                    onSelectQuality = {},
                    onSensitivityChange = {},
                    onMasterVolumeChange = {},
                    onAmbientVolumeChange = {},
                    onSfxVolumeChange = {},
                    onShowFpsChange = {},
                    onInvertYChange = {},
                    onBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("fps_chip_fps_120").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fps_chip_fps_90").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fps_chip_fps_60").assertIsDisplayed()
        composeTestRule.onNodeWithTag("fps_chip_fps_30").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quality_chip_auto").assertIsDisplayed()
    }

    @Test
    fun `auth screen renders cleanly without crashing when firebase is uninitialized`() {
        composeTestRule.setContent {
            ProjectDarknessTheme {
                com.example.ui.AuthScreen(onAuthSuccess = {}, onContinueOffline = {})
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("offline_play_button").assertIsDisplayed()
    }

    @Test
    fun `main activity launches cleanly in sensor landscape without uncaught exception`() {
        val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        val activity = controller.get()
        org.junit.Assert.assertFalse(activity.isFinishing)
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            activity.requestedOrientation,
        )
        val info = activity.packageManager.getActivityInfo(activity.componentName, 0)
        assertEquals(
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            info.screenOrientation,
        )
    }

    @Test
    fun `all procedural 3D horror textures generate valid non-empty bitmaps`() {
        for (kind in com.example.engine3d.TextureKind.entries) {
            val bmp = com.example.engine3d.ProceduralTextures3D.generateBitmap(kind, size = 128)
            assertEquals(128, bmp.width)
            assertEquals(128, bmp.height)
            bmp.recycle()
        }
    }

    @Test
    fun `capture cutscene overlay renders blackout layer caption and accessible skip control`() {
        var skipped = false
        composeTestRule.setContent {
            ProjectDarknessTheme {
                com.example.ui.CaptureCutsceneOverlay(
                    caption = "Ela arrasta você para as profundezas sombrias da casa...",
                    blackoutAlpha = 0.75f,
                    onSkip = { skipped = true },
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("capture_blackout_layer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("capture_caption_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("skip_capture_button").assertIsDisplayed()
    }

    @Test
    fun `awakening cutscene overlay renders eyelid shutter peek hint caption and skip button`() {
        var skipped = false
        composeTestRule.setContent {
            ProjectDarknessTheme {
                com.example.ui.AwakeningCutsceneOverlay(
                    caption = "Fique imóvel! A Vovó procura você pelo quarto...",
                    eyelidOpen = 0.55f,
                    peekEnabled = true,
                    onSkip = { skipped = true },
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("awakening_eyelid_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("awakening_peek_hint").assertIsDisplayed()
        composeTestRule.onNodeWithTag("awakening_caption_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("skip_awakening_button").assertIsDisplayed()
    }
}
