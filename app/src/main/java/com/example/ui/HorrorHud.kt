package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.engine3d.DialogueLine
import com.example.engine3d.GameHudSnapshot
import com.example.engine3d.PerformanceTelemetry
import com.example.ui.theme.BoneParchment
import com.example.ui.theme.CrimsonBlood
import com.example.ui.theme.CrimsonBright
import com.example.ui.theme.FogMist
import com.example.ui.theme.LanternAmber
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid

@Composable
fun BloodAndDangerVignette(
    bloodPulseAlpha: Float,
    dangerLevel: Float,
    modifier: Modifier = Modifier,
) {
    val totalAlpha = maxOf(bloodPulseAlpha, dangerLevel * 0.45f).coerceIn(0f, 0.92f)
    if (totalAlpha <= 0.02f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.maxDimension * 0.72f
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    CrimsonBlood.copy(alpha = totalAlpha * 0.45f),
                    CrimsonBright.copy(alpha = totalAlpha * 0.85f),
                ),
                center = center,
                radius = radius,
            )
        )
        if (bloodPulseAlpha > 0.15f) {
            // Stylized blood droplets near screen corners
            drawCircle(
                color = CrimsonBlood.copy(alpha = bloodPulseAlpha * 0.65f),
                radius = size.minDimension * 0.06f,
                center = Offset(size.width * 0.10f, size.height * 0.16f),
            )
            drawCircle(
                color = CrimsonBlood.copy(alpha = bloodPulseAlpha * 0.55f),
                radius = size.minDimension * 0.08f,
                center = Offset(size.width * 0.88f, size.height * 0.22f),
            )
            drawCircle(
                color = CrimsonBlood.copy(alpha = bloodPulseAlpha * 0.60f),
                radius = size.minDimension * 0.07f,
                center = Offset(size.width * 0.82f, size.height * 0.82f),
            )
        }
    }
}

@Composable
fun TopHorrorHud(
    hud: GameHudSnapshot,
    telemetry: PerformanceTelemetry,
    showFpsHud: Boolean,
    onPauseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        // Left: Objective + Health/Battery + Inventory
        Column(
            modifier = Modifier.widthIn(max = 310.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                color = ObsidianSurface.copy(alpha = 0.80f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.border(1.dp, ObsidianOutline, RoundedCornerShape(8.dp)),
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        text = hud.storyPhase.chapterTitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = LanternAmber,
                    )
                    Text(
                        text = hud.objectiveText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BoneParchment,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = ObsidianSurface.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.width(110.dp),
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        Text("VIDA ${hud.health}%", style = MaterialTheme.typography.labelSmall, color = BoneParchment)
                        LinearProgressIndicator(
                            progress = { (hud.health / 100f).coerceIn(0f, 1f) },
                            color = CrimsonBright,
                            trackColor = ObsidianVoid,
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                        )
                    }
                }
                if (hud.hasFlashlight) {
                    Surface(
                        color = ObsidianSurface.copy(alpha = 0.78f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.width(110.dp),
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Text("LUZ ${hud.battery}%", style = MaterialTheme.typography.labelSmall, color = LanternAmber)
                            LinearProgressIndicator(
                                progress = { (hud.battery / 100f).coerceIn(0f, 1f) },
                                color = LanternAmber,
                                trackColor = ObsidianVoid,
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                            )
                        }
                    }
                }
            }

            if (hud.inventory.isNotEmpty()) {
                Surface(
                    color = ObsidianSurface.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    val labels = hud.inventory.joinToString(" • ") { id ->
                        when (id) {
                            "key_study" -> "Chave Escritório"
                            "wire_cutters" -> "Alicate"
                            "crowbar" -> "Pé de Cabra"
                            "fuse" -> "Fusível"
                            "master_key" -> "Chave Mestra"
                            else -> id
                        }
                    }
                    Text(
                        text = "Itens: $labels",
                        style = MaterialTheme.typography.labelSmall,
                        color = FogMist,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Right: FPS Telemetry + Pause button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showFpsHud) {
                Surface(
                    color = ObsidianSurface.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .border(1.dp, ObsidianOutline, RoundedCornerShape(8.dp))
                        .testTag("fps_telemetry_pill"),
                ) {
                    Text(
                        text = "${telemetry.currentFps}/${telemetry.targetFps} FPS • ${telemetry.activeQuality.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LanternAmber,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            IconButton(
                onClick = onPauseClick,
                modifier = Modifier
                    .size(48.dp)
                    .background(ObsidianSurface.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                    .border(1.dp, ObsidianOutline, RoundedCornerShape(10.dp))
                    .testTag("pause_game_button"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Pause,
                    contentDescription = "Pausar jogo",
                    tint = BoneParchment,
                )
            }
        }
    }
}

@Composable
fun CinematicIntroOverlay(
    caption: String,
    onSkip: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top letterbox
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.TopCenter)
                .background(Color.Black),
        )
        // Bottom letterbox with caption & skip
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.92f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = caption,
                style = MaterialTheme.typography.titleMedium,
                color = BoneParchment,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(
                onClick = onSkip,
                modifier = Modifier.testTag("skip_intro_button"),
            ) {
                Text("Pular Introdução >>", color = LanternAmber)
            }
        }
    }
}

