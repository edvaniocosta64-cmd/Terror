package com.example.engine3d

import com.example.data.QualityPreference
import com.example.data.TargetFpsMode

enum class QualityTier(
    val label: String,
    val renderScale: Float,
    val drawDistance: Float,
    val maxDynamicLights: Int,
    val enableParticles: Boolean,
    val enableShadows: Boolean = true,
    val volumetricFogLayers: Int = 12,
    val textureSizePx: Int = 256,
) {
    ULTRA("Ultra", 1.00f, 44f, 4, true, enableShadows = true, volumetricFogLayers = 18, textureSizePx = 256),
    HIGH("Alta", 0.85f, 34f, 3, true, enableShadows = true, volumetricFogLayers = 12, textureSizePx = 256),
    MEDIUM("Média", 0.70f, 25f, 2, true, enableShadows = true, volumetricFogLayers = 6, textureSizePx = 128),
    LOW("Baixa", 0.55f, 18f, 1, false, enableShadows = false, volumetricFogLayers = 0, textureSizePx = 128);

    fun stepDown(): QualityTier = when (this) {
        ULTRA -> HIGH
        HIGH -> MEDIUM
        MEDIUM -> LOW
        LOW -> LOW
    }

    fun stepUp(): QualityTier = when (this) {
        LOW -> MEDIUM
        MEDIUM -> HIGH
        HIGH -> ULTRA
        ULTRA -> ULTRA
    }
}

data class PerformanceTelemetry(
    val currentFps: Int = 60,
    val targetFps: Int = 60,
    val activeQuality: QualityTier = QualityTier.HIGH,
    val frameTimeMs: Float = 16.6f,
    val deviceMaxRefreshHz: Int = 60,
)

class AutoQualityController(
    private var deviceMaxHz: Int = 60,
) {
    var targetMode: TargetFpsMode = TargetFpsMode.AUTO
    var qualityPreference: QualityPreference = QualityPreference.AUTO

    var activeTier: QualityTier = QualityTier.HIGH
        private set

    private var frameCount = 0
    private var accumulatedTimeSec = 0.0
    private var lowPerfDurationSec = 0.0
    private var highHeadroomDurationSec = 0.0
    private var lastReportedFps = 60
    private var lastAvgFrameMs = 16.6f

    fun updateDeviceMaxHz(maxHz: Int) {
        deviceMaxHz = maxHz.coerceIn(30, 144)
    }

    fun effectiveTargetFps(): Int {
        val desired = when (targetMode) {
            TargetFpsMode.AUTO -> when {
                deviceMaxHz >= 115 -> 120
                deviceMaxHz >= 85 -> 90
                else -> 60
            }
            TargetFpsMode.FPS_120 -> 120
            TargetFpsMode.FPS_90 -> 90
            TargetFpsMode.FPS_60 -> 60
            TargetFpsMode.FPS_30 -> 30
        }
        return desired.coerceAtMost(deviceMaxHz.coerceAtLeast(30))
    }

    fun configurePreference(fpsMode: TargetFpsMode, qualityPref: QualityPreference) {
        targetMode = fpsMode
        qualityPreference = qualityPref
        when (qualityPref) {
            QualityPreference.ULTRA -> activeTier = QualityTier.ULTRA
            QualityPreference.HIGH -> activeTier = QualityTier.HIGH
            QualityPreference.MEDIUM -> activeTier = QualityTier.MEDIUM
            QualityPreference.LOW -> activeTier = QualityTier.LOW
            QualityPreference.AUTO -> {
                // Keep current auto tier or initialize sensible tier
                if (effectiveTargetFps() >= 90 && activeTier == QualityTier.ULTRA) {
                    activeTier = QualityTier.HIGH
                }
            }
        }
    }

    /**
     * Records a rendered frame delta and dynamically scales graphic quality if in AUTO mode.
     * Returns true if the active QualityTier changed (e.g. to resize GLSurfaceView fixed resolution).
     */
    fun recordFrame(deltaSec: Double): Boolean {
        val clamped = deltaSec.coerceIn(0.001, 0.25)
        frameCount++
        accumulatedTimeSec += clamped

        if (accumulatedTimeSec < 0.5) return false

        val avgFps = (frameCount / accumulatedTimeSec).toFloat()
        lastAvgFrameMs = ((accumulatedTimeSec / frameCount) * 1000.0).toFloat()
        lastReportedFps = avgFps.toInt().coerceAtLeast(1)

        val windowSec = accumulatedTimeSec
        frameCount = 0
        accumulatedTimeSec = 0.0

        if (qualityPreference != QualityPreference.AUTO) return false

        val target = effectiveTargetFps().toFloat()
        var tierChanged = false

        if (avgFps < target * 0.86f) {
            lowPerfDurationSec += windowSec
            highHeadroomDurationSec = 0.0
            if (lowPerfDurationSec >= 1.5) {
                val next = activeTier.stepDown()
                if (next != activeTier) {
                    activeTier = next
                    tierChanged = true
                }
                lowPerfDurationSec = 0.0
            }
        } else if (avgFps >= target * 0.96f) {
            highHeadroomDurationSec += windowSec
            lowPerfDurationSec = 0.0
            if (highHeadroomDurationSec >= 5.0) {
                val next = activeTier.stepUp()
                if (next != activeTier) {
                    activeTier = next
                    tierChanged = true
                }
                highHeadroomDurationSec = 0.0
            }
        } else {
            lowPerfDurationSec = (lowPerfDurationSec - windowSec * 0.5).coerceAtLeast(0.0)
        }

        return tierChanged
    }

    fun snapshot(): PerformanceTelemetry = PerformanceTelemetry(
        currentFps = lastReportedFps,
        targetFps = effectiveTargetFps(),
        activeQuality = activeTier,
        frameTimeMs = lastAvgFrameMs,
        deviceMaxRefreshHz = deviceMaxHz,
    )
}
