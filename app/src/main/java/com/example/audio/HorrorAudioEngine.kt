package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.SoundPool
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class FootstepSurface {
    WOOD,
    DIRT,
    TILE_STONE,
}

enum class VoicePriority(val level: Int) {
    AMBIENT_REACTION(0),
    GRANNY_MUTTER(1),
    DIALOGUE(2),
    CUTSCENE_URGENT(3),
}

/**
 * Hybrid horror audio engine:
 * - Real Ogg Vorbis SFX preloaded in SoundPool (varied surface footsteps, doors, locks, creaks, hits, tension)
 * - Real Brazilian Portuguese voice lines played via non-blocking MediaPlayer with spatial attenuation & music ducking
 * - Real looping environmental ambience layers (night crickets, wind, interior horror drone)
 * - Continuous MODE_STREAM AudioTrack horror ostinato & dynamic heartbeat + pure-Kotlin fallback for JVM unit tests
 */
class HorrorAudioEngine(
    private val context: Context? = null,
) {
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
    private var isReleased = false

    @Volatile
    private var isVoicePlaying = false

    @Volatile
    private var listenerX = 0f

    @Volatile
    private var listenerZ = 0f

    @Volatile
    private var listenerYawRad = PI.toFloat()

    // Anti-repetition indices for natural footstep variation
    private var lastWoodStepIdx = -1
    private var lastDirtStepIdx = -1
    private var lastTileStepIdx = -1
    private var lastGrannyStepIdx = -1
    private var lastDoorCreakIdx = -1
    private var lastWoodCreakIdx = -1
    private var lastBreathIdx = -1
    private val stepRng = Random(1337)

    // Real Android SoundPool & MediaPlayer backing
    private var soundPool: SoundPool? = null
    private val loadedSoundIds = ConcurrentHashMap.newKeySet<Int>()

    private var spStepWood = IntArray(0)
    private var spStepDirt = IntArray(0)
    private var spStepTile = IntArray(0)
    private var spGrannyStep = IntArray(0)
    private var spDoorOpen = 0
    private var spDoorClose = 0
    private var spDoorSlam = 0
    private var spDoorCreak = IntArray(0)
    private var spDoorknobRattle = 0
    private var spLockRattle = 0
    private var spUnlockDoor = 0
    private var spWoodCreak = IntArray(0)
    private var spWindowWind = 0
    private var spDistantNoise = 0
    private var spDistantMetal = 0
    private var spHorrorSting = 0
    private var spAmbushSurprise = 0
    private var spBodyThud = 0
    private var spBodyDrag = 0
    private var spScaredBreath = IntArray(0)
    private var spPickupKey = 0
    private var spPickupItem = 0
    private var spPaperNote = 0
    private var spFlashlightClick = 0
    private var spWireCut = 0

    private val voiceLock = Any()
    private val voiceGeneration = AtomicInteger(0)
    private var activeVoicePlayer: MediaPlayer? = null

    @Volatile
    private var activeVoiceCueId: String? = null

    @Volatile
    private var activeVoicePriority: VoicePriority = VoicePriority.AMBIENT_REACTION

    private val ambientPlayersLock = Any()
    private var ambientInitStarted = false
    private var ambCricketsPlayer: MediaPlayer? = null
    private var ambWindPlayer: MediaPlayer? = null
    private var ambHousePlayer: MediaPlayer? = null

    // Mixer synthesizer phases (continuous horror score + heartbeat + unit-test mixer)
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

    // Software-mixed one-shot SFX & fallback cues
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

    init {
        initSoundPoolIfAvailable()
    }

    private fun initSoundPoolIfAvailable() {
        val ctx = context?.applicationContext ?: context ?: return
        try {
            val sp = SoundPool.Builder()
                .setMaxStreams(16)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .build()

            sp.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    loadedSoundIds.add(sampleId)
                }
            }

            fun load(resId: Int): Int = try {
                sp.load(ctx, resId, 1)
            } catch (_: Throwable) {
                0
            }

            spStepWood = intArrayOf(
                load(R.raw.sfx_step_wood_1),
                load(R.raw.sfx_step_wood_2),
                load(R.raw.sfx_step_wood_3),
                load(R.raw.sfx_step_wood_4),
            )
            spStepDirt = intArrayOf(
                load(R.raw.sfx_step_dirt_1),
                load(R.raw.sfx_step_dirt_2),
                load(R.raw.sfx_step_dirt_3),
                load(R.raw.sfx_step_dirt_4),
            )
            spStepTile = intArrayOf(
                load(R.raw.sfx_step_tile_1),
                load(R.raw.sfx_step_tile_2),
                load(R.raw.sfx_step_tile_3),
                load(R.raw.sfx_step_tile_4),
            )
            spGrannyStep = intArrayOf(
                load(R.raw.sfx_granny_step_1),
                load(R.raw.sfx_granny_step_2),
                load(R.raw.sfx_granny_step_3),
            )
            spDoorOpen = load(R.raw.sfx_door_open)
            spDoorClose = load(R.raw.sfx_door_close)
            spDoorSlam = load(R.raw.sfx_door_slam)
            spDoorCreak = intArrayOf(
                load(R.raw.sfx_door_creak_1),
                load(R.raw.sfx_door_creak_2),
            )
            spDoorknobRattle = load(R.raw.sfx_doorknob_rattle)
            spLockRattle = load(R.raw.sfx_lock_rattle)
            spUnlockDoor = load(R.raw.sfx_unlock_door)
            spWoodCreak = intArrayOf(
                load(R.raw.sfx_wood_creak_1),
                load(R.raw.sfx_wood_creak_2),
            )
            spWindowWind = load(R.raw.sfx_window_wind)
            spDistantNoise = load(R.raw.sfx_distant_noise)
            spDistantMetal = load(R.raw.sfx_distant_metal)
            spHorrorSting = load(R.raw.sfx_horror_sting)
            spAmbushSurprise = load(R.raw.sfx_ambush_surprise)
            spBodyThud = load(R.raw.sfx_body_thud)
            spBodyDrag = load(R.raw.sfx_body_drag)
            spScaredBreath = intArrayOf(
                load(R.raw.sfx_scared_breath_1),
                load(R.raw.sfx_scared_breath_2),
            )
            spPickupKey = load(R.raw.sfx_pickup_key)
            spPickupItem = load(R.raw.sfx_pickup_item)
            spPaperNote = load(R.raw.sfx_paper_note)
            spFlashlightClick = load(R.raw.sfx_flashlight_click)
            spWireCut = load(R.raw.sfx_wire_cut)

            soundPool = sp
        } catch (_: Throwable) {
            soundPool = null
        }
    }

    fun updateVolumes(master: Float, ambient: Float, sfx: Float) {
        masterVol = master.coerceIn(0f, 1f)
        ambientVol = ambient.coerceIn(0f, 1f)
        sfxVol = sfx.coerceIn(0f, 1f)
        updateAmbientLoopVolumes()
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
        updateAmbientLoopVolumes()
    }

    fun setMenuMode(inMenu: Boolean) {
        isMenuMode = inMenu
        if (inMenu) {
            heartbeatIntensity = 0f
            musicTension = 0.25f
        }
        updateAmbientLoopVolumes()
    }

    fun setPaused(paused: Boolean) {
        isPaused = paused
        synchronized(ambientPlayersLock) {
            listOf(ambCricketsPlayer, ambWindPlayer, ambHousePlayer).forEach { mp ->
                try {
                    if (paused && mp?.isPlaying == true) {
                        mp.pause()
                    } else if (!paused && !isReleased && mp != null && !mp.isPlaying) {
                        mp.start()
                    }
                } catch (_: Throwable) {
                }
            }
        }
        synchronized(voiceLock) {
            try {
                if (paused && activeVoicePlayer?.isPlaying == true) {
                    activeVoicePlayer?.pause()
                } else if (!paused && !isReleased && activeVoicePlayer != null && !activeVoicePlayer!!.isPlaying) {
                    activeVoicePlayer?.start()
                }
            } catch (_: Throwable) {
            }
        }
        updateAmbientLoopVolumes()
    }

    fun startAmbient() {
        ensureRealAmbientLayersStarted()
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

    private fun ensureRealAmbientLayersStarted() {
        val ctx = context?.applicationContext ?: context ?: return
        synchronized(ambientPlayersLock) {
            if (ambientInitStarted || isReleased) return
            ambientInitStarted = true
        }
        scope.launch(Dispatchers.IO) {
            fun createLooping(resId: Int): MediaPlayer? {
                return try {
                    MediaPlayer.create(ctx, resId)?.apply {
                        isLooping = true
                        setVolume(0f, 0f)
                        if (!isPaused && !isReleased) {
                            start()
                        }
                    }
                } catch (_: Throwable) {
                    null
                }
            }

            val crickets = createLooping(R.raw.amb_night_crickets)
            val wind = createLooping(R.raw.amb_wind_loop)
            val house = createLooping(R.raw.amb_house_horror)

            synchronized(ambientPlayersLock) {
                if (isReleased) {
                    listOfNotNull(crickets, wind, house).forEach {
                        try {
                            it.release()
                        } catch (_: Throwable) {
                        }
                    }
                    return@launch
                }
                ambCricketsPlayer = crickets
                ambWindPlayer = wind
                ambHousePlayer = house
            }
            updateAmbientLoopVolumes()
        }
    }

    private fun updateAmbientLoopVolumes() {
        val duck = if (isVoicePlaying) 0.52f else 1.0f
        val base = masterVol * ambientVol * duck
        val cricketsGain = when {
            isPaused || isReleased -> 0f
            isMenuMode -> base * 0.42f
            isInsideHouse -> base * 0.08f
            else -> base * 0.62f
        }
        val windGain = when {
            isPaused || isReleased -> 0f
            isMenuMode -> base * 0.34f
            isInsideHouse -> base * (0.20f + 0.14f * musicTension)
            else -> base * 0.56f
        }
        val houseGain = when {
            isPaused || isReleased -> 0f
            isMenuMode -> base * 0.28f
            isInsideHouse -> base * (0.36f + 0.34f * musicTension)
            else -> base * 0.14f
        }
        synchronized(ambientPlayersLock) {
            try {
                ambCricketsPlayer?.setVolume(cricketsGain, cricketsGain)
                ambWindPlayer?.setVolume(windGain, windGain)
                ambHousePlayer?.setVolume(houseGain, houseGain)
            } catch (_: Throwable) {
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
        val duck = if (isVoicePlaying) 0.55f else 1.0f
        val musicGain = master * ambientVol * duck
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
        val hasRealAmbient = ambHousePlayer != null || ambCricketsPlayer != null

        for (f in 0 until frameCount) {
            // Stereo band-limited atmospheric wind (scaled down when real OGG ambient loops are active)
            rngState = (rngState * 6364136223846793005L + 1442695040888963407L)
            val noiseL = ((rngState ushr 33).toInt() / 2147483647.0) * 2.0 - 1.0
            rngState = (rngState * 6364136223846793005L + 1442695040888963407L)
            val noiseR = ((rngState ushr 33).toInt() / 2147483647.0) * 2.0 - 1.0

            val windCutoff = if (isInsideHouse) 0.045 else 0.075
            windFilterL += windCutoff * (noiseL - windFilterL)
            windFilterR += windCutoff * (noiseR - windFilterR)

            lfoPhase += 2.0 * PI * 0.16 * dtSample
            val windSweep = 0.55 + 0.45 * sin(lfoPhase)

            // Warm horror pad + speaker-audible mid-bass drone
            val rootFreq = if (isInsideHouse) 146.83 else 110.00 // D3 or A2
            phaseDroneLow += 2.0 * PI * rootFreq * dtSample
            phaseDroneMid += 2.0 * PI * (rootFreq * 1.498) * dtSample
            phasePad1 += 2.0 * PI * (rootFreq * 2.0) * dtSample
            phasePad2 += 2.0 * PI * (rootFreq * 2.378) * dtSample
            phaseString += 2.0 * PI * (rootFreq * 3.0 + sin(lfoPhase * 2.8) * 2.4) * dtSample

            val drone = (0.20 * sin(phaseDroneLow) + 0.15 * sin(phaseDroneMid)) * (if (hasRealAmbient) 0.65 else 1.0)
            val darkPad = (0.14 * sin(phasePad1) + 0.11 * sin(phasePad2)) * (0.72 + 0.28 * sin(lfoPhase * 1.3))
            val bowedString = (0.07 + 0.15 * tension) * sin(phaseString) * (0.65 + 0.35 * sin(lfoPhase * 2.1))

            // Haunting old-piano / music-box ostinato (always active in Menu, Road & House)
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
                isMenuMode -> 0.26
                isInsideHouse -> 0.22 + 0.11 * tension
                else -> 0.20 + 0.10 * tension
            }
            val pianoBody = pianoEnv * (
                0.68 * sin(phasePiano) +
                    0.26 * sin(phasePiano * 2.003) +
                    0.10 * sin(phasePiano * 3.01)
                ) * pianoGain

            val bassFreq = if (isInsideHouse) 146.83 else 110.0
            phaseBass += 2.0 * PI * bassFreq * dtSample
            val bassTime = noteTimer + (if (noteIndex % 2 == 1) notePeriod else 0.0)
            val bassEnv = exp(-bassTime * 1.8)
            val bassTone = bassEnv * (0.6 * sin(phaseBass) + 0.4 * sin(phaseBass * 2.0)) * 0.18

            val panMod = 0.14 * sin(lfoPhase * 1.7 + noteIndex * 0.9)
            val pianoL = pianoBody * (1.0 - panMod)
            val pianoR = pianoBody * (1.0 + panMod)

            // Dynamic lub-dub heartbeat
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

            val windAmt = if (hasRealAmbient) 0.05 else if (isInsideHouse) 0.24 else if (isMenuMode) 0.18 else 0.30
            var mixL = (windFilterL * windSweep * windAmt + drone + darkPad + bowedString * 0.9 + pianoL + bassTone + heart) * musicGain
            var mixR = (windFilterR * windSweep * windAmt + drone + darkPad + bowedString * 1.1 + pianoR + bassTone + heart) * musicGain

            // Mix software fallback one-shot SFX when active
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

        if (activeSounds.isNotEmpty()) {
            activeSounds.removeAll { it.cursor >= it.pcm.size }
        }
    }

    // --- Real OGG + Fallback SFX Triggers ---

    fun playDoorOpen() {
        if (!playSpMono(spDoorOpen, 0.92f, jitterPitch = true)) {
            enqueueMono(doorCreakPcm, 0.85f)
        }
    }

    fun playDoorClose() {
        if (!playSpMono(spDoorClose, 0.92f, jitterPitch = true)) {
            enqueueMono(doorSlamPcm, 0.75f)
        }
    }

    fun playDoorCreak() {
        val idx = nextNonRepeating(spDoorCreak.size, lastDoorCreakIdx).also { lastDoorCreakIdx = it }
        val sid = spDoorCreak.getOrNull(idx) ?: 0
        if (!playSpMono(sid, 0.88f, rate = 0.94f + stepRng.nextFloat() * 0.12f)) {
            enqueueMono(doorCreakPcm, 0.85f)
        }
    }

    fun playSlowDoorCreak(srcX: Float, srcZ: Float) {
        val sid = spDoorCreak.getOrNull(1) ?: spDoorCreak.getOrNull(0) ?: 0
        if (!playSpSpatial(sid, 0.96f, srcX, srcZ, maxDist = 22f, rate = 0.88f)) {
            enqueueSpatial(slowDoorCreakPcm, 0.95f, srcX, srcZ, maxDist = 20f)
        }
    }

    fun playDoorSlam() {
        if (!playSpMono(spDoorSlam, 1.0f, rate = 0.96f)) {
            enqueueMono(doorSlamPcm, 1.0f)
        }
    }

    fun playLockedRattle() {
        if (!playSpMono(spLockRattle, 0.90f, jitterPitch = true)) {
            enqueueMono(lockRattlePcm, 0.85f)
        }
    }

    fun playUnlockDoor() {
        if (!playSpMono(spUnlockDoor, 0.92f, rate = 1.0f)) {
            enqueueMono(pickupPcm, 0.82f)
        }
    }

    fun playDoorknobRattle(srcX: Float, srcZ: Float) {
        if (!playSpSpatial(spDoorknobRattle, 0.96f, srcX, srcZ, maxDist = 20f)) {
            enqueueSpatial(doorknobRattlePcm, 0.95f, srcX, srcZ, maxDist = 18f)
        }
    }

    fun playPickupKey() {
        if (!playSpMono(spPickupKey, 0.92f)) {
            enqueueMono(pickupPcm, 0.90f)
        }
    }

    fun playPickupItem() {
        if (!playSpMono(spPickupItem, 0.92f, jitterPitch = true)) {
            enqueueMono(pickupPcm, 0.85f)
        }
    }

    fun playPaperNote() {
        if (!playSpMono(spPaperNote, 0.90f, jitterPitch = true)) {
            enqueueMono(pickupPcm, 0.72f)
        }
    }

    fun playFlashlightClick() {
        if (!playSpMono(spFlashlightClick, 0.88f, rate = 0.96f + stepRng.nextFloat() * 0.08f)) {
            enqueueMono(clickPcm, 0.82f)
        }
    }

    fun playWireCut() {
        if (!playSpMono(spWireCut, 0.95f)) {
            enqueueMono(clickPcm, 0.90f)
        }
    }

    fun playFootstep(stealth: Boolean, surface: FootstepSurface = FootstepSurface.WOOD) {
        val (arr, last) = when (surface) {
            FootstepSurface.WOOD -> spStepWood to lastWoodStepIdx
            FootstepSurface.DIRT -> spStepDirt to lastDirtStepIdx
            FootstepSurface.TILE_STONE -> spStepTile to lastTileStepIdx
        }
        val idx = nextNonRepeating(arr.size, last)
        when (surface) {
            FootstepSurface.WOOD -> lastWoodStepIdx = idx
            FootstepSurface.DIRT -> lastDirtStepIdx = idx
            FootstepSurface.TILE_STONE -> lastTileStepIdx = idx
        }
        val sid = arr.getOrNull(idx) ?: 0
        val gain = if (stealth) 0.42f else 0.74f
        val rate = if (stealth) 0.88f + stepRng.nextFloat() * 0.08f else 0.94f + stepRng.nextFloat() * 0.13f
        if (!playSpMono(sid, gain, rate = rate)) {
            enqueueMono(if (stealth) stealthStepPcm else footstepPcm, if (stealth) 0.45f else 0.68f)
        }
    }

    fun playSpatialFootstep(
        stealth: Boolean,
        srcX: Float,
        srcZ: Float,
        surface: FootstepSurface = FootstepSurface.WOOD,
        isGranny: Boolean = true,
    ) {
        val arr = if (isGranny && spGrannyStep.isNotEmpty() && surface != FootstepSurface.DIRT) {
            spGrannyStep
        } else {
            when (surface) {
                FootstepSurface.WOOD -> spStepWood
                FootstepSurface.DIRT -> spStepDirt
                FootstepSurface.TILE_STONE -> spStepTile
            }
        }
        val idx = if (isGranny && arr === spGrannyStep) {
            nextNonRepeating(arr.size, lastGrannyStepIdx).also { lastGrannyStepIdx = it }
        } else {
            nextNonRepeating(arr.size, lastWoodStepIdx).also { lastWoodStepIdx = it }
        }
        val sid = arr.getOrNull(idx) ?: 0
        val gain = if (stealth) 0.52f else if (isGranny) 0.92f else 0.80f
        val rate = if (isGranny) 0.86f + stepRng.nextFloat() * 0.10f else 0.94f + stepRng.nextFloat() * 0.12f
        if (!playSpSpatial(sid, gain, srcX, srcZ, maxDist = 20f, rate = rate)) {
            enqueueSpatial(if (stealth) stealthStepPcm else footstepPcm, gain, srcX, srcZ, maxDist = 18f)
        }
    }

    fun playHorrorSting() {
        if (!playSpMono(spHorrorSting, 1.0f)) {
            enqueueMono(stingPcm, 1.0f)
        }
    }

    fun playAmbushSurprise() {
        if (!playSpMono(spAmbushSurprise, 1.0f)) {
            enqueueMono(ambushPcm, 1.0f)
        }
    }

    fun playBodyThud() {
        if (!playSpMono(spBodyThud, 0.98f)) {
            enqueueMono(bodyThudPcm, 0.96f)
        }
    }

    fun playWoodCreak() {
        val idx = nextNonRepeating(spWoodCreak.size, lastWoodCreakIdx).also { lastWoodCreakIdx = it }
        val sid = spWoodCreak.getOrNull(idx) ?: 0
        if (!playSpMono(sid, 0.86f, jitterPitch = true)) {
            enqueueMono(woodCreakPcm, 0.85f)
        }
    }

    fun playSpatialWoodCreak(srcX: Float, srcZ: Float) {
        val idx = nextNonRepeating(spWoodCreak.size, lastWoodCreakIdx).also { lastWoodCreakIdx = it }
        val sid = spWoodCreak.getOrNull(idx) ?: 0
        if (!playSpSpatial(sid, 0.90f, srcX, srcZ, maxDist = 22f, rate = 0.93f + stepRng.nextFloat() * 0.12f)) {
            enqueueSpatial(woodCreakPcm, 0.88f, srcX, srcZ, maxDist = 20f)
        }
    }

    fun playBodyDrag() {
        if (!playSpMono(spBodyDrag, 0.88f, rate = 0.90f + stepRng.nextFloat() * 0.14f)) {
            enqueueMono(bodyDragPcm, 0.80f)
        }
    }

    fun playDistantNoise() {
        val sid = if (stepRng.nextBoolean() && spDistantMetal != 0) spDistantMetal else spDistantNoise
        if (!playSpMono(sid, 0.78f, rate = 0.92f + stepRng.nextFloat() * 0.12f)) {
            enqueueMono(distantNoisePcm, 0.75f)
        }
    }

    fun playWindowWind(srcX: Float, srcZ: Float) {
        if (!playSpSpatial(spWindowWind, 0.82f, srcX, srcZ, maxDist = 24f)) {
            enqueueSpatial(windowWindPcm, 0.78f, srcX, srcZ, maxDist = 22f)
        }
    }

    fun playScaredBreath() {
        val idx = nextNonRepeating(spScaredBreath.size, lastBreathIdx).also { lastBreathIdx = it }
        val sid = spScaredBreath.getOrNull(idx) ?: 0
        if (!playSpMono(sid, 0.84f, rate = 0.96f + stepRng.nextFloat() * 0.08f)) {
            enqueueMono(youthCuePcm, 0.55f)
        }
    }

    fun playDialogueCue(isGranny: Boolean) = enqueueMono(if (isGranny) grannyCuePcm else youthCuePcm, 0.72f)

    fun playProtagonistVoice(lineIndex: Int) {
        val cueId = if (lineIndex == 0) "jovem_wake_1" else "jovem_wake_2"
        if (!playVoiceCue(cueId, isGranny = false, spatial = false)) {
            val buf = if (lineIndex == 0) protagonistWakeVoice0 else protagonistWakeVoice1
            enqueueMono(buf, 0.95f)
        }
    }

    fun playGrannyVoice(lineIndex: Int, srcX: Float, srcZ: Float) {
        val cueId = when (lineIndex) {
            0 -> "vovo_wake_1"
            1 -> "vovo_wake_2"
            else -> "vovo_search_room"
        }
        if (!playVoiceCue(cueId, isGranny = true, srcX = srcX, srcZ = srcZ, spatial = true)) {
            val buf = when (lineIndex) {
                0 -> grannyRoomVoice0
                1 -> grannyRoomVoice1
                else -> grannySearchMutter
            }
            enqueueSpatial(buf, 1.0f, srcX, srcZ, maxDist = 24f)
        }
    }

    /**
     * Plays a real Brazilian Portuguese voice file from res/raw asynchronously on IO dispatcher
     * with priority-based anti-overlap gating, spatial stereo attenuation & ambient music ducking.
     * Returns true if a real voice resource was scheduled or cleanly suppressed due to priority/distance,
     * false only if no Android resource/context exists (allowing headless synth fallback).
     */
    fun playVoiceCue(
        cueId: String?,
        isGranny: Boolean,
        srcX: Float = listenerX,
        srcZ: Float = listenerZ,
        spatial: Boolean = false,
        priority: VoicePriority = defaultPriorityFor(cueId),
    ): Boolean {
        val resId = cueId?.let { resolveVoiceResId(it) }
        val ctx = context?.applicationContext ?: context
        if (ctx == null || resId == null || isReleased) {
            return false
        }

        val (leftGain, rightGain) = computeVoiceStereoGains(srcX, srcZ, spatial)
        if (leftGain <= 0.01f && rightGain <= 0.01f) return true

        val gen: Int
        synchronized(voiceLock) {
            if (isVoicePlaying) {
                // Prevent lower-priority lines from interrupting active dialogues/cutscenes,
                // and prevent ambient mutters/reactions from overlapping one another.
                if (priority.level < activeVoicePriority.level ||
                    (priority.level <= VoicePriority.GRANNY_MUTTER.level && priority.level <= activeVoicePriority.level)
                ) {
                    return true
                }
            }
            gen = voiceGeneration.incrementAndGet()
            isVoicePlaying = true
            activeVoiceCueId = cueId
            activeVoicePriority = priority
        }
        updateAmbientLoopVolumes()

        scope.launch(Dispatchers.IO) {
            var created: MediaPlayer? = null
            try {
                if (isReleased || voiceGeneration.get() != gen) return@launch
                created = MediaPlayer.create(ctx, resId)
                if (created == null || isReleased || voiceGeneration.get() != gen) {
                    created?.release()
                    synchronized(voiceLock) {
                        if (voiceGeneration.get() == gen && activeVoicePlayer == null) {
                            isVoicePlaying = false
                            activeVoiceCueId = null
                        }
                    }
                    updateAmbientLoopVolumes()
                    return@launch
                }

                created.setVolume(leftGain, rightGain)
                created.setOnCompletionListener { mp ->
                    synchronized(voiceLock) {
                        if (activeVoicePlayer === mp) {
                            activeVoicePlayer = null
                            isVoicePlaying = false
                            activeVoiceCueId = null
                        }
                    }
                    try {
                        mp.release()
                    } catch (_: Throwable) {
                    }
                    updateAmbientLoopVolumes()
                }
                created.setOnErrorListener { mp, _, _ ->
                    synchronized(voiceLock) {
                        if (activeVoicePlayer === mp) {
                            activeVoicePlayer = null
                            isVoicePlaying = false
                            activeVoiceCueId = null
                        }
                    }
                    try {
                        mp.release()
                    } catch (_: Throwable) {
                    }
                    updateAmbientLoopVolumes()
                    true
                }

                synchronized(voiceLock) {
                    if (isReleased || voiceGeneration.get() != gen) {
                        created.release()
                        return@launch
                    }
                    val prev = activeVoicePlayer
                    activeVoicePlayer = created
                    try {
                        prev?.stop()
                    } catch (_: Throwable) {
                    }
                    try {
                        prev?.release()
                    } catch (_: Throwable) {
                    }
                    if (!isPaused) {
                        created.start()
                    }
                }
            } catch (_: Throwable) {
                try {
                    created?.release()
                } catch (_: Throwable) {
                }
                synchronized(voiceLock) {
                    if (voiceGeneration.get() == gen && activeVoicePlayer == null) {
                        isVoicePlaying = false
                        activeVoiceCueId = null
                    }
                }
                updateAmbientLoopVolumes()
            }
        }
        return true
    }

    fun stopVoice() {
        voiceGeneration.incrementAndGet()
        synchronized(voiceLock) {
            try {
                activeVoicePlayer?.stop()
            } catch (_: Throwable) {
            }
            try {
                activeVoicePlayer?.release()
            } catch (_: Throwable) {
            }
            activeVoicePlayer = null
            isVoicePlaying = false
            activeVoiceCueId = null
        }
        updateAmbientLoopVolumes()
    }

    fun isVoiceCurrentlyPlaying(): Boolean = isVoicePlaying

    fun getActiveVoiceCueId(): String? = activeVoiceCueId

    fun isEnginePaused(): Boolean = isPaused

    fun isEngineReleased(): Boolean = isReleased

    internal fun computeVoiceStereoGains(srcX: Float, srcZ: Float, spatial: Boolean): Pair<Float, Float> {
        val voiceGain = (masterVol * sfxVol * 1.0f).coerceIn(0f, 1.0f)
        if (!spatial) return voiceGain to voiceGain

        val dx = srcX - listenerX
        val dz = srcZ - listenerZ
        val dist = sqrt(dx * dx + dz * dz)
        val maxDist = 26.0f
        if (dist >= maxDist) return 0f to 0f

        val fadeOut = 1.0f - ((dist - (maxDist - 6.0f)) / 6.0f).coerceIn(0f, 1f)
        val atten = ((1.0f / (1.0f + 0.07f * dist + 0.008f * dist * dist)) * fadeOut).coerceIn(0f, 1.0f)
        val total = (voiceGain * atten).coerceIn(0f, 1.0f)

        val rightX = -cos(listenerYawRad)
        val rightZ = sin(listenerYawRad)
        val panDot = if (dist > 0.05f) ((dx * rightX + dz * rightZ) / dist).coerceIn(-1f, 1f) else 0f
        val left = (total * (1f - 0.35f * panDot)).coerceIn(0f, 1f)
        val right = (total * (1f + 0.35f * panDot)).coerceIn(0f, 1f)
        return left to right
    }

    private fun defaultPriorityFor(cueId: String?): VoicePriority = when (cueId) {
        "vovo_ambush", "jovem_capture_gasp", "vovo_attack", "jovem_hurt" -> VoicePriority.CUTSCENE_URGENT
        "vovo_patrol_1", "vovo_patrol_2", "vovo_investigate", "vovo_lost" -> VoicePriority.GRANNY_MUTTER
        "jovem_lantern", "jovem_key", "jovem_item", "jovem_note", "jovem_locked", "jovem_hide" -> VoicePriority.AMBIENT_REACTION
        else -> VoicePriority.DIALOGUE
    }

    internal fun resolveVoiceResId(cueId: String): Int? = VOICE_RESOURCE_MAP[cueId]

    private fun nextNonRepeating(size: Int, lastIdx: Int): Int {
        if (size <= 0) return 0
        if (size == 1) return 0
        var candidate = stepRng.nextInt(size)
        if (candidate == lastIdx) {
            candidate = (candidate + 1 + stepRng.nextInt(size - 1)) % size
        }
        return candidate
    }

    private fun playSpMono(
        soundId: Int,
        gain: Float,
        rate: Float = 1.0f,
        jitterPitch: Boolean = false,
    ): Boolean {
        val sp = soundPool ?: return false
        if (soundId == 0 || !loadedSoundIds.contains(soundId)) return false
        val vol = (masterVol * sfxVol * gain).coerceIn(0f, 1.0f)
        if (vol <= 0.01f) return true
        val finalRate = if (jitterPitch) (0.94f + stepRng.nextFloat() * 0.12f) else rate.coerceIn(0.6f, 1.6f)
        return try {
            sp.play(soundId, vol, vol, 1, 0, finalRate) != 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun playSpSpatial(
        soundId: Int,
        gain: Float,
        srcX: Float,
        srcZ: Float,
        maxDist: Float = 20f,
        rate: Float = 1.0f,
    ): Boolean {
        val sp = soundPool ?: return false
        if (soundId == 0 || !loadedSoundIds.contains(soundId)) return false
        val dx = srcX - listenerX
        val dz = srcZ - listenerZ
        val dist = sqrt(dx * dx + dz * dz)
        if (dist >= maxDist) return true

        val atten = (1.0f / (1.0f + 0.14f * dist + 0.025f * dist * dist)).coerceIn(0.10f, 1.0f)
        val totalGain = (masterVol * sfxVol * gain * atten).coerceIn(0f, 1.0f)
        if (totalGain <= 0.01f) return true

        val rightX = -cos(listenerYawRad)
        val rightZ = sin(listenerYawRad)
        val panDot = if (dist > 0.05f) ((dx * rightX + dz * rightZ) / dist).coerceIn(-1f, 1f) else 0f
        val angle = (panDot + 1f) * (PI.toFloat() * 0.25f)
        val leftGain = (cos(angle) * totalGain * 1.15f).coerceIn(0f, 1f)
        val rightGain = (sin(angle) * totalGain * 1.15f).coerceIn(0f, 1f)

        return try {
            sp.play(soundId, leftGain, rightGain, 1, 0, rate.coerceIn(0.6f, 1.6f)) != 0
        } catch (_: Throwable) {
            false
        }
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
        isReleased = true
        ambientJob?.cancel()
        activeSounds.clear()
        try {
            soundPool?.release()
        } catch (_: Throwable) {
        }
        soundPool = null
        synchronized(ambientPlayersLock) {
            listOfNotNull(ambCricketsPlayer, ambWindPlayer, ambHousePlayer).forEach { mp ->
                try {
                    mp.stop()
                } catch (_: Throwable) {
                }
                try {
                    mp.release()
                } catch (_: Throwable) {
                }
            }
            ambCricketsPlayer = null
            ambWindPlayer = null
            ambHousePlayer = null
        }
        voiceGeneration.incrementAndGet()
        synchronized(voiceLock) {
            try {
                activeVoicePlayer?.stop()
            } catch (_: Throwable) {
            }
            try {
                activeVoicePlayer?.release()
            } catch (_: Throwable) {
            }
            activeVoicePlayer = null
            isVoicePlaying = false
            activeVoiceCueId = null
        }
        scope.cancel()
    }

    companion object {
        val VOICE_RESOURCE_MAP: Map<String, Int> = mapOf(
            "vovo_greeting_1" to R.raw.voice_vovo_greeting_1,
            "vovo_greeting_2" to R.raw.voice_vovo_greeting_2,
            "vovo_door_slam" to R.raw.voice_vovo_door_slam,
            "vovo_ambush" to R.raw.voice_vovo_ambush,
            "vovo_wake_1" to R.raw.voice_vovo_wake_1,
            "vovo_wake_2" to R.raw.voice_vovo_wake_2,
            "vovo_search_room" to R.raw.voice_vovo_search_room,
            "vovo_patrol_1" to R.raw.voice_vovo_patrol_1,
            "vovo_patrol_2" to R.raw.voice_vovo_patrol_2,
            "vovo_investigate" to R.raw.voice_vovo_investigate,
            "vovo_found" to R.raw.voice_vovo_found,
            "vovo_lost" to R.raw.voice_vovo_lost,
            "vovo_pursuit" to R.raw.voice_vovo_pursuit,
            "vovo_attack" to R.raw.voice_vovo_attack,
            "jovem_porch" to R.raw.voice_jovem_porch,
            "jovem_wake_1" to R.raw.voice_jovem_wake_1,
            "jovem_wake_2" to R.raw.voice_jovem_wake_2,
            "jovem_capture_gasp" to R.raw.voice_jovem_capture_gasp,
            "jovem_lantern" to R.raw.voice_jovem_lantern,
            "jovem_key" to R.raw.voice_jovem_key,
            "jovem_item" to R.raw.voice_jovem_item,
            "jovem_note" to R.raw.voice_jovem_note,
            "jovem_locked" to R.raw.voice_jovem_locked,
            "jovem_hide" to R.raw.voice_jovem_hide,
            "jovem_hurt" to R.raw.voice_jovem_hurt,
            "jovem_escape" to R.raw.voice_jovem_escape,
        )

        val ALL_RAW_RESOURCES: List<Pair<String, Int>> = listOf(
            "sfx_step_wood_1" to R.raw.sfx_step_wood_1,
            "sfx_step_wood_2" to R.raw.sfx_step_wood_2,
            "sfx_step_wood_3" to R.raw.sfx_step_wood_3,
            "sfx_step_wood_4" to R.raw.sfx_step_wood_4,
            "sfx_step_dirt_1" to R.raw.sfx_step_dirt_1,
            "sfx_step_dirt_2" to R.raw.sfx_step_dirt_2,
            "sfx_step_dirt_3" to R.raw.sfx_step_dirt_3,
            "sfx_step_dirt_4" to R.raw.sfx_step_dirt_4,
            "sfx_step_tile_1" to R.raw.sfx_step_tile_1,
            "sfx_step_tile_2" to R.raw.sfx_step_tile_2,
            "sfx_step_tile_3" to R.raw.sfx_step_tile_3,
            "sfx_step_tile_4" to R.raw.sfx_step_tile_4,
            "sfx_granny_step_1" to R.raw.sfx_granny_step_1,
            "sfx_granny_step_2" to R.raw.sfx_granny_step_2,
            "sfx_granny_step_3" to R.raw.sfx_granny_step_3,
            "sfx_door_open" to R.raw.sfx_door_open,
            "sfx_door_close" to R.raw.sfx_door_close,
            "sfx_door_slam" to R.raw.sfx_door_slam,
            "sfx_door_creak_1" to R.raw.sfx_door_creak_1,
            "sfx_door_creak_2" to R.raw.sfx_door_creak_2,
            "sfx_doorknob_rattle" to R.raw.sfx_doorknob_rattle,
            "sfx_lock_rattle" to R.raw.sfx_lock_rattle,
            "sfx_unlock_door" to R.raw.sfx_unlock_door,
            "sfx_wood_creak_1" to R.raw.sfx_wood_creak_1,
            "sfx_wood_creak_2" to R.raw.sfx_wood_creak_2,
            "amb_night_crickets" to R.raw.amb_night_crickets,
            "amb_wind_loop" to R.raw.amb_wind_loop,
            "amb_house_horror" to R.raw.amb_house_horror,
            "sfx_window_wind" to R.raw.sfx_window_wind,
            "sfx_distant_noise" to R.raw.sfx_distant_noise,
            "sfx_distant_metal" to R.raw.sfx_distant_metal,
            "sfx_horror_sting" to R.raw.sfx_horror_sting,
            "sfx_ambush_surprise" to R.raw.sfx_ambush_surprise,
            "sfx_body_thud" to R.raw.sfx_body_thud,
            "sfx_body_drag" to R.raw.sfx_body_drag,
            "sfx_scared_breath_1" to R.raw.sfx_scared_breath_1,
            "sfx_scared_breath_2" to R.raw.sfx_scared_breath_2,
            "sfx_heartbeat_real" to R.raw.sfx_heartbeat_real,
            "sfx_pickup_key" to R.raw.sfx_pickup_key,
            "sfx_pickup_item" to R.raw.sfx_pickup_item,
            "sfx_paper_note" to R.raw.sfx_paper_note,
            "sfx_flashlight_click" to R.raw.sfx_flashlight_click,
            "sfx_wire_cut" to R.raw.sfx_wire_cut,
        ) + VOICE_RESOURCE_MAP.entries.map { (k, v) -> "voice_$k" to v }
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
            0 -> listOf(0.10 to 0.22, 0.36 to 0.32, 0.86 to 0.24, 1.16 to 0.18, 1.42 to 0.34)
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