@Composable
fun DialogueSubtitleBox(
    dialogue: DialogueLine,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = ObsidianVoid.copy(alpha = 0.90f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth(0.90f)
            .border(
                width = 1.dp,
                color = if (dialogue.isGranny) CrimsonBlood else FogMist.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onAdvance)
            .testTag("dialogue_subtitle_box"),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
            Text(
                text = dialogue.speaker.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = if (dialogue.isGranny) CrimsonBright else LanternAmber,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "\"${dialogue.text}\"",
                style = MaterialTheme.typography.bodyLarge,
                color = BoneParchment,
            )
        }
    }
}

@Composable
fun NoteReaderOverlay(
    title: String,
    body: String,
    onClose: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .border(1.5.dp, LanternAmber, RoundedCornerShape(14.dp)),
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Text(text = title, style = MaterialTheme.typography.headlineMedium, color = LanternAmber)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = body, style = MaterialTheme.typography.bodyLarge, color = BoneParchment)
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonBlood),
                    modifier = Modifier.align(Alignment.End).testTag("close_note_button"),
                ) {
                    Text("Fechar")
                }
            }
        }
    }
}

@Composable
fun PauseSaveOverlay(
    saveBanner: String?,
    onResume: () -> Unit,
    onSaveSlot: (slotId: String, slotName: String) -> Unit,
    onOpenSettings: () -> Unit,
    onExitToMenu: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .border(1.dp, CrimsonBlood.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("JOGO PAUSADO", style = MaterialTheme.typography.headlineMedium, color = BoneParchment)

                saveBanner?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = LanternAmber)
                }

                Button(
                    onClick = onResume,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonBlood),
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("resume_game_button"),
                ) {
                    Text("Continuar Jogando")
                }

                Text("SALVAR PROGRESSO NA NUVEM", style = MaterialTheme.typography.labelSmall, color = FogMist)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("slot_1" to "Slot 1", "slot_2" to "Slot 2", "slot_3" to "Slot 3").forEach { (id, name) ->
                        OutlinedButton(
                            onClick = { onSaveSlot(id, name) },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("save_$id"),
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("pause_settings_button"),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Configurações")
                }

                TextButton(
                    onClick = onExitToMenu,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("exit_to_menu_button"),
                ) {
                    Text("Sair para o Menu Inicial", color = FogMist)
                }
            }
        }
    }
}

@Composable
fun OutcomeOverlay(
    escaped: Boolean,
    playTimeSeconds: Int,
    onRetry: () -> Unit,
    onExitToMenu: () -> Unit,
) {
    val mins = playTimeSeconds / 60
    val secs = playTimeSeconds % 60
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (escaped) ObsidianVoid.copy(alpha = 0.85f) else CrimsonDarkBackground())
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = ObsidianSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth()
                .border(1.5.dp, if (escaped) LanternAmber else CrimsonBright, RoundedCornerShape(16.dp)),
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (escaped) "VOCÊ ESCAPOU!" else "VOCÊ FOI CAPTURADO",
                    style = MaterialTheme.typography.displayMedium,
                    color = if (escaped) LanternAmber else CrimsonBright,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = if (escaped) {
                        "Você destrancou a porta da frente e fugiu da casa da Vovó na madrugada. Tempo: %02d:%02d".format(mins, secs)
                    } else {
                        "A Vovó alcançou você na escuridão... Tente ser mais silencioso ou esconda-se no armário!"
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = BoneParchment,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonBlood),
                    modifier = Modifier.fillMaxWidth().height(50.dp).testTag("outcome_retry_button"),
                ) {
                    Text(if (escaped) "Jogar Novamente" else "Tentar Novamente")
                }
                OutlinedButton(
                    onClick = onExitToMenu,
                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("outcome_menu_button"),
                ) {
                    Text("Voltar ao Menu Inicial")
                }
            }
        }
    }
}

private fun CrimsonDarkBackground(): Color = Color(0xCC280408)
