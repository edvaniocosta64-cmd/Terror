package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.GameSettings
import com.example.data.QualityPreference
import com.example.data.RenderBackend
import com.example.data.TargetFpsMode
import com.example.ui.theme.BoneParchment
import com.example.ui.theme.CrimsonBlood
import com.example.ui.theme.FogMist
import com.example.ui.theme.LanternAmber
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianVoid

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: GameSettings,
    deviceMaxRefreshHz: Int,
    onSelectFps: (TargetFpsMode) -> Unit,
    onSelectQuality: (QualityPreference) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onMasterVolumeChange: (Float) -> Unit,
    onAmbientVolumeChange: (Float) -> Unit,
    onSfxVolumeChange: (Float) -> Unit,
    onShowFpsChange: (Boolean) -> Unit,
    onInvertYChange: (Boolean) -> Unit,
    onSelectRenderBackend: (RenderBackend) -> Unit = {},
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = BoneParchment,
                    )
                }
                Text(
                    text = "CONFIGURAÇÕES",
                    style = MaterialTheme.typography.headlineMedium,
                    color = BoneParchment,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Performance & FPS Section
            SettingsSectionCard(title = "DESEMPENHO & GRÁFICOS 3D") {
                Text(
                    text = "Taxa de Quadros Alvo (Tela detectada: ${deviceMaxRefreshHz} Hz)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FogMist,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TargetFpsMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.targetFpsMode == mode,
                            onClick = { onSelectFps(mode) },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonBlood,
                                selectedLabelColor = BoneParchment,
                            ),
                            modifier = Modifier.testTag("fps_chip_${mode.name.lowercase()}"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Qualidade Gráfica (Escala de Resolução & Iluminação)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FogMist,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    QualityPreference.entries.forEach { q ->
                        FilterChip(
                            selected = settings.qualityPreference == q,
                            onClick = { onSelectQuality(q) },
                            label = { Text(q.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LanternAmber,
                                selectedLabelColor = ObsidianVoid,
                            ),
                            modifier = Modifier.testTag("quality_chip_${q.name.lowercase()}"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Motor Gráfico 3D (PBR / Fallback)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FogMist,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RenderBackend.entries.forEach { backend ->
                        FilterChip(
                            selected = settings.renderBackend == backend,
                            onClick = { onSelectRenderBackend(backend) },
                            label = { Text(backend.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrimsonBlood,
                                selectedLabelColor = BoneParchment,
                            ),
                            modifier = Modifier.testTag("backend_chip_${backend.name.lowercase()}"),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SettingsToggleRow(
                    title = "Mostrar FPS e Qualidade na Tela",
                    checked = settings.showFpsHud,
                    testTag = "toggle_show_fps",
                    onCheckedChange = onShowFpsChange,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Camera Controls Section
            SettingsSectionCard(title = "CONTROLES DE TOQUE") {
                SettingsSliderRow(
                    label = "Sensibilidade da Câmera",
                    valueText = String.format("%.1fx", settings.cameraSensitivity),
                    value = settings.cameraSensitivity,
                    range = 0.3f..2.5f,
                    onValueChange = onSensitivityChange,
                )
                SettingsToggleRow(
                    title = "Inverter Eixo Vertical (Y)",
                    checked = settings.invertY,
                    testTag = "toggle_invert_y",
                    onCheckedChange = onInvertYChange,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Section
            SettingsSectionCard(title = "ÁUDIO & AMBIENTE DE TERROR") {
                SettingsSliderRow(
                    label = "Volume Geral",
                    valueText = "${(settings.masterVolume * 100).toInt()}%",
                    value = settings.masterVolume,
                    range = 0f..1f,
                    onValueChange = onMasterVolumeChange,
                )
                SettingsSliderRow(
                    label = "Sons Ambientes (Vento & Tensão)",
                    valueText = "${(settings.ambientVolume * 100).toInt()}%",
                    value = settings.ambientVolume,
                    range = 0f..1f,
                    onValueChange = onAmbientVolumeChange,
                )
                SettingsSliderRow(
                    label = "Efeitos (Portas, Passos & Susto)",
                    valueText = "${(settings.sfxVolume * 100).toInt()}%",
                    value = settings.sfxVolume,
                    range = 0f..1f,
                    onValueChange = onSfxVolumeChange,
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = ObsidianSurface,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ObsidianOutline, RoundedCornerShape(14.dp)),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = LanternAmber,
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = BoneParchment)
            Text(text = valueText, style = MaterialTheme.typography.labelSmall, color = LanternAmber)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = CrimsonBlood,
                activeTrackColor = CrimsonBlood,
            ),
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, color = BoneParchment)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BoneParchment,
                checkedTrackColor = CrimsonBlood,
            ),
            modifier = Modifier.testTag(testTag),
        )
    }
}
