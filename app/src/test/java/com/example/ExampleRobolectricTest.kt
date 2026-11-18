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
}
