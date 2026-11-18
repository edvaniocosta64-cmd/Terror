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
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Real-time stereo horror music & spatial SFX mixer backed by a single reliable MODE_STREAM
 * AudioTrack (eliminates Android MODE_STATIC underruns and AudioFlinger track limits on budget devices).
 */
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
    private var musicTension = 0.0f // 0..1 suspense music intensity

    @Volatile
    private var isInsideHouse = false

    @Volatile
    private var isMenuMode = true

    @Volatile
    private var isPaused = false

    @Volatile
    private var listenerX = 0f

    @Volatile
    private var listenerZ = 0f

    @Volatile
    private var listenerYawRad = PI.toFloat()

    // Mixer synthesizer phases
    private var phaseDroneLow = 0.0
    private var phaseDroneMid = 0.0
    private var phasePad1 = 0.0
    private var phasePad2 = 0.0
    private var phaseString = 0.0
    private var phasePiano = 0.0
    private var phaseBass = 0.0
    private var windFilterL = 0.0
    private var windFilterR = 0.0
    private var lfoPhase = 0.0
    private var heartTimer = 0.0
    private var noteTimer = 0.0
    private var noteIndex = 0
    private var rngState = 0x5A17C3L

    // Software-mixed one-shot SFX & dialogue voices
    private class ActiveSound(
        val pcm: ShortArray,
        val leftGain: Float,
        val rightGain: Float,
        var cursor: Int = 0,
    )

    private val activeSounds = CopyOnWriteArrayList<ActiveSound>()

    private val doorCreakPcm: ShortArray by lazy { synthDoorCreak(1.0) }
    private val slowDoorCreakPcm: ShortArray by lazy { synthDoorCreak(2.1) }
    private val doorSlamPcm: ShortArray by lazy { synthDoorSlam() }
    private val lockRattlePcm: ShortArray by lazy { synthLockRattle() }
    private val doorknobRattlePcm: ShortArray by lazy { synthDoorknobRattle() }
    private val pickupPcm: ShortArray by lazy { synthPickupChime() }
    private val clickPcm: ShortArray by lazy { synthFlashlightClick() }
    private val footstepPcm: ShortArray by lazy { synthFootstep(false) }
    private val stealthStepPcm: ShortArray by lazy { synthFootstep(true) }
    private val stingPcm: ShortArray by lazy { synthHorrorSting() }
    private val ambushPcm: ShortArray by lazy { synthAmbushSurprise() }
    private val bodyThudPcm: ShortArray by lazy { synthBodyThud() }
    private val woodCreakPcm: ShortArray by lazy { synthWoodCreak() }
    private val bodyDragPcm: ShortArray by lazy { synthBodyDrag() }
    private val distantNoisePcm: ShortArray by lazy { synthDistantNoise() }
    private val windowWindPcm: ShortArray by lazy { synthWindowWind() }
    private val grannyCuePcm: ShortArray by lazy { synthVoiceTone(isGranny = true) }
    private val youthCuePcm: ShortArray by lazy { synthVoiceTone(isGranny = false) }
    private val protagonistWakeVoice0: ShortArray by lazy { synthProtagonistLine(0) }
    private val protagonistWakeVoice1: ShortArray by lazy { synthProtagonistLine(1) }
    private val grannyRoomVoice0: ShortArray by lazy { synthGrannySpeech(0) }
    private val grannyRoomVoice1: ShortArray by lazy { synthGrannySpeech(1) }
    private val grannySearchMutter: ShortArray by lazy { synthGrannySpeech(2) }

    fun updateVolumes(master: Float, ambient: Float, sfx: Float) {
        masterVol = master.coerceIn(0f, 1f)
        ambientVol = ambient.coerceIn(0f, 1f)
        sfxVol = sfx.coerceIn(0f, 1f)
    }

    fun updateListener(x: Float, z: Float, yawRad: Float) {
        listenerX = x
        listenerZ = z
        listenerYawRad = yawRad
    }

    fun updateEnvironment(
        insideHouse: Boolean,
        dangerLevel: Float,
        suspenseLevel: Float = dangerLevel,
    ) {
        isInsideHouse = insideHouse
        heartbeatIntensity = dangerLevel.coerceIn(0f, 1f)
        musicTension = suspenseLevel.coerceIn(0f, 1f)
    }

    fun setMenuMode(inMenu: Boolean) {
        isMenuMode = inMenu
        if (inMenu) {
            heartbeatIntensity = 0f
            musicTension = 0.25f
        }
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
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(4096)

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                track.play()
                var wasPaused = false
                // 512 stereo frames = 1024 shorts (~23ms ultra-responsive SFX + music buffer)
                val stereoChunk = ShortArray(1024)

                while (isActive) {
                    if (isPaused) {
                        if (!wasPaused) {
                            wasPaused = true
                            try {
                                track.pause()
                                track.flush()
                            } catch (_: Throwable) {
                            }
                        }
                        Thread.sleep(50)
                        continue
                    } else if (wasPaused) {
                        wasPaused = false
                        try {
                            track.play()
                        } catch (_: Throwable) {
                        }
                    }

                    mixStereoFrame(stereoChunk)
                    track.write(stereoChunk, 0, stereoChunk.size)
                }
            } catch (_: Throwable) {
                // Gracefully degrade on headless / JVM test environments
            } finally {
                try {
                    track?.pause()
                    track?.flush()
                    track?.stop()
                    track?.release()
                } catch (_: Throwable) {
                }
            }
        }
    }

    /**
     * Synthesizes one block of continuous stereo horror music, ambient wind, heartbeat,
     * and software-mixed spatial SFX/voices into [outStereo] (interleaved L, R shorts).
     */
    fun mixStereoFrame(outStereo: ShortArray) {
        val frameCount = outStereo.size / 2
        val master = masterVol
        val musicGain = master * ambientVol
        val danger = if (isMenuMode) 0f else heartbeatIntensity
        val tension = if (isMenuMode) 0.25f else musicTension
        val bpm = 58.0 + danger * 86.0
        val beatPeriod = 60.0 / bpm
        val notePeriod = when {
            isMenuMode -> 1.50
            isInsideHouse -> 1.38 - tension * 0.45
            else -> 1.72 - tension * 0.35
        }
        val dtSample = 1.0 / sampleRate

        // Speaker-friendly D minor horror motif (D4, F4, E4, C#4, Bb3, A3, G3, A3)
        val houseNotes = doubleArrayOf(293.66, 349.23, 329.63, 277.18, 233.08, 220.00, 196.00, 220.00)
        // Haunting sparse menu/road theme (A3, C4, B3, E4, F4, E4, C4, B3)
        val roadMenuNotes = doubleArrayOf(220.00, 261.63, 246.94, 329.63, 349.23, 329.63, 261.63, 246.94)

        val currentNotes = if (isInsideHouse) houseNotes else roadMenuNotes

        for (f in 0 until frameCount) {
            // Stereo band-limited atmospheric wind (audible on phone speakers!)
            rngState = (rngState * 6364136223846793005L + 1442695040888963407L)
            val noiseL = ((rngState ushr 33).toInt() / 2147483647.0) * 2.0 - 1.0
            rngState = (rngState * 6364136223846793005L + 1442695040888963407L)
            val noiseR = ((rngState ushr 33).toInt() / 2147483647.0) * 2.0 - 1.0

            val windCutoff = if (isInsideHouse) 0.045 else 0.075
            windFilterL += windCutoff * (noiseL - windFilterL)
            windFilterR += windCutoff * (noiseR - windFilterR)

            lfoPhase += 2.0 * PI * 0.16 * dtSample
            val windSweep = 0.55 + 0.45 * sin(lfoPhase)

            // Warm horror pad + speaker-audible mid-bass drone (110 Hz / 146.8 Hz + octave harmonics!)
            val rootFreq = if (isInsideHouse) 146.83 else 110.00 // D3 or A2
            phaseDroneLow += 2.0 * PI * rootFreq * dtSample
            phaseDroneMid += 2.0 * PI * (rootFreq * 1.498) * dtSample // eerie fifth/tritone shimmer
            phasePad1 += 2.0 * PI * (rootFreq * 2.0) * dtSample
            phasePad2 += 2.0 * PI * (rootFreq * 2.378) * dtSample // minor third pad
            phaseString += 2.0 * PI * (rootFreq * 3.0 + sin(lfoPhase * 2.8) * 2.4) * dtSample

            val drone = 0.24 * sin(phaseDroneLow) + 0.18 * sin(phaseDroneMid)
            val darkPad = (0.16 * sin(phasePad1) + 0.13 * sin(phasePad2)) * (0.72 + 0.28 * sin(lfoPhase * 1.3))
            val bowedString = (0.08 + 0.16 * tension) * sin(phaseString) * (0.65 + 0.35 * sin(lfoPhase * 2.1))

            // Haunting old-piano / music-box ostinato (always active in Menu, Road & House!)
            noteTimer += dtSample
            if (noteTimer >= notePeriod) {
                noteTimer -= notePeriod
                noteIndex = (noteIndex + 1) % currentNotes.size
                phasePiano = 0.0
                if (noteIndex % 2 == 0) phaseBass = 0.0
            }
            val freq = currentNotes[noteIndex]
            phasePiano += 2.0 * PI * freq * dtSample
            val pianoEnv = exp(-noteTimer * (if (isInsideHouse) 2.5 else 2.1))
            val pianoGain = when {
                isMenuMode -> 0.28
                isInsideHouse -> 0.24 + 0.12 * tension
                else -> 0.22 + 0.10 * tension
            }
            val pianoBody = pianoEnv * (
                0.68 * sin(phasePiano) +
                    0.26 * sin(phasePiano * 2.003) +
                    0.10 * sin(phasePiano * 3.01)
                ) * pianoGain

            // Deep ominous piano bass hit every 2 notes (at audible 146.8Hz / 110Hz octave mix)
            val bassFreq = if (isInsideHouse) 146.83 else 110.0
            phaseBass += 2.0 * PI * bassFreq * dtSample
            val bassTime = noteTimer + (if (noteIndex % 2 == 1) notePeriod else 0.0)
            val bassEnv = exp(-bassTime * 1.8)
            val bassTone = bassEnv * (0.6 * sin(phaseBass) + 0.4 * sin(phaseBass * 2.0)) * 0.20

            // Subtle stereo width for piano & strings
            val panMod = 0.14 * sin(lfoPhase * 1.7 + noteIndex * 0.9)
            val pianoL = pianoBody * (1.0 - panMod)
            val pianoR = pianoBody * (1.0 + panMod)

            // Dynamic lub-dub heartbeat (speaker-audible 98Hz / 82Hz + harmonic punch)
            heartTimer += dtSample
            if (heartTimer >= beatPeriod) heartTimer -= beatPeriod
            var heart = 0.0
            if (danger > 0.05f) {
                if (heartTimer < 0.11) {
                    val t = heartTimer
                    heart = exp(-t * 32.0) * (sin(2.0 * PI * 96.0 * t) + 0.45 * sin(2.0 * PI * 185.0 * t))
                } else if (heartTimer in 0.15..0.26) {
                    val t = heartTimer - 0.15
                    heart = 0.76 * exp(-t * 36.0) * (sin(2.0 * PI * 84.0 * t) + 0.40 * sin(2.0 * PI * 162.0 * t))
                }
                heart *= danger * 0.55
            }

            val windAmt = if (isInsideHouse) 0.26 else if (isMenuMode) 0.20 else 0.34
            var mixL = (windFilterL * windSweep * windAmt + drone + darkPad + bowedString * 0.9 + pianoL + bassTone + heart) * musicGain
            var mixR = (windFilterR * windSweep * windAmt + drone + darkPad + bowedString * 1.1 + pianoR + bassTone + heart) * musicGain

            // Mix active one-shot SFX & character voices
            if (activeSounds.isNotEmpty()) {
                val it = activeSounds.iterator()
                while (it.hasNext()) {
                    val s = it.next()
                    val c = s.cursor
                    if (c < s.pcm.size) {
                        val sampleNorm = s.pcm[c] / 32768.0
                        mixL += sampleNorm * s.leftGain
                        mixR += sampleNorm * s.rightGain
                        s.cursor = c + 1
                    }
                }
            }

            outStereo[f * 2] = (mixL.coerceIn(-0.98, 0.98) * Short.MAX_VALUE).toInt().toShort()
            outStereo[f * 2 + 1] = (mixR.coerceIn(-0.98, 0.98) * Short.MAX_VALUE).toInt().toShort()
        }

        // Remove finished one-shot sounds cleanly outside tight sample loop
        if (activeSounds.isNotEmpty()) {
            activeSounds.removeAll { it.cursor >= it.pcm.size }
        }
    }

    fun playDoorCreak() = enqueueMono(doorCreakPcm, 0.85f)
    fun playSlowDoorCreak(srcX: Float, srcZ: Float) = enqueueSpatial(slowDoorCreakPcm, 0.95f, srcX, srcZ, maxDist = 20f)
    fun playDoorSlam() = enqueueMono(doorSlamPcm, 1.0f)
    fun playLockedRattle() = enqueueMono(lockRattlePcm, 0.85f)
    fun playDoorknobRattle(srcX: Float, srcZ: Float) = enqueueSpatial(doorknobRattlePcm, 0.95f, srcX, srcZ, maxDist = 18f)
    fun playPickupKey() = enqueueMono(pickupPcm, 0.90f)
    fun playFlashlightClick() = enqueueMono(clickPcm, 0.82f)
    fun playFootstep(stealth: Boolean) = enqueueMono(if (stealth) stealthStepPcm else footstepPcm, if (stealth) 0.45f else 0.68f)
    fun playSpatialFootstep(stealth: Boolean, srcX: Float, srcZ: Float) = enqueueSpatial(
        if (stealth) stealthStepPcm else footstepPcm,
        if (stealth) 0.52f else 0.82f,
        srcX,
        srcZ,
        maxDist = 18f,
    )
    fun playHorrorSting() = enqueueMono(stingPcm, 1.0f)
    fun playAmbushSurprise() = enqueueMono(ambushPcm, 1.0f)
    fun playBodyThud() = enqueueMono(bodyThudPcm, 0.96f)
    fun playWoodCreak() = enqueueMono(woodCreakPcm, 0.85f)
    fun playSpatialWoodCreak(srcX: Float, srcZ: Float) = enqueueSpatial(woodCreakPcm, 0.88f, srcX, srcZ, maxDist = 20f)
    fun playBodyDrag() = enqueueMono(bodyDragPcm, 0.80f)
    fun playDistantNoise() = enqueueMono(distantNoisePcm, 0.75f)
    fun playWindowWind(srcX: Float, srcZ: Float) = enqueueSpatial(windowWindPcm, 0.78f, srcX, srcZ, maxDist = 22f)
    fun playDialogueCue(isGranny: Boolean) = enqueueMono(if (isGranny) grannyCuePcm else youthCuePcm, 0.75f)

    fun playProtagonistVoice(lineIndex: Int) {
        val buf = if (lineIndex == 0) protagonistWakeVoice0 else protagonistWakeVoice1
        enqueueMono(buf, 0.95f)
    }

    fun playGrannyVoice(lineIndex: Int, srcX: Float, srcZ: Float) {
        val buf = when (lineIndex) {
            0 -> grannyRoomVoice0
            1 -> grannyRoomVoice1
            else -> grannySearchMutter
        }
        enqueueSpatial(buf, 1.0f, srcX, srcZ, maxDist = 24f)
    }

    private fun enqueueMono(buffer: ShortArray, gain: Float) {
        val vol = (masterVol * sfxVol * gain).coerceIn(0f, 1.2f)
        if (vol <= 0.01f) return
        if (activeSounds.size >= 14) {
            activeSounds.removeAt(0)
        }
        activeSounds.add(ActiveSound(buffer, vol, vol))
    }

    private fun enqueueSpatial(
        monoBuffer: ShortArray,
        gain: Float,
        srcX: Float,
        srcZ: Float,
        maxDist: Float = 20f,
    ) {
        val dx = srcX - listenerX
        val dz = srcZ - listenerZ
        val dist = sqrt(dx * dx + dz * dz)
        if (dist >= maxDist) return
        val atten = (1.0f / (1.0f + 0.14f * dist + 0.025f * dist * dist)).coerceIn(0.10f, 1.0f)
        val totalGain = (masterVol * sfxVol * gain * atten).coerceIn(0f, 1.2f)
        if (totalGain <= 0.01f) return

        val rightX = -cos(listenerYawRad)
        val rightZ = sin(listenerYawRad)
        val panDot = if (dist > 0.05f) ((dx * rightX + dz * rightZ) / dist).coerceIn(-1f, 1f) else 0f
        val angle = (panDot + 1f) * (PI.toFloat() * 0.25f)
        val leftGain = cos(angle) * totalGain * 1.15f
        val rightGain = sin(angle) * totalGain * 1.15f

        if (activeSounds.size >= 14) {
            activeSounds.removeAt(0)
        }
        activeSounds.add(ActiveSound(monoBuffer, leftGain, rightGain))
    }

    fun release() {
        ambientJob?.cancel()
        activeSounds.clear()
        scope.cancel()
    }

    private fun synthDoorCreak(durationMult: Double = 1.0): ShortArray {
        val count = (sampleRate * 0.55 * durationMult).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = sin(PI * (i.toDouble() / count))
            val freq = 210.0 + 135.0 * sin(t * (10.5 / durationMult)) + 34.0 * sin(t * 27.0)
            val s = (sin(2.0 * PI * freq * t) + 0.55 * sin(2.0 * PI * freq * 2.1 * t)) * env * 0.55
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthDoorSlam(): ShortArray {
        val count = (sampleRate * 0.44).toInt()
        val rng = Random(42)
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val thump = exp(-t * 16.0) * (sin(2.0 * PI * 115.0 * t) + 0.5 * sin(2.0 * PI * 190.0 * t))
            val crack = exp(-t * 28.0) * (rng.nextDouble() * 2.0 - 1.0) * 0.72
            val s = (thump * 0.85 + crack) * 0.88
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthLockRattle(): ShortArray {
        val count = (sampleRate * 0.24).toInt()
        val rng = Random(7)
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val pulse = if ((i / 900) % 2 == 0) 1.0 else 0.25
            val s = exp(-t * 14.0) * pulse * (sin(2.0 * PI * 760.0 * t) * 0.45 + (rng.nextDouble() * 2 - 1) * 0.40)
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthPickupChime(): ShortArray {
        val count = (sampleRate * 0.30).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 9.5)
            val s = env * (0.55 * sin(2.0 * PI * 587.0 * t) + 0.40 * sin(2.0 * PI * 880.0 * t))
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthFlashlightClick(): ShortArray {
        val count = (sampleRate * 0.07).toInt()
        val rng = Random(9)
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val s = exp(-t * 75.0) * (sin(2.0 * PI * 1450.0 * t) * 0.55 + (rng.nextDouble() * 2 - 1) * 0.42)
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthFootstep(stealth: Boolean): ShortArray {
        val count = (sampleRate * 0.13).toInt()
        val rng = Random(13)
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val freq = if (stealth) 145.0 else 185.0
            val thump = exp(-t * 32.0) * (sin(2.0 * PI * freq * t) * 0.60 + 0.25 * sin(2.0 * PI * freq * 1.8 * t))
            val crunch = exp(-t * 40.0) * (rng.nextDouble() * 2 - 1) * 0.38
            val s = (thump + crunch) * 0.88
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthHorrorSting(): ShortArray {
        val count = (sampleRate * 0.85).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 3.4)
            val s = env * (
                0.38 * sin(2.0 * PI * 220.0 * t) +
                    0.38 * sin(2.0 * PI * 233.08 * t) +
                    0.34 * sin(2.0 * PI * 311.13 * t) +
                    0.28 * sin(2.0 * PI * 155.56 * t)
                )
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthAmbushSurprise(): ShortArray {
        val count = (sampleRate * 1.20).toInt()
        val rng = Random(77)
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 2.5)
            val subDrop = exp(-t * 5.0) * sin(2.0 * PI * (145.0 - t * 48.0).coerceAtLeast(85.0) * t)
            val shriek = sin(2.0 * PI * 440.0 * t) * 0.32 + sin(2.0 * PI * 466.16 * t) * 0.32 + sin(2.0 * PI * 622.25 * t) * 0.24
            val burst = exp(-t * 22.0) * (rng.nextDouble() * 2.0 - 1.0) * 0.48
            val s = env * (subDrop * 0.65 + shriek * 0.80) + burst
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthBodyThud(): ShortArray {
        val count = (sampleRate * 0.40).toInt()
        val rng = Random(99)
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val low = exp(-t * 14.0) * sin(2.0 * PI * 115.0 * t)
            val woodRes = exp(-t * 20.0) * sin(2.0 * PI * 220.0 * t) * 0.48
            val softNoise = exp(-t * 26.0) * (rng.nextDouble() * 2.0 - 1.0) * 0.40
            val s = (low * 0.85 + woodRes + softNoise) * 0.90
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthWoodCreak(): ShortArray {
        val count = (sampleRate * 0.80).toInt()
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = sin(PI * (i.toDouble() / count)) * exp(-t * 1.2)
            val freq = 185.0 + 115.0 * sin(t * 7.5) + 38.0 * sin(t * 23.0)
            val s = env * (0.58 * sin(2.0 * PI * freq * t) + 0.40 * sin(2.0 * PI * (freq * 1.85) * t)) * 0.72
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthBodyDrag(): ShortArray {
        val count = (sampleRate * 0.68).toInt()
        val rng = Random(123)
        var lp = 0.0
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = sin(PI * (i.toDouble() / count))
            val n = rng.nextDouble() * 2.0 - 1.0
            lp += 0.12 * (n - lp)
            val scrape = lp * (0.72 + 0.28 * sin(t * 19.0)) + 0.20 * sin(2.0 * PI * 165.0 * t)
            val s = env * scrape * 0.82
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthDistantNoise(): ShortArray {
        val count = (sampleRate * 0.92).toInt()
        val rng = Random(205)
        var lp = 0.0
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 2.8) * (0.45 + 0.55 * sin(PI * (i.toDouble() / count)))
            lp += 0.08 * ((rng.nextDouble() * 2.0 - 1.0) - lp)
            val rumble = sin(2.0 * PI * 124.0 * t) * 0.38 + sin(2.0 * PI * 212.0 * t) * 0.24
            val s = env * (lp * 0.8 + rumble) * 0.72
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthVoiceTone(isGranny: Boolean): ShortArray {
        val count = (sampleRate * 0.22).toInt()
        val baseFreq = if (isGranny) 265.0 else 175.0
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = sin(PI * (i.toDouble() / count)) * exp(-t * 5.0)
            val vib = sin(2.0 * PI * 12.0 * t) * (if (isGranny) 18.0 else 5.0)
            val s = env * (0.65 * sin(2.0 * PI * (baseFreq + vib) * t) + 0.35 * sin(2.0 * PI * (baseFreq * 2.0 + vib) * t)) * 0.65
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthDoorknobRattle(): ShortArray {
        val count = (sampleRate * 0.68).toInt()
        val rng = Random(31)
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val burstA = exp(-((t - 0.12) * 28.0) * ((t - 0.12) * 28.0))
            val burstB = exp(-((t - 0.34) * 26.0) * ((t - 0.34) * 26.0))
            val burstC = exp(-((t - 0.50) * 30.0) * ((t - 0.50) * 30.0))
            val env = burstA + burstB * 0.9 + burstC * 0.75
            val clack = sin(2.0 * PI * 540.0 * t) * 0.45 + sin(2.0 * PI * 1180.0 * t) * 0.28 + (rng.nextDouble() * 2 - 1) * 0.38
            val s = env * clack * 0.85
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthWindowWind(): ShortArray {
        val count = (sampleRate * 1.45).toInt()
        val rng = Random(51)
        var bp = 0.0
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRate
            val env = sin(PI * (i.toDouble() / count))
            val noise = rng.nextDouble() * 2.0 - 1.0
            bp += 0.065 * (noise - bp)
            val howl = sin(2.0 * PI * (310.0 + 95.0 * sin(t * 2.6)) * t) * 0.22
            val s = env * (bp * 0.82 + howl) * 0.78
            (s.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun synthProtagonistLine(lineIndex: Int): ShortArray {
        val syllables = if (lineIndex == 0) {
            listOf(0.08 to 0.26, 0.48 to 0.22, 0.74 to 0.20, 0.98 to 0.26)
        } else {
            listOf(0.08 to 0.18, 0.30 to 0.24, 0.58 to 0.16, 0.78 to 0.28)
        }
        val durationSec = if (lineIndex == 0) 1.38 else 1.18
        val raw = DoubleArray((sampleRate * durationSec).toInt())
        val basePitch = if (lineIndex == 0) 142.0 else 160.0
        for (i in raw.indices) {
            val t = i.toDouble() / sampleRate
            var env = 0.0
            for ((sIdx, pair) in syllables.withIndex()) {
                val (start, len) = pair
                if (t >= start && t <= start + len) {
                    val u = (t - start) / len
                    env = maxOf(env, sin(PI * u) * (1.0 - sIdx * 0.05))
                }
            }
            val pitchMod = basePitch + (if (lineIndex == 0) sin(t * 5.0) * 10.0 else t * 24.0)
            val formant = 0.48 * sin(2.0 * PI * pitchMod * t) +
                0.34 * sin(2.0 * PI * pitchMod * 2.0 * t) +
                0.20 * sin(2.0 * PI * pitchMod * 3.1 * t) +
                0.10 * sin(2.0 * PI * pitchMod * 4.2 * t)
            raw[i] = env * formant * 0.70
        }
        return applyRoomReverb(raw, wetMix = 0.24)
    }

    private fun synthGrannySpeech(lineIndex: Int): ShortArray {
        val syllables = when (lineIndex) {
            0 -> listOf(
                0.10 to 0.22, 0.36 to 0.32, 0.86 to 0.24, 1.16 to 0.18, 1.42 to 0.34,
            )
            1 -> listOf(
                0.08 to 0.22, 0.36 to 0.30, 0.72 to 0.20, 0.96 to 0.32,
                1.48 to 0.22, 1.76 to 0.20, 2.02 to 0.24, 2.30 to 0.32,
            )
            else -> listOf(0.10 to 0.26, 0.52 to 0.30, 0.92 to 0.22)
        }
        val durationSec = when (lineIndex) {
            0 -> 2.25
            1 -> 2.78
            else -> 1.35
        }
        val raw = DoubleArray((sampleRate * durationSec).toInt())
        val rng = Random(400 + lineIndex)
        for (i in raw.indices) {
            val t = i.toDouble() / sampleRate
            var env = 0.0
            for ((sIdx, pair) in syllables.withIndex()) {
                val (start, len) = pair
                if (t >= start && t <= start + len) {
                    val u = (t - start) / len
                    env = maxOf(env, sin(PI * u) * (if (sIdx == syllables.lastIndex) 1.05 else 0.92))
                }
            }
            val vib = sin(2.0 * PI * 9.5 * t) * 14.0
            val f0 = 242.0 - 22.0 * sin(t * 2.1) + vib
            val rasp = (rng.nextDouble() * 2.0 - 1.0) * 0.12
            val timbre = 0.45 * sin(2.0 * PI * f0 * t) +
                0.32 * sin(2.0 * PI * (f0 * 2.02) * t) +
                0.20 * sin(2.0 * PI * (f0 * 3.15) * t) + rasp
            raw[i] = env * timbre * 0.72
        }
        return applyRoomReverb(raw, wetMix = 0.38)
    }

    private fun applyRoomReverb(input: DoubleArray, wetMix: Double): ShortArray {
        val out = input.copyOf()
        val taps = intArrayOf(
            (sampleRate * 0.055).toInt(),
            (sampleRate * 0.115).toInt(),
            (sampleRate * 0.185).toInt(),
            (sampleRate * 0.275).toInt(),
        )
        val decays = doubleArrayOf(0.45, 0.28, 0.16, 0.09)
        for (k in taps.indices) {
            val delay = taps[k]
            val dGain = decays[k] * wetMix
            for (i in delay until out.size) {
                out[i] += input[i - delay] * dGain
            }
        }
        return ShortArray(out.size) { i ->
            (out[i].coerceIn(-0.98, 0.98) * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
