package com.example.engine3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class HorrorGlRenderer(
    private val gameEngine: HorrorGameEngine,
    private val performanceManager: AutoQualityController,
    private val onTelemetryUpdate: (PerformanceTelemetry) -> Unit,
    private val onQualityScaleChanged: (QualityTier) -> Unit,
) : GLSurfaceView.Renderer {

    private var programId = 0

    // Attribute & Uniform locations
    private var aPosition = 0
    private var aNormal = 0
    private var aTexCoord = 0
    private var uMvpMatrix = 0
    private var uModelMatrix = 0
    private var uTexture = 0
    private var uTintColor = 0
    private var uSpecular = 0
    private var uEmissive = 0
    private var uAlphaScale = 0
    private var uCamPos = 0
    private var uCamDir = 0
    private var uFlashlightOn = 0
    private var uFlashlightBattery = 0
    private var uTime = 0
    private var uFogDensity = 0
    private var uFogColor = 0
    private var uAmbientColor = 0
    private var uLightPositions = 0
    private var uLightColors = 0
    private var uActiveLightCount = 0

    // GPU Assets
    private val textures = TextureAtlasSet()
    private var staticBatches: List<GpuSceneBatch> = emptyList()
    private var charMeshes: CharacterMeshLibrary? = null
    private var propMeshes: PropMeshLibrary? = null
    private var loadedTextureRes = 0

    // Pre-allocated matrices & light arrays (zero GC allocations during onDrawFrame)
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val rollMatrix = FloatArray(16)
    private val tmpViewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val lightPosArray = FloatArray(18)
    private val lightColArray = FloatArray(18)

    private var aspectRatio = 16f / 9f
    private var lastFrameNano = 0L
    private var elapsedSec = 0f
    private var telemetryEmitCounter = 0
    private var lastAppliedTier: QualityTier? = null

    private val meshDrawCallback = MeshDrawCallback { mat, mesh, tex, r, g, b, spec, emit, doubleSided ->
        drawMeshWithMatrix(mat, mesh, tex, r, g, b, spec, emit, alphaScale = 1f, doubleSided = doubleSided)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        releaseGpuResources()

        GLES20.glClearColor(0.02f, 0.025f, 0.042f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormal = GLES20.glGetAttribLocation(programId, "aNormal")
        aTexCoord = GLES20.glGetAttribLocation(programId, "aTexCoord")

        uMvpMatrix = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
        uModelMatrix = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        uTexture = GLES20.glGetUniformLocation(programId, "uTexture")
        uTintColor = GLES20.glGetUniformLocation(programId, "uTintColor")
        uSpecular = GLES20.glGetUniformLocation(programId, "uSpecular")
        uEmissive = GLES20.glGetUniformLocation(programId, "uEmissive")
        uAlphaScale = GLES20.glGetUniformLocation(programId, "uAlphaScale")
        uCamPos = GLES20.glGetUniformLocation(programId, "uCamPos")
        uCamDir = GLES20.glGetUniformLocation(programId, "uCamDir")
        uFlashlightOn = GLES20.glGetUniformLocation(programId, "uFlashlightOn")
        uFlashlightBattery = GLES20.glGetUniformLocation(programId, "uFlashlightBattery")
        uTime = GLES20.glGetUniformLocation(programId, "uTime")
        uFogDensity = GLES20.glGetUniformLocation(programId, "uFogDensity")
        uFogColor = GLES20.glGetUniformLocation(programId, "uFogColor")
        uAmbientColor = GLES20.glGetUniformLocation(programId, "uAmbientColor")
        uLightPositions = GLES20.glGetUniformLocation(programId, "uLightPositions")
        uLightColors = GLES20.glGetUniformLocation(programId, "uLightColors")
        uActiveLightCount = GLES20.glGetUniformLocation(programId, "uActiveLightCount")

        loadedTextureRes = performanceManager.activeTier.textureSizePx
        textures.uploadAll(loadedTextureRes)
        staticBatches = SceneBuilder3D.buildStaticGpuBatches()
        charMeshes = CharacterMeshLibrary()
        propMeshes = PropMeshLibrary()

        lastFrameNano = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        aspectRatio = width.toFloat() / height.coerceAtLeast(1).toFloat()
        val drawDist = performanceManager.activeTier.drawDistance
        Matrix.perspectiveM(projMatrix, 0, 68f, aspectRatio, 0.08f, drawDist)
    }

    override fun onDrawFrame(gl: GL10?) {
        val nowNano = System.nanoTime()
        val targetFps = performanceManager.effectiveTargetFps()
        val minFrameNanos = 1_000_000_000L / targetFps.coerceAtLeast(30)

        val elapsedNano = nowNano - lastFrameNano
        if (elapsedNano < minFrameNanos - 800_000L) {
            val sleepMs = (minFrameNanos - elapsedNano) / 1_000_000L
            if (sleepMs in 1..30) {
                try {
                    Thread.sleep(sleepMs)
                } catch (_: InterruptedException) {
                }
            }
        }

        val postSleepNano = System.nanoTime()
        val dtSec = ((postSleepNano - lastFrameNano) / 1_000_000_000.0).coerceIn(0.001, 0.1)
        lastFrameNano = postSleepNano
        elapsedSec += dtSec.toFloat()

        gameEngine.step(dtSec.toFloat())

        val tierChanged = performanceManager.recordFrame(dtSec)
        val activeTierNow = performanceManager.activeTier
        if (tierChanged || activeTierNow != lastAppliedTier) {
            lastAppliedTier = activeTierNow
            Matrix.perspectiveM(projMatrix, 0, 68f, aspectRatio, 0.08f, activeTierNow.drawDistance)
            if (tierChanged) {
                onQualityScaleChanged(activeTierNow)
            }
        }
        telemetryEmitCounter++
        if (telemetryEmitCounter >= 12) {
            telemetryEmitCounter = 0
            onTelemetryUpdate(performanceManager.snapshot())
        }

        val cap = gameEngine.captureState
        val inCapture = cap.stage != CaptureStage.NONE
        val awake = gameEngine.awakeningState
        val inAwakening = !inCapture && awake.stage != AwakeningStage.NONE

        val camZCheck = when {
            inCapture -> cap.camPosZ
            inAwakening -> awake.camPosZ
            else -> gameEngine.playerZ
        }
        val insideHouse = camZCheck < 10.0f
        val fogR = if (insideHouse) 0.018f else 0.026f
        val fogG = if (insideHouse) 0.015f else 0.034f
        val fogB = if (insideHouse) 0.020f else 0.056f
        GLES20.glClearColor(fogR, fogG, fogB, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (programId == 0) return

        GLES20.glUseProgram(programId)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glUniform1i(uTexture, 0)

        // Camera with subtle horror shake when slammed or hit
        val shakeX = if (gameEngine.cameraShakeAmount > 0f) {
            sin(elapsedSec * 48f) * 0.055f * gameEngine.cameraShakeAmount
        } else 0f
        val shakeY = if (gameEngine.cameraShakeAmount > 0f) {
            cos(elapsedSec * 57f) * 0.045f * gameEngine.cameraShakeAmount
        } else 0f

        val camX: Float
        val camY: Float
        val camZ: Float
        val lookX: Float
        val lookY: Float
        val lookZ: Float

        if (inCapture) {
            camX = cap.camPosX + shakeX
            camY = cap.camPosY + shakeY
            camZ = cap.camPosZ
            val dx = cap.camLookX - camX
            val dy = cap.camLookY - camY
            val dz = cap.camLookZ - camZ
            val invLen = 1f / kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001f)
            lookX = dx * invLen
            lookY = dy * invLen
            lookZ = dz * invLen

            Matrix.setLookAtM(
                tmpViewMatrix, 0,
                camX, camY, camZ,
                cap.camLookX, cap.camLookY, cap.camLookZ,
                0f, 1f, 0f,
            )
            if (kotlin.math.abs(cap.cameraRollRad) > 0.001f) {
                Matrix.setIdentityM(rollMatrix, 0)
                Matrix.rotateM(rollMatrix, 0, cap.cameraRollRad * (180f / PI.toFloat()), 0f, 0f, 1f)
                Matrix.multiplyMM(viewMatrix, 0, rollMatrix, 0, tmpViewMatrix, 0)
            } else {
                System.arraycopy(tmpViewMatrix, 0, viewMatrix, 0, 16)
            }
        } else if (inAwakening && awake.useThirdPersonCamera) {
            camX = awake.camPosX + shakeX
            camY = awake.camPosY + shakeY
            camZ = awake.camPosZ
            val dx = awake.camLookX - camX
            val dy = awake.camLookY - camY
            val dz = awake.camLookZ - camZ
            val invLen = 1f / kotlin.math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001f)
            lookX = dx * invLen
            lookY = dy * invLen
            lookZ = dz * invLen

            Matrix.setLookAtM(
                viewMatrix, 0,
                camX, camY, camZ,
                awake.camLookX, awake.camLookY, awake.camLookZ,
                0f, 1f, 0f,
            )
        } else {
            camX = (if (inAwakening) awake.camPosX else gameEngine.playerX) + shakeX
            camY = (if (inAwakening) awake.camPosY else gameEngine.playerY) + shakeY
            camZ = if (inAwakening) awake.camPosZ else gameEngine.playerZ

            val cosPitch = cos(gameEngine.playerPitchRad)
            lookX = sin(gameEngine.playerYawRad) * cosPitch
            lookY = sin(gameEngine.playerPitchRad)
            lookZ = cos(gameEngine.playerYawRad) * cosPitch

            Matrix.setLookAtM(
                viewMatrix, 0,
                camX, camY, camZ,
                camX + lookX, camY + lookY, camZ + lookZ,
                0f, 1f, 0f,
            )
        }
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        GLES20.glUniform3f(uCamPos, camX, camY, camZ)
        GLES20.glUniform3f(uCamDir, lookX, lookY, lookZ)
        val allowFlashlight = gameEngine.flashlightOn && !inCapture && !inAwakening && !gameEngine.isHiding
        GLES20.glUniform1f(uFlashlightOn, if (allowFlashlight) 1f else 0f)
        GLES20.glUniform1f(uFlashlightBattery, (gameEngine.battery / 100f).coerceIn(0f, 1f))
        GLES20.glUniform1f(uTime, elapsedSec)
        val fogDensity = when {
            inCapture && cap.useThirdPersonCamera -> 0.155f
            insideHouse -> 0.056f
            else -> 0.074f
        }
        GLES20.glUniform1f(uFogDensity, fogDensity)
        GLES20.glUniform3f(uFogColor, fogR, fogG, fogB)
        if (insideHouse) {
            val scale = if (inCapture) cap.interiorLightScale.coerceIn(0.35f, 1.0f) else 1.0f
            GLES20.glUniform3f(uAmbientColor, 0.175f * scale, 0.148f * scale, 0.132f * scale)
        } else {
            GLES20.glUniform3f(uAmbientColor, 0.085f, 0.095f, 0.132f)
        }

        val lightScale = if (inCapture) cap.interiorLightScale else 1.0f
        uploadNearestPointLights(camX, camY, camZ, lightScale)

        val tier = performanceManager.activeTier

        // 1. Static Opaque World Architecture (Road, Forest, House, Furniture, Glowing Windows, Car)
        Matrix.setIdentityM(modelMatrix, 0)
        for (batch in staticBatches) {
            if (batch.transparent) continue
            drawMeshWithMatrix(
                mat = modelMatrix,
                mesh = batch.gpuMesh,
                texture = batch.texture,
                tintR = batch.tintR,
                tintG = batch.tintG,
                tintB = batch.tintB,
                specular = batch.specular,
                emissive = batch.emissive,
                alphaScale = 1f,
                doubleSided = batch.doubleSided,
            )
        }

        // 2. Dynamic Paneled Doors (Ground & Upper Floors + Secret Bookshelf Door)
        val props = propMeshes
        if (props != null) {
            for (door in gameEngine.doors) {
                val yawDeg = door.def.closedYawDeg + door.currentAngleDeg
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, door.def.hingeX, door.def.baseY + door.def.height * 0.5f, door.def.hingeZ)
                Matrix.rotateM(modelMatrix, 0, -yawDeg, 0f, 1f, 0f)
                Matrix.scaleM(modelMatrix, 0, door.def.width, door.def.height, door.def.thickness)
                val panelTex = if (door.def.isSecretBookshelf) TextureKind.BOOKSHELF_WOOD else TextureKind.WOOD_FLOOR
                drawMeshWithMatrix(
                    modelMatrix, props.doorPanelMesh, panelTex,
                    if (door.def.isSecretBookshelf) 0.95f else 0.88f,
                    if (door.def.isSecretBookshelf) 0.90f else 0.78f,
                    if (door.def.isSecretBookshelf) 0.85f else 0.70f,
                    specular = if (door.def.isSecretBookshelf) 0.12f else 0.18f,
                    emissive = 0f, alphaScale = 1f, doubleSided = false,
                )

                if (!door.def.isSecretBookshelf) {
                    Matrix.setIdentityM(modelMatrix, 0)
                    Matrix.translateM(modelMatrix, 0, door.def.hingeX, door.def.baseY + 1.08f, door.def.hingeZ)
                    Matrix.rotateM(modelMatrix, 0, -yawDeg, 0f, 1f, 0f)
                    Matrix.translateM(modelMatrix, 0, door.def.width - 0.14f, 0f, 0f)
                    if (door.def.id == "door_granny_room" && kotlin.math.abs(gameEngine.doorKnobTurnDeg) > 0.1f) {
                        Matrix.rotateM(modelMatrix, 0, gameEngine.doorKnobTurnDeg, 0f, 0f, 1f)
                    }
                    drawMeshWithMatrix(
                        modelMatrix, props.doorKnobMesh, TextureKind.METAL_PROP,
                        1.0f, 0.82f, 0.38f, specular = 0.85f, emissive = 0f, alphaScale = 1f, doubleSided = false,
                    )
                }
            }

            // 3. Detailed 3D World Collectibles, Notes & Secret Crimson Tome Lever
            for (item in WorldMap3D.itemDefs) {
                if (item.kind == InteractKind.ITEM && gameEngine.collectedItems.contains(item.id)) continue
                if (item.kind == InteractKind.HIDE_CLOSET ||
                    item.kind == InteractKind.HIDE_BED ||
                    item.kind == InteractKind.FUSE_BOX ||
                    item.kind == InteractKind.DRAWER ||
                    item.kind == InteractKind.ESCAPE_CAR
                ) continue

                if (item.kind == InteractKind.SECRET_LEVER) {
                    val secretUnlocked = gameEngine.doors.firstOrNull { it.def.id == "secret_bookshelf_door" }?.isUnlocked == true
                    Matrix.setIdentityM(modelMatrix, 0)
                    Matrix.translateM(modelMatrix, 0, item.x, item.y, item.z)
                    Matrix.rotateM(modelMatrix, 0, 90f, 0f, 0f, 1f)
                    Matrix.rotateM(modelMatrix, 0, if (secretUnlocked) -36f else -14f, 1f, 0f, 0f)
                    drawMeshWithMatrix(
                        modelMatrix, props.noteMesh, TextureKind.BED_FABRIC,
                        item.r * 1.25f, item.g * 1.1f, item.b * 1.1f,
                        specular = 0.25f, emissive = 0.18f, alphaScale = 1f, doubleSided = false,
                    )
                    continue
                }

                val bob = sin(elapsedSec * 2.3f + item.x) * 0.025f
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, item.x, item.y + bob, item.z)
                Matrix.rotateM(modelMatrix, 0, elapsedSec * 36f, 0f, 1f, 0f)

                val itemMesh = when (item.id) {
                    "lantern" -> props.flashlightPickupMesh
                    "key_study", "master_key", "car_key" -> props.keyMesh
                    "medkit" -> props.medkitMesh
                    "crowbar" -> props.crowbarMesh
                    "gas_can" -> props.gasCanMesh
                    "spark_plug" -> props.sparkPlugMesh
                    else -> if (item.kind == InteractKind.NOTE) props.noteMesh else props.genericToolMesh
                }
                val itemTex = if (item.kind == InteractKind.NOTE) TextureKind.VICTORIAN_WALLPAPER else TextureKind.METAL_PROP
                drawMeshWithMatrix(
                    modelMatrix, itemMesh, itemTex,
                    item.r * 1.15f, item.g * 1.15f, item.b * 1.15f,
                    specular = 0.65f, emissive = 0.25f, alphaScale = 1f, doubleSided = false,
                )
            }
        }

        // 4. Transparent Decals & Contact Shadows (Blood Pools + Static & Granny Contact Shadows)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)

        Matrix.setIdentityM(modelMatrix, 0)
        for (batch in staticBatches) {
            if (!batch.transparent) continue
            if (batch.texture == TextureKind.SOFT_SHADOW && !tier.enableShadows) continue
            drawMeshWithMatrix(
                mat = modelMatrix,
                mesh = batch.gpuMesh,
                texture = batch.texture,
                tintR = batch.tintR,
                tintG = batch.tintG,
                tintB = batch.tintB,
                specular = batch.specular,
                emissive = batch.emissive,
                alphaScale = 1f,
                doubleSided = true,
            )
        }

        // Dynamic Ground Contact Shadow beneath Granny & Player (floor-aware for upstairs!)
        if (props != null && tier.enableShadows) {
            val grannyFloorY = WorldMap3D.floorHeightAt(gameEngine.granny.x, gameEngine.granny.z)
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, gameEngine.granny.x, grannyFloorY + 0.01f, gameEngine.granny.z)
            Matrix.scaleM(modelMatrix, 0, 1.05f, 1f, 1.05f)
            drawMeshWithMatrix(
                modelMatrix, props.shadowDecalMesh, TextureKind.SOFT_SHADOW,
                1f, 1f, 1f, specular = 0f, emissive = 0f, alphaScale = 0.85f, doubleSided = true,
            )

            if (inCapture && cap.useThirdPersonCamera) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, cap.bodyX, 0.015f, cap.bodyZ + 0.15f)
                Matrix.scaleM(modelMatrix, 0, 0.85f, 1f, 1.45f)
                drawMeshWithMatrix(
                    modelMatrix, props.shadowDecalMesh, TextureKind.SOFT_SHADOW,
                    1f, 1f, 1f, specular = 0f, emissive = 0f, alphaScale = 0.78f, doubleSided = true,
                )
            } else if (inAwakening && awake.useThirdPersonCamera) {
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, awake.bodyX, awake.bodyY - 0.03f, awake.bodyZ)
                Matrix.scaleM(modelMatrix, 0, 0.85f, 1f, 1.15f)
                drawMeshWithMatrix(
                    modelMatrix, props.shadowDecalMesh, TextureKind.SOFT_SHADOW,
                    1f, 1f, 1f, specular = 0f, emissive = 0f, alphaScale = 0.55f, doubleSided = true,
                )
            } else if (!gameEngine.isCinematicIntro && !gameEngine.isHiding && !inCapture && !inAwakening) {
                val floorY = WorldMap3D.floorHeightAt(gameEngine.playerX, gameEngine.playerZ)
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, gameEngine.playerX, floorY + 0.01f, gameEngine.playerZ)
                Matrix.scaleM(modelMatrix, 0, 0.90f, 1f, 0.90f)
                drawMeshWithMatrix(
                    modelMatrix, props.shadowDecalMesh, TextureKind.SOFT_SHADOW,
                    1f, 1f, 1f, specular = 0f, emissive = 0f, alphaScale = 0.70f, doubleSided = true,
                )
            }
        }

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)

        // 5. Full Skeletal 3D Characters (Granny + Full 3D Unconscious/Awakening Protagonist OR First-Person Rig)
        val chars = charMeshes
        if (chars != null) {
            GrannyRigAnimator.drawGranny(gameEngine.granny, chars, elapsedSec, meshDrawCallback)
            if (inCapture && cap.useThirdPersonCamera) {
                PlayerRigAnimator.drawFullProtagonist3D(
                    bodyX = cap.bodyX,
                    bodyY = cap.bodyY,
                    bodyZ = cap.bodyZ,
                    bodyYawDeg = cap.bodyYawDeg,
                    bodyLiePitchDeg = cap.bodyLiePitchDeg,
                    dragPhase = cap.dragPullPhase,
                    isBeingDragged = cap.isDraggingBody,
                    breathPhase = gameEngine.playerBreathPhase,
                    meshes = chars,
                    drawer = meshDrawCallback,
                )
            } else if (inAwakening && awake.useThirdPersonCamera) {
                PlayerRigAnimator.drawFullProtagonist3D(
                    bodyX = awake.bodyX,
                    bodyY = awake.bodyY,
                    bodyZ = awake.bodyZ,
                    bodyYawDeg = awake.bodyYawDeg,
                    bodyLiePitchDeg = awake.bodyLiePitchDeg,
                    dragPhase = 0f,
                    isBeingDragged = false,
                    breathPhase = gameEngine.playerBreathPhase,
                    meshes = chars,
                    drawer = meshDrawCallback,
                    eyeOpenAmount = awake.eyeOpenAmount,
                    jawOpenAmount = awake.protagonistJawOpen,
                    headYawDeg = awake.headYawDeg,
                    headPitchDeg = awake.headPitchDeg,
                    hipPitchDeg = awake.hipPitchDeg,
                    kneeFlexDeg = awake.kneeFlexDeg,
                    armReachPitchDeg = awake.armReachPitchDeg,
                    elbowFlexDeg = awake.elbowFlexDeg,
                    isCrawling = awake.isCrawling,
                    walkCycle = awake.walkCycle,
                    moveSpeedNorm = awake.moveSpeedNorm,
                )
            } else if (!inCapture && !inAwakening) {
                val playerFloorY = WorldMap3D.floorHeightAt(gameEngine.playerX, gameEngine.playerZ)
                val solidAhead = WorldMap3D.rayDistToSolidAhead(
                    camX = camX,
                    camZ = camZ,
                    yawRad = gameEngine.playerYawRad,
                    doors = gameEngine.doors,
                    entityFloorY = playerFloorY,
                )
                val armTuckFrac = ((0.58f - solidAhead) / 0.42f).coerceIn(0f, 1f)
                PlayerRigAnimator.drawPlayerLowerBody(gameEngine, chars, meshDrawCallback)
                PlayerRigAnimator.drawFirstPersonArms(gameEngine, chars, camX, camY, camZ, meshDrawCallback, armTuckFrac = armTuckFrac)
            }
        }

        // 6. Volumetric-Style Layered Mist, Floating Dust Motes & Lantern Halos
        if (props != null && tier.volumetricFogLayers > 0) {
            drawVolumetricFogLayers(props, camX, camY, camZ, tier)
        }

        // Clean up GL state
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun drawVolumetricFogLayers(
        props: PropMeshLibrary,
        camX: Float,
        camY: Float,
        camZ: Float,
        tier: QualityTier,
    ) {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)

        val yawDeg = gameEngine.playerYawRad * (180f / PI.toFloat())
        val layerCount = tier.volumetricFogLayers

        // Ambient drifting mist billboards along the road & carport yard
        for (i in 0 until layerCount) {
            val seed = i * 1.37f
            val driftX = sin(elapsedSec * 0.22f + seed) * 2.2f + ((i % 5) - 2) * 1.85f
            val fogZ = 10.8f + ((i * 2.4f + elapsedSec * 0.18f) % 21.0f)
            val fogY = 0.65f + (i % 3) * 0.42f
            val scale = 2.8f + (i % 4) * 0.75f

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, driftX, fogY, fogZ)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, scale * 1.6f, scale * 0.85f, 1f)
            drawMeshWithMatrix(
                modelMatrix, props.fogPlaneMesh, TextureKind.FOG_PUFF,
                0.78f, 0.84f, 0.96f, specular = 0f, emissive = 0.15f, alphaScale = 0.17f, doubleSided = true,
            )
        }

        if (camZ > 6.0f && camX < 14.0f) {
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, 0f, 1.85f, 10.65f)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, 2.5f, 2.2f, 1f)
            drawMeshWithMatrix(
                modelMatrix, props.fogPlaneMesh, TextureKind.FOG_PUFF,
                1.0f, 0.66f, 0.28f, specular = 0f, emissive = 0.85f, alphaScale = 0.22f, doubleSided = true,
            )

            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, -2.36f, 2.65f, 22.0f)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, 3.2f, 2.8f, 1f)
            drawMeshWithMatrix(
                modelMatrix, props.fogPlaneMesh, TextureKind.FOG_PUFF,
                0.62f, 0.76f, 1.0f, specular = 0f, emissive = 0.75f, alphaScale = 0.18f, doubleSided = true,
            )
        } else if (camX > 13.0f && camZ < 10.5f) {
            // Carport warm amber floodlight mist halo around Getaway Sedan
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, 19.5f, 2.25f, 5.8f)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, 2.8f, 2.2f, 1f)
            drawMeshWithMatrix(
                modelMatrix, props.fogPlaneMesh, TextureKind.FOG_PUFF,
                0.96f, 0.68f, 0.36f, specular = 0f, emissive = 0.72f, alphaScale = 0.20f, doubleSided = true,
            )
        } else {
            // Inside house eerie dust haze & floating indoor dust motes
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, camX + sin(elapsedSec * 0.4f) * 1.4f, camY - 0.15f, camZ - 3.2f)
            Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(modelMatrix, 0, 3.4f, 1.9f, 1f)
            drawMeshWithMatrix(
                modelMatrix, props.fogPlaneMesh, TextureKind.FOG_PUFF,
                0.55f, 0.52f, 0.58f, specular = 0f, emissive = 0.10f, alphaScale = 0.14f, doubleSided = true,
            )

            if (tier.enableParticles) {
                for (m in 0 until 7) {
                    val mx = camX + sin(elapsedSec * 0.45f + m * 2.1f) * 2.4f
                    val my = (camY - 0.35f) + ((m * 0.27f + elapsedSec * 0.08f) % 1.35f)
                    val mz = camZ + cos(elapsedSec * 0.38f + m * 1.7f) * 2.4f
                    val sz = 0.16f + (m % 3) * 0.05f
                    Matrix.setIdentityM(modelMatrix, 0)
                    Matrix.translateM(modelMatrix, 0, mx, my, mz)
                    Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
                    Matrix.scaleM(modelMatrix, 0, sz, sz, 1f)
                    drawMeshWithMatrix(
                        modelMatrix, props.fogPlaneMesh, TextureKind.DUST_MOTE,
                        0.92f, 0.88f, 0.78f, specular = 0f, emissive = 0.35f, alphaScale = 0.45f, doubleSided = true,
                    )
                }
            }
        }

        GLES20.glDepthMask(true)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun drawMeshWithMatrix(
        mat: FloatArray,
        mesh: GpuMesh,
        texture: TextureKind,
        tintR: Float,
        tintG: Float,
        tintB: Float,
        specular: Float,
        emissive: Float,
        alphaScale: Float,
        doubleSided: Boolean,
    ) {
        if (doubleSided) {
            GLES20.glDisable(GLES20.GL_CULL_FACE)
        } else {
            GLES20.glEnable(GLES20.GL_CULL_FACE)
        }

        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, mat, 0)
        GLES20.glUniformMatrix4fv(uMvpMatrix, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrix, 1, false, mat, 0)
        GLES20.glUniform3f(uTintColor, tintR, tintG, tintB)
        GLES20.glUniform1f(uSpecular, specular)
        GLES20.glUniform1f(uEmissive, emissive)
        GLES20.glUniform1f(uAlphaScale, alphaScale)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures.idFor(texture))

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vbo)
        val strideBytes = 8 * 4
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, strideBytes, 0)

        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, strideBytes, 3 * 4)

        GLES20.glEnableVertexAttribArray(aTexCoord)
        GLES20.glVertexAttribPointer(aTexCoord, 2, GLES20.GL_FLOAT, false, strideBytes, 6 * 4)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.ibo)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mesh.indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
    }

    private fun uploadNearestPointLights(camX: Float, camY: Float, camZ: Float, lightScale: Float) {
        val maxLights = (performanceManager.activeTier.maxDynamicLights + 2).coerceIn(2, 6)
        val sorted = WorldMap3D.pointLights.sortedBy { l ->
            val dx = l.x - camX
            val dy = l.y - camY
            val dz = l.z - camZ
            dx * dx + dy * dy * 2.2f + dz * dz
        }
        val count = minOf(maxLights, sorted.size)
        for (i in 0 until 6) {
            if (i < count) {
                val l = sorted[i]
                val flicker = if (l.slowFlicker) {
                    val slowWave = 0.76f + 0.24f * sin(elapsedSec * 2.1f + l.flickerSeed) * cos(elapsedSec * 3.7f + l.flickerSeed)
                    val dip = if (sin(elapsedSec * 0.85f + l.flickerSeed * 2.3f) > 0.94f) 0.35f else 1.0f
                    slowWave * dip * lightScale
                } else {
                    (0.92f + 0.08f * sin(elapsedSec * 6.4f + l.flickerSeed) * cos(elapsedSec * 11.2f + l.flickerSeed)) * lightScale
                }
                lightPosArray[i * 3] = l.x
                lightPosArray[i * 3 + 1] = l.y
                lightPosArray[i * 3 + 2] = l.z
                lightColArray[i * 3] = l.r * flicker
                lightColArray[i * 3 + 1] = l.g * flicker
                lightColArray[i * 3 + 2] = l.b * flicker
            } else {
                lightPosArray[i * 3] = 0f
                lightPosArray[i * 3 + 1] = 0f
                lightPosArray[i * 3 + 2] = 0f
                lightColArray[i * 3] = 0f
                lightColArray[i * 3 + 1] = 0f
                lightColArray[i * 3 + 2] = 0f
            }
        }
        GLES20.glUniform1f(uActiveLightCount, count.toFloat())
        GLES20.glUniform3fv(uLightPositions, 6, lightPosArray, 0)
        GLES20.glUniform3fv(uLightColors, 6, lightColArray, 0)
    }

    private fun releaseGpuResources() {
        textures.releaseAll()
        staticBatches.forEach { it.gpuMesh.release() }
        staticBatches = emptyList()
        charMeshes?.release()
        charMeshes = null
        propMeshes?.release()
        propMeshes = null
    }

    private fun createProgram(vsSource: String, fsSource: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vsSource)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fsSource)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            uniform mat4 uMvpMatrix;
            uniform mat4 uModelMatrix;

            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec2 aTexCoord;

            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec2 vTexCoord;

            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vNormal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);
                vTexCoord = aTexCoord;
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;

            uniform sampler2D uTexture;
            uniform vec3 uTintColor;
            uniform float uSpecular;
            uniform float uEmissive;
            uniform float uAlphaScale;

            uniform vec3 uCamPos;
            uniform vec3 uCamDir;
            uniform float uFlashlightOn;
            uniform float uFlashlightBattery;
            uniform float uTime;
            uniform float uFogDensity;
            uniform vec3 uFogColor;
            uniform vec3 uAmbientColor;
            uniform float uActiveLightCount;
            uniform vec3 uLightPositions[6];
            uniform vec3 uLightColors[6];

            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec2 vTexCoord;

            void main() {
                vec4 texSample = texture2D(uTexture, vTexCoord);
                float outAlpha = texSample.a * uAlphaScale;
                if (outAlpha < 0.02) discard;

                vec3 baseColor = texSample.rgb * uTintColor;
                vec3 rawN = normalize(vNormal);
                // Procedural micro-detail normal perturbation for plaster, skin & wood relief
                float g1 = sin(vTexCoord.x * 58.0 + vWorldPos.z * 7.0);
                float g2 = cos(vTexCoord.y * 58.0 + vWorldPos.x * 7.0);
                vec3 N = normalize(rawN + vec3(g1 * 0.035, 0.0, g2 * 0.035));
                vec3 toCam = uCamPos - vWorldPos;
                float camDist = length(toCam);
                vec3 V = toCam / max(camDist, 0.001);

                // Vertical hemisphere bounce ambient + cold moonlight directional
                vec3 hemiAmbient = mix(vec3(0.052, 0.046, 0.056), uAmbientColor, N.y * 0.5 + 0.5);
                vec3 moonDir = normalize(vec3(-0.32, 0.85, 0.42));
                float moonDiff = max(dot(N, moonDir), 0.0);
                float fresnel = pow(1.0 - max(dot(N, V), 0.0), 3.0);
                vec3 lit = baseColor * (hemiAmbient + vec3(0.09, 0.11, 0.16) * moonDiff);
                lit += vec3(0.035, 0.045, 0.075) * fresnel;

                // Dynamic flickering & antique warm point lights
                for (int i = 0; i < 6; i++) {
                    if (float(i) >= uActiveLightCount) break;
                    vec3 lVec = uLightPositions[i] - vWorldPos;
                    float lDist = length(lVec);
                    vec3 L = lVec / max(lDist, 0.001);
                    float atten = 1.0 / (1.0 + 0.085 * lDist + 0.026 * lDist * lDist);
                    float diff = max(dot(N, L), 0.0);
                    vec3 H = normalize(L + V);
                    float spec = pow(max(dot(N, H), 0.0), 22.0) * uSpecular;
                    lit += (baseColor * diff + vec3(spec)) * uLightColors[i] * atten * 1.75;
                }

                // First-person flashlight spotlight with soft edge & specular sheen
                if (uFlashlightOn > 0.5) {
                    vec3 fVec = normalize(vWorldPos - uCamPos);
                    float theta = dot(fVec, normalize(uCamDir));
                    float spot = smoothstep(0.83, 0.965, theta);
                    float fAtten = 1.0 / (1.0 + 0.085 * camDist + 0.026 * camDist * camDist);
                    float flicker = uFlashlightBattery < 0.22 ? (0.62 + 0.38 * sin(uTime * 29.0)) : 1.0;
                    float fDiff = max(dot(N, V), 0.18);
                    float fSpec = pow(max(dot(N, V), 0.0), 28.0) * uSpecular * 0.85;
                    lit += (baseColor * fDiff + vec3(fSpec)) * vec3(1.0, 0.94, 0.82) * spot * fAtten * 2.05 * flicker;
                }

                // Self-luminous windows, glowing lantern cores, hostile eyes, volumetric fog wisps
                lit += baseColor * uEmissive;

                // Distance + subtle low-lying ground mist fog
                float heightFog = 1.0 + clamp(1.3 - vWorldPos.y, 0.0, 0.8) * 0.22;
                float fogFactor = exp(-pow(camDist * uFogDensity * heightFog, 1.55));
                fogFactor = clamp(fogFactor, 0.0, 1.0);
                vec3 finalColor = mix(uFogColor, lit, fogFactor);

                gl_FragColor = vec4(finalColor, outAlpha);
            }
        """
    }
}
