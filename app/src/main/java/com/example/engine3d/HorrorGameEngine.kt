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

data class GameHudSnapshot(
    val storyPhase: StoryPhase = StoryPhase.INTRO_ROAD,
    val objectiveText: String = "Explore a estrada na escuridão",
    val health: Int = 100,
    val battery: Int = 100,
    val hasFlashlight: Boolean = false,
    val flashlightOn: Boolean = false,
    val isCrouching: Boolean = false,
    val isHiding: Boolean = false,
    val isCinematicIntro: Boolean = true,
    val introCaption: String = "",
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

    var activeNoteTitle: String? = null
    var activeNoteBody: String? = null

    private val dialogueQueue: ArrayDeque<DialogueLine> = ArrayDeque()
    private var currentDialogue: DialogueLine? = null
    private var dialogueTimer: Float = 0f
    private var porchDialogueTriggered: Boolean = false
    private var frontDoorSlammed: Boolean = false

    @Volatile var isPaused: Boolean = false
    var isCaught: Boolean = false
    var isEscaped: Boolean = false

    fun resetNewStory() {
        playerX = 0.0f
        playerY = WorldMap3D.PLAYER_EYE_STANDING
        playerZ = 27.5f
        playerYawRad = PI.toFloat()
        playerPitchRad = 0.02f
        health = 100
        battery = 100f
        hasFlashlight = false
        flashlightOn = false
        isCrouching = false
        isHiding = false
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
        activeNoteTitle = null
        activeNoteBody = null
        toastText = null
        isCaught = false
        isEscaped = false
        isPaused = false
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

    fun addLookDelta(dx: Float, dy: Float, sensitivity: Float, invertY: Boolean) {
        if (isCinematicIntro || isPaused || isCaught || isEscaped || activeNoteTitle != null) return
        val scale = 0.0042f * sensitivity
        playerYawRad -= dx * scale
        val pitchSign = if (invertY) 1f else -1f
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
            isHiding = false
            showToast("Você saiu do armário.")
            return
        }
        isCrouching = !isCrouching
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
            isHiding = false
            showToast("Você saiu do armário.")
            return
        }
        val prompt = findBestInteraction() ?: return

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
                flashlightOn = false
                audioEngine.playDoorCreak()
                showToast("Escondido no armário. A Vovó não pode vê-lo aqui.")
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

        // 1. Cinematic Intro on the isolated road at night
        if (isCinematicIntro) {
            cinematicTimer += dt
            val progress = (cinematicTimer / 6.5f).coerceIn(0f, 1f)
            playerX = sin(cinematicTimer * 0.9f) * 0.12f
            playerZ = 27.5f - progress * 9.5f // walks from Z=27.5 -> Z=18.0
            playerY = WorldMap3D.PLAYER_EYE_STANDING + sin(cinematicTimer * 4.5f) * 0.03f
            playerYawRad = PI.toFloat() + sin(cinematicTimer * 0.6f) * 0.08f
            playerPitchRad = 0.04f - progress * 0.03f
            if (cinematicTimer >= 6.5f) {
                isCinematicIntro = false
                playerYawRad = PI.toFloat()
                playerPitchRad = 0f
            }
            return
        }

        // 2. Smooth crouch eye-height transition
        val targetEye = when {
            isHiding -> 1.35f
            isCrouching -> WorldMap3D.PLAYER_EYE_CROUCH
            else -> WorldMap3D.PLAYER_EYE_STANDING
        }
        playerY += (targetEye - playerY) * (dt * 10f).coerceAtMost(1f)

        // 3. Player movement (disabled while reading note or hiding inside closet)
        var emittedNoiseRadius = 0f
        if (!isHiding && activeNoteTitle == null) {
            val mag = sqrt(moveInputX * moveInputX + moveInputY * moveInputY)
            if (mag > 0.05f) {
                val baseSpeed = if (isCrouching) 1.75f else if (mag > 0.78f) 3.85f else 2.65f
                val forwardX = sin(playerYawRad)
                val forwardZ = cos(playerYawRad)
                val rightX = -cos(playerYawRad)
                val rightZ = sin(playerYawRad)

                val worldDx = (forwardX * moveInputY + rightX * moveInputX) * baseSpeed * dt
                val worldDz = (forwardZ * moveInputY + rightZ * moveInputX) * baseSpeed * dt

                val (nx, nz) = WorldMap3D.resolveMovement(playerX, playerZ, worldDx, worldDz, doors)
                playerX = nx
                playerZ = nz

                emittedNoiseRadius = if (isCrouching) 1.2f else if (mag > 0.78f) 8.5f else 4.2f
                stepTimer += dt * baseSpeed
                if (stepTimer >= 1.55f) {
                    stepTimer = 0f
                    audioEngine.playFootstep(stealth = isCrouching)
                }
            }
        }

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

        // 6. Story Triggers (Porch Dialogue -> Entering House -> Door Slam!)
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
        audioEngine.updateEnvironment(insideHouse = playerZ < 10.0f, dangerLevel = danger)
    }

    fun findBestInteraction(): InteractionPrompt? {
        if (isCinematicIntro || isHiding) return null
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

        val objective = when (storyPhase) {
            StoryPhase.INTRO_ROAD -> "Caminhe pela estrada até a casa iluminada"
            StoryPhase.PORCH_DIALOGUE -> "Fale com a senhora e entre na casa"
            StoryPhase.INSIDE_MYSTERY -> "Explore a casa, encontre a Lanterna e pistas de saída"
            StoryPhase.GRANNYS_PURSUIT -> "CUIDADO! Corte o fio, ligue o fusível e use a Chave Mestra!"
            StoryPhase.ESCAPED -> "Você escapou da Casa da Vovó!"
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
            isCinematicIntro = isCinematicIntro,
            introCaption = caption,
            activeDialogue = currentDialogue,
            activeNoteTitle = activeNoteTitle,
            activeNoteBody = activeNoteBody,
            interactionPrompt = findBestInteraction(),
            inventory = inventoryItems.toList(),
            bloodPulseAlpha = bloodPulseAlpha,
            dangerLevel = danger,
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

    private fun showToast(msg: String) {
        toastText = msg
        toastTimer = 3.2f
    }
}
