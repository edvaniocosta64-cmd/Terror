package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
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

    @Test
    fun `all 69 res_raw ogg sfx ambient and pt-BR voice files resolve and load cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entries = com.example.audio.HorrorAudioEngine.ALL_RAW_RESOURCES
        assertEquals(69, entries.size)
        for ((name, resId) in entries) {
            org.junit.Assert.assertTrue("Resource ID for $name must be non-zero", resId != 0)
            context.resources.openRawResource(resId).use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                assertEquals("Header read failed for $name", 4, read)
                assertEquals("Resource $name must be a valid Ogg file", "OggS", String(header, Charsets.US_ASCII))
            }
        }

        val engine = com.example.audio.HorrorAudioEngine(context)
        engine.updateVolumes(master = 0.85f, ambient = 0.75f, sfx = 0.90f)
        engine.setMenuMode(false)
        engine.setPaused(false)
        engine.updateListener(0f, 5f, 0f)
        for (cueId in com.example.audio.HorrorAudioEngine.VOICE_RESOURCE_MAP.keys) {
            org.junit.Assert.assertTrue(
                "Voice cue $cueId should resolve to a real raw resource",
                engine.playVoiceCue(cueId, isGranny = cueId.startsWith("vovo_"), srcX = 2f, srcZ = 1f, spatial = true),
            )
        }
        for (surface in com.example.audio.FootstepSurface.entries) {
            engine.playFootstep(stealth = false, surface = surface)
            engine.playSpatialFootstep(stealth = false, srcX = 2f, srcZ = 1f, surface = surface, isGranny = true)
        }
        engine.playDoorOpen()
        engine.playDoorClose()
        engine.playDoorCreak()
        engine.playDoorSlam()
        engine.playLockedRattle()
        engine.playUnlockDoor()
        engine.playHorrorSting()
        engine.playAmbushSurprise()
        engine.playBodyThud()
        engine.playBodyDrag()
        engine.playScaredBreath()
        engine.playPaperNote()
        engine.playWireCut()
        engine.release()
    }

    @Test
    fun `voice priority prevents low-priority overlap, stopVoice interrupts cleanly, and pause-resume-release lifecycle works`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audio = com.example.audio.HorrorAudioEngine(context)
        audio.updateVolumes(master = 0.90f, ambient = 0.80f, sfx = 0.95f)
        audio.setMenuMode(false)
        audio.updateListener(0f, 0f, 0f)

        // Start a DIALOGUE line
        org.junit.Assert.assertTrue(audio.playVoiceCue("vovo_wake_1", isGranny = true))
        org.junit.Assert.assertTrue(audio.isVoiceCurrentlyPlaying())
        assertEquals("vovo_wake_1", audio.getActiveVoiceCueId())

        // Lower-priority ambient reaction must NOT overwrite active dialogue
        audio.playVoiceCue("jovem_item", isGranny = false, priority = com.example.audio.VoicePriority.AMBIENT_REACTION)
        assertEquals("vovo_wake_1", audio.getActiveVoiceCueId())

        // Higher-priority CUTSCENE_URGENT interrupts active dialogue cleanly
        audio.playVoiceCue("vovo_ambush", isGranny = true, priority = com.example.audio.VoicePriority.CUTSCENE_URGENT)
        assertEquals("vovo_ambush", audio.getActiveVoiceCueId())

        // Distant spatial voice beyond max distance fades to zero and does not hijack active playback
        val farGains = audio.computeVoiceStereoGains(srcX = 40f, srcZ = 0f, spatial = true)
        assertEquals(0f, farGains.first, 0.001f)
        assertEquals(0f, farGains.second, 0.001f)
        val nearGains = audio.computeVoiceStereoGains(srcX = 2f, srcZ = 1f, spatial = true)
        org.junit.Assert.assertTrue(nearGains.first > 0.3f && nearGains.second > 0.3f)

        // Lifecycle pause -> resume -> stopVoice -> release
        audio.setPaused(true)
        org.junit.Assert.assertTrue(audio.isEnginePaused())
        audio.setPaused(false)
        org.junit.Assert.assertFalse(audio.isEnginePaused())

        audio.stopVoice()
        org.junit.Assert.assertFalse(audio.isVoiceCurrentlyPlaying())
        assertEquals(null, audio.getActiveVoiceCueId())

        audio.release()
        org.junit.Assert.assertTrue(audio.isEngineReleased())
    }

    @Test
    fun `settings screen shows Filament PBR default and OpenGL ES 2 fallback backend chips`() {
        assertEquals(com.example.data.RenderBackend.FILAMENT, GameSettings().renderBackend)
        assertEquals(com.example.data.RenderBackend.OPENGL_ES2, com.example.data.RenderBackend.fromName("OPENGL_ES2"))
        assertEquals(com.example.data.RenderBackend.FILAMENT, com.example.data.RenderBackend.fromName("UNKNOWN"))

        composeTestRule.setContent {
            ProjectDarknessTheme {
                SettingsScreen(
                    settings = GameSettings(),
                    deviceMaxRefreshHz = 90,
                    onSelectFps = {},
                    onSelectQuality = {},
                    onSensitivityChange = {},
                    onMasterVolumeChange = {},
                    onAmbientVolumeChange = {},
                    onSfxVolumeChange = {},
                    onShowFpsChange = {},
                    onInvertYChange = {},
                    onSelectRenderBackend = {},
                    onBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("backend_chip_filament")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("backend_chip_opengl_es2")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `packaged glb character assets exist in APK assets and have valid glTF 2 binary headers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (assetPath in listOf("models/granny.glb", "models/protagonist.glb")) {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            org.junit.Assert.assertTrue("$assetPath must be larger than 50KB", bytes.size > 50_000)
            val magic = String(bytes, 0, 4, Charsets.US_ASCII)
            assertEquals("glTF", magic)
            val version = java.nio.ByteBuffer.wrap(bytes, 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            assertEquals(2, version)
        }
    }
}
