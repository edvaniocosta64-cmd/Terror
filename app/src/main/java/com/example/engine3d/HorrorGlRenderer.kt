package com.example.engine3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
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
    private var staticBuffer: FloatBuffer? = null
    private var staticVertexCount = 0

    // Reusable unit cube buffer for dynamic objects (doors, items, articulated Granny parts, fog motes)
    private var unitCubeBuffer: FloatBuffer? = null
    private val unitCubeVertexCount = 36

    // Shader handles
    private var aPosition = 0
    private var aNormal = 0
    private var aColor = 0
    private var aMatKind = 0
    private var uMvpMatrix = 0
    private var uModelMatrix = 0
    private var uCamPos = 0
    private var uCamDir = 0
    private var uFlashlightOn = 0
    private var uFlashlightBattery = 0
    private var uTime = 0
    private var uFogDensity = 0
    private var uColorOverride = 0
    private var uUseOverride = 0
    private var uMatOverride = 0
    private var uLightPositions = 0
    private var uLightColors = 0
    private var uActiveLightCount = 0

    // Pre-allocated matrices (zero GC allocations in onDrawFrame!)
    private val projMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val vpMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val lightPosArray = FloatArray(12) // 4 lights * vec3
    private val lightColArray = FloatArray(12) // 4 lights * vec3

    private var aspectRatio = 16f / 9f
    private var lastFrameNano = 0L
    private var elapsedSec = 0f
    private var telemetryEmitCounter = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.02f, 0.025f, 0.04f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        programId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPosition = GLES20.glGetAttribLocation(programId, "aPosition")
        aNormal = GLES20.glGetAttribLocation(programId, "aNormal")
        aColor = GLES20.glGetAttribLocation(programId, "aColor")
        aMatKind = GLES20.glGetAttribLocation(programId, "aMatKind")

        uMvpMatrix = GLES20.glGetUniformLocation(programId, "uMvpMatrix")
        uModelMatrix = GLES20.glGetUniformLocation(programId, "uModelMatrix")
        uCamPos = GLES20.glGetUniformLocation(programId, "uCamPos")
        uCamDir = GLES20.glGetUniformLocation(programId, "uCamDir")
        uFlashlightOn = GLES20.glGetUniformLocation(programId, "uFlashlightOn")
        uFlashlightBattery = GLES20.glGetUniformLocation(programId, "uFlashlightBattery")
        uTime = GLES20.glGetUniformLocation(programId, "uTime")
        uFogDensity = GLES20.glGetUniformLocation(programId, "uFogDensity")
        uColorOverride = GLES20.glGetUniformLocation(programId, "uColorOverride")
        uUseOverride = GLES20.glGetUniformLocation(programId, "uUseOverride")
        uMatOverride = GLES20.glGetUniformLocation(programId, "uMatOverride")
        uLightPositions = GLES20.glGetUniformLocation(programId, "uLightPositions")
        uLightColors = GLES20.glGetUniformLocation(programId, "uLightColors")
        uActiveLightCount = GLES20.glGetUniformLocation(programId, "uActiveLightCount")

        buildStaticWorldBuffer()
        buildUnitCubeBuffer()
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

        // Smooth frame pacer when target FPS < display native Hz (e.g. 60 or 30 FPS mode)
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
        if (tierChanged) {
            val tier = performanceManager.activeTier
            Matrix.perspectiveM(projMatrix, 0, 68f, aspectRatio, 0.08f, tier.drawDistance)
            onQualityScaleChanged(tier)
        }
        telemetryEmitCounter++
        if (telemetryEmitCounter >= 12) {
            telemetryEmitCounter = 0
            onTelemetryUpdate(performanceManager.snapshot())
        }

        val insideHouse = gameEngine.playerZ < 10.0f
        if (insideHouse) {
            GLES20.glClearColor(0.015f, 0.012f, 0.018f, 1.0f)
        } else {
            GLES20.glClearColor(0.024f, 0.032f, 0.052f, 1.0f)
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(programId)

        // Camera matrices with subtle horror shake when damaged or slammed
        val shakeX = if (gameEngine.cameraShakeAmount > 0f) {
            sin(elapsedSec * 48f) * 0.06f * gameEngine.cameraShakeAmount
        } else 0f
        val shakeY = if (gameEngine.cameraShakeAmount > 0f) {
            cos(elapsedSec * 57f) * 0.05f * gameEngine.cameraShakeAmount
        } else 0f

        val camX = gameEngine.playerX + shakeX
        val camY = gameEngine.playerY + shakeY
        val camZ = gameEngine.playerZ

        val cosPitch = cos(gameEngine.playerPitchRad)
        val lookX = sin(gameEngine.playerYawRad) * cosPitch
        val lookY = sin(gameEngine.playerPitchRad)
        val lookZ = cos(gameEngine.playerYawRad) * cosPitch

        Matrix.setLookAtM(
            viewMatrix, 0,
            camX, camY, camZ,
            camX + lookX, camY + lookY, camZ + lookZ,
            0f, 1f, 0f,
        )
        Matrix.multiplyMM(vpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // Upload shared lighting uniforms
        GLES20.glUniform3f(uCamPos, camX, camY, camZ)
        GLES20.glUniform3f(uCamDir, lookX, lookY, lookZ)
        GLES20.glUniform1f(uFlashlightOn, if (gameEngine.flashlightOn) 1f else 0f)
        GLES20.glUniform1f(uFlashlightBattery, (gameEngine.battery / 100f).coerceIn(0f, 1f))
        GLES20.glUniform1f(uTime, elapsedSec)
        GLES20.glUniform1f(uFogDensity, if (insideHouse) 0.145f else 0.088f)

        uploadNearestPointLights(camX, camZ)

        // 1. Draw Static World Geometry
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uMvpMatrix, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrix, 1, false, modelMatrix, 0)
        GLES20.glUniform1f(uUseOverride, 0f)

        staticBuffer?.let { buf ->
            bindVertexBuffer(buf)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, staticVertexCount)
        }

        // Bind Unit Cube once for all dynamic props, doors & articulated Granny
        unitCubeBuffer?.let { cubeBuf ->
            bindVertexBuffer(cubeBuf)
            GLES20.glUniform1f(uUseOverride, 1f)

            // 2. Draw Hinged 3D Doors
            for (door in gameEngine.doors) {
                val yawDeg = door.def.closedYawDeg + door.currentAngleDeg
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, door.def.hingeX, door.def.height * 0.5f, door.def.hingeZ)
                Matrix.rotateM(modelMatrix, 0, -yawDeg, 0f, 1f, 0f)
                Matrix.translateM(modelMatrix, 0, door.def.width * 0.5f, 0f, 0f)
                Matrix.scaleM(modelMatrix, 0, door.def.width, door.def.height, door.def.thickness)
                drawCube(0.28f, 0.16f, 0.09f, MaterialKind.WOOD_FLOOR.id)
            }

            // 3. Draw Interactive 3D World Items (collectibles & notes)
            for (item in WorldMap3D.itemDefs) {
                if (item.kind == InteractKind.ITEM && gameEngine.collectedItems.contains(item.id)) continue
                if (item.kind == InteractKind.HIDE_CLOSET || item.kind == InteractKind.FUSE_BOX) continue
                val bob = sin(elapsedSec * 2.4f + item.x) * 0.03f
                Matrix.setIdentityM(modelMatrix, 0)
                Matrix.translateM(modelMatrix, 0, item.x, item.y + bob, item.z)
                Matrix.rotateM(modelMatrix, 0, elapsedSec * 35f, 0f, 1f, 0f)
                val sx = if (item.kind == InteractKind.NOTE) 0.24f else 0.18f
                val sy = if (item.kind == InteractKind.NOTE) 0.03f else 0.12f
                val sz = if (item.kind == InteractKind.NOTE) 0.30f else 0.18f
                Matrix.scaleM(modelMatrix, 0, sx, sy, sz)
                drawCube(item.r, item.g, item.b, MaterialKind.LANTERN_GLOW.id)
            }

            // 4. Draw Articulated 3D Granny Character (with facial expressions + blood stains!)
            drawArticulatedGranny()

            // 5. Floating fog motes (if quality allows)
            if (performanceManager.activeTier.enableParticles) {
                drawFogMotes(camX, camZ)
            }
        }
    }

    private fun drawArticulatedGranny() {
        val g = gameEngine.granny
        val swing = sin(g.walkCycle) * 22f
        val crouchHunch = if (g.aiState == GrannyAiState.HUNTING_PLAYER) 14f else 8f
        val isHostile = g.expression == GrannyExpression.MENACING_GRIN || g.expression == GrannyExpression.HOSTILE_SHRIEK

        // Legs
        drawGrannyPart(g.x, 0.38f, g.z, g.yawDeg, swing, 0f, -0.11f, 0f, 0f, 0.13f, 0.76f, 0.14f, 0.16f, 0.14f, 0.16f, MaterialKind.GRANNY_DRESS.id)
        drawGrannyPart(g.x, 0.38f, g.z, g.yawDeg, -swing, 0f, 0.11f, 0f, 0f, 0.13f, 0.76f, 0.14f, 0.16f, 0.14f, 0.16f, MaterialKind.GRANNY_DRESS.id)

        // Torso & old Victorian tattered dress
        drawGrannyPart(g.x, 0.96f, g.z, g.yawDeg, crouchHunch, 0f, 0f, 0f, 0f, 0.42f, 0.78f, 0.28f, 0.18f, 0.16f, 0.20f, MaterialKind.GRANNY_DRESS.id)

        // Stylized blood stains on Granny's dress/apron front (+Z local face)
        drawGrannyPart(g.x, 0.88f, g.z, g.yawDeg, crouchHunch, 0f, -0.06f, 0.0f, 0.145f, 0.16f, 0.24f, 0.02f, 0.48f, 0.04f, 0.05f, MaterialKind.BLOOD_DECAL.id)
        drawGrannyPart(g.x, 0.68f, g.z, g.yawDeg, crouchHunch, 0f, 0.08f, 0.0f, 0.145f, 0.13f, 0.18f, 0.02f, 0.44f, 0.03f, 0.04f, MaterialKind.BLOOD_DECAL.id)

        // Arms (reaching forward when hunting, relaxed/gesturing when greeting)
        val armReachX = if (g.aiState == GrannyAiState.HUNTING_PLAYER) -68f + swing * 0.5f else -12f - swing * 0.6f
        drawGrannyPart(g.x, 1.10f, g.z, g.yawDeg, armReachX, 0f, -0.27f, 0f, 0.08f, 0.11f, 0.56f, 0.11f, 0.68f, 0.60f, 0.54f, MaterialKind.GRANNY_SKIN.id)
        drawGrannyPart(g.x, 1.10f, g.z, g.yawDeg, armReachX, 0f, 0.27f, 0f, 0.08f, 0.11f, 0.56f, 0.11f, 0.68f, 0.60f, 0.54f, MaterialKind.GRANNY_SKIN.id)

        // Head + Hair Bun
        drawGrannyPart(g.x, 1.54f, g.z, g.yawDeg, crouchHunch * 0.5f, g.headTiltDeg, 0f, 0f, 0.04f, 0.27f, 0.31f, 0.27f, 0.72f, 0.64f, 0.58f, MaterialKind.GRANNY_SKIN.id)
        drawGrannyPart(g.x, 1.62f, g.z, g.yawDeg, crouchHunch * 0.5f, g.headTiltDeg, 0f, 0.03f, -0.14f, 0.20f, 0.18f, 0.16f, 0.68f, 0.69f, 0.73f, MaterialKind.GRANNY_HAIR.id)

        // Eyes (glow faint pale-red when hostile/menacing)
        val eyeMat = if (isHostile) MaterialKind.GRANNY_EYE_GLOW.id else MaterialKind.METAL_PROP.id
        val eyeR = if (isHostile) 0.95f else 0.08f
        val eyeG = if (isHostile) 0.25f else 0.07f
        val eyeB = if (isHostile) 0.22f else 0.07f
        val eyeScaleY = if (g.expression == GrannyExpression.CREEPY_STARE || g.expression == GrannyExpression.HOSTILE_SHRIEK) 0.055f else 0.04f
        drawGrannyPart(g.x, 1.57f, g.z, g.yawDeg, crouchHunch * 0.5f, g.headTiltDeg, -0.065f, 0f, 0.175f, 0.048f, eyeScaleY, 0.02f, eyeR, eyeG, eyeB, eyeMat)
        drawGrannyPart(g.x, 1.57f, g.z, g.yawDeg, crouchHunch * 0.5f, g.headTiltDeg, 0.065f, 0f, 0.175f, 0.048f, eyeScaleY, 0.02f, eyeR, eyeG, eyeB, eyeMat)

        // Eyebrows (animated angle per expression!)
        val browAngle = when (g.expression) {
            GrannyExpression.GENTLE_SMILE -> 8f
            GrannyExpression.CREEPY_STARE -> 0f
            GrannyExpression.SUSPICIOUS -> -16f
            GrannyExpression.MENACING_GRIN -> -26f
            GrannyExpression.HOSTILE_SHRIEK -> -32f
        }
        drawGrannyPart(g.x, 1.62f, g.z, g.yawDeg, crouchHunch * 0.5f, g.headTiltDeg + browAngle, -0.065f, 0f, 0.178f, 0.065f, 0.018f, 0.02f, 0.45f, 0.44f, 0.46f, MaterialKind.GRANNY_HAIR.id)
        drawGrannyPart(g.x, 1.62f, g.z, g.yawDeg, crouchHunch * 0.5f, g.headTiltDeg - browAngle, 0.065f, 0f, 0.178f, 0.065f, 0.018f, 0.02f, 0.45f, 0.44f, 0.46f, MaterialKind.GRANNY_HAIR.id)

        // Animated Mouth / Jaw
        val mouthH = 0.02f + g.jawOpenAmount * 0.075f
        val mouthW = if (g.expression == GrannyExpression.GENTLE_SMILE || g.expression == GrannyExpression.MENACING_GRIN) 0.11f else 0.08f
        drawGrannyPart(g.x, 1.46f - g.jawOpenAmount * 0.02f, g.z, g.yawDeg, crouchHunch * 0.5f, g.headTiltDeg, 0f, 0f, 0.176f, mouthW, mouthH, 0.022f, 0.18f, 0.04f, 0.05f, MaterialKind.BLOOD_DECAL.id)
    }

    private fun drawGrannyPart(
        baseX: Float, baseY: Float, baseZ: Float,
        yawDeg: Float, pitchX: Float, rollZ: Float,
        localX: Float, localY: Float, localZ: Float,
        scaleX: Float, scaleY: Float, scaleZ: Float,
        r: Float, g: Float, b: Float, matId: Float,
    ) {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, baseX, baseY, baseZ)
        Matrix.rotateM(modelMatrix, 0, yawDeg, 0f, 1f, 0f)
        Matrix.translateM(modelMatrix, 0, localX, localY, localZ)
        if (pitchX != 0f) Matrix.rotateM(modelMatrix, 0, pitchX, 1f, 0f, 0f)
        if (rollZ != 0f) Matrix.rotateM(modelMatrix, 0, rollZ, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, scaleX, scaleY, scaleZ)
        drawCube(r, g, b, matId)
    }

    private fun drawFogMotes(camX: Float, camZ: Float) {
        for (i in 0 until 10) {
            val phase = i * 0.77f + elapsedSec * 0.35f
            val mx = camX + sin(phase * 1.3f + i) * 4.5f
            val my = 0.4f + ((phase * 0.6f) % 2.4f)
            val mz = camZ + cos(phase * 1.1f + i * 2f) * 4.5f
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, mx, my, mz)
            Matrix.scaleM(modelMatrix, 0, 0.04f, 0.04f, 0.04f)
            drawCube(0.45f, 0.50f, 0.58f, MaterialKind.LANTERN_GLOW.id)
        }
    }

    private fun drawCube(r: Float, g: Float, b: Float, matId: Float) {
        Matrix.multiplyMM(mvpMatrix, 0, vpMatrix, 0, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uMvpMatrix, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrix, 1, false, modelMatrix, 0)
        GLES20.glUniform3f(uColorOverride, r, g, b)
        GLES20.glUniform1f(uMatOverride, matId)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, unitCubeVertexCount)
    }

    private fun uploadNearestPointLights(camX: Float, camZ: Float) {
        val maxLights = performanceManager.activeTier.maxDynamicLights.coerceIn(1, 4)
        val sorted = WorldMap3D.pointLights.sortedBy { l ->
            val dx = l.x - camX
            val dz = l.z - camZ
            dx * dx + dz * dz
        }
        val count = minOf(maxLights, sorted.size)
        for (i in 0 until count) {
            val l = sorted[i]
            val flicker = 0.86f + 0.14f * sin(elapsedSec * 7.5f + l.flickerSeed) * cos(elapsedSec * 13.1f + l.flickerSeed)
            lightPosArray[i * 3] = l.x
            lightPosArray[i * 3 + 1] = l.y
            lightPosArray[i * 3 + 2] = l.z
            lightColArray[i * 3] = l.r * flicker
            lightColArray[i * 3 + 1] = l.g * flicker
            lightColArray[i * 3 + 2] = l.b * flicker
        }
        GLES20.glUniform1i(uActiveLightCount, count)
        GLES20.glUniform3fv(uLightPositions, 4, lightPosArray, 0)
        GLES20.glUniform3fv(uLightColors, 4, lightColArray, 0)
    }

    private fun bindVertexBuffer(buf: FloatBuffer) {
        val strideBytes = 10 * 4
        buf.position(0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, strideBytes, buf)

        buf.position(3)
        GLES20.glEnableVertexAttribArray(aNormal)
        GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, strideBytes, buf)

        buf.position(6)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glVertexAttribPointer(aColor, 3, GLES20.GL_FLOAT, false, strideBytes, buf)

        buf.position(9)
        GLES20.glEnableVertexAttribArray(aMatKind)
        GLES20.glVertexAttribPointer(aMatKind, 1, GLES20.GL_FLOAT, false, strideBytes, buf)
    }

    private fun buildStaticWorldBuffer() {
        val boxes = WorldMap3D.staticBoxes
        val floatsPerBox = 36 * 10
        val data = FloatArray(boxes.size * floatsPerBox)
        var offset = 0
        for (b in boxes) {
            offset = writeBoxVertices(data, offset, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ, b.r, b.g, b.b, b.material.id)
        }
        staticVertexCount = boxes.size * 36
        staticBuffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    private fun buildUnitCubeBuffer() {
        val data = FloatArray(36 * 10)
        writeBoxVertices(data, 0, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 1f, 1f, 1f, 0f)
        unitCubeBuffer = ByteBuffer.allocateDirect(data.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(data)
                position(0)
            }
    }

    private fun writeBoxVertices(
        out: FloatArray,
        startOffset: Int,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        r: Float, g: Float, b: Float, matId: Float,
    ): Int {
        var idx = startOffset
        fun v(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
            out[idx++] = x; out[idx++] = y; out[idx++] = z
            out[idx++] = nx; out[idx++] = ny; out[idx++] = nz
            out[idx++] = r; out[idx++] = g; out[idx++] = b
            out[idx++] = matId
        }
        fun quad(
            ax: Float, ay: Float, az: Float,
            bx: Float, by: Float, bz: Float,
            cx: Float, cy: Float, cz: Float,
            dx: Float, dy: Float, dz: Float,
            nx: Float, ny: Float, nz: Float,
        ) {
            v(ax, ay, az, nx, ny, nz); v(bx, by, bz, nx, ny, nz); v(cx, cy, cz, nx, ny, nz)
            v(ax, ay, az, nx, ny, nz); v(cx, cy, cz, nx, ny, nz); v(dx, dy, dz, nx, ny, nz)
        }
        // Front (+Z)
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0f, 0f, 1f)
        // Back (-Z)
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0f, 0f, -1f)
        // Left (-X)
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1f, 0f, 0f)
        // Right (+X)
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1f, 0f, 0f)
        // Top (+Y)
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0f, 1f, 0f)
        // Bottom (-Y)
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0f, -1f, 0f)
        return idx
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
            uniform vec3 uColorOverride;
            uniform float uUseOverride;
            uniform float uMatOverride;

            attribute vec3 aPosition;
            attribute vec3 aNormal;
            attribute vec3 aColor;
            attribute float aMatKind;

            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying float vMatKind;

            void main() {
                vec4 worldPos = uModelMatrix * vec4(aPosition, 1.0);
                vWorldPos = worldPos.xyz;
                vNormal = normalize((uModelMatrix * vec4(aNormal, 0.0)).xyz);
                vColor = mix(aColor, uColorOverride, uUseOverride);
                vMatKind = mix(aMatKind, uMatOverride, uUseOverride);
                gl_Position = uMvpMatrix * vec4(aPosition, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;

            uniform vec3 uCamPos;
            uniform vec3 uCamDir;
            uniform float uFlashlightOn;
            uniform float uFlashlightBattery;
            uniform float uTime;
            uniform float uFogDensity;
            uniform int uActiveLightCount;
            uniform vec3 uLightPositions[4];
            uniform vec3 uLightColors[4];

            varying vec3 vWorldPos;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying float vMatKind;

            void main() {
                vec3 N = normalize(vNormal);
                vec3 toCam = uCamPos - vWorldPos;
                float camDist = length(toCam);
                vec3 V = toCam / max(camDist, 0.001);

                // Procedural surface micro-detail (wood planks, stone, foliage, blood sheen)
                float grain = 1.0;
                if (vMatKind > 1.5 && vMatKind < 3.5) {
                    // Wood planks / aged wallpaper stripes
                    float stripe = sin(vWorldPos.x * 9.0 + vWorldPos.z * 9.0 + vWorldPos.y * 5.0);
                    grain = 0.88 + 0.12 * stripe;
                } else if (vMatKind > 7.5 && vMatKind < 8.5) {
                    // Cellar damp stone blocks
                    float block = sin(vWorldPos.x * 4.5) * cos(vWorldPos.y * 6.0) * sin(vWorldPos.z * 4.5);
                    grain = 0.82 + 0.18 * block;
                } else if (vMatKind > 5.5 && vMatKind < 6.5) {
                    // Stylized blood decal sheen
                    grain = 1.08;
                }

                vec3 baseColor = vColor * grain;

                // Self-luminous lanterns & hostile eyes
                if (vMatKind > 6.5 && vMatKind < 7.5) {
                    gl_FragColor = vec4(baseColor * 1.15, 1.0);
                    return;
                }
                if (vMatKind > 12.5) {
                    gl_FragColor = vec4(baseColor * 1.35, 1.0);
                    return;
                }

                // Cold night moonlight ambient
                vec3 moonDir = normalize(vec3(-0.35, 0.85, 0.40));
                float moonDiff = max(dot(N, moonDir), 0.0);
                vec3 lit = baseColor * (vec3(0.06, 0.075, 0.11) + vec3(0.08, 0.10, 0.14) * moonDiff);

                // Dynamic Point Lights
                for (int i = 0; i < 4; i++) {
                    if (i >= uActiveLightCount) break;
                    vec3 lVec = uLightPositions[i] - vWorldPos;
                    float lDist = length(lVec);
                    vec3 L = lVec / max(lDist, 0.001);
                    float atten = 1.0 / (1.0 + 0.16 * lDist + 0.075 * lDist * lDist);
                    float diff = max(dot(N, L), 0.0);
                    lit += baseColor * uLightColors[i] * diff * atten * 1.45;
                }

                // First-Person Flashlight Cone
                if (uFlashlightOn > 0.5) {
                    vec3 fVec = normalize(vWorldPos - uCamPos);
                    float theta = dot(fVec, normalize(uCamDir));
                    float spot = smoothstep(0.84, 0.96, theta);
                    float fAtten = 1.0 / (1.0 + 0.09 * camDist + 0.028 * camDist * camDist);
                    float flicker = uFlashlightBattery < 0.22 ? (0.65 + 0.35 * sin(uTime * 27.0)) : 1.0;
                    float fDiff = max(dot(N, V), 0.18);
                    lit += baseColor * vec3(1.0, 0.94, 0.80) * spot * fAtten * fDiff * 1.85 * flicker;
                }

                // Exponential night fog
                float fogFactor = exp(-pow(camDist * uFogDensity, 1.65));
                fogFactor = clamp(fogFactor, 0.0, 1.0);
                vec3 fogColor = vec3(0.025, 0.030, 0.048);
                vec3 finalColor = mix(fogColor, lit, fogFactor);

                gl_FragColor = vec4(finalColor, 1.0);
            }
        """
    }
}
