package com.example.engine3d

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class GrannyExpression {
    GENTLE_SMILE,
    CREEPY_STARE,
    SUSPICIOUS,
    MENACING_GRIN,
    HOSTILE_SHRIEK,
}

enum class GrannyAiState {
    WAITING_AT_DOOR,
    GREETING_DIALOGUE,
    CLOSING_DOOR_MYSTERY,
    PATROLLING_STRANGE,
    INVESTIGATING_NOISE,
    HUNTING_PLAYER,
}

data class GrannyState(
    var x: Float = 0.0f,
    var z: Float = 9.3f,
    var yawDeg: Float = 0.0f, // 0 = facing +Z toward the road!
    var aiState: GrannyAiState = GrannyAiState.WAITING_AT_DOOR,
    var expression: GrannyExpression = GrannyExpression.GENTLE_SMILE,
    var walkCycle: Float = 0.0f,
    var jawOpenAmount: Float = 0.0f,
    var headTiltDeg: Float = 0.0f,
    var stateTimer: Float = 0.0f,
    var attackCooldown: Float = 0.0f,
    var noiseTargetX: Float = 0.0f,
    var noiseTargetZ: Float = 0.0f,
    var waypointIndex: Int = 0,
    var creepPauseTimer: Float = 0.0f,
) {
    private val patrolWaypoints = listOf(
        0.0f to 5.8f,   // Foyer
        6.2f to 6.0f,   // Kitchen doorway/interior
        0.0f to 1.5f,   // Hallway center
        -5.8f to 6.0f,  // Living room
        0.0f to -4.5f,  // North hallway
        5.8f to -4.0f,  // Granny's room
    )

    fun update(
        dt: Float,
        playerX: Float,
        playerZ: Float,
        playerHidden: Boolean,
        playerNoiseRadius: Float,
        flashlightOn: Boolean,
        doors: List<DoorState>,
        onTriggerDialogueHint: (String, String) -> Unit,
        onAttackPlayer: () -> Unit,
    ) {
        if (attackCooldown > 0f) {
            attackCooldown = (attackCooldown - dt).coerceAtLeast(0f)
        }
        stateTimer += dt

        val dxToPlayer = playerX - x
        val dzToPlayer = playerZ - z
        val distToPlayer = sqrt(dxToPlayer * dxToPlayer + dzToPlayer * dzToPlayer)

        when (aiState) {
            GrannyAiState.WAITING_AT_DOOR -> {
                // Gentle breathing & eerie head tilt at entrance
                yawDeg = 0f
                expression = GrannyExpression.GENTLE_SMILE
                headTiltDeg = sin(stateTimer * 1.1f) * 4f
                jawOpenAmount = 0f
            }

            GrannyAiState.GREETING_DIALOGUE -> {
                // Face player gently on porch
                val targetYaw = atan2(dxToPlayer, dzToPlayer) * (180f / Math.PI.toFloat())
                yawDeg = targetYaw
                // Subtle unsettling flickers between gentle smile and creepy stare
                expression = if ((stateTimer % 5.0f) > 4.2f) GrannyExpression.CREEPY_STARE else GrannyExpression.GENTLE_SMILE
                headTiltDeg = sin(stateTimer * 1.8f) * 6f
                jawOpenAmount = (0.25f + 0.25f * sin(stateTimer * 12f)).coerceIn(0f, 0.5f)
            }

            GrannyAiState.CLOSING_DOOR_MYSTERY -> {
                // Step back inside hallway slowly while door shuts
                val targetX = 0.0f
                val targetZ = 5.4f
                moveToward(targetX, targetZ, dt, 1.35f, doors)
                val dist = sqrt((targetX - x) * (targetX - x) + (targetZ - z) * (targetZ - z))
                if (dist < 0.35f || stateTimer > 3.5f) {
                    aiState = GrannyAiState.PATROLLING_STRANGE
                    expression = GrannyExpression.SUSPICIOUS
                    stateTimer = 0f
                }
            }

            GrannyAiState.PATROLLING_STRANGE -> {
                if (creepPauseTimer > 0f) {
                    creepPauseTimer -= dt
                    expression = GrannyExpression.CREEPY_STARE
                    headTiltDeg = 16f * sin(stateTimer * 0.8f)
                    jawOpenAmount = 0.08f
                } else {
                    expression = if (distToPlayer < 6.0f && !playerHidden) GrannyExpression.SUSPICIOUS else GrannyExpression.CREEPY_STARE
                    headTiltDeg = sin(stateTimer * 2.1f) * 8f
                    jawOpenAmount = 0.0f

                    val (wx, wz) = patrolWaypoints[waypointIndex % patrolWaypoints.size]
                    val arrived = moveToward(wx, wz, dt, 1.38f, doors)
                    if (arrived) {
                        waypointIndex = (waypointIndex + 1) % patrolWaypoints.size
                        creepPauseTimer = 1.6f
                        if (waypointIndex == 2) {
                            onTriggerDialogueHint("Vovó", "Fique à vontade, querido... a sopa já vai ficar pronta.")
                        } else if (waypointIndex == 4) {
                            onTriggerDialogueHint("Vovó", "Só não tente abrir as portas trancadas...")
                        }
                    }
                }

                // Hear loud noise -> investigate
                if (!playerHidden && playerNoiseRadius > distToPlayer && distToPlayer < 9.5f) {
                    noiseTargetX = playerX
                    noiseTargetZ = playerZ
                    aiState = GrannyAiState.INVESTIGATING_NOISE
                    expression = GrannyExpression.SUSPICIOUS
                    stateTimer = 0f
                    onTriggerDialogueHint("Vovó", "Eu ouvi passos... Onde você está?")
                }
            }

            GrannyAiState.INVESTIGATING_NOISE -> {
                expression = GrannyExpression.MENACING_GRIN
                headTiltDeg = -10f
                jawOpenAmount = 0.15f
                val arrived = moveToward(noiseTargetX, noiseTargetZ, dt, 2.05f, doors)
                if (!playerHidden && (distToPlayer < 4.5f || (flashlightOn && distToPlayer < 7.0f))) {
                    aiState = GrannyAiState.HUNTING_PLAYER
                    stateTimer = 0f
                    onTriggerDialogueHint("Vovó", "ACHEI VOCÊ, QUERIDO!")
                } else if (arrived || stateTimer > 7.0f) {
                    aiState = GrannyAiState.PATROLLING_STRANGE
                    stateTimer = 0f
                }
            }

            GrannyAiState.HUNTING_PLAYER -> {
                expression = if (distToPlayer < 3.2f) GrannyExpression.HOSTILE_SHRIEK else GrannyExpression.MENACING_GRIN
                headTiltDeg = sin(stateTimer * 7f) * 10f
                jawOpenAmount = if (distToPlayer < 3.2f) 0.85f else 0.35f

                if (playerHidden && distToPlayer > 2.2f) {
                    // Lost sight of player if they hid inside wardrobe
                    if (stateTimer > 4.5f) {
                        aiState = GrannyAiState.PATROLLING_STRANGE
                        stateTimer = 0f
                        onTriggerDialogueHint("Vovó", "Para onde ele foi...?")
                    } else {
                        moveToward(noiseTargetX, noiseTargetZ, dt, 2.2f, doors)
                    }
                } else {
                    noiseTargetX = playerX
                    noiseTargetZ = playerZ
                    val chaseSpeed = 2.68f
                    moveToward(playerX, playerZ, dt, chaseSpeed, doors)

                    if (distToPlayer < 1.35f && attackCooldown <= 0f && !playerHidden) {
                        attackCooldown = 2.6f
                        onAttackPlayer()
                    }
                }
            }
        }
    }

    private fun moveToward(
        targetX: Float,
        targetZ: Float,
        dt: Float,
        speed: Float,
        doors: List<DoorState>,
    ): Boolean {
        val dx = targetX - x
        val dz = targetZ - z
        val dist = sqrt(dx * dx + dz * dz)
        if (dist < 0.35f) return true

        val step = (speed * dt).coerceAtMost(dist)
        val vx = (dx / dist) * step
        val vz = (dz / dist) * step

        val targetYaw = atan2(dx, dz) * (180f / Math.PI.toFloat())
        yawDeg = targetYaw
        walkCycle += dt * speed * 4.2f

        // Granny can pass through unlocked/open interior doors, otherwise slides around walls
        val openDoorsOnly = doors.filter { it.def.id == "front_door" || !it.isUnlocked }
        val (nx, nz) = WorldMap3D.resolveMovement(x, z, vx, vz, openDoorsOnly)
        x = nx
        z = nz
        return false
    }
}
