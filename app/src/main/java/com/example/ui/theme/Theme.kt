package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarknessColorScheme = darkColorScheme(
    primary = CrimsonBlood,
    onPrimary = BoneParchment,
    primaryContainer = CrimsonDark,
    onPrimaryContainer = BoneParchment,
    secondary = LanternAmber,
    onSecondary = ObsidianVoid,
    secondaryContainer = ObsidianElevated,
    onSecondaryContainer = LanternGlow,
    tertiary = FogMist,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = BoneParchment,
    surface = ObsidianSurface,
    onSurface = BoneParchment,
    surfaceVariant = ObsidianElevated,
    onSurfaceVariant = FogMist,
    outline = ObsidianOutline,
    error = CrimsonBright,
    onError = ObsidianVoid,
)

@Composable
fun ProjectDarknessTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarknessColorScheme,
        typography = DarknessTypography,
        content = content,
    )
}
