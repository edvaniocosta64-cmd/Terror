package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "darkness_settings")

enum class TargetFpsMode(val label: String, val targetFps: Int) {
    AUTO("Auto (Máx. 120)", 120),
    FPS_120("120 FPS", 120),
    FPS_90("90 FPS", 90),
    FPS_60("60 FPS", 60),
    FPS_30("30 FPS", 30);

    companion object {
        fun fromName(raw: String?): TargetFpsMode =
            entries.firstOrNull { it.name == raw } ?: AUTO
    }
}

enum class QualityPreference(val label: String) {
    AUTO("Automática"),
    ULTRA("Ultra"),
    HIGH("Alta"),
    MEDIUM("Média"),
    LOW("Baixa");

    companion object {
        fun fromName(raw: String?): QualityPreference =
            entries.firstOrNull { it.name == raw } ?: AUTO
    }
}

enum class RenderBackend(val label: String) {
    FILAMENT("Filament PBR"),
    OPENGL_ES2("OpenGL ES 2.0");

    companion object {
        fun fromName(raw: String?): RenderBackend =
            entries.firstOrNull { it.name == raw } ?: FILAMENT
    }
}

data class GameSettings(
    val targetFpsMode: TargetFpsMode = TargetFpsMode.AUTO,
    val qualityPreference: QualityPreference = QualityPreference.AUTO,
    val renderBackend: RenderBackend = RenderBackend.FILAMENT,
    val cameraSensitivity: Float = 1.0f,
    val masterVolume: Float = 0.85f,
    val ambientVolume: Float = 0.80f,
    val sfxVolume: Float = 0.90f,
    val showFpsHud: Boolean = true,
    val invertY: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val TARGET_FPS = stringPreferencesKey("target_fps_mode")
        val QUALITY = stringPreferencesKey("quality_preference")
        val RENDER_BACKEND = stringPreferencesKey("render_backend")
        val SENSITIVITY = floatPreferencesKey("camera_sensitivity")
        val MASTER_VOL = floatPreferencesKey("master_volume")
        val AMBIENT_VOL = floatPreferencesKey("ambient_volume")
        val SFX_VOL = floatPreferencesKey("sfx_volume")
        val SHOW_FPS = booleanPreferencesKey("show_fps_hud")
        val INVERT_Y = booleanPreferencesKey("invert_y")
    }

    val settingsFlow: Flow<GameSettings> = context.settingsDataStore.data.map { prefs ->
        GameSettings(
            targetFpsMode = TargetFpsMode.fromName(prefs[Keys.TARGET_FPS]),
            qualityPreference = QualityPreference.fromName(prefs[Keys.QUALITY]),
            renderBackend = RenderBackend.fromName(prefs[Keys.RENDER_BACKEND]),
            cameraSensitivity = (prefs[Keys.SENSITIVITY] ?: 1.0f).coerceIn(0.3f, 2.5f),
            masterVolume = (prefs[Keys.MASTER_VOL] ?: 0.85f).coerceIn(0f, 1f),
            ambientVolume = (prefs[Keys.AMBIENT_VOL] ?: 0.80f).coerceIn(0f, 1f),
            sfxVolume = (prefs[Keys.SFX_VOL] ?: 0.90f).coerceIn(0f, 1f),
            showFpsHud = prefs[Keys.SHOW_FPS] ?: true,
            invertY = prefs[Keys.INVERT_Y] ?: false,
        )
    }

    suspend fun updateSettings(transform: (GameSettings) -> GameSettings) {
        context.settingsDataStore.edit { prefs ->
            val current = GameSettings(
                targetFpsMode = TargetFpsMode.fromName(prefs[Keys.TARGET_FPS]),
                qualityPreference = QualityPreference.fromName(prefs[Keys.QUALITY]),
                renderBackend = RenderBackend.fromName(prefs[Keys.RENDER_BACKEND]),
                cameraSensitivity = (prefs[Keys.SENSITIVITY] ?: 1.0f).coerceIn(0.3f, 2.5f),
                masterVolume = (prefs[Keys.MASTER_VOL] ?: 0.85f).coerceIn(0f, 1f),
                ambientVolume = (prefs[Keys.AMBIENT_VOL] ?: 0.80f).coerceIn(0f, 1f),
                sfxVolume = (prefs[Keys.SFX_VOL] ?: 0.90f).coerceIn(0f, 1f),
                showFpsHud = prefs[Keys.SHOW_FPS] ?: true,
                invertY = prefs[Keys.INVERT_Y] ?: false,
            )
            val next = transform(current)
            prefs[Keys.TARGET_FPS] = next.targetFpsMode.name
            prefs[Keys.QUALITY] = next.qualityPreference.name
            prefs[Keys.RENDER_BACKEND] = next.renderBackend.name
            prefs[Keys.SENSITIVITY] = next.cameraSensitivity.coerceIn(0.3f, 2.5f)
            prefs[Keys.MASTER_VOL] = next.masterVolume.coerceIn(0f, 1f)
            prefs[Keys.AMBIENT_VOL] = next.ambientVolume.coerceIn(0f, 1f)
            prefs[Keys.SFX_VOL] = next.sfxVolume.coerceIn(0f, 1f)
            prefs[Keys.SHOW_FPS] = next.showFpsHud
            prefs[Keys.INVERT_Y] = next.invertY
        }
    }
}
