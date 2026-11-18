package com.example.engine3d

import com.example.audio.HorrorAudioEngine
import com.example.data.GameSave
import com.example.data.StoryPhase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class DialogueLine(
    val speaker: String,
    val text: String,
    val isGranny: Boolean,
    val expression: GrannyExpression = GrannyExpression.GENTLE_SMILE,
    val durationSec: Float = 4.5f,
)

data class InteractionPrompt(
    val targetId: String,
    val actionLabel: String,
    val subtitleHint: String = "",
    val canExecute: Boolean = true,
)

enum class CaptureStage {
    NONE,
    AMBUSH_TURN,
    COLLAPSE_FALL,
    BLACKOUT_UNCONSCIOUS,
    GRANNY_LOOMING,
    DRAGGING_INSIDE,
    DARK_AFTERMATH,
}

data class CaptureCinematicState(
    val stage: CaptureStage = CaptureStage.NONE,
    val stageTimer: Float = 0f,
    val totalTimer: Float = 0f,
    val blackoutAlpha: Float = 0f,
    val cameraRollRad: Float = 0f,
    val camPosX: Float = 0f,
    val camPosY: Float = WorldMap3D.PLAYER_EYE_STANDING,
    val camPosZ: Float = 8.4f,
    val camLookX: Float = 0f,
    val camLookY: Float = 1.4f,
    val camLookZ: Float = 6.0f,
    val useThirdPersonCamera: Boolean = false,
    val bodyX: Float = 0f,
    val bodyY: Float = 0.145f,
    val bodyZ: Float = 7.6f,
    val bodyYawDeg: Float = 180f,
    val bodyLiePitchDeg: Float = -90f,
    val isDraggingBody: Boolean = false,
    val dragPullPhase: Float = 0f,
    val caption: String = "",
    val interiorLightScale: Float = 1.0f,
)

enum class AwakeningStage {
    NONE,
    WAKE_LYING,
    SIT_UP_AND_LOOK,
    HEAR_GRANNY_VOICE,
    RUN_AND_HIDE_UNDER_BED,
    GRANNY_SEARCH_ROOM,
}

data class AwakeningCinematicState(
    val stage: AwakeningStage = AwakeningStage.NONE,
    val stageTimer: Float = 0f,
    val totalTimer: Float = 0f,
    val eyelidOverlayOpen: Float = 1f,
    val camPosX: Float = 9.0f,
    val camPosY: Float = 1.4f,
    val camPosZ: Float = -5.8f,
    val camLookX: Float = 9.0f,
    val camLookY: Float = 0.8f,
    val camLookZ: Float = -5.8f,
    val useThirdPersonCamera: Boolean = false,
    val hideUnderBedPerspective: Boolean = false,
    val allowPeekLook: Boolean = false,
    val bodyX: Float = 9.05f,
    val bodyY: Float = 0.75f,
    val bodyZ: Float = -5.90f,
    val bodyYawDeg: Float = -90f,
    val bodyLiePitchDeg: Float = -90f,
    val eyeOpenAmount: Float = 0f,
    val protagonistJawOpen: Float = 0f,
    val headYawDeg: Float = 0f,
    val headPitchDeg: Float = 0f,
    val hipPitchDeg: Float? = null,
    val kneeFlexDeg: Float? = null,
    val armReachPitchDeg: Float? = null,
    val elbowFlexDeg: Float = -14f,
    val isCrawling: Boolean = false,
    val walkCycle: Float = 0f,
    val moveSpeedNorm: Float = 0f,
    val caption: String = "",
)

data class GameHudSnapshot(
    val storyPhase: StoryPhase = StoryPhase.INTRO_ROAD,
    val objectiveText: String = "Explore a estrada na escuridão",
    val health: Int = 100,
    val battery: Int = 100,
    val hasFlashlight: Boolean = false,
    val flashlightOn: Boolean = false,
    val isCrouching: Boolean = false,
    val isHiding: Boolean = false,
    val isHidingUnderBed: Boolean = false,
    val isCinematicIntro: Boolean = true,
    val introCaption: String = "",
    val isCaptureCutscene: Boolean = false,
    val captureStage: CaptureStage = CaptureStage.NONE,
    val captureBlackoutAlpha: Float = 0f,
    val captureCaption: String = "",
    val isAwakeningCutscene: Boolean = false,
    val awakeningStage: AwakeningStage = AwakeningStage.NONE,
    val awakeningEyelidOpen: Float = 1f,
    val awakeningCaption: String = "",
    val awakeningPeekEnabled: Boolean = false,
    val activeDialogue: DialogueLine? = null,
    val activeNoteTitle: String? = null,
    val activeNoteBody: String? = null,
    val interactionPrompt: InteractionPrompt? = null,
    val inventory: List<String> = emptyList(),
    val bloodPulseAlpha: Float = 0f,
    val dangerLevel: Float = 0f,
    val frontWireCut: Boolean = false,
    val frontFuseInserted: Boolean = false,
    val frontPadlockOpened: Boolean = false,
    val isCaught: Boolean = false,
    val isEscaped: Boolean = false,
    val playTimeSeconds: Int = 0,
    val statusToast: String? = null,
)

