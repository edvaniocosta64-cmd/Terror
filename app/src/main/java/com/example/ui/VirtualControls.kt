package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.engine3d.InteractionPrompt
import com.example.ui.theme.BoneParchment
import com.example.ui.theme.CrimsonBlood
import com.example.ui.theme.FogMist
import com.example.ui.theme.LanternAmber
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid
import kotlin.math.sqrt

@Composable
fun VirtualJoystick(
    modifier: Modifier = Modifier,
    onMoveChanged: (x: Float, y: Float) -> Unit,
) {
    var knobOffset by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(136.dp)
            .testTag("virtual_joystick")
            .pointerInput(Unit) {
                val maxRadius = size.width * 0.36f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    active = true
                    val center = Offset(size.width / 2f, size.height / 2f)
                    fun updateFromPos(pos: Offset) {
                        val delta = pos - center
                        val dist = sqrt(delta.x * delta.x + delta.y * delta.y)
                        val clamped = if (dist > maxRadius) {
                            delta * (maxRadius / dist)
                        } else {
                            delta
                        }
                        knobOffset = clamped
                        // +Y up means moving forward (-delta.y)
                        onMoveChanged(
                            (clamped.x / maxRadius).coerceIn(-1f, 1f),
                            (-clamped.y / maxRadius).coerceIn(-1f, 1f),
                        )
                    }
                    updateFromPos(down.position)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        updateFromPos(change.position)
                        change.consume()
                    }
                    active = false
                    knobOffset = Offset.Zero
                    onMoveChanged(0f, 0f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerR = size.minDimension * 0.44f
            drawCircle(
                color = ObsidianVoid.copy(alpha = 0.42f),
                radius = outerR,
            )
            drawCircle(
                color = if (active) LanternAmber.copy(alpha = 0.72f) else FogMist.copy(alpha = 0.35f),
                radius = outerR,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = FogMist.copy(alpha = 0.16f),
                radius = outerR * 0.45f,
                style = Stroke(width = 1.dp.toPx()),
            )
            val knobPos = center + knobOffset
            drawCircle(
                color = if (active) CrimsonBlood.copy(alpha = 0.85f) else BoneParchment.copy(alpha = 0.55f),
                radius = outerR * 0.38f,
                center = knobPos,
            )
        }
    }
}

@Composable
fun TouchLookPad(
    modifier: Modifier = Modifier,
    onLookDelta: (dx: Float, dy: Float) -> Unit,
) {
    Box(
        modifier = modifier
            .testTag("touch_look_pad")
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        val delta = change.positionChange()
                        if (delta != Offset.Zero) {
                            onLookDelta(delta.x, delta.y)
                            change.consume()
                        }
                    }
                }
            }
    )
}

@Composable
fun ActionButtonsCluster(
    interactionPrompt: InteractionPrompt?,
    hasFlashlight: Boolean,
    flashlightOn: Boolean,
    isCrouching: Boolean,
    isHiding: Boolean,
    onInteract: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onToggleCrouch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (interactionPrompt != null) {
            Surface(
                color = ObsidianSurface.copy(alpha = 0.90f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.border(1.dp, LanternAmber, RoundedCornerShape(10.dp)),
            ) {
                Text(
                    text = interactionPrompt.actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = LanternAmber,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Crouch / Hide Exit
            SmallFloatingActionButton(
                onClick = onToggleCrouch,
                containerColor = if (isCrouching || isHiding) LanternAmber else ObsidianSurface.copy(alpha = 0.85f),
                contentColor = if (isCrouching || isHiding) ObsidianVoid else BoneParchment,
                shape = CircleShape,
                modifier = Modifier
                    .size(52.dp)
                    .border(1.dp, ObsidianOutline, CircleShape)
                    .testTag("crouch_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.AccessibilityNew,
                    contentDescription = if (isCrouching) "Levantar" else "Agachar",
                )
            }

            // Flashlight Toggle
            SmallFloatingActionButton(
                onClick = onToggleFlashlight,
                containerColor = when {
                    flashlightOn -> LanternAmber
                    hasFlashlight -> ObsidianSurface.copy(alpha = 0.85f)
                    else -> ObsidianVoid.copy(alpha = 0.60f)
                },
                contentColor = if (flashlightOn) ObsidianVoid else BoneParchment,
                shape = CircleShape,
                modifier = Modifier
                    .size(52.dp)
                    .border(1.dp, ObsidianOutline, CircleShape)
                    .testTag("flashlight_button"),
            ) {
                Icon(
                    imageVector = if (flashlightOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                    contentDescription = "Lanterna",
                )
            }

            // Primary Interact Button
            FloatingActionButton(
                onClick = onInteract,
                containerColor = if (interactionPrompt != null) CrimsonBlood else ObsidianSurface.copy(alpha = 0.85f),
                contentColor = BoneParchment,
                shape = CircleShape,
                modifier = Modifier
                    .size(64.dp)
                    .border(
                        width = 1.5.dp,
                        color = if (interactionPrompt != null) LanternAmber else ObsidianOutline,
                        shape = CircleShape,
                    )
                    .testTag("interact_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.TouchApp,
                    contentDescription = "Interagir",
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}
