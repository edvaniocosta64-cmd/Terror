package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class HorrorAudioEngine {
    private val sampleRate = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ambientJob: Job? = null

    @Volatile
    private var masterVol = 0.85f

    @Volatile
    private var ambientVol = 0.80f

    @Volatile
    private var sfxVol = 0.90f

    @Volatile
    private var heartbeatIntensity = 0.0f // 0..1 when Granny is close or hurt

    @Volatile
    private var isInsideHouse = false

    @Volatile
    private var isPaused = false

    private val doorCreakPcm: ShortArray by lazy { synthDoorCreak() }
    private val doorSlamPcm: ShortArray by lazy { synthDoorSlam() }
    private val lockRattlePcm: ShortArray by lazy { synthLockRattle() }
    private val pickupPcm: ShortArray by lazy { synthPickupChime() }
    private val clickPcm: ShortArray by lazy { synthFlashlightClick() }
    private val footstepPcm: ShortArray by lazy { synthFootstep(false) }
    private val stealthStepPcm: ShortArray by lazy { synthFootstep(true) }
    private val stingPcm: ShortArray by lazy { synthHorrorSting() }
    private val grannyCuePcm: ShortArray by lazy { synthVoiceTone(isGranny = true) }
    private val youthCuePcm: ShortArray by lazy { synthVoiceTone(isGranny = false) }

    fun updateVolumes(master: Float, ambient: Float, sfx: Float) {
        masterVol = master.coerceIn(0f, 1f)
        ambientVol = ambient.coerceIn(0f, 1f)
        sfxVol = sfx.coerceIn(0f, 1f)
    }

    fun updateEnvironment(insideHouse: Boolean, dangerLevel: Float) {
        isInsideHouse = insideHouse
        heartbeatIntensity = dangerLevel.coerceIn(0f, 1f)
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    fun startAmbient() {
        if (ambientJob?.isActive == true) return
        ambientJob = scope.launch {
            var track: AudioTrack? = null
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(2048)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()

                val chunk = ShortArray(1024)
                var phaseDrone1 = 0.0
                var phaseDrone2 = 0.0
                var windFilter = 0.0
                var lfoPhase = 0.0
                var heartTimer = 0.0
                var rngState = 0x5A17C3L

                while (isActive) {
                    if (isPaused) {
                        Thread.sleep(60)
                        continue
                    }
                    val vol = masterVol * ambientVol
                    val danger = heartbeatIntensity
                    val bpm = 58.0 + danger * 86.0
                    val beatPeriod = 60.0 / bpm
                    val dtSample = 1.0 / sampleRate

                    for (i in chunk.indices) {
                        // Fast LCG white noise for wind
                        rngState = (rngState * 6364136223846793005L + 1442695040888963407L)
                        val noise = ((rngState ushr 33).toInt() / 2147483647.0) * 2.0 - 1.0
                        val windCutoff = if (isInsideHouse) 0.015 else 0.038
                        windFilter += windCutoff * (noise - windFilter)

                        lfoPhase += 2.0 * PI * 0.14 * dtSample
                        val windMod = 0.55 + 0.45 * sin(lfoPhase)

                        // Deep horror drone
                        val droneFreq = if (isInsideHouse) 52.0 else 44.0
                        phaseDrone1 += 2.0 * PI * droneFreq * dtSample
                        phaseDrone2 += 2.0 * PI * (droneFreq * 1.498) * dtSample // tritone/fifth tension
                        val drone = 0.25 * sin(phaseDrone1) + 0.15 * sin(phaseDrone2)

                        // Dynamic lub-dub heartbeat
                        heartTimer += dtSample
                        if (heartTimer >= beatPeriod) heartTimer -= beatPeriod
                        var heart = 0.0
                        if (danger > 0.05f) {
                            if (heartTimer < 0.09) {
                                val t = heartTimer
                                heart = exp(-t * 38.0) * sin(2.0 * PI * 48.0 * t)
                            } else if (heartTimer in 0.14..0.23) {
                                val t = heartTimer - 0.14
                                heart = 0.72 * exp(-t * 42.0) * sin(2.0 * PI * 42.0 * t)
                            }
                            heart *= danger * 1.15
                        }

                        val mixed = (windFilter * windMod * 0.42 + drone * 0.35 + heart) * vol
                        val clamped = (mixed.coerceIn(-0.98, 0.98) * Short.MAX_VALUE).toInt().toShort()
                        chunk[i] = clamped
                    }
                    track.write(chunk, 0, chunk.size)
                }
            } catch (_: Throwable) {
                // Gracefully degrade on headless / test environments
            } finally {
                try {
                    track?.stop()
                    track?.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun playDoorCreak() = playPcmBuffer(doorCreakPcm, 0.75f)
    fun playDoorSlam() = playPcmBuffer(doorSlamPcm, 1.0f)
    fun playLockedRattle() = playPcmBuffer(lockRattlePcm, 0.8f)
    fun playPickupKey() = playPcmBuffer(pickupPcm, 0.85f)
    fun playFlashlightClick() = playPcmBuffer(clickPcm, 0.7f)
    fun playFootstep(stealth: Boolean) = playPcmBuffer(if (stealth) stealthStepPcm else footstepPcm, if (stealth) 0.35f else 0.55f)
    fun playHorrorSting() = playPcmBuffer(stingPcm, 1.0f)
    fun playDialogueCue(isGranny: Boolean) = playPcmBuffer(if (isGranny) grannyCuePcm else youthCuePcm, 0.55f)

    private fun playPcmBuffer(buffer: ShortArray, gain: Float) {
        val vol = (masterVol * sfxVol * gain).coerceIn(0f, 1f)
        if (vol <= 0.01f) return
        scope.launch {
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.setVolume(vol)
                track.play()
                val durationMs = (buffer.size * 1000L) / sampleRate + 60L
                Thread.sleep(durationMs)
            } catch (_: Throwable) {
            } finally {
                try {
                    track?.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun release() {
        ambientJob?.cancel()
        scope.cancel()
    }

    private fun synthDoorCreak(): ShortArray {
        val count = (sampleRate * 0.55).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = sin(PI * (i.toDouble() / count))
            val freq = 190.0 + 130.0 * sin(t * 11.0)
            val s = (sin(2.0 * PI * freq * t) + 0.45 * sin(2.0 * PI * freq * 2.1 * t)) * env * 0.45
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthDoorSlam(): ShortArray {
        val count = (sampleRate * 0.42).toInt()
        val rng = Random(42)
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val thump = exp(-t * 18.0) * sin(2.0 * PI * 68.0 * t)
            val crack = exp(-t * 32.0) * (rng.nextDouble() * 2.0 - 1.0) * 0.65
            val s = (thump * 0.9 + crack) * 0.85
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthLockRattle(): ShortArray {
        val count = (sampleRate * 0.22).toInt()
        val rng = Random(7)
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val pulse = if ((i / 900) % 2 == 0) 1.0 else 0.25
            val s = exp(-t * 14.0) * pulse * (sin(2.0 * PI * 760.0 * t) * 0.4 + (rng.nextDouble() * 2 - 1) * 0.35)
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthPickupChime(): ShortArray {
        val count = (sampleRate * 0.28).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 10.0)
            val s = env * (0.5 * sin(2.0 * PI * 587.0 * t) + 0.35 * sin(2.0 * PI * 880.0 * t))
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthFlashlightClick(): ShortArray {
        val count = (sampleRate * 0.06).toInt()
        val rng = Random(9)
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val s = exp(-t * 85.0) * (sin(2.0 * PI * 1450.0 * t) * 0.5 + (rng.nextDouble() * 2 - 1) * 0.4)
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthFootstep(stealth: Boolean): ShortArray {
        val count = (sampleRate * 0.11).toInt()
        val rng = Random(13)
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val freq = if (stealth) 85.0 else 115.0
            val s = exp(-t * 36.0) * (sin(2.0 * PI * freq * t) * 0.5 + (rng.nextDouble() * 2 - 1) * 0.28)
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthHorrorSting(): ShortArray {
        val count = (sampleRate * 0.75).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 3.8)
            // Dissonant minor second + tritone horror sting
            val s = env * (
                0.35 * sin(2.0 * PI * 220.0 * t) +
                    0.35 * sin(2.0 * PI * 233.08 * t) +
                    0.30 * sin(2.0 * PI * 311.13 * t) +
                    0.25 * sin(2.0 * PI * 92.5 * t)
                )
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthVoiceTone(isGranny: Boolean): ShortArray {
        val count = (sampleRate * 0.16).toInt()
        val baseFreq = if (isGranny) 265.0 else 155.0
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = sin(PI * (i.toDouble() / count)) * exp(-t * 6.0)
            val vib = sin(2.0 * PI * 14.0 * t) * (if (isGranny) 18.0 else 4.0)
            val s = env * sin(2.0 * PI * (baseFreq + vib) * t) * 0.45
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