class HorrorGameEngine(
    private val audioEngine: HorrorAudioEngine,
) {
    // Player 3D state
    var playerX: Float = 0.0f
    var playerY: Float = WorldMap3D.PLAYER_EYE_STANDING
    var playerZ: Float = 27.5f
    var playerYawRad: Float = PI.toFloat() // PI faces -Z (toward house!)
    var playerPitchRad: Float = 0.0f

    var health: Int = 100
    var battery: Float = 100f
    var hasFlashlight: Boolean = false
    var flashlightOn: Boolean = false
    var isCrouching: Boolean = false
    var isHiding: Boolean = false
    var storyPhase: StoryPhase = StoryPhase.INTRO_ROAD

    // Touch controls input (-1..1)
    @Volatile var moveInputX: Float = 0f
    @Volatile var moveInputY: Float = 0f

    // World mutable states
    val doors: MutableList<DoorState> = WorldMap3D.doorDefs.map { DoorState(it) }.toMutableList()
    val collectedItems: MutableSet<String> = mutableSetOf()
    val inventoryItems: MutableList<String> = mutableListOf()
    val readNotes: MutableSet<String> = mutableSetOf()
    val granny: GrannyState = GrannyState()

    // Cinematic / Narrative / Feedback timers
    var isCinematicIntro: Boolean = true
    var cinematicTimer: Float = 0f
    var playTimeSecFloat: Float = 0f
    var bloodPulseAlpha: Float = 0f
    var cameraShakeAmount: Float = 0f
    var stepTimer: Float = 0f
    var toastText: String? = null
    var toastTimer: Float = 0f

    // Player full-body & first-person arms animation state
    var playerWalkCycle: Float = 0f
    var playerMoveSpeedNorm: Float = 0f
    var playerBreathPhase: Float = 0f
    var playerBodyYawRad: Float = PI.toFloat()
    var interactAnimTimer: Float = 0f

    var activeNoteTitle: String? = null
    var activeNoteBody: String? = null

    private val dialogueQueue: ArrayDeque<DialogueLine> = ArrayDeque()
    private var currentDialogue: DialogueLine? = null
    private var dialogueTimer: Float = 0f
    private var porchDialogueTriggered: Boolean = false
    private var frontDoorSlammed: Boolean = false

    // Part 2: Capture & Dragging Cutscene state
    var captureState: CaptureCinematicState = CaptureCinematicState()
    private var captureArmed: Boolean = false
    private var captureDelayTimer: Float = 0f
    private var captureStartCamX: Float = 0f
    private var captureStartCamY: Float = WorldMap3D.PLAYER_EYE_STANDING
    private var captureStartCamZ: Float = 8.4f
    private var collapseThudPlayed: Boolean = false
    private var blackoutNoisePlayed: Boolean = false
    private var loomingCreakPlayed: Boolean = false
    private var dragSoundAccum: Float = 0f
    private var dragSoundBeat: Int = 0
    private var aftermathNoisePlayed: Boolean = false

    // Part 3: Awakening, Spatial Granny Voice, Under-Bed Hiding & Room Search
    var awakeningState: AwakeningCinematicState = AwakeningCinematicState()
    var doorKnobTurnDeg: Float = 0f
    var isHidingUnderBed: Boolean = false
    var activeHideSpotId: String? = null
    private var underBedPeekYawOffset: Float = 0f
    private var underBedPeekPitchOffset: Float = 0f
    private var wakeLine1Played: Boolean = false
    private var wakeLine2Played: Boolean = false
    private var wakeWindPlayed: Boolean = false
    private var grannyVoice1Played: Boolean = false
    private var grannyVoice2Played: Boolean = false
    private var hallwayCreakPlayed: Boolean = false
    private var doorknobRattlePlayed: Boolean = false
    private var bedroomDoorCreakPlayed: Boolean = false
    private var grannySearchMutterPlayed: Boolean = false
    private var hideStepAccum: Float = 0f
    private var grannySearchStepAccum: Float = 0f
    private var houseAmbientTimer: Float = 4.5f

    @Volatile var isPaused: Boolean = false
    var isCaught: Boolean = false
    var isEscaped: Boolean = false

    fun resetNewStory() {
        playerX = 0.0f
        playerY = WorldMap3D.PLAYER_EYE_STANDING
        playerZ = 27.5f
        playerYawRad = PI.toFloat() // PI faces -Z (toward house!)
        playerPitchRad = 0.02f
        health = 100
        battery = 100f
        hasFlashlight = false
        flashlightOn = false
        isCrouching = false
        isHiding = false
        isHidingUnderBed = false
        activeHideSpotId = null
        underBedPeekYawOffset = 0f
        underBedPeekPitchOffset = 0f
        storyPhase = StoryPhase.INTRO_ROAD

        doors.clear()
        doors.addAll(WorldMap3D.doorDefs.map { DoorState(it) })
        collectedItems.clear()
        inventoryItems.clear()
        readNotes.clear()

        granny.x = 0.0f
        granny.z = 9.3f
        granny.yawDeg = 0f
        granny.aiState = GrannyAiState.WAITING_AT_DOOR
        granny.expression = GrannyExpression.GENTLE_SMILE
        granny.stateTimer = 0f
        granny.attackCooldown = 0f
        granny.isLoomingCapture = false
        granny.isDraggingCapture = false
        granny.isSearchingRoom = false
        granny.searchBendDeg = 0f
        granny.dragPullPhase = 0f
        granny.dragGripBlend = 0f

        isCinematicIntro = true
        cinematicTimer = 0f
        playTimeSecFloat = 0f
        bloodPulseAlpha = 0f
        cameraShakeAmount = 0f
        dialogueQueue.clear()
        currentDialogue = null
        dialogueTimer = 0f
        porchDialogueTriggered = false
        frontDoorSlammed = false
        captureState = CaptureCinematicState()
        captureArmed = false
        captureDelayTimer = 0f
        collapseThudPlayed = false
        blackoutNoisePlayed = false
        loomingCreakPlayed = false
        dragSoundAccum = 0f
        dragSoundBeat = 0
        aftermathNoisePlayed = false

        awakeningState = AwakeningCinematicState()
        doorKnobTurnDeg = 0f
        wakeLine1Played = false
        wakeLine2Played = false
        wakeWindPlayed = false
        grannyVoice1Played = false
        grannyVoice2Played = false
        hallwayCreakPlayed = false
        doorknobRattlePlayed = false
        bedroomDoorCreakPlayed = false
        grannySearchMutterPlayed = false
        hideStepAccum = 0f
        grannySearchStepAccum = 0f
        houseAmbientTimer = 4.5f

        activeNoteTitle = null
        activeNoteBody = null
        toastText = null
        isCaught = false
        isEscaped = false
        isPaused = false

        playerWalkCycle = 0f
        playerMoveSpeedNorm = 0f
        playerBreathPhase = 0f
        playerBodyYawRad = PI.toFloat()
        interactAnimTimer = 0f
        stepTimer = 0f
    }

    fun restoreFromSave(save: GameSave) {
        resetNewStory()
        isCinematicIntro = false
        storyPhase = save.resolvedPhase
        playerX = save.playerX.toFloat()
        playerY = save.playerY.toFloat().coerceIn(0.8f, 2.0f)
        playerZ = save.playerZ.toFloat()
        playerYawRad = save.playerYaw.toFloat()
        health = save.health.toInt().coerceIn(1, 100)
        battery = save.battery.toFloat().coerceIn(0f, 100f)
        collectedItems.addAll(save.collectedItems)
        inventoryItems.addAll(save.inventoryItems)
        readNotes.addAll(save.readNotes)
        hasFlashlight = inventoryItems.contains("lantern") || collectedItems.contains("lantern")
        flashlightOn = save.flashlightOn && hasFlashlight && battery > 0f
        playTimeSecFloat = save.playTimeSeconds.toFloat()

        val frontDoor = doors.firstOrNull { it.def.id == "front_door" }
        for (door in doors) {
            if (save.unlockedDoors.contains(door.def.id)) {
                door.isUnlocked = true
            }
        }
        if (frontDoor != null) {
            frontDoor.frontWireCut = save.unlockedDoors.contains("front_wire_cut")
            frontDoor.frontFuseInserted = save.unlockedDoors.contains("front_fuse_inserted")
            frontDoor.frontPadlockOpened = save.unlockedDoors.contains("front_padlock_opened")
        }

        if (storyPhase != StoryPhase.INTRO_ROAD && storyPhase != StoryPhase.PORCH_DIALOGUE) {
            porchDialogueTriggered = true
            frontDoorSlammed = true
            val front = doors.firstOrNull { it.def.id == "front_door" }
            if (front != null && !save.unlockedDoors.contains("front_door")) {
                front.isOpen = false
                front.isUnlocked = false
                front.currentAngleDeg = 0f
            }
            granny.x = 5.5f
            granny.z = 5.5f
            granny.aiState = if (storyPhase == StoryPhase.GRANNYS_PURSUIT) {
                GrannyAiState.HUNTING_PLAYER
            } else {
                GrannyAiState.PATROLLING_STRANGE
            }
        }
        showToast("Progresso carregado: ${save.chapterTitle}")
    }

    fun toGameSave(userId: String, slotId: String, slotName: String): GameSave {
        val unlockedIds = doors.filter { it.isUnlocked }.map { it.def.id }.toMutableList()
        val front = doors.firstOrNull { it.def.id == "front_door" }
        if (front?.frontWireCut == true) unlockedIds.add("front_wire_cut")
        if (front?.frontFuseInserted == true) unlockedIds.add("front_fuse_inserted")
        if (front?.frontPadlockOpened == true) unlockedIds.add("front_padlock_opened")
        return GameSave(
            id = slotId,
            userId = userId,
            slotName = slotName,
            chapterTitle = storyPhase.chapterTitle,
            storyPhase = storyPhase.name,
            playerX = playerX.toDouble(),
            playerY = playerY.toDouble(),
            playerZ = playerZ.toDouble(),
            playerYaw = playerYawRad.toDouble(),
            health = health.toLong(),
            battery = battery.toInt().toLong(),
            flashlightOn = flashlightOn,
            unlockedDoors = unlockedIds,
            collectedItems = collectedItems.toList(),
            inventoryItems = inventoryItems.toList(),
            readNotes = readNotes.toList(),
            playTimeSeconds = playTimeSecFloat.toLong(),
        )
    }

    fun skipCinematicIntro() {
        if (!isCinematicIntro) return
        isCinematicIntro = false
        playerX = 0.0f
        playerZ = 18.0f
        playerYawRad = PI.toFloat()
        playerPitchRad = 0.0f
    }

    fun skipCaptureCutscene() {
        if (captureState.stage == CaptureStage.NONE) return
        finishCaptureCutscene()
    }

    fun skipAwakeningCutscene() {
        if (awakeningState.stage == AwakeningStage.NONE) return
        finishAwakeningSequence()
    }

    fun addLookDelta(dx: Float, dy: Float, sensitivity: Float, invertY: Boolean) {
        if (isPaused || isCaught || isEscaped || activeNoteTitle != null) return
        if (isCinematicIntro || captureState.stage != CaptureStage.NONE) return
        val scale = 0.0042f * sensitivity
        val pitchSign = if (invertY) 1f else -1f

        if (awakeningState.stage != AwakeningStage.NONE) {
            if (!awakeningState.allowPeekLook) return
            val spot = WorldMap3D.underBedSpots.firstOrNull()
            val baseYaw = spot?.lookOutYawRad ?: playerYawRad
            underBedPeekYawOffset = (underBedPeekYawOffset - dx * scale).coerceIn(-0.48f, 0.48f)
            underBedPeekPitchOffset = (underBedPeekPitchOffset + dy * scale * pitchSign).coerceIn(-0.18f, 0.28f)
            playerYawRad = baseYaw + underBedPeekYawOffset
            playerPitchRad = 0.04f + underBedPeekPitchOffset
            return
        }

        if (isHiding && isHidingUnderBed) {
            val spot = WorldMap3D.underBedSpots.firstOrNull { it.id == activeHideSpotId }
            val baseYaw = spot?.lookOutYawRad ?: playerYawRad
            underBedPeekYawOffset = (underBedPeekYawOffset - dx * scale).coerceIn(-0.62f, 0.62f)
            playerYawRad = baseYaw + underBedPeekYawOffset
            playerPitchRad = (playerPitchRad + dy * scale * pitchSign).coerceIn(-0.18f, 0.32f)
            return
        }

        playerYawRad -= dx * scale
        playerPitchRad = (playerPitchRad + dy * scale * pitchSign).coerceIn(-1.15f, 1.15f)
    }

    fun toggleFlashlight() {
        if (!hasFlashlight) {
            showToast("Você ainda não tem uma lanterna. Procure na sala!")
            return
        }
        if (battery <= 0.5f && !flashlightOn) {
            showToast("Bateria da lanterna esgotada!")
            return
        }
        flashlightOn = !flashlightOn
        audioEngine.playFlashlightClick()
    }

    fun toggleCrouch() {
        if (isHiding) {
            exitHidingSpot()
            return
        }
        isCrouching = !isCrouching
    }

    private fun exitHidingSpot() {
        if (isHidingUnderBed) {
            val spot = WorldMap3D.underBedSpots.firstOrNull { it.id == activeHideSpotId }
            if (spot != null) {
                playerX = spot.exitX
                playerZ = spot.exitZ
                playerY = WorldMap3D.floorHeightAt(spot.exitX, spot.exitZ) + WorldMap3D.PLAYER_EYE_CROUCH
                playerPitchRad = 0.0f
            }
            isHiding = false
            isHidingUnderBed = false
            activeHideSpotId = null
            underBedPeekYawOffset = 0f
            isCrouching = true
            audioEngine.playWoodCreak()
            showToast("Você saiu debaixo da cama.")
        } else {
            isHiding = false
            showToast("Você saiu do armário.")
        }
    }

    fun advanceDialogue() {
        if (currentDialogue == null) return
        currentDialogue = dialogueQueue.removeFirstOrNull()
        dialogueTimer = 0f
        currentDialogue?.let { audioEngine.playDialogueCue(it.isGranny) }
    }

    fun dismissNote() {
        activeNoteTitle = null
        activeNoteBody = null
    }

    fun interactWithTarget() {
        if (activeNoteTitle != null) {
            dismissNote()
            return
        }
        if (currentDialogue != null && storyPhase == StoryPhase.PORCH_DIALOGUE) {
            advanceDialogue()
            return
        }
        if (isHiding) {
            exitHidingSpot()
            return
        }
        val prompt = findBestInteraction() ?: return

        // Trigger first-person physical arm reach animation!
        interactAnimTimer = 1.0f

        // Check doors first
        val door = doors.firstOrNull { it.def.id == prompt.targetId }
        if (door != null) {
            handleDoorInteraction(door)
            return
        }

        // Check world items/notes/props
        val item = WorldMap3D.itemDefs.firstOrNull { it.id == prompt.targetId } ?: return
        when (item.kind) {
            InteractKind.ITEM -> handleItemPickup(item)
            InteractKind.NOTE -> {
                readNotes.add(item.id)
                activeNoteTitle = item.name
                activeNoteBody = item.noteBody
                audioEngine.playPickupKey()
                if (item.id == "note_study" && storyPhase != StoryPhase.GRANNYS_PURSUIT) {
                    triggerGrannyPursuitReveal()
                }
            }
            InteractKind.HIDE_CLOSET -> {
                isHiding = true
                isHidingUnderBed = false
                flashlightOn = false
                audioEngine.playDoorCreak()
                showToast("Escondido no armário. A Vovó não pode vê-lo aqui.")
            }
            InteractKind.HIDE_BED -> {
                val spot = WorldMap3D.underBedSpots.firstOrNull { it.id == item.id }
                if (spot != null) {
                    isHiding = true
                    isHidingUnderBed = true
                    activeHideSpotId = spot.id
                    flashlightOn = false
                    isCrouching = true
                    underBedPeekYawOffset = 0f
                    playerX = spot.hideX
                    playerY = spot.hideY
                    playerZ = spot.hideZ
                    playerYawRad = spot.lookOutYawRad
                    playerPitchRad = 0.04f
                    audioEngine.playWoodCreak()
                    showToast("Escondido debaixo da cama. Toque em Agachar ou Interagir para sair.")
                }
            }
            InteractKind.FUSE_BOX -> {
                val front = doors.firstOrNull { it.def.id == "front_door" } ?: return
                if (front.frontFuseInserted) {
                    showToast("O fusível já está encaixado e ativo.")
                } else if (inventoryItems.contains("fuse")) {
                    front.frontFuseInserted = true
                    inventoryItems.remove("fuse")
                    audioEngine.playPickupKey()
                    showToast("Fusível encaixado! Trava elétrica liberada.")
                } else {
                    audioEngine.playLockedRattle()
                    showToast("Falta o Fusível Elétrico para ligar a trava.")
                }
            }
            InteractKind.DOOR -> Unit
        }
    }

    private fun handleDoorInteraction(door: DoorState) {
        if (door.def.id == "front_door") {
            if (!frontDoorSlammed) {
                showToast("Entre na casa pela porta aberta.")
                return
            }
            if (!door.frontWireCut) {
                if (inventoryItems.contains("wire_cutters")) {
                    door.frontWireCut = true
                    audioEngine.playFlashlightClick()
                    showToast("Fio do alarme cortado!")
                } else {
                    audioEngine.playLockedRattle()
                    showToast("Um cabo de alarme bloqueia a porta. Precisa de um Alicate!")
                    return
                }
            }
            if (!door.frontFuseInserted) {
                audioEngine.playLockedRattle()
                showToast("Trava elétrica sem energia! Coloque o Fusível na caixa ao lado.")
                return
            }
            if (!door.frontPadlockOpened) {
                if (inventoryItems.contains("master_key")) {
                    door.frontPadlockOpened = true
                    door.isUnlocked = true
                    door.isOpen = true
                    audioEngine.playDoorCreak()
                    showToast("PORTA DESTRANCADA! Corra para fora da casa!")
                } else {
                    audioEngine.playLockedRattle()
                    showToast("Falta a Chave Mestra para abrir o cadeado!")
                    return
                }
            } else {
                door.isOpen = !door.isOpen
                audioEngine.playDoorCreak()
            }
            return
        }

        if (!door.isUnlocked) {
            val req = door.def.requiredItemId
            if (req != null && inventoryItems.contains(req)) {
                door.isUnlocked = true
                door.isOpen = true
                audioEngine.playDoorCreak()
                showToast("${door.def.title} destrancada!")
                if (req == "crowbar") {
                    triggerGrannyPursuitReveal()
                }
            } else {
                audioEngine.playLockedRattle()
                showToast(door.def.lockedHint)
            }
            return
        }

        door.isOpen = !door.isOpen
        audioEngine.playDoorCreak()
    }

    private fun handleItemPickup(item: WorldItemDef) {
        collectedItems.add(item.id)
        audioEngine.playPickupKey()
        when (item.id) {
            "lantern" -> {
                hasFlashlight = true
                flashlightOn = true
                showToast("Você pegou a Lanterna Tática!")
            }
            "medkit" -> {
                health = (health + 45).coerceAtMost(100)
                bloodPulseAlpha = 0f
                showToast("Kit Médico usado! Vida restaurada.")
            }
            "battery" -> {
                battery = (battery + 55f).coerceAtMost(100f)
                showToast("Pilha carregada! Bateria em ${battery.toInt()}%.")
            }
            else -> {
                inventoryItems.add(item.id)
                showToast("Coletado: ${item.name}")
                if (item.id == "master_key") {
                    triggerGrannyPursuitReveal()
                }
            }
        }
    }

    private fun triggerGrannyPursuitReveal() {
        if (storyPhase == StoryPhase.GRANNYS_PURSUIT) return
        storyPhase = StoryPhase.GRANNYS_PURSUIT
        granny.aiState = GrannyAiState.HUNTING_PLAYER
        granny.noiseTargetX = playerX
        granny.noiseTargetZ = playerZ
        audioEngine.playHorrorSting()
        enqueueDialogue(
            DialogueLine(
                speaker = "Vovó",
                text = "Você não devia ter mexido nas minhas coisas, querido...",
                isGranny = true,
                expression = GrannyExpression.MENACING_GRIN,
                durationSec = 4.2f,
            )
        )
    }

    fun step(dtRaw: Float) {
        if (isPaused || isCaught || isEscaped) return
        val dt = dtRaw.coerceIn(0.001f, 0.05f)
        playTimeSecFloat += dt
        playerBreathPhase += dt * (if (isCrouching) 1.5f else 1.85f)
        if (interactAnimTimer > 0f) {
            interactAnimTimer = (interactAnimTimer - dt * 2.2f).coerceAtLeast(0f)
        }

        if (toastTimer > 0f) {
            toastTimer -= dt
            if (toastTimer <= 0f) toastText = null
        }

        if (bloodPulseAlpha > 0f) {
            val floorMin = if (health < 35) 0.25f else 0f
            bloodPulseAlpha = (bloodPulseAlpha - dt * 0.45f).coerceAtLeast(floorMin)
        }
        if (cameraShakeAmount > 0f) {
            cameraShakeAmount = (cameraShakeAmount - dt * 2.5f).coerceAtLeast(0f)
        }

        // Advance dialogue timer
        currentDialogue?.let { cur ->
            dialogueTimer += dt
            if (dialogueTimer >= cur.durationSec) {
                advanceDialogue()
            }
        }

        // 1. Cinematic Intro on the isolated road at night -> Granny appears at the lit doorway
        if (isCinematicIntro) {
            cinematicTimer += dt
            val progress = (cinematicTimer / 7.2f).coerceIn(0f, 1f)
            val eased = progress * progress * (3f - 2f * progress)
            playerX = sin(cinematicTimer * 0.85f) * 0.14f
            playerZ = 27.5f - eased * 9.5f // walks smoothly from Z=27.5 -> Z=18.0
            playerWalkCycle += dt * 5.4f
            playerMoveSpeedNorm = 0.62f
            // Subtle head look scanning dark trees -> streetlamp -> illuminated house porch
            val headPan = sin(progress * Math.PI.toFloat() * 1.5f) * 0.14f
            playerYawRad = PI.toFloat() + headPan
            playerBodyYawRad = PI.toFloat()
            playerY = WorldMap3D.PLAYER_EYE_STANDING + sin(playerWalkCycle * 2f) * 0.028f
            playerPitchRad = 0.05f - eased * 0.03f

            audioEngine.updateListener(playerX, playerZ, playerYawRad)
            audioEngine.updateEnvironment(
                insideHouse = false,
                dangerLevel = 0f,
                suspenseLevel = 0.25f + progress * 0.22f,
            )
            stepTimer += dt
            if (stepTimer >= 0.56f) {
                stepTimer = 0f
                audioEngine.playFootstep(stealth = false)
            }

            // Granny slowly steps forward to the lit threshold during the second half of the intro
            val appearT = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
            granny.z = 8.4f + appearT * 1.15f
            granny.walkCycle += dt * (if (appearT in 0.01f..0.98f) 4.2f else 0f)
            granny.currentMoveSpeedNorm = if (appearT in 0.01f..0.98f) 0.45f else 0f
            granny.update(
                dt = dt,
                playerX = playerX,
                playerZ = playerZ,
                playerHidden = false,
                playerNoiseRadius = 0f,
                flashlightOn = false,
                doors = doors,
                onTriggerDialogueHint = { _, _ -> },
                onAttackPlayer = {},
            )

            if (cinematicTimer >= 7.2f) {
                isCinematicIntro = false
                playerYawRad = PI.toFloat()
                playerBodyYawRad = PI.toFloat()
                playerPitchRad = 0f
                playerMoveSpeedNorm = 0f
                granny.z = 9.55f
            }
            return
        }

        // 1b. Part 2: Cinematic Capture & Dragging Sequence inside the house
        if (captureState.stage != CaptureStage.NONE) {
            stepCaptureCutscene(dt)
            audioEngine.updateListener(captureState.camPosX, captureState.camPosZ, playerYawRad)
            return
        }

        // 1c. Part 3: Awakening in Bedroom -> Scary Distant Voice -> Hiding Under Bed -> Granny Search
        if (awakeningState.stage != AwakeningStage.NONE) {
            stepAwakeningCutscene(dt)
            audioEngine.updateListener(awakeningState.camPosX, awakeningState.camPosZ, playerYawRad)
            return
        }

        audioEngine.updateListener(playerX, playerZ, playerYawRad)

        // 2. Smooth stair elevation + crouch / under-bed eye-height transition & body yaw lag
        val floorY = if (isHidingUnderBed) 0f else WorldMap3D.floorHeightAt(playerX, playerZ)
        val baseEye = when {
            isHiding && isHidingUnderBed -> 0.25f
            isHiding -> 1.35f
            isCrouching -> WorldMap3D.PLAYER_EYE_CROUCH
            else -> WorldMap3D.PLAYER_EYE_STANDING
        }
        val targetEye = floorY + baseEye
        playerY += (targetEye - playerY) * (dt * 10f).coerceAtMost(1f)
        playerBodyYawRad = lerpRad(playerBodyYawRad, playerYawRad, (dt * 8f).coerceAtMost(1f))

        // 3. Player movement (disabled while reading note or hiding inside closet)
        var emittedNoiseRadius = 0f
        var targetSpeedNorm = 0f
        if (!isHiding && activeNoteTitle == null) {
            val mag = sqrt(moveInputX * moveInputX + moveInputY * moveInputY)
            if (mag > 0.05f) {
                val baseSpeed = if (isCrouching) 1.75f else if (mag > 0.78f) 3.85f else 2.65f
                targetSpeedNorm = (baseSpeed / 3.85f).coerceIn(0.25f, 1f)
                playerWalkCycle += dt * baseSpeed * 3.1f

                val forwardX = sin(playerYawRad)
                val forwardZ = cos(playerYawRad)
                val rightX = -cos(playerYawRad)
                val rightZ = sin(playerYawRad)

                val worldDx = (forwardX * moveInputY + rightX * moveInputX) * baseSpeed * dt
                val worldDz = (forwardZ * moveInputY + rightZ * moveInputX) * baseSpeed * dt

                val (nx, nz) = WorldMap3D.resolveMovement(playerX, playerZ, worldDx, worldDz, doors)
                playerX = nx
                playerZ = nz

                // Subtle organic head bob when walking/running
                val headBobY = sin(playerWalkCycle * 2f) * 0.022f * targetSpeedNorm
                playerY += headBobY

                emittedNoiseRadius = if (isCrouching) 1.2f else if (mag > 0.78f) 8.5f else 4.2f
                stepTimer += dt * baseSpeed
                if (stepTimer >= 1.55f) {
                    stepTimer = 0f
                    audioEngine.playFootstep(stealth = isCrouching)
                }
            }
        }
        playerMoveSpeedNorm += (targetSpeedNorm - playerMoveSpeedNorm) * (dt * 10f).coerceAtMost(1f)

        // 4. Flashlight battery drain
        if (flashlightOn) {
            battery = (battery - dt * 1.1f).coerceAtLeast(0f)
            if (battery <= 0f) {
                flashlightOn = false
                audioEngine.playFlashlightClick()
                showToast("A bateria da lanterna acabou!")
            }
        }

        // 5. Animate all swinging 3D doors smoothly
        for (d in doors) {
            val targetAngle = if (d.isOpen) d.def.openDeltaDeg else 0f
            d.currentAngleDeg += (targetAngle - d.currentAngleDeg) * (dt * 7.5f).coerceAtMost(1f)
        }

        // 6. Story Triggers (Porch Dialogue -> Entering House -> Door Slam -> Capture Sequence!)
        if (!porchDialogueTriggered && playerZ <= 13.6f && abs(playerX) < 3.6f) {
            porchDialogueTriggered = true
            storyPhase = StoryPhase.PORCH_DIALOGUE
            granny.aiState = GrannyAiState.GREETING_DIALOGUE
            enqueueDialogue(
                DialogueLine(
                    speaker = "Vovó",
                    text = "Olá, meu jovem... Você está perdido?",
                    isGranny = true,
                    expression = GrannyExpression.GENTLE_SMILE,
                    durationSec = 4.2f,
                )
            )
            enqueueDialogue(
                DialogueLine(
                    speaker = "Jovem",
                    text = "Estou... Não sei onde estou. Pode me ajudar?",
                    isGranny = false,
                    expression = GrannyExpression.GENTLE_SMILE,
                    durationSec = 4.0f,
                )
            )
            enqueueDialogue(
                DialogueLine(
                    speaker = "Vovó",
                    text = "Claro, querido. Entre. Está muito frio lá fora.",
                    isGranny = true,
                    expression = GrannyExpression.GENTLE_SMILE,
                    durationSec = 4.5f,
                )
            )
        }

        if (!frontDoorSlammed && playerZ <= 8.85f && abs(playerX) < 3.0f) {
            frontDoorSlammed = true
            captureArmed = true
            captureDelayTimer = 0f
            storyPhase = StoryPhase.INSIDE_MYSTERY
            val front = doors.firstOrNull { it.def.id == "front_door" }
            if (front != null) {
                front.isOpen = false
                front.isUnlocked = false
            }
            audioEngine.playDoorSlam()
            cameraShakeAmount = 0.35f
            granny.aiState = GrannyAiState.CLOSING_DOOR_MYSTERY
            granny.stateTimer = 0f
            granny.doorSlamGestureTimer = 1.0f
            enqueueDialogue(
                DialogueLine(
                    speaker = "Vovó",
                    text = "Agora você está seguro aqui dentro... Não tente sair esta noite.",
                    isGranny = true,
                    expression = GrannyExpression.SUSPICIOUS,
                    durationSec = 4.5f,
                )
            )
            showToast("A porta da frente bateu e trancou atrás de você!")
        }

        // Arm Part 2 capture ambush 1.2s after stepping inside the house
        if (captureArmed) {
            captureDelayTimer += dt
            if (captureDelayTimer >= 1.2f && playerZ < 9.5f && !isEscaped && !isCaught) {
                startCaptureCutscene()
                return
            }
        }

        // Check Escape!
        if (frontDoorSlammed && playerZ > 11.2f) {
            storyPhase = StoryPhase.ESCAPED
            isEscaped = true
            audioEngine.playPickupKey()
            return
        }

        // 7. Update Granny 3D AI & Horror Tension
        granny.update(
            dt = dt,
            playerX = playerX,
            playerZ = playerZ,
            playerHidden = isHiding,
            playerNoiseRadius = emittedNoiseRadius,
            flashlightOn = flashlightOn,
            doors = doors,
            onTriggerDialogueHint = { speaker, text ->
                if (currentDialogue == null) {
                    enqueueDialogue(DialogueLine(speaker, text, isGranny = true, expression = granny.expression, durationSec = 3.8f))
                }
            },
            onAttackPlayer = {
                health = (health - 38).coerceAtLeast(0)
                bloodPulseAlpha = 0.92f
                cameraShakeAmount = 0.85f
                audioEngine.playHorrorSting()
                if (health <= 0) {
                    isCaught = true
                } else {
                    showToast("A Vovó atingiu você! Fuja ou se esconda!")
                }
            },
        )

        val distGranny = sqrt((playerX - granny.x) * (playerX - granny.x) + (playerZ - granny.z) * (playerZ - granny.z))
        val danger = if (storyPhase == StoryPhase.INTRO_ROAD || storyPhase == StoryPhase.PORCH_DIALOGUE) {
            0f
        } else {
            ((10f - distGranny) / 8.5f).coerceIn(0f, 1f)
        }
        val suspense = if (captureArmed) 0.55f else danger
        audioEngine.updateEnvironment(insideHouse = playerZ < 10.0f, dangerLevel = danger, suspenseLevel = suspense)

        // Atmospheric positional wood creaks & window draft inside the house
        if (playerZ < 10.0f && !captureArmed) {
            houseAmbientTimer -= dt
            if (houseAmbientTimer <= 0f) {
                houseAmbientTimer = 7.5f + ((playTimeSecFloat * 1.7f) % 5.5f)
                val beat = (playTimeSecFloat.toInt() % 3)
                when (beat) {
                    0 -> audioEngine.playSpatialWoodCreak(granny.x, granny.z)
                    1 -> audioEngine.playWindowWind(11.8f, playerZ.coerceIn(-14f, 6f))
                    else -> audioEngine.playDistantNoise()
                }
            }
        }
    }

    private fun startCaptureCutscene() {
        captureArmed = false
        captureDelayTimer = 0f
        moveInputX = 0f
        moveInputY = 0f
        activeNoteTitle = null
        activeNoteBody = null
        toastText = null
        dialogueQueue.clear()
        currentDialogue = DialogueLine(
            speaker = "Vovó",
            text = "Você não devia ter confiado na velha da estrada, querido...",
            isGranny = true,
            expression = GrannyExpression.MENACING_GRIN,
            durationSec = 3.2f,
        )
        dialogueTimer = 0f

        captureStartCamX = playerX.coerceIn(-1.6f, 1.6f)
        captureStartCamY = playerY
        captureStartCamZ = playerZ.coerceIn(6.6f, 8.6f)

        collapseThudPlayed = false
        blackoutNoisePlayed = false
        loomingCreakPlayed = false
        dragSoundAccum = 0f
        dragSoundBeat = 0
        aftermathNoisePlayed = false

        granny.x = 0.08f
        granny.z = (captureStartCamZ - 1.55f).coerceIn(4.8f, 6.8f)
        granny.yawDeg = 0f
        granny.expression = GrannyExpression.MENACING_GRIN
        granny.isLoomingCapture = true
        granny.isDraggingCapture = false
        granny.dragPullPhase = 0f
        granny.dragGripBlend = 0f
        granny.headTiltDeg = -10f
        granny.headLookYawDeg = 0f
        granny.headLookPitchDeg = -4f
        granny.jawOpenAmount = 0.35f

        captureState = CaptureCinematicState(
            stage = CaptureStage.AMBUSH_TURN,
            stageTimer = 0f,
            totalTimer = 0f,
            blackoutAlpha = 0f,
            camPosX = captureStartCamX,
            camPosY = captureStartCamY,
            camPosZ = captureStartCamZ,
            camLookX = granny.x,
            camLookY = 1.48f,
            camLookZ = granny.z,
            useThirdPersonCamera = false,
            caption = "Um calafrio corta o corredor... A Vovó surge das sombras!",
            interiorLightScale = 0.72f,
        )
        audioEngine.playAmbushSurprise()
        audioEngine.updateEnvironment(insideHouse = true, dangerLevel = 0.85f, suspenseLevel = 0.92f)
    }

    private fun stepCaptureCutscene(dt: Float) {
        val nextStageTimer = captureState.stageTimer + dt
        val nextTotalTimer = captureState.totalTimer + dt
        granny.breathPhase += dt * 3.2f

        when (captureState.stage) {
            CaptureStage.AMBUSH_TURN -> {
                val dur = 2.2f
                val t = (nextStageTimer / dur).coerceIn(0f, 1f)
                val ease = t * t * (3f - 2f * t)

                granny.x = 0.08f * (1f - ease)
                granny.z = ((captureStartCamZ - 1.55f).coerceIn(4.8f, 6.8f)) + ease * 0.45f
                granny.walkCycle += dt * 4.4f
                granny.currentMoveSpeedNorm = 0.42f
                granny.expression = if (t > 0.55f) GrannyExpression.HOSTILE_SHRIEK else GrannyExpression.MENACING_GRIN
                granny.jawOpenAmount = 0.32f + 0.48f * t
                granny.headTiltDeg = -12f * sin(t * PI.toFloat() * 0.7f)
                granny.isLoomingCapture = true

                val camX = captureStartCamX * (1f - ease)
                val camY = captureStartCamY + (1.56f - captureStartCamY) * ease
                val camZ = captureStartCamZ + (7.35f - captureStartCamZ) * ease
                val flicker = 0.42f + 0.32f * abs(sin(nextStageTimer * 24f))

                captureState = captureState.copy(
                    stageTimer = nextStageTimer,
                    totalTimer = nextTotalTimer,
                    camPosX = camX,
                    camPosY = camY,
                    camPosZ = camZ,
                    camLookX = granny.x,
                    camLookY = 1.46f - 0.08f * ease,
                    camLookZ = granny.z,
                    cameraRollRad = sin(nextStageTimer * 16f) * 0.045f * ease,
                    blackoutAlpha = 0f,
                    interiorLightScale = flicker,
                    caption = "Um calafrio corta o corredor... A Vovó surge das sombras!",
                )
                audioEngine.updateEnvironment(insideHouse = true, dangerLevel = 0.85f, suspenseLevel = 0.94f)

                if (nextStageTimer >= dur) {
                    captureState = captureState.copy(stage = CaptureStage.COLLAPSE_FALL, stageTimer = 0f)
                }
            }

            CaptureStage.COLLAPSE_FALL -> {
                val dur = 2.0f
                val t = (nextStageTimer / dur).coerceIn(0f, 1f)
                val fallEase = t * t

                val camX = 0.08f * fallEase
                val camY = 1.56f - 1.32f * fallEase // drops to 0.24f floor level
                val camZ = 7.35f + 0.30f * fallEase
                val lookY = 1.38f - 0.85f * fallEase + 0.45f * (fallEase * fallEase)
                val roll = 0.68f * fallEase // ~39 deg sideways collapse
                val fade = ((t - 0.52f) / 0.48f).coerceIn(0f, 1f)

                if (t >= 0.72f && !collapseThudPlayed) {
                    collapseThudPlayed = true
                    audioEngine.playBodyThud()
                    audioEngine.playWoodCreak()
                }

                granny.currentMoveSpeedNorm = 0f
                granny.expression = GrannyExpression.MENACING_GRIN
                granny.headLookPitchDeg = 16f * fallEase

                captureState = captureState.copy(
                    stageTimer = nextStageTimer,
                    totalTimer = nextTotalTimer,
                    camPosX = camX,
                    camPosY = camY,
                    camPosZ = camZ,
                    camLookX = 0f,
                    camLookY = lookY,
                    camLookZ = granny.z,
                    cameraRollRad = roll,
                    blackoutAlpha = fade,
                    interiorLightScale = 0.44f * (1f - 0.5f * t),
                    caption = "Sua visão escurece... Suas forças se esvaem no chão frio.",
                )
                audioEngine.updateEnvironment(insideHouse = true, dangerLevel = 0.90f, suspenseLevel = 0.96f)

                if (nextStageTimer >= dur) {
                    currentDialogue = null
                    dialogueQueue.clear()
                    granny.x = 0f
                    granny.z = 6.45f
                    granny.yawDeg = 0f
                    granny.isLoomingCapture = true
                    granny.isDraggingCapture = false
                    granny.dragGripBlend = 0f
                    granny.headLookYawDeg = 0f
                    granny.headLookPitchDeg = 22f
                    granny.headTiltDeg = 6f
                    granny.jawOpenAmount = 0.22f
                    captureState = captureState.copy(
                        stage = CaptureStage.BLACKOUT_UNCONSCIOUS,
                        stageTimer = 0f,
                        blackoutAlpha = 1f,
                        cameraRollRad = 0f,
                        useThirdPersonCamera = true,
                        bodyX = 0f,
                        bodyY = 0.145f,
                        bodyZ = 7.6f,
                        bodyYawDeg = 180f,
                        bodyLiePitchDeg = -90f,
                        isDraggingBody = false,
                        caption = "Você perdeu a consciência...",
                    )
                }
            }

            CaptureStage.BLACKOUT_UNCONSCIOUS -> {
                val dur = 1.6f
                if (nextStageTimer >= 0.65f && !blackoutNoisePlayed) {
                    blackoutNoisePlayed = true
                    audioEngine.playDistantNoise()
                }
                captureState = captureState.copy(
                    stageTimer = nextStageTimer,
                    totalTimer = nextTotalTimer,
                    blackoutAlpha = 1f,
                    useThirdPersonCamera = true,
                    interiorLightScale = 0.35f,
                    caption = "Você perdeu a consciência...",
                )
                audioEngine.updateEnvironment(insideHouse = true, dangerLevel = 0.45f, suspenseLevel = 0.90f)

                if (nextStageTimer >= dur) {
                    captureState = captureState.copy(stage = CaptureStage.GRANNY_LOOMING, stageTimer = 0f)
                }
            }

            CaptureStage.GRANNY_LOOMING -> {
                val dur = 3.0f
                val t = (nextStageTimer / dur).coerceIn(0f, 1f)
                val wakeFade = (1f - (t / 0.38f)).coerceIn(0.12f, 1f)
                val gripBlend = ((t - 0.55f) / 0.45f).coerceIn(0f, 1f)

                if (t >= 0.58f && !loomingCreakPlayed) {
                    loomingCreakPlayed = true
                    audioEngine.playWoodCreak()
                    audioEngine.playHorrorSting()
                }

                granny.isLoomingCapture = true
                granny.isDraggingCapture = false
                granny.dragGripBlend = gripBlend
                granny.expression = GrannyExpression.MENACING_GRIN
                granny.headTiltDeg = sin(nextStageTimer * 1.7f) * 10f
                granny.headLookPitchDeg = 24f + gripBlend * 8f

                val camX = 1.55f - 0.20f * t
                val camY = 0.76f + 0.16f * t
                val camZ = 8.20f - 0.45f * t
                val flicker = 0.50f + 0.10f * sin(nextStageTimer * 19f)

                captureState = captureState.copy(
                    stageTimer = nextStageTimer,
                    totalTimer = nextTotalTimer,
                    blackoutAlpha = wakeFade,
                    useThirdPersonCamera = true,
                    camPosX = camX,
                    camPosY = camY,
                    camPosZ = camZ,
                    camLookX = 0f,
                    camLookY = 0.72f - 0.28f * gripBlend,
                    camLookZ = 6.95f,
                    bodyX = 0f,
                    bodyY = 0.145f,
                    bodyZ = 7.6f,
                    isDraggingBody = false,
                    interiorLightScale = flicker,
                    caption = "A Vovó encara você caído... e agarra seus tornozelos.",
                )
                audioEngine.updateEnvironment(insideHouse = true, dangerLevel = 0.78f, suspenseLevel = 0.96f)

                if (nextStageTimer >= dur) {
                    dragSoundAccum = 0.7f
                    dragSoundBeat = 0
                    captureState = captureState.copy(stage = CaptureStage.DRAGGING_INSIDE, stageTimer = 0f)
                }
            }

            CaptureStage.DRAGGING_INSIDE -> {
                val dur = 7.7f
                val t = (nextStageTimer / dur).coerceIn(0f, 1f)
                val eased = t * t * (3f - 2f * t)
                val tugPhase = captureState.dragPullPhase + dt * 4.2f
                val dragPulse = sin(tugPhase) * 0.035f
                val dragZ = (7.6f - 11.4f * eased) + dragPulse // 7.6f -> -3.8f

                granny.isLoomingCapture = false
                granny.isDraggingCapture = true
                granny.dragGripBlend = 1.0f
                granny.dragPullPhase = tugPhase
                granny.x = 0f
                granny.z = dragZ - 1.15f
                granny.yawDeg = 0f
                granny.walkCycle -= dt * 3.6f
                granny.currentMoveSpeedNorm = 0.55f
                granny.expression = GrannyExpression.MENACING_GRIN
                granny.headTiltDeg = sin(nextStageTimer * 2.2f) * 8f
                granny.headLookPitchDeg = 18f

                dragSoundAccum += dt
                if (dragSoundAccum >= 0.95f) {
                    dragSoundAccum = 0f
                    when (dragSoundBeat % 3) {
                        0 -> audioEngine.playBodyDrag()
                        1 -> {
                            audioEngine.playWoodCreak()
                            audioEngine.playFootstep(stealth = false)
                        }
                        else -> {
                            audioEngine.playBodyDrag()
                            audioEngine.playDistantNoise()
                        }
                    }
                    dragSoundBeat++
                }

                // Cinematic camera tracking on East corridor side (clear of walls & clock at X=-2.55)
                val camX = 1.55f + 0.15f * sin(t * PI.toFloat())
                val camY = 0.94f + 0.24f * sin(t * PI.toFloat())
                val camZ = (dragZ + 0.95f).coerceIn(-2.85f, 8.2f)
                val flicker = (0.50f - 0.18f * t) + 0.08f * sin(nextStageTimer * 23f)
                val lateVignette = ((t - 0.85f) / 0.15f).coerceIn(0f, 1f) * 0.18f

                captureState = captureState.copy(
                    stageTimer = nextStageTimer,
                    totalTimer = nextTotalTimer,
                    blackoutAlpha = 0.10f + lateVignette,
                    useThirdPersonCamera = true,
                    camPosX = camX,
                    camPosY = camY,
                    camPosZ = camZ,
                    camLookX = 0f,
                    camLookY = 0.48f,
                    camLookZ = dragZ - 0.35f,
                    bodyX = sin(tugPhase * 0.5f) * 0.035f,
                    bodyY = 0.145f,
                    bodyZ = dragZ,
                    isDraggingBody = true,
                    dragPullPhase = tugPhase,
                    interiorLightScale = flicker,
                    caption = "Ela arrasta você para as profundezas sombrias da casa...",
                )
                audioEngine.updateEnvironment(insideHouse = true, dangerLevel = 0.92f, suspenseLevel = 1.0f)

                if (nextStageTimer >= dur) {
                    captureState = captureState.copy(stage = CaptureStage.DARK_AFTERMATH, stageTimer = 0f)
                }
            }

            CaptureStage.DARK_AFTERMATH -> {
                val dur = 5.0f
                val t = (nextStageTimer / dur).coerceIn(0f, 1f)

                if (nextStageTimer >= 0.4f && !aftermathNoisePlayed) {
                    aftermathNoisePlayed = true
                    audioEngine.playDistantNoise()
                }

                granny.isDraggingCapture = false
                granny.dragGripBlend = (1f - t * 2.5f).coerceAtLeast(0f)
                granny.z = -4.95f - t * 1.15f
                granny.walkCycle -= dt * (if (t < 0.7f) 1.8f else 0f)
                granny.currentMoveSpeedNorm = if (t < 0.7f) 0.30f else 0f
                granny.expression = GrannyExpression.CREEPY_STARE
                granny.headLookPitchDeg = 8f

                val camX = 1.45f - t * 1.15f
                val camY = 1.05f + t * 0.65f
                val camZ = -2.5f + t * 0.85f
                val endFade = ((t - 0.84f) / 0.16f).coerceIn(0f, 1f) * 0.75f
                val flicker = 0.32f + 0.06f * sin(nextStageTimer * 17f)

                captureState = captureState.copy(
                    stageTimer = nextStageTimer,
                    totalTimer = nextTotalTimer,
                    blackoutAlpha = endFade,
                    useThirdPersonCamera = true,
                    camPosX = camX,
                    camPosY = camY,
                    camPosZ = camZ,
                    camLookX = -0.2f * t,
                    camLookY = 0.90f + t * 0.38f,
                    camLookZ = -4.6f - t * 0.8f,
                    bodyX = 0f,
                    bodyY = 0.145f,
                    bodyZ = -3.8f,
                    isDraggingBody = false,
                    interiorLightScale = flicker,
                    caption = "Tudo fica em silêncio na escuridão... Em breve você despertará.",
                )
                audioEngine.updateEnvironment(insideHouse = true, dangerLevel = 0.35f, suspenseLevel = 0.82f)

                if (nextStageTimer >= dur) {
                    finishCaptureCutscene()
                }
            }

            CaptureStage.NONE -> Unit
        }
    }

    private fun finishCaptureCutscene() {
        captureArmed = false
        captureState = CaptureCinematicState(stage = CaptureStage.NONE)
        startAwakeningSequence()
    }

    private fun startAwakeningSequence() {
        currentDialogue = null
        dialogueQueue.clear()
        moveInputX = 0f
        moveInputY = 0f
        toastText = null
        bloodPulseAlpha = 0.25f

        // Close the bedroom door so Granny can rattle the handle & push it open menacingly
        doors.firstOrNull { it.def.id == "door_granny_room" }?.let { bedDoor ->
            bedDoor.isOpen = false
            bedDoor.isUnlocked = true
            bedDoor.currentAngleDeg = 0f
        }
        doorKnobTurnDeg = 0f

        underBedPeekYawOffset = 0f
        underBedPeekPitchOffset = 0f
        wakeLine1Played = false
        wakeLine2Played = false
        wakeWindPlayed = false
        grannyVoice1Played = false
        grannyVoice2Played = false
        hallwayCreakPlayed = false
        doorknobRattlePlayed = false
        bedroomDoorCreakPlayed = false
        grannySearchMutterPlayed = false
        hideStepAccum = 0f
        grannySearchStepAccum = 0f

        granny.x = 0.6f
        granny.z = 2.0f
        granny.yawDeg = 180f
        granny.isLoomingCapture = false
        granny.isDraggingCapture = false
        granny.isSearchingRoom = false
        granny.searchBendDeg = 0f
        granny.dragGripBlend = 0f
        granny.currentMoveSpeedNorm = 0f
        granny.expression = GrannyExpression.CREEPY_STARE

        awakeningState = AwakeningCinematicState(
            stage = AwakeningStage.WAKE_LYING,
            stageTimer = 0f,
            totalTimer = 0f,
            eyelidOverlayOpen = 0f,
            camPosX = 7.45f,
            camPosY = 1.48f,
            camPosZ = -4.25f,
            camLookX = 9.45f,
            camLookY = 0.85f,
            camLookZ = -5.85f,
            useThirdPersonCamera = true,
            hideUnderBedPerspective = false,
            allowPeekLook = false,
            bodyX = 9.10f,
            bodyY = 0.74f,
            bodyZ = -5.90f,
            bodyYawDeg = -90f,
            bodyLiePitchDeg = -90f,
            eyeOpenAmount = 0f,
            protagonistJawOpen = 0f,
            headYawDeg = 0f,
            headPitchDeg = 0f,
            caption = "Aos poucos você recobra os sentidos em um quarto estranho...",
        )
        audioEngine.updateEnvironment(insideHouse = true, dangerLevel = 0.20f, suspenseLevel = 0.68f)
    }

    private fun stepAwakeningCutscene(dt: Float) {
        val nextStageTimer = awakeningState.stageTimer + dt
        val nextTotalTimer = awakeningState.totalTimer + dt
        granny.breathPhase += dt * 2.1f

        when (awakeningState.stage) {
            AwakeningStage.WAKE_LYING -> {
                val dur = 3.8f
                val t = (nextStageTimer / dur).coerceIn(0f, 1f)
                // Gradual drowsy eyelid flutter before clearing
                val eyelid = when {
                    t < 0.35f -> (t / 0.35f) * 0.58f
                    t < 0.48f -> 0.58f - ((t - 0.35f) / 0.13f) * 0.28f
                    else -> (0.30f + ((t - 0.48f) / 0.52f) * 0.70f).coerceIn(0f, 1f)
                }

                if (nextStageTimer >= 0.70f && !wakeWindPlayed) {
                    wakeWindPlayed = true
                    audioEngine.playWindowWind(11.9f, -5.8f)
                }
                if (nextStageTimer >= 1.35f && !wakeLine1Played) {
                    wakeLine1Played = true
                    audioEngine.playProtagonistVoice(0)
                    currentDialogue = DialogueLine(
                        speaker = "Jovem",
                        text = "Ugh... Onde eu estou?",
                        isGranny = false,
                        durationSec = 2.45f,
                    )
                    dialogueTimer = 0f
                }

                val talking = wakeLine1Played && nextStageTimer < 2.95f
                val jaw = if (talking) (0.25f + 0.30f * abs(sin(nextStageTimer * 13f))) else 0f
                val headScan = sin((t - 0.35f).coerceAtLeast(0f) * PI.toFloat() * 1.4f) * 18f

                awakeningState = awakeningState.copy(
                    stageTimer = nextStageTimer,
                    totalTimer = nextTotalTimer,
                    eyelidOverlayOpen = eyelid,
                    camPosX = 7.45f + t * 0.25f,
                    camPosY = 1.48f - t * 0.08f,
                    camPosZ = -4.25f - t * 0.15f,
                    camLookX = 9.45f,
                    camLookY = 0.88f,
                    camLookZ = -5.85f,
                    useThirdPersonCamera = true,
                    hideUnderBedPerspective = false,
                    allowPeekLook = false,
                    bodyX = 9.10f,
                    bodyY = 0.74f,
                    bodyZ = -5.90f,
                    bodyYawDeg = -90f,
                    bodyLiePitchDeg = -90f,
                    eyeOpenAmount = eyelid,
                    protagonistJawOpen = jaw,
                    headYawDeg = headScan,
                    headPitchDeg = -8f * t,
                    armReachPitchDeg = 18f - t * 14f,
                    kneeFlexDeg = 8f + t * 16f,
                    caption = "Aos poucos você recobra os sentidos em um quarto estranho...",
                )
                audioEngine.updateEnvironment(insideHouse = true, dangerLevel = 0.18f, suspenseLevel = 0.65f)

                if (nextStageTimer >= dur) {
                    awakeningState = awakeningState.copy(stage = AwakeningStage.SIT_UP_AND_LOOK, stageTimer = 0f)
                }
            }

            AwakeningStage.SIT_UP_AND_LOOK -> {
                val dur = 4.2f
                val t = (nextStageTimer / dur).coerceIn(0f, 1f)
                val sitEase = (t / 0.42f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

                if (nextStageTimer >= 1.65f && !wakeLine2Played) {
                    wakeLine2Played = true
                    audioEngine.playProtagonistVoice(1)
                    currentDialogue = DialogueLine(
                        speaker = "Jovem",
                        text = "Que lugar é esse?",
                        isGranny = false,
                        durationSec = 2.5f,
                    )
                    dialogueTimer = 0f
                }

                // Smoothly swing torso up (-90 -> 0) and turn legs over South edge of bed (-90 -> 0)
                val liePitch = -90f * (1f - sitEase)
                val bodyYaw = -90f * (1f - sitEase)
                val bodyX = 9.10f - sitEase * 0.25f
                val bodyY = 0.74f + sitEase * 0.02f
                val bodyZ = -5.90f + sitEase * 0.65f
                val hipPitch = -2f - sitEase * 76f
                val kneeFlex = 14f + sitEase * 74f

                val lookPhase = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                val headYaw = sin(lookPhase * PI.toFloat() * 2.1f) * 38f - lookPhase * 12f
                val headPitch = -4f + sin(lookPhase * PI.toFloat()) * 6f
                val jaw = if (wakeLine2Played && nextStageTimer < 3.4f) 0.25f + 0.35f * abs(sin(nextStageTimer * 14f)) else 0f

                awakeningState = awakeningState.copy(
                    stageTimer = nextStageTimer,
                    totalTimer = nextTotalTimer,
                    eyelidOverlayOpen = 1f,
                    camPosX = 6.95f + sin(lookPhase * PI.toFloat()) * 0.35f,
                    camPosY = 1.45f,
                    camPosZ = -3.85f,
                    camLookX = bodyX,
                    camLookY = 1.15f,
                    camLookZ = bodyZ,
                    useThirdPersonCamera = true,
                    hideUnderBedPerspective = false,
                    allowPeekLook = false,
                    bodyX = bodyX,
                    bodyY = bodyY,
                    bodyZ = bodyZ,
                    bodyYawDeg = bodyYaw,
                    bodyLiePitchDeg = liePitch,
                    eyeOpenAmount = 1f,
                    protagonistJawOpen = jaw,
                    headYawDeg = headYaw,
                    headPitchDeg = headPitch,
                    hipPitchDeg = hipPitch,
                    kneeFlexDeg = kneeFlex,
                    armReachPitchDeg = 16f - sitEase * 12f,
                    elbowFlexDeg = -18f,
                    caption = "Você se senta na beira da cama, confuso e assustado.",
                )
                audioEngine.updateEnvironment(insideHouse = true, dangerLevel = 0.24f, suspenseLevel = 0.72f)

                if (nextStageTimer >= dur) {
                    awakeningState = awakeningState.copy(stage = AwakeningStage.HEAR_GRANNY_VOICE, stageTimer = 0f)
                }
            }

            AwakeningStage.HEAR_GRANNY_VOICE -> {
                val dur = 6.4f
                val t = (nextStageTimer / dur).coerceIn(0f, 1f)

                if (nextStageTimer >= 0.30f && !grannyVoice1Played) {
                    grannyVoice1Played = true
                    audioEngine.playGrannyVoice(0, 1.2f, -1.2f)
                    currentDialogue = DialogueLine(
                        speaker = "Vovó",
                        text = "Meu querido... Você já acordou?",
                        isGranny = true,
                        expression = GrannyExpression.CREEPY_STARE,
                        durationSec = 2.75f,
                    )
                    dialogueTimer = 0f
                }
                if (nextStageTimer >= 2.45f && !hallwayCreakPlayed) {
                    hallwayCreakPlayed = true
                    audioEngine.playSpatialWoodCreak(2.0f, -1.5f)
                }
                if (nextStageTimer >= 3.20f && !grannyVoice2Played) {
                    grannyVoice2Played = true
                    audioEngine.playGrannyVoice(1, 1.8f, -1.5f)
                    currentDialogue = DialogueLine(
                        speaker = "Vovó",
                        text = "Não adianta se esconder... Eu sei que você está aqui.",
                        isGranny = true,
                        expression = GrannyExpression.MENACING_GRIN,
                        durationSec = 3.1f,
                    )
                    dialogueTimer = 0f
                }

                // Protagonist snaps his head toward the bedroom doorway (West, X=3.0, Z=-1.5 -> headYaw ~ -62 deg)
                val turnProgress = (nextStageTimer / 0.7f).coerceIn(0f, 1f)
                awakeningState = awakeningState.copy(
                    stageTimer = nextStageTimer,
                    totalTimer = nextTotalTimer,
                    eyelidOverlayOpen = 1f,
                    camPosX = 9.85f,
                    camPosY = 1.46f,
                    camPosZ = -4.20f,
                    camLookX = 5.8f - t * 1.4f,
                    camLookY = 1.22f,
                    camLookZ = -3.4f,
                    useThirdPersonCamera = true,
                    hideUnderBedPerspective = false,
                    allowPeekLook = false,
                    bodyX = 8.85f,
                    bodyY = 0.76f,
                    bodyZ = -5.25f,
                    bodyYawDeg = 0f,
                    bodyLiePitchDeg = 0f,
                    eyeOpenAmount = 1f,
                    protagonistJawOpen = 0.08f,
                    headYawDeg = -64f * turnProgress,
                    headPitchDeg = -2f,
                    hipPitchDeg = -78f,
                    kneeFlexDeg = 88f,
                    armReachPitchDeg = 4f,
                    elbowFlexDeg = -22f,
                    caption = "Uma voz arrepiante ecoa pelo corredor distante...",
                )
                audioEngine.updateEnvironment(insideHouse = true, dangerLevel = 0.48f, suspenseLevel = 0.88f)

                if (nextStageTimer >= dur) {
                    currentDialogue = null
                    dialogueQueue.clear()
                    hideStepAccum = 0.25f
                    awakeningState = awakeningState.copy(stage = AwakeningStage.RUN_AND_HIDE_UNDER_BED, stageTimer = 0f)
                }
            }

            AwakeningStage.RUN_AND_HIDE_UNDER_BED -> {
                val dur = 3.6f
                val t = (nextStageTimer / dur).coerceIn(0f, 1f)

                val bodyX: Float
                val bodyY: Float
                val bodyZ: Float
                val bodyYaw: Float
                val liePitch: Float
                val isCrawling: Boolean
                val hipPitch: Float?
                val kneeFlex: Float?
                val armPitch: Float?
                val walkSpd: Float

                when {
                    t < 0.28f -> {
                        // Phase A: Stand up hurriedly beside bed
                        val u = (t / 0.28f).coerceIn(0f, 1f)
                        bodyX = 8.85f - u * 0.45f
                        bodyY = 0.76f + u * 0.04f
                        bodyZ = -5.25f + u * 0.90f
                        bodyYaw = -95f * u
                        liePitch = u * 8f
                        isCrawling = false
                        hipPitch = -78f * (1f - u)
                        kneeFlex = 88f * (1f - u) + 12f
                        armPitch = null
                        walkSpd = u * 0.6f
                    }
                    t < 0.62f -> {
                        // Phase B: Hurry along bedside & drop into crouch
                        val u = ((t - 0.28f) / 0.34f).coerceIn(0f, 1f)
                        bodyX = 8.40f - u * 0.45f
                        bodyY = 0.80f - u * 0.36f
                        bodyZ = -4.35f - u * 0.65f
                        bodyYaw = -95f + u * 185f // faces +X under the bed!
                        liePitch = 8f + u * 54f
                        isCrawling = u > 0.55f
                        hipPitch = null
                        kneeFlex = null
                        armPitch = null
                        walkSpd = 0.85f
                    }
                    else -> {
                        // Phase C: Crawl underneath the raised bed rail (X: 7.95 -> 8.95)
                        val u = ((t - 0.62f) / 0.38f).coerceIn(0f, 1f)
                        bodyX = 7.95f + u * 1.00f
                        bodyY = 0.44f - u * 0.20f
                        bodyZ = -5.00f - u * 0.85f
                        bodyYaw = 95f
                        liePitch = 62f + u * 22f
                        isCrawling = true
                        hipPitch = 36f
                        kneeFlex = 76f
                        armPitch = -86f
                        walkSpd = 0.5f * (1f - u * 0.6f)
                    }
                }

                hideStepAccum += dt
                if (hideStepAccum >= 0.42f && t < 0.88f) {
                    hideStepAccum = 0f
                    audioEngine.playFootstep(stealth = t > 0.55f)
                }

                // Transition smoothly to Under-Bed POV over final 0.55s
                val camBlend = ((t - 0.78f) / 0.22f).coerceIn(0f, 1f)
                val thirdX = 6.35f + camBlend * (8.95f - 6.35f)
                val thirdY = (1.35f - t * 0.72f) + camBlend * (0.25f - (1.35f - t * 0.72f))
                val thirdZ = -3.45f + camBlend * (-5.85f + 3.45f)

                val spot = WorldMap3D.underBedSpots.first()
                playerYawRad = spot.lookOutYawRad
                playerPitchRad = 0.04f

                awakeningState = awakeningState.copy(
                    stageTimer = nextStageTimer,
                    totalTimer = nextTotalTimer,
                    eyelidOverlayOpen = 1f,
                    camPosX = thirdX,
                    camPosY = thirdY,
                    camPosZ = thirdZ,
                    camLookX = if (camBlend > 0.85f) 8.95f + sin(spot.lookOutYawRad) * 4f else bodyX,
                    camLookY = if (camBlend > 0.85f) 0.32f else bodyY + 0.35f,
                    camLookZ = if (camBlend > 0.85f) -5.85f + cos(spot.lookOutYawRad) * 4f else bodyZ,
                    useThirdPersonCamera = camBlend < 0.92f,
                    hideUnderBedPerspective = camBlend >= 0.92f,
                    allowPeekLook = camBlend >= 0.92f,
                    bodyX = bodyX,
                    bodyY = bodyY,
                    bodyZ = bodyZ,
                    bodyYawDeg = bodyYaw,
                    bodyLiePitchDeg = liePitch,
                    eyeOpenAmount = 1f,
                    protagonistJawOpen = 0f,
                    headYawDeg = 0f,
                    headPitchDeg = -10f,
                    hipPitchDeg = hipPitch,
                    kneeFlexDeg = kneeFlex,
                    armReachPitchDeg = armPitch,
                    isCrawling = isCrawling,
                    walkCycle = awakeningState.walkCycle + dt * 9f * walkSpd,
                    moveSpeedNorm = walkSpd,
                    caption = "Rápido! Você corre e se esconde embaixo da cama...",
                )
                audioEngine.updateEnvironment(insideHouse = true, dangerLevel = 0.66f, suspenseLevel = 0.92f)

                if (nextStageTimer >= dur) {
                    granny.x = 0.5f
                    granny.z = 2.0f
                    granny.yawDeg = 155f
                    granny.isSearchingRoom = true
                    granny.searchBendDeg = 6f
                    grannySearchStepAccum = 0.35f
                    awakeningState = awakeningState.copy(
                        stage = AwakeningStage.GRANNY_SEARCH_ROOM,
                        stageTimer = 0f,
                        useThirdPersonCamera = false,
                        hideUnderBedPerspective = true,
                        allowPeekLook = true,
                    )
                }
            }

            AwakeningStage.GRANNY_SEARCH_ROOM -> {
                val dur = 10.6f
                val t = nextStageTimer

                // 1. Granny waypoints synced to approaching corridor -> knob -> opening door -> room scan -> beside bed -> exit
                val bedDoor = doors.firstOrNull { it.def.id == "door_granny_room" }
                var grannyMoving = false

                when {
                    t < 2.2f -> {
                        // Slow approaching footsteps down corridor
                        val u = (t / 2.2f).coerceIn(0f, 1f)
                        granny.x = 0.5f + u * 1.75f
                        granny.z = 2.0f - u * 3.45f
                        granny.yawDeg = 148f
                        granny.searchBendDeg = 4f
                        grannyMoving = true
                    }
                    t < 3.6f -> {
                        // Stops outside door; doorknob turns & rattles!
                        granny.x = 2.25f
                        granny.z = -1.45f
                        granny.yawDeg = 92f
                        doorKnobTurnDeg = sin((t - 2.2f) * 18f) * 34f
                        if (!doorknobRattlePlayed) {
                            doorknobRattlePlayed = true
                            audioEngine.playDoorknobRattle(3.0f, -1.5f)
                        }
                    }
                    t < 5.4f -> {
                        // Slow creepy door opening & Granny steps across threshold (clear of doorway jam)
                        doorKnobTurnDeg = 0f
                        if (bedDoor != null) {
                            bedDoor.isOpen = true
                            val openTarget = bedDoor.def.openDeltaDeg
                            bedDoor.currentAngleDeg += (openTarget - bedDoor.currentAngleDeg) * (dt * 1.9f).coerceAtMost(1f)
                        }
                        if (!bedroomDoorCreakPlayed) {
                            bedroomDoorCreakPlayed = true
                            audioEngine.playSlowDoorCreak(3.0f, -1.5f)
                        }
                        val u = ((t - 3.6f) / 1.8f).coerceIn(0f, 1f)
                        granny.x = 2.25f + u * 2.35f // -> 4.60f
                        granny.z = -1.45f - u * 0.55f // -> -2.00f
                        granny.yawDeg = 104f
                        granny.headLookYawDeg = sin((t - 3.6f) * 2.2f) * 28f
                        grannyMoving = true
                    }
                    t < 7.6f -> {
                        // Enters bedroom slowly toward bed edge (X=6.55, Z=-4.85 — right in under-bed line of sight!)
                        if (bedDoor != null) {
                            bedDoor.isOpen = true
                            bedDoor.currentAngleDeg += (bedDoor.def.openDeltaDeg - bedDoor.currentAngleDeg) * (dt * 2.2f).coerceAtMost(1f)
                        }
                        val u = ((t - 5.4f) / 2.2f).coerceIn(0f, 1f)
                        granny.x = 4.60f + u * 1.95f // 4.60 -> 6.55f
                        granny.z = -2.00f - u * 2.85f // -2.00 -> -4.85f
                        granny.yawDeg = 144f
                        granny.searchBendDeg = 14f + sin((t - 5.4f) * 2.0f) * 6f
                        granny.headLookYawDeg = sin((t - 5.4f) * 2.6f) * 34f
                        grannyMoving = true
                    }
                    t < 8.7f -> {
                        // Suspicious pause near the bed legs while peeking slightly down
                        granny.x = 6.55f
                        granny.z = -4.85f
                        granny.yawDeg = 122f
                        granny.searchBendDeg = 20f
                        granny.headLookYawDeg = sin((t - 7.6f) * 3.1f) * 36f
                        granny.headLookPitchDeg = 14f
                        if (!grannySearchMutterPlayed) {
                            grannySearchMutterPlayed = true
                            audioEngine.playGrannyVoice(2, granny.x, granny.z)
                        }
                    }
                    else -> {
                        // Walks back out into the corridor leaving player safe under the bed
                        val u = ((t - 8.7f) / 1.9f).coerceIn(0f, 1f)
                        if (u < 0.5f) {
                            val k = u / 0.5f
                            granny.x = 6.55f - k * 2.15f // -> 4.40f
                            granny.z = -4.85f + k * 2.95f // -> -1.90f
                            granny.yawDeg = -36f
                        } else {
                            val k = (u - 0.5f) / 0.5f
                            granny.x = 4.40f - k * 3.20f // -> 1.20f
                            granny.z = -1.90f + k * 2.40f // -> +0.50f
                            granny.yawDeg = -60f
                        }
                        granny.searchBendDeg = 8f
                        granny.headLookPitchDeg = 0f
                        granny.headLookYawDeg = 0f
                        grannyMoving = true
                    }
                }

                if (grannyMoving) {
                    granny.walkCycle += dt * 4.4f
                    granny.currentMoveSpeedNorm = 0.42f
                    grannySearchStepAccum += dt
                    if (grannySearchStepAccum >= 0.62f) {
                        grannySearchStepAccum = 0f
                        audioEngine.playSpatialFootstep(stealth = false, srcX = granny.x, srcZ = granny.z)
                    }
                } else {
                    granny.currentMoveSpeedNorm = 0f
                }

                val spot = WorldMap3D.underBedSpots.first()
                playerYawRad = spot.lookOutYawRad + underBedPeekYawOffset
                playerPitchRad = 0.04f + underBedPeekPitchOffset

                val caption = when {
                    t < 2.2f -> "Passos lentos se aproximam pelo corredor..."
                    t < 3.6f -> "A maçaneta gira devagar do lado de fora..."
                    t < 5.6f -> "A porta range e se abre lentamente..."
                    t < 8.7f -> "Fique imóvel! A Vovó procura você pelo quarto..."
                    else -> "Ela se afasta pelo corredor. Agora você pode explorar."
                }

                val distToBed = sqrt((spot.hideX - granny.x) * (spot.hideX - granny.x) + (spot.hideZ - granny.z) * (spot.hideZ - granny.z))
                val danger = ((8f - distToBed) / 6f).coerceIn(0.25f, 0.95f)

                awakeningState = awakeningState.copy(
                    stageTimer = nextStageTimer,
                    totalTimer = nextTotalTimer,
                    eyelidOverlayOpen = 1f,
                    camPosX = spot.hideX,
                    camPosY = spot.hideY,
                    camPosZ = spot.hideZ,
                    useThirdPersonCamera = false,
                    hideUnderBedPerspective = true,
                    allowPeekLook = true,
                    caption = caption,
                )
                audioEngine.updateEnvironment(insideHouse = true, dangerLevel = danger, suspenseLevel = 0.95f)

                if (nextStageTimer >= dur) {
                    finishAwakeningSequence()
                }
            }

            AwakeningStage.NONE -> Unit
        }
    }

    private fun finishAwakeningSequence() {
        val spot = WorldMap3D.underBedSpots.first()
        awakeningState = AwakeningCinematicState(stage = AwakeningStage.NONE)
        doorKnobTurnDeg = 0f
        currentDialogue = null
        dialogueQueue.clear()

        // Ensure door stays slightly ajar and Granny resumes patrol cleanly outside
        doors.firstOrNull { it.def.id == "door_granny_room" }?.let { bedDoor ->
            bedDoor.isOpen = true
            bedDoor.isUnlocked = true
            bedDoor.currentAngleDeg = bedDoor.def.openDeltaDeg
        }

        // Player ends tucked safely under the awakening bed in free gameplay!
        isHiding = true
        isHidingUnderBed = true
        isCrouching = true
        activeHideSpotId = spot.id
        underBedPeekYawOffset = 0f
        underBedPeekPitchOffset = 0f
        playerX = spot.hideX
        playerY = spot.hideY
        playerZ = spot.hideZ
        playerYawRad = spot.lookOutYawRad
        playerBodyYawRad = spot.lookOutYawRad
        playerPitchRad = 0.04f
        bloodPulseAlpha = 0f

        granny.x = 1.0f
        granny.z = 0.5f
        granny.yawDeg = 180f
        granny.isLoomingCapture = false
        granny.isDraggingCapture = false
        granny.isSearchingRoom = false
        granny.searchBendDeg = 0f
        granny.dragGripBlend = 0f
        granny.currentMoveSpeedNorm = 0f
        granny.headLookYawDeg = 0f
        granny.headLookPitchDeg = 0f
        granny.aiState = GrannyAiState.PATROLLING_STRANGE
        granny.expression = GrannyExpression.CREEPY_STARE
        granny.stateTimer = 0f

        showToast("Ela saiu do quarto. Toque em Agachar ou Interagir para sair debaixo da cama!")
    }

    fun findBestInteraction(): InteractionPrompt? {
        if (isCinematicIntro || captureState.stage != CaptureStage.NONE || awakeningState.stage != AwakeningStage.NONE || isHiding) return null
        val lookX = sin(playerYawRad)
        val lookZ = cos(playerYawRad)

        var bestScore = Float.MAX_VALUE
        var best: InteractionPrompt? = null

        // Check items
        for (item in WorldMap3D.itemDefs) {
            if (item.kind == InteractKind.ITEM && collectedItems.contains(item.id)) continue
            val dx = item.x - playerX
            val dz = item.z - playerZ
            val dist = sqrt(dx * dx + dz * dz)
            if (dist > 2.4f) continue
            val dot = (dx * lookX + dz * lookZ) / dist.coerceAtLeast(0.01f)
            if (dot < 0.35f && dist > 1.0f) continue
            if (dist < bestScore) {
                bestScore = dist
                val verb = when (item.kind) {
                    InteractKind.ITEM -> "Pegar ${item.name}"
                    InteractKind.NOTE -> "Ler ${item.name}"
                    InteractKind.HIDE_CLOSET -> "Esconder no Armário"
                    InteractKind.HIDE_BED -> "Esconder Debaixo da Cama"
                    InteractKind.FUSE_BOX -> "Inspecionar Caixa de Energia"
                    InteractKind.DOOR -> item.name
                }
                best = InteractionPrompt(item.id, verb, item.description)
            }
        }

        // Check doors
        for (door in doors) {
            val yawRad = (door.def.closedYawDeg + door.currentAngleDeg) * (PI.toFloat() / 180f)
            val midX = door.def.hingeX + cos(yawRad) * (door.def.width * 0.5f)
            val midZ = door.def.hingeZ + sin(yawRad) * (door.def.width * 0.5f)
            val dx = midX - playerX
            val dz = midZ - playerZ
            val dist = sqrt(dx * dx + dz * dz)
            if (dist > 2.35f) continue
            if (dist < bestScore) {
                bestScore = dist
                val label = when {
                    door.def.id == "front_door" && frontDoorSlammed && !door.isUnlocked -> "Destrancar Porta da Frente"
                    !door.isUnlocked -> "Abrir ${door.def.title} (Trancada)"
                    door.isOpen -> "Fechar ${door.def.title}"
                    else -> "Abrir ${door.def.title}"
                }
                best = InteractionPrompt(door.def.id, label)
            }
        }
        return best
    }

    fun hudSnapshot(): GameHudSnapshot {
        val distGranny = sqrt((playerX - granny.x) * (playerX - granny.x) + (playerZ - granny.z) * (playerZ - granny.z))
        val danger = if (storyPhase == StoryPhase.INTRO_ROAD || storyPhase == StoryPhase.PORCH_DIALOGUE) {
            0f
        } else {
            ((10f - distGranny) / 8.5f).coerceIn(0f, 1f)
        }
        val front = doors.firstOrNull { it.def.id == "front_door" }

        val isCapture = captureState.stage != CaptureStage.NONE
        val isAwakening = awakeningState.stage != AwakeningStage.NONE
        val objective = when {
            isCapture -> "Cena da Captura — Você caiu na armadilha da Vovó"
            isAwakening -> "O Despertar — Esconda-se da Vovó no quarto"
            storyPhase == StoryPhase.INTRO_ROAD -> "Caminhe pela estrada até a casa iluminada"
            storyPhase == StoryPhase.PORCH_DIALOGUE -> "Fale com a senhora e entre na casa"
            storyPhase == StoryPhase.INSIDE_MYSTERY -> "Explore a casa, encontre a Lanterna e pistas de saída"
            storyPhase == StoryPhase.GRANNYS_PURSUIT -> "CUIDADO! Corte o fio, ligue o fusível e use a Chave Mestra!"
            else -> "Você escapou da Casa da Vovó!"
        }

        val caption = if (isCinematicIntro) {
            when {
                cinematicTimer < 2.2f -> "Uma estrada isolada no meio da madrugada..."
                cinematicTimer < 4.5f -> "A neblina gelada encobre a floresta sem fim."
                else -> "Adiante, uma casa antiga acende uma luz solitária."
            }
        } else ""

        return GameHudSnapshot(
            storyPhase = storyPhase,
            objectiveText = objective,
            health = health,
            battery = battery.toInt(),
            hasFlashlight = hasFlashlight,
            flashlightOn = flashlightOn,
            isCrouching = isCrouching,
            isHiding = isHiding,
            isHidingUnderBed = isHidingUnderBed,
            isCinematicIntro = isCinematicIntro,
            introCaption = caption,
            isCaptureCutscene = isCapture,
            captureStage = captureState.stage,
            captureBlackoutAlpha = captureState.blackoutAlpha,
            captureCaption = captureState.caption,
            isAwakeningCutscene = isAwakening,
            awakeningStage = awakeningState.stage,
            awakeningEyelidOpen = awakeningState.eyelidOverlayOpen,
            awakeningCaption = awakeningState.caption,
            awakeningPeekEnabled = awakeningState.allowPeekLook,
            activeDialogue = currentDialogue,
            activeNoteTitle = activeNoteTitle,
            activeNoteBody = activeNoteBody,
            interactionPrompt = findBestInteraction(),
            inventory = inventoryItems.toList(),
            bloodPulseAlpha = bloodPulseAlpha,
            dangerLevel = when {
                isCapture -> 0.75f
                isAwakening -> if (awakeningState.stage == AwakeningStage.GRANNY_SEARCH_ROOM) 0.85f else 0.40f
                else -> danger
            },
            frontWireCut = front?.frontWireCut ?: false,
            frontFuseInserted = front?.frontFuseInserted ?: false,
            frontPadlockOpened = front?.frontPadlockOpened ?: false,
            isCaught = isCaught,
            isEscaped = isEscaped,
            playTimeSeconds = playTimeSecFloat.toInt(),
            statusToast = toastText,
        )
    }

    private fun enqueueDialogue(line: DialogueLine) {
        if (currentDialogue == null) {
            currentDialogue = line
            dialogueTimer = 0f
            audioEngine.playDialogueCue(line.isGranny)
        } else {
            dialogueQueue.addLast(line)
        }
    }

    private fun lerpRad(a: Float, b: Float, alpha: Float): Float {
        var diff = (b - a) % (2f * PI.toFloat())
        if (diff > PI.toFloat()) diff -= 2f * PI.toFloat()
        if (diff < -PI.toFloat()) diff += 2f * PI.toFloat()
        return a + diff * alpha
    }

    private fun showToast(msg: String) {
        toastText = msg
        toastTimer = 3.2f
    }
}
