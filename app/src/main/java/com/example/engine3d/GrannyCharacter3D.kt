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
    var breathPhase: Float = 0.0f,
    var blinkTimer: Float = 2.4f,
    var blinkAmount: Float = 0.0f,
    var headLookYawDeg: Float = 0.0f,
    var headLookPitchDeg: Float = 0.0f,
    var currentMoveSpeedNorm: Float = 0.0f,
    var doorSlamGestureTimer: Float = 0.0f,
    var isLoomingCapture: Boolean = false,
    var isDraggingCapture: Boolean = false,
    var dragPullPhase: Float = 0.0f,
    var dragGripBlend: Float = 0.0f,
    var isSearchingRoom: Boolean = false,
    var searchBendDeg: Float = 0.0f,
    var y: Float = 0.0f,
    var huntBlend: Float = 0.0f,
    var searchBlend: Float = 0.0f,
    var doorReachArmBlend: Float = 0.0f,
    var eyeDartX: Float = 0.0f,
    var eyeDartY: Float = 0.0f,
    var noiseTargetY: Float = 0.0f,
) {
    private var navPath: List<NavNode> = emptyList()
    private var navGoalId: String = ""
    private var navRefreshTimer: Float = 0f
    private var stuckTimer: Float = 0f
    private var lastProgressX: Float = 0f
    private var lastProgressZ: Float = 9.3f

    private val patrolWaypoints = listOf(
        Triple(0.0f, 0f, 5.8f),                    // Foyer
        Triple(-11.5f, 0f, 6.2f),                  // Expanded Living Room
        Triple(-12.0f, 0f, -4.5f),                 // Study
        Triple(-13.8f, 0f, -11.5f),                // Bathroom
        Triple(-8.5f, 0f, -10.5f),                 // NW Master Bedroom
        Triple(6.2f, 0f, -2.8f),                   // Awakening Bedroom
        Triple(13.8f, 0f, 5.9f),                   // Expanded Kitchen & Pantry
        Triple(9.1f, WorldMap3D.UPPER_FLOOR_Y, -14.8f), // Stair Landing
        Triple(9.0f, WorldMap3D.UPPER_FLOOR_Y, -18.8f), // Attic
        Triple(0.0f, 0f, -10.5f),                  // Cellar (when unlocked)
    )

    fun resetNavigationState() {
        y = 0f
        huntBlend = 0f
        searchBlend = 0f
        doorReachArmBlend = 0f
        eyeDartX = 0f
        eyeDartY = 0f
        noiseTargetY = 0f
        navPath = emptyList()
        navGoalId = ""
        navRefreshTimer = 0f
        stuckTimer = 0f
        lastProgressX = x
        lastProgressZ = z
    }

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
        playerY: Float = 0f,
        onOpenDoor: (DoorState) -> Unit = {},
    ) {
        if (attackCooldown > 0f) {
            attackCooldown = (attackCooldown - dt).coerceAtLeast(0f)
        }
        if (doorSlamGestureTimer > 0f) {
            doorSlamGestureTimer = (doorSlamGestureTimer - dt * 0.9f).coerceAtLeast(0f)
        }
        if (doorReachArmBlend > 0f) {
            doorReachArmBlend = (doorReachArmBlend - dt * 1.6f).coerceAtLeast(0f)
        }
        stateTimer += dt
        navRefreshTimer += dt
        breathPhase += dt * if (aiState == GrannyAiState.HUNTING_PLAYER) 3.4f else 1.7f

        // Smooth animation blend targets
        val huntTarget = if (aiState == GrannyAiState.HUNTING_PLAYER) 1f else 0f
        huntBlend += (huntTarget - huntBlend) * (dt * 6f).coerceAtMost(1f)
        val searchTarget = if (isSearchingRoom || creepPauseTimer > 0f || aiState == GrannyAiState.INVESTIGATING_NOISE) 1f else 0f
        searchBlend += (searchTarget - searchBlend) * (dt * 5f).coerceAtMost(1f)

        // Subtle restless eye darting (stronger when suspicious/searching)
        val dartAmp = if (aiState == GrannyAiState.HUNTING_PLAYER) 0.45f else 1.0f
        eyeDartX = sin(stateTimer * 2.9f + breathPhase * 0.4f) * 0.0045f * dartAmp
        eyeDartY = cos(stateTimer * 2.1f) * 0.0022f * dartAmp

        // Natural periodic eye blinking
        blinkTimer -= dt
        if (blinkTimer <= 0f) {
            blinkTimer = 3.2f + (stateTimer % 2.1f)
        }
        blinkAmount = if (blinkTimer < 0.16f) {
            sin((blinkTimer / 0.16f) * Math.PI.toFloat())
        } else {
            0f
        }

        val dxToPlayer = playerX - x
        val dyToPlayer = playerY - y
        val dzToPlayer = playerZ - z
        val horizDistToPlayer = sqrt(dxToPlayer * dxToPlayer + dzToPlayer * dzToPlayer)
        val distToPlayer = sqrt(dxToPlayer * dxToPlayer + dyToPlayer * dyToPlayer + dzToPlayer * dzToPlayer)
        val angleToPlayerDeg = atan2(dxToPlayer, dzToPlayer) * (180f / Math.PI.toFloat())
        val sameFloorAsPlayer = kotlin.math.abs(dyToPlayer) < 1.3f

        // Reset move norm each frame; moveToward sets it proportionally
        currentMoveSpeedNorm = 0f

        when (aiState) {
            GrannyAiState.WAITING_AT_DOOR -> {
                yawDeg = 0f
                y = 0f
                expression = GrannyExpression.GENTLE_SMILE
                headTiltDeg = sin(stateTimer * 1.1f) * 4.5f
                jawOpenAmount = 0f
                updateHeadTrack(angleToPlayerDeg, distToPlayer, dt, maxYaw = 42f)
            }

            GrannyAiState.GREETING_DIALOGUE -> {
                yawDeg = lerpAngleDeg(yawDeg, angleToPlayerDeg, dt * 4.5f)
                y = 0f
                expression = if ((stateTimer % 5.0f) > 4.2f) GrannyExpression.CREEPY_STARE else GrannyExpression.GENTLE_SMILE
                headTiltDeg = sin(stateTimer * 1.8f) * 6f
                jawOpenAmount = (0.25f + 0.25f * sin(stateTimer * 12f)).coerceIn(0f, 0.5f)
                updateHeadTrack(angleToPlayerDeg, distToPlayer, dt, maxYaw = 35f)
            }

            GrannyAiState.CLOSING_DOOR_MYSTERY -> {
                val targetX = 0.0f
                val targetZ = 5.4f
                moveToward(targetX, 0f, targetZ, dt, 1.35f, doors, onOpenDoor)
                updateHeadTrack(angleToPlayerDeg, distToPlayer, dt, maxYaw = 50f)
                val dist = sqrt((targetX - x) * (targetX - x) + (targetZ - z) * (targetZ - z))
                if (dist < 0.35f || stateTimer > 3.5f) {
                    aiState = GrannyAiState.PATROLLING_STRANGE
                    expression = GrannyExpression.SUSPICIOUS
                    stateTimer = 0f
                    navPath = emptyList()
                }
            }

            GrannyAiState.PATROLLING_STRANGE -> {
                if (creepPauseTimer > 0f) {
                    creepPauseTimer -= dt
                    expression = GrannyExpression.CREEPY_STARE
                    headTiltDeg = 16f * sin(stateTimer * 0.8f)
                    searchBendDeg = 10f * sin(stateTimer * 1.4f).coerceAtLeast(0f)
                    jawOpenAmount = 0.08f
                    if (!playerHidden && distToPlayer < 8.5f && sameFloorAsPlayer) {
                        updateHeadTrack(angleToPlayerDeg, distToPlayer, dt, maxYaw = 55f)
                    } else {
                        headLookYawDeg += (sin(stateTimer * 1.2f) * 38f - headLookYawDeg) * (dt * 3f).coerceAtMost(1f)
                    }
                } else {
                    searchBendDeg *= (1f - (dt * 5f).coerceAtMost(0.9f))
                    expression = if (distToPlayer < 6.0f && !playerHidden && sameFloorAsPlayer) {
                        GrannyExpression.SUSPICIOUS
                    } else {
                        GrannyExpression.CREEPY_STARE
                    }
                    headTiltDeg = sin(stateTimer * 2.1f) * 8f
                    jawOpenAmount = 0.0f
                    if (!playerHidden && distToPlayer < 8.0f && sameFloorAsPlayer) {
                        updateHeadTrack(angleToPlayerDeg, distToPlayer, dt, maxYaw = 50f)
                    } else {
                        headLookYawDeg *= (1f - (dt * 4f).coerceAtMost(0.9f))
                    }

                    val (wx, wy, wz) = patrolWaypoints[waypointIndex % patrolWaypoints.size]
                    val arrived = moveToward(wx, wy, wz, dt, 1.42f, doors, onOpenDoor)
                    if (arrived) {
                        waypointIndex = (waypointIndex + 1) % patrolWaypoints.size
                        creepPauseTimer = 1.4f
                        navPath = emptyList()
                        if (waypointIndex == 2) {
                            onTriggerDialogueHint("Vovó", "Fique à vontade, querido... a sopa já vai ficar pronta.")
                        } else if (waypointIndex == 5) {
                            onTriggerDialogueHint("Vovó", "Só não tente abrir as portas trancadas...")
                        }
                    }
                }

                // Hear loud noise -> investigate
                if (!playerHidden && playerNoiseRadius > distToPlayer && distToPlayer < 10.5f) {
                    noiseTargetX = playerX
                    noiseTargetY = playerY
                    noiseTargetZ = playerZ
                    aiState = GrannyAiState.INVESTIGATING_NOISE
                    expression = GrannyExpression.SUSPICIOUS
                    stateTimer = 0f
                    navPath = emptyList()
                    onTriggerDialogueHint("Vovó", "Eu ouvi passos... Onde você está?")
                }
            }

            GrannyAiState.INVESTIGATING_NOISE -> {
                expression = GrannyExpression.MENACING_GRIN
                headTiltDeg = -10f
                jawOpenAmount = 0.15f
                updateHeadTrack(angleToPlayerDeg, distToPlayer, dt, maxYaw = 45f)
                val arrived = moveToward(noiseTargetX, noiseTargetY, noiseTargetZ, dt, 2.08f, doors, onOpenDoor)
                val seesPlayer = !playerHidden && sameFloorAsPlayer &&
                    (horizDistToPlayer < 4.8f || (flashlightOn && horizDistToPlayer < 7.8f))
                if (seesPlayer) {
                    aiState = GrannyAiState.HUNTING_PLAYER
                    stateTimer = 0f
                    navPath = emptyList()
                    onTriggerDialogueHint("Vovó", "ACHEI VOCÊ, QUERIDO!")
                } else if (arrived || stateTimer > 8.5f) {
                    aiState = GrannyAiState.PATROLLING_STRANGE
                    stateTimer = 0f
                    navPath = emptyList()
                }
            }

            GrannyAiState.HUNTING_PLAYER -> {
                expression = if (distToPlayer < 3.2f) GrannyExpression.HOSTILE_SHRIEK else GrannyExpression.MENACING_GRIN
                headTiltDeg = sin(stateTimer * 7f) * 10f
                jawOpenAmount = if (distToPlayer < 3.2f) 0.85f else 0.35f
                updateHeadTrack(angleToPlayerDeg, distToPlayer, dt, maxYaw = 38f)

                if (playerHidden && distToPlayer > 2.2f) {
                    if (stateTimer > 5.0f) {
                        aiState = GrannyAiState.PATROLLING_STRANGE
                        stateTimer = 0f
                        navPath = emptyList()
                        onTriggerDialogueHint("Vovó", "Para onde ele foi...?")
                    } else {
                        moveToward(noiseTargetX, noiseTargetY, noiseTargetZ, dt, 2.25f, doors, onOpenDoor)
                    }
                } else {
                    noiseTargetX = playerX
                    noiseTargetY = playerY
                    noiseTargetZ = playerZ
                    val chaseSpeed = 2.72f
                    moveToward(playerX, playerY, playerZ, dt, chaseSpeed, doors, onOpenDoor)

                    if (horizDistToPlayer < 1.38f && sameFloorAsPlayer && attackCooldown <= 0f && !playerHidden) {
                        attackCooldown = 2.6f
                        onAttackPlayer()
                    }
                }
            }
        }
    }

    private fun updateHeadTrack(targetWorldYawDeg: Float, dist: Float, dt: Float, maxYaw: Float) {
        var relYaw = targetWorldYawDeg - yawDeg
        while (relYaw > 180f) relYaw -= 360f
        while (relYaw < -180f) relYaw += 360f
        val clampedYaw = relYaw.coerceIn(-maxYaw, maxYaw)
        headLookYawDeg += (clampedYaw - headLookYawDeg) * (dt * 6f).coerceAtMost(1f)
        val pitchTarget = if (dist < 4f) -6f else 0f
        headLookPitchDeg += (pitchTarget - headLookPitchDeg) * (dt * 5f).coerceAtMost(1f)
    }

    private fun lerpAngleDeg(current: Float, target: Float, alpha: Float): Float {
        var diff = target - current
        while (diff > 180f) diff -= 360f
        while (diff < -180f) diff += 360f
        return current + diff * alpha.coerceIn(0f, 1f)
    }

    private fun moveToward(
        targetX: Float,
        targetY: Float,
        targetZ: Float,
        dt: Float,
        speed: Float,
        doors: List<DoorState>,
        onOpenDoor: (DoorState) -> Unit,
    ): Boolean {
        val totalDx = targetX - x
        val totalDy = kotlin.math.abs(targetY - y)
        val totalDz = targetZ - z
        val totalHorizDist = sqrt(totalDx * totalDx + totalDz * totalDz)
        if (totalHorizDist < 0.35f && totalDy < 0.85f) return true

        // Automatically swing open unlocked interior doors ahead
        for (d in doors) {
            if (d.def.id == "front_door" || d.def.isSecretBookshelf) continue
            if (!d.isUnlocked || d.isOpen) continue
            if (kotlin.math.abs(d.def.baseY - y) > 1.15f) continue
            val yawRad = (d.def.closedYawDeg + d.currentAngleDeg) * (Math.PI.toFloat() / 180f)
            val midX = d.def.hingeX + cos(yawRad) * (d.def.width * 0.5f)
            val midZ = d.def.hingeZ + sin(yawRad) * (d.def.width * 0.5f)
            val ddSq = (midX - x) * (midX - x) + (midZ - z) * (midZ - z)
            if (ddSq < 1.65f * 1.65f) {
                d.isOpen = true
                doorReachArmBlend = 1.0f
                onOpenDoor(d)
            }
        }

        // Route via BFS nav-graph when navigating across rooms or floors
        val goalNode = WorldMap3D.nearestNavNode(targetX, targetY, targetZ)
        if (navPath.isEmpty() || navGoalId != goalNode.id || navRefreshTimer > 0.75f) {
            navRefreshTimer = 0f
            navGoalId = goalNode.id
            navPath = WorldMap3D.findNavPath(x, y, z, targetX, targetY, targetZ, doors)
        }

        // Advance past waypoints already reached
        while (navPath.isNotEmpty()) {
            val head = navPath.first()
            val hDx = head.x - x
            val hDz = head.z - z
            val hDist = sqrt(hDx * hDx + hDz * hDz)
            if (hDist < 0.85f && kotlin.math.abs(head.y - y) < 0.85f) {
                navPath = navPath.drop(1)
            } else {
                break
            }
        }

        val useNode = navPath.isNotEmpty() && (totalHorizDist > 2.2f || totalDy > 0.85f)
        val steerX = if (useNode) navPath.first().x else targetX
        val steerZ = if (useNode) navPath.first().z else targetZ

        val dx = steerX - x
        val dz = steerZ - z
        val dist = sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)

        val step = (speed * dt).coerceAtMost(dist)
        val vx = (dx / dist) * step
        val vz = (dz / dist) * step

        val targetYaw = atan2(dx, dz) * (180f / Math.PI.toFloat())
        yawDeg = lerpAngleDeg(yawDeg, targetYaw, dt * 8f)
        walkCycle += dt * speed * 4.2f
        currentMoveSpeedNorm = (speed / 2.7f).coerceIn(0.2f, 1.0f)

        val closedBlockingDoors = doors.filter { !it.isOpen && (it.def.id == "front_door" || !it.isUnlocked) }
        val (nx, nz) = WorldMap3D.resolveMovement(x, z, vx, vz, closedBlockingDoors, entityFloorY = y)

        val movedDist = sqrt((nx - lastProgressX) * (nx - lastProgressX) + (nz - lastProgressZ) * (nz - lastProgressZ))
        if (movedDist < 0.03f) {
            stuckTimer += dt
            if (stuckTimer > 0.65f) {
                stuckTimer = 0f
                navPath = emptyList()
                // If patrolling into a locked sector, move to next waypoint
                if (aiState == GrannyAiState.PATROLLING_STRANGE) {
                    waypointIndex = (waypointIndex + 1) % patrolWaypoints.size
                } else if (navPath.isEmpty()) {
                    // Subtle tangent nudge to unhook from furniture corner
                    val perpX = -vz * 1.4f
                    val perpZ = vx * 1.4f
                    val (ux, uz) = WorldMap3D.resolveMovement(nx, nz, perpX, perpZ, closedBlockingDoors, entityFloorY = y)
                    x = ux
                    z = uz
                    y = WorldMap3D.floorHeightAt(x, z)
                    lastProgressX = x
                    lastProgressZ = z
                    return false
                }
            }
        } else {
            stuckTimer = 0f
            lastProgressX = nx
            lastProgressZ = nz
        }

        x = nx
        z = nz
        y = WorldMap3D.floorHeightAt(x, z)
        return false
    }
}
