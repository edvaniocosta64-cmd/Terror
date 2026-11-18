package com.example.ui

import android.opengl.GLSurfaceView
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.GameSettings
import com.example.engine3d.AutoQualityController
import com.example.engine3d.HorrorGameEngine
import com.example.engine3d.HorrorGlRenderer
import com.example.engine3d.PerformanceTelemetry
import com.example.engine3d.QualityTier
import com.example.ui.theme.BoneParchment
import com.example.ui.theme.LanternAmber
import com.example.ui.theme.ObsidianSurface
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    gameEngine: HorrorGameEngine,
    performanceManager: AutoQualityController,
    settings: GameSettings,
    telemetry: PerformanceTelemetry,
    saveBanner: String?,
    onTelemetryUpdate: (PerformanceTelemetry) -> Unit,
    onSaveSlot: (slotId: String, slotName: String) -> Unit,
    onClearSaveBanner: () -> Unit,
    onOpenSettings: () -> Unit,
    onExitToMenu: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPauseOpen by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf(gameEngine.hudSnapshot()) }
    var activeGlView by remember { mutableStateOf<GLSurfaceView?>(null) }

    BackHandler {
        if (hud.activeNoteTitle != null) {
            gameEngine.dismissNote()
        } else if (!hud.isCaught && !hud.isEscaped) {
            isPauseOpen = !isPauseOpen
            gameEngine.isPaused = isPauseOpen
        }
    }

    // Apply Android 11+ hardware frame rate hint & dynamic render scale whenever target/quality changes
    LaunchedEffect(activeGlView, settings.targetFpsMode, settings.qualityPreference) {
        val glView = activeGlView ?: return@LaunchedEffect
        glView.post {
            applySurfaceRateAndScale(
                glView = glView,
                targetFps = performanceManager.effectiveTargetFps(),
                tier = performanceManager.activeTier,
            )
        }
    }

    // Poll simulation HUD snapshot at ~20Hz
    LaunchedEffect(Unit) {
        while (true) {
            hud = gameEngine.hudSnapshot()
            delay(50L)
        }
    }

    DisposableEffect(lifecycleOwner, activeGlView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> activeGlView?.onResume()
                Lifecycle.Event.ON_PAUSE -> activeGlView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activeGlView?.onPause()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // True OpenGL ES 3D Viewport
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    preserveEGLContextOnPause = true
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            post {
                                applySurfaceRateAndScale(
                                    glView = this@apply,
                                    targetFps = performanceManager.effectiveTargetFps(),
                                    tier = performanceManager.activeTier,
                                )
                            }
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                    })
                    val renderer = HorrorGlRenderer(
                        gameEngine = gameEngine,
                        performanceManager = performanceManager,
                        onTelemetryUpdate = onTelemetryUpdate,
                        onQualityScaleChanged = { newTier ->
                            post {
                                applySurfaceRateAndScale(
                                    glView = this,
                                    targetFps = performanceManager.effectiveTargetFps(),
                                    tier = newTier,
                                )
                            }
                        },
                    )
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    activeGlView = this
                }
            },
        )

        // Blood & danger vignette
        BloodAndDangerVignette(
            bloodPulseAlpha = hud.bloodPulseAlpha,
            dangerLevel = hud.dangerLevel,
        )

        // Subtle center reticle dot
        if (!hud.isCinematicIntro && !isPauseOpen && hud.activeNoteTitle == null) {
            Canvas(modifier = Modifier.size(16.dp).align(Alignment.Center)) {
                val dotColor = if (hud.interactionPrompt != null) LanternAmber else Color.White.copy(alpha = 0.55f)
                drawCircle(
                    color = dotColor,
                    radius = if (hud.interactionPrompt != null) 4.5.dp.toPx() else 2.2.dp.toPx(),
                    center = Offset(size.width / 2f, size.height / 2f),
                )
            }
        }

        // Right-half Touch-Look Drag Zone
        if (!hud.isCinematicIntro && !isPauseOpen && !hud.isCaught && !hud.isEscaped) {
            TouchLookPad(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 160.dp),
                onLookDelta = { dx, dy ->
                    gameEngine.addLookDelta(
                        dx = dx,
                        dy = dy,
                        sensitivity = settings.cameraSensitivity,
                        invertY = settings.invertY,
                    )
                },
            )
        }

        // Top HUD
        if (!hud.isCinematicIntro) {
            TopHorrorHud(
                hud = hud,
                telemetry = telemetry,
                showFpsHud = settings.showFpsHud,
                onPauseClick = {
                    onClearSaveBanner()
                    isPauseOpen = true
                    gameEngine.isPaused = true
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // Status toast banner
        hud.statusToast?.let { toast ->
            Surface(
                color = ObsidianSurface.copy(alpha = 0.88f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(top = 86.dp),
            ) {
                Text(
                    text = toast,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LanternAmber,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        // Dialogue Subtitles Box
        hud.activeDialogue?.let { line ->
            DialogueSubtitleBox(
                dialogue = line,
                onAdvance = { gameEngine.advanceDialogue() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = 148.dp),
            )
        }

        // Virtual Joystick & Action Buttons
        if (!hud.isCinematicIntro && !isPauseOpen && !hud.isCaught && !hud.isEscaped) {
            VirtualJoystick(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(start = 20.dp, bottom = 20.dp),
                onMoveChanged = { x, y ->
                    gameEngine.moveInputX = x
                    gameEngine.moveInputY = y
                },
            )

            ActionButtonsCluster(
                interactionPrompt = hud.interactionPrompt,
                hasFlashlight = hud.hasFlashlight,
                flashlightOn = hud.flashlightOn,
                isCrouching = hud.isCrouching,
                isHiding = hud.isHiding,
                onInteract = { gameEngine.interactWithTarget() },
                onToggleFlashlight = { gameEngine.toggleFlashlight() },
                onToggleCrouch = { gameEngine.toggleCrouch() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(end = 20.dp, bottom = 20.dp),
            )
        }

        // Cinematic Intro Overlay
        if (hud.isCinematicIntro) {
            CinematicIntroOverlay(
                caption = hud.introCaption,
                onSkip = { gameEngine.skipCinematicIntro() },
            )
        }

        // Note Reader Modal
        if (hud.activeNoteTitle != null && hud.activeNoteBody != null) {
            NoteReaderOverlay(
                title = hud.activeNoteTitle!!,
                body = hud.activeNoteBody!!,
                onClose = { gameEngine.dismissNote() },
            )
        }

        // Pause & Save Modal
        if (isPauseOpen) {
            PauseSaveOverlay(
                saveBanner = saveBanner,
                onResume = {
                    isPauseOpen = false
                    gameEngine.isPaused = false
                },
                onSaveSlot = { slotId, slotName ->
                    onSaveSlot(slotId, slotName)
                },
                onOpenSettings = {
                    isPauseOpen = false
                    onOpenSettings()
                },
                onExitToMenu = {
                    isPauseOpen = false
                    gameEngine.isPaused = false
                    onExitToMenu()
                },
            )
        }

        // Victory / Caught Outcome Overlay
        if (hud.isCaught || hud.isEscaped) {
            OutcomeOverlay(
                escaped = hud.isEscaped,
                playTimeSeconds = hud.playTimeSeconds,
                onRetry = {
                    gameEngine.resetNewStory()
                },
                onExitToMenu = {
                    gameEngine.isPaused = false
                    onExitToMenu()
                },
            )
        }
    }
}

private fun applySurfaceRateAndScale(
    glView: GLSurfaceView,
    targetFps: Int,
    tier: QualityTier,
) {
    val holder = glView.holder
    val baseW = glView.width
    val baseH = glView.height
    if (baseW > 0 && baseH > 0) {
        val scaledW = (baseW * tier.renderScale).toInt().coerceAtLeast(360)
        val scaledH = (baseH * tier.renderScale).toInt().coerceAtLeast(240)
        try {
            holder.setFixedSize(scaledW, scaledH)
        } catch (_: Throwable) {
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val surface = holder.surface
        if (surface != null && surface.isValid) {
            try {
                surface.setFrameRate(
                    targetFps.toFloat(),
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                )
            } catch (_: Throwable) {
            }
        }
    }
}
