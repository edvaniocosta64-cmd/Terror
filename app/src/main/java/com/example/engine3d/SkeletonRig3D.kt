package com.example.engine3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class SubMeshDraw(
    val mesh: GpuMesh,
    val texture: TextureKind,
    val tintR: Float = 1f,
    val tintG: Float = 1f,
    val tintB: Float = 1f,
    val specular: Float = 0.18f,
    val emissive: Float = 0f,
    val doubleSided: Boolean = false,
)

enum class CharacterMeshKind {
    SCULPTED_HEAD_GRANNY,
    JAW_GRANNY,
    HAIR_BUN,
    HAIR_STRAND,
    NECK,
    TORSO_GRANNY,
    SKIRT_GRANNY,
    SHAWL_COLLAR,
    EYEBALL,
    IRIS_PUPIL,
    BROW,
    EYELID,
    UPPER_ARM,
    FOREARM,
    JOINT_SPHERE,
    PALM,
    FINGER,
    DISTAL_FINGER,
    SLEEVE_CUFF,
    JACKET_COLLAR,
    THIGH,
    SHIN,
    FOOT_SHOE,
    SCULPTED_HEAD_PLAYER,
    SHORT_HAIR_PLAYER,
    TORSO_PLAYER,
    FLASHLIGHT_BODY,
    FLASHLIGHT_LENS,
}

/**
 * Renderer-agnostic provider of organic 3D character anatomical meshes.
 * Parameterized by backend mesh handle [M] ([MeshData] on CPU, [GpuMesh] on OpenGL ES, or Filament mesh).
 */
interface CharacterMeshProvider<M> {
    val sculptedHeadGranny: M
    val jawGranny: M
    val hairBun: M
    val hairStrand: M
    val neckMesh: M
    val torsoGranny: M
    val skirtGranny: M
    val shawlCollar: M
    val eyeball: M
    val irisPupil: M
    val browMesh: M
    val eyelidMesh: M
    val upperArm: M
    val forearm: M
    val jointSphere: M
    val palmMesh: M
    val fingerMesh: M
    val distalFinger: M
    val sleeveCuff: M
    val jacketCollar: M
    val thighMesh: M
    val shinMesh: M
    val footShoeMesh: M
    val sculptedHeadPlayer: M
    val shortHairPlayer: M
    val torsoPlayer: M
    val flashlightBody: M
    val flashlightLens: M
}

/**
 * Pure CPU anatomical 3D character mesh library shared by Granny and the Player character.
 * Zero OpenGL calls: builds sculpted, UV-mapped, normal-smoothed [MeshData] once for any renderer.
 */
class CharacterCpuMeshLibrary : CharacterMeshProvider<MeshData> {
    override val sculptedHeadGranny: MeshData = MeshBuilder3D().apply {
        addSculptedHead(rx = 0.106f, ry = 0.130f, rz = 0.116f, rings = 22, sectors = 28, gauntness = 0.68f)
    }.build()

    override val jawGranny: MeshData = MeshBuilder3D().apply {
        addEllipsoid(cx = 0f, cy = -0.022f, cz = 0.042f, rx = 0.068f, ry = 0.040f, rz = 0.068f, rings = 10, sectors = 16)
    }.build()

    override val hairBun: MeshData = MeshBuilder3D().apply {
        addEllipsoid(cx = 0f, cy = 0.04f, cz = -0.108f, rx = 0.088f, ry = 0.082f, rz = 0.078f, rings = 12, sectors = 16)
        addEllipsoid(cx = 0f, cy = 0.032f, cz = -0.01f, rx = 0.112f, ry = 0.105f, rz = 0.118f, rings = 12, sectors = 18)
        addEllipsoid(cx = 0.082f, cy = 0.055f, cz = 0.018f, rx = 0.036f, ry = 0.042f, rz = 0.052f, rings = 8, sectors = 12)
        addEllipsoid(cx = -0.082f, cy = 0.055f, cz = 0.018f, rx = 0.036f, ry = 0.042f, rz = 0.052f, rings = 8, sectors = 12)
    }.build()

    override val hairStrand: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.15f, yTop = 0.02f, rxBottom = 0.011f, rzBottom = 0.013f, rxTop = 0.030f, rzTop = 0.026f, rings = 5, sectors = 10)
    }.build()

    override val neckMesh: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.02f, yTop = 0.13f, rxBottom = 0.056f, rzBottom = 0.056f, rxTop = 0.048f, rzTop = 0.050f, rings = 5, sectors = 12)
    }.build()

    override val torsoGranny: MeshData = MeshBuilder3D().apply {
        addSculptedTorso(
            yBottom = 0f, yTop = 0.50f,
            hipRx = 0.155f, hipRz = 0.125f,
            waistRx = 0.135f, waistRz = 0.108f,
            chestRx = 0.172f, chestRz = 0.128f,
            shoulderRx = 0.188f, shoulderRz = 0.105f,
            hunchCurveZ = 0.040f,
            sectors = 20,
        )
    }.build()

    override val skirtGranny: MeshData = MeshBuilder3D().apply {
        addFlaredSkirt(yBottom = -0.74f, yTop = 0.05f, topRx = 0.155f, topRz = 0.125f, botRx = 0.31f, botRz = 0.28f, rings = 9, sectors = 28)
    }.build()

    override val shawlCollar: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.14f, yTop = 0.04f, rxBottom = 0.22f, rzBottom = 0.16f, rxTop = 0.10f, rzTop = 0.09f, rings = 4, sectors = 18, bulgeFactor = 0.015f)
    }.build()

    override val eyeball: MeshData = MeshBuilder3D().apply {
        addEllipsoid(rx = 0.019f, ry = 0.019f, rz = 0.019f, rings = 10, sectors = 14)
    }.build()

    override val irisPupil: MeshData = MeshBuilder3D().apply {
        addEllipsoid(rx = 0.010f, ry = 0.010f, rz = 0.008f, rings = 8, sectors = 12)
    }.build()

    override val browMesh: MeshData = MeshBuilder3D().apply {
        addEllipsoid(rx = 0.028f, ry = 0.007f, rz = 0.010f, rings = 6, sectors = 10)
    }.build()

    override val eyelidMesh: MeshData = MeshBuilder3D().apply {
        addEllipsoid(rx = 0.022f, ry = 0.013f, rz = 0.022f, rings = 8, sectors = 12)
    }.build()

    override val upperArm: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.27f, yTop = 0f, rxBottom = 0.039f, rzBottom = 0.041f, rxTop = 0.052f, rzTop = 0.052f, rings = 6, sectors = 14, bulgeFactor = 0.005f)
    }.build()

    override val forearm: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.25f, yTop = 0f, rxBottom = 0.029f, rzBottom = 0.032f, rxTop = 0.041f, rzTop = 0.041f, rings = 6, sectors = 14, bulgeFactor = 0.004f)
    }.build()

    override val jointSphere: MeshData = MeshBuilder3D().apply {
        addEllipsoid(rx = 0.044f, ry = 0.044f, rz = 0.044f, rings = 8, sectors = 12)
    }.build()

    override val palmMesh: MeshData = MeshBuilder3D().apply {
        addEllipsoid(cx = 0f, cy = -0.040f, cz = 0f, rx = 0.036f, ry = 0.044f, rz = 0.017f, rings = 8, sectors = 12)
    }.build()

    override val fingerMesh: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.036f, yTop = 0f, rxBottom = 0.008f, rzBottom = 0.008f, rxTop = 0.010f, rzTop = 0.010f, rings = 3, sectors = 8)
        addEllipsoid(cx = 0f, cy = 0f, cz = 0f, rx = 0.010f, ry = 0.010f, rz = 0.010f, rings = 5, sectors = 8)
    }.build()

    override val distalFinger: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.032f, yTop = 0f, rxBottom = 0.006f, rzBottom = 0.006f, rxTop = 0.0085f, rzTop = 0.0085f, rings = 3, sectors = 8)
    }.build()

    override val sleeveCuff: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.045f, yTop = 0.015f, rxBottom = 0.044f, rzBottom = 0.044f, rxTop = 0.048f, rzTop = 0.048f, rings = 3, sectors = 12, bulgeFactor = 0.004f)
    }.build()

    override val jacketCollar: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.02f, yTop = 0.065f, rxBottom = 0.115f, rzBottom = 0.095f, rxTop = 0.082f, rzTop = 0.074f, rings = 3, sectors = 14)
    }.build()

    override val thighMesh: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.39f, yTop = 0f, rxBottom = 0.058f, rzBottom = 0.060f, rxTop = 0.082f, rzTop = 0.084f, rings = 7, sectors = 14, bulgeFactor = 0.006f)
    }.build()

    override val shinMesh: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.38f, yTop = 0f, rxBottom = 0.038f, rzBottom = 0.040f, rxTop = 0.056f, rzTop = 0.058f, rings = 7, sectors = 14, bulgeFactor = 0.008f)
    }.build()

    override val footShoeMesh: MeshData = MeshBuilder3D().apply {
        addModeledBoot()
    }.build()

    override val sculptedHeadPlayer: MeshData = MeshBuilder3D().apply {
        addSculptedHead(rx = 0.104f, ry = 0.128f, rz = 0.114f, rings = 20, sectors = 26, gauntness = 0.10f)
    }.build()

    override val shortHairPlayer: MeshData = MeshBuilder3D().apply {
        addEllipsoid(cx = 0f, cy = 0.042f, cz = -0.014f, rx = 0.109f, ry = 0.095f, rz = 0.116f, rings = 10, sectors = 16)
        addEllipsoid(cx = 0f, cy = 0.068f, cz = 0.025f, rx = 0.096f, ry = 0.062f, rz = 0.088f, rings = 8, sectors = 14)
    }.build()

    override val torsoPlayer: MeshData = MeshBuilder3D().apply {
        addSculptedTorso(
            yBottom = 0f, yTop = 0.54f,
            hipRx = 0.155f, hipRz = 0.115f,
            waistRx = 0.145f, waistRz = 0.108f,
            chestRx = 0.195f, chestRz = 0.132f,
            shoulderRx = 0.215f, shoulderRz = 0.112f,
            hunchCurveZ = 0.0f,
            sectors = 20,
        )
    }.build()

    override val flashlightBody: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.09f, yTop = 0.07f, rxBottom = 0.021f, rzBottom = 0.021f, rxTop = 0.022f, rzTop = 0.022f, rings = 4, sectors = 14)
        addTaperedLimb(yBottom = 0.07f, yTop = 0.12f, rxBottom = 0.023f, rzBottom = 0.023f, rxTop = 0.036f, rzTop = 0.036f, rings = 4, sectors = 16)
    }.build()

    override val flashlightLens: MeshData = MeshBuilder3D().apply {
        addEllipsoid(cx = 0f, cy = 0.121f, cz = 0f, rx = 0.031f, ry = 0.008f, rz = 0.031f, rings = 6, sectors = 14)
    }.build()

    fun meshFor(kind: CharacterMeshKind): MeshData = when (kind) {
        CharacterMeshKind.SCULPTED_HEAD_GRANNY -> sculptedHeadGranny
        CharacterMeshKind.JAW_GRANNY -> jawGranny
        CharacterMeshKind.HAIR_BUN -> hairBun
        CharacterMeshKind.HAIR_STRAND -> hairStrand
        CharacterMeshKind.NECK -> neckMesh
        CharacterMeshKind.TORSO_GRANNY -> torsoGranny
        CharacterMeshKind.SKIRT_GRANNY -> skirtGranny
        CharacterMeshKind.SHAWL_COLLAR -> shawlCollar
        CharacterMeshKind.EYEBALL -> eyeball
        CharacterMeshKind.IRIS_PUPIL -> irisPupil
        CharacterMeshKind.BROW -> browMesh
        CharacterMeshKind.EYELID -> eyelidMesh
        CharacterMeshKind.UPPER_ARM -> upperArm
        CharacterMeshKind.FOREARM -> forearm
        CharacterMeshKind.JOINT_SPHERE -> jointSphere
        CharacterMeshKind.PALM -> palmMesh
        CharacterMeshKind.FINGER -> fingerMesh
        CharacterMeshKind.DISTAL_FINGER -> distalFinger
        CharacterMeshKind.SLEEVE_CUFF -> sleeveCuff
        CharacterMeshKind.JACKET_COLLAR -> jacketCollar
        CharacterMeshKind.THIGH -> thighMesh
        CharacterMeshKind.SHIN -> shinMesh
        CharacterMeshKind.FOOT_SHOE -> footShoeMesh
        CharacterMeshKind.SCULPTED_HEAD_PLAYER -> sculptedHeadPlayer
        CharacterMeshKind.SHORT_HAIR_PLAYER -> shortHairPlayer
        CharacterMeshKind.TORSO_PLAYER -> torsoPlayer
        CharacterMeshKind.FLASHLIGHT_BODY -> flashlightBody
        CharacterMeshKind.FLASHLIGHT_LENS -> flashlightLens
    }

    fun allEntries(): Map<CharacterMeshKind, MeshData> = CharacterMeshKind.entries.associateWith { meshFor(it) }
}

/**
 * OpenGL ES 2.0 GPU mesh library built from [CharacterCpuMeshLibrary] for [HorrorGlRenderer].
 */
class CharacterMeshLibrary(
    val cpuMeshes: CharacterCpuMeshLibrary = CharacterCpuMeshLibrary(),
) : CharacterMeshProvider<GpuMesh> {
    override val sculptedHeadGranny: GpuMesh = cpuMeshes.sculptedHeadGranny.toGpuMesh()
    override val jawGranny: GpuMesh = cpuMeshes.jawGranny.toGpuMesh()
    override val hairBun: GpuMesh = cpuMeshes.hairBun.toGpuMesh()
    override val hairStrand: GpuMesh = cpuMeshes.hairStrand.toGpuMesh()
    override val neckMesh: GpuMesh = cpuMeshes.neckMesh.toGpuMesh()
    override val torsoGranny: GpuMesh = cpuMeshes.torsoGranny.toGpuMesh()
    override val skirtGranny: GpuMesh = cpuMeshes.skirtGranny.toGpuMesh()
    override val shawlCollar: GpuMesh = cpuMeshes.shawlCollar.toGpuMesh()
    override val eyeball: GpuMesh = cpuMeshes.eyeball.toGpuMesh()
    override val irisPupil: GpuMesh = cpuMeshes.irisPupil.toGpuMesh()
    override val browMesh: GpuMesh = cpuMeshes.browMesh.toGpuMesh()
    override val eyelidMesh: GpuMesh = cpuMeshes.eyelidMesh.toGpuMesh()
    override val upperArm: GpuMesh = cpuMeshes.upperArm.toGpuMesh()
    override val forearm: GpuMesh = cpuMeshes.forearm.toGpuMesh()
    override val jointSphere: GpuMesh = cpuMeshes.jointSphere.toGpuMesh()
    override val palmMesh: GpuMesh = cpuMeshes.palmMesh.toGpuMesh()
    override val fingerMesh: GpuMesh = cpuMeshes.fingerMesh.toGpuMesh()
    override val distalFinger: GpuMesh = cpuMeshes.distalFinger.toGpuMesh()
    override val sleeveCuff: GpuMesh = cpuMeshes.sleeveCuff.toGpuMesh()
    override val jacketCollar: GpuMesh = cpuMeshes.jacketCollar.toGpuMesh()
    override val thighMesh: GpuMesh = cpuMeshes.thighMesh.toGpuMesh()
    override val shinMesh: GpuMesh = cpuMeshes.shinMesh.toGpuMesh()
    override val footShoeMesh: GpuMesh = cpuMeshes.footShoeMesh.toGpuMesh()
    override val sculptedHeadPlayer: GpuMesh = cpuMeshes.sculptedHeadPlayer.toGpuMesh()
    override val shortHairPlayer: GpuMesh = cpuMeshes.shortHairPlayer.toGpuMesh()
    override val torsoPlayer: GpuMesh = cpuMeshes.torsoPlayer.toGpuMesh()
    override val flashlightBody: GpuMesh = cpuMeshes.flashlightBody.toGpuMesh()
    override val flashlightLens: GpuMesh = cpuMeshes.flashlightLens.toGpuMesh()

    fun release() {
        listOf(
            sculptedHeadGranny, jawGranny, hairBun, hairStrand, neckMesh,
            torsoGranny, skirtGranny, shawlCollar, eyeball, irisPupil,
            browMesh, eyelidMesh, upperArm, forearm, jointSphere,
            palmMesh, fingerMesh, distalFinger, sleeveCuff, jacketCollar,
            thighMesh, shinMesh, footShoeMesh,
            sculptedHeadPlayer, shortHairPlayer, torsoPlayer, flashlightBody, flashlightLens,
        ).forEach { it.release() }
    }
}

/**
 * Lightweight pre-allocated column-major 4x4 matrix stack for hierarchical skeletal animation.
 * Pure Kotlin math (zero OpenGL runtime dependency so animators work on CPU, OpenGL ES, and Filament).
 */
class BoneMatrixStack(maxDepth: Int = 12) {
    private val stack = Array(maxDepth) { FloatArray(16) }
    private val tmp12 = FloatArray(12)
    private var topIndex = 0

    fun resetIdentity() {
        topIndex = 0
        val m = stack[0]
        m.fill(0f)
        m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
    }

    fun current(): FloatArray = stack[topIndex]

    fun push() {
        val next = (topIndex + 1).coerceAtMost(stack.lastIndex)
        System.arraycopy(stack[topIndex], 0, stack[next], 0, 16)
        topIndex = next
    }

    fun pop() {
        if (topIndex > 0) topIndex--
    }

    fun translate(x: Float, y: Float, z: Float) {
        val m = stack[topIndex]
        for (i in 0 until 4) {
            m[12 + i] += m[i] * x + m[4 + i] * y + m[8 + i] * z
        }
    }

    fun rotate(deg: Float, ax: Float, ay: Float, az: Float) {
        if (deg == 0f) return
        val len = sqrt(ax * ax + ay * ay + az * az)
        if (len < 1e-6f) return
        val x = ax / len
        val y = ay / len
        val z = az / len
        val rad = deg * (PI.toFloat() / 180f)
        val s = sin(rad)
        val c = cos(rad)
        val nc = 1f - c

        val r00 = x * x * nc + c
        val r10 = y * x * nc + z * s
        val r20 = z * x * nc - y * s
        val r01 = x * y * nc - z * s
        val r11 = y * y * nc + c
        val r21 = z * y * nc + x * s
        val r02 = x * z * nc + y * s
        val r12 = y * z * nc - x * s
        val r22 = z * z * nc + c

        val m = stack[topIndex]
        System.arraycopy(m, 0, tmp12, 0, 12)
        for (i in 0 until 4) {
            val a0 = tmp12[i]
            val a1 = tmp12[4 + i]
            val a2 = tmp12[8 + i]
            m[i] = a0 * r00 + a1 * r10 + a2 * r20
            m[4 + i] = a0 * r01 + a1 * r11 + a2 * r21
            m[8 + i] = a0 * r02 + a1 * r12 + a2 * r22
        }
    }

    fun scale(sx: Float, sy: Float, sz: Float) {
        val m = stack[topIndex]
        for (i in 0 until 4) {
            m[i] *= sx
            m[4 + i] *= sy
            m[8 + i] *= sz
        }
    }
}

fun interface RigDrawCallback<M> {
    fun draw(
        modelMatrix: FloatArray,
        mesh: M,
        texture: TextureKind,
        tintR: Float,
        tintG: Float,
        tintB: Float,
        specular: Float,
        emissive: Float,
        doubleSided: Boolean,
    )
}

fun interface MeshDrawCallback {
    fun draw(
        modelMatrix: FloatArray,
        mesh: GpuMesh,
        texture: TextureKind,
        tintR: Float,
        tintG: Float,
        tintB: Float,
        specular: Float,
        emissive: Float,
        doubleSided: Boolean,
    )
}

/**
 * Drives the full 3D rigged Granny character with skeletal joint hierarchy,
 * breathing, stride cycles, head tracking toward the player, blinking,
 * facial expression animations, and menacing claw reaches.
 */
object GrannyRigAnimator {
    private val stack = BoneMatrixStack()

    fun drawGranny(
        g: GrannyState,
        meshes: CharacterMeshLibrary,
        elapsedSec: Float,
        drawer: MeshDrawCallback,
    ) {
        drawGrannyGeneric(g, meshes, elapsedSec) { mat, mesh, tex, r, gr, b, spec, emit, ds ->
            drawer.draw(mat, mesh, tex, r, gr, b, spec, emit, ds)
        }
    }

    fun <M> drawGrannyGeneric(
        g: GrannyState,
        meshes: CharacterMeshProvider<M>,
        elapsedSec: Float,
        drawer: RigDrawCallback<M>,
    ) {
        val speedNorm = g.currentMoveSpeedNorm.coerceIn(0f, 1f)
        val breath = sin(g.breathPhase)
        val strideSin = sin(g.walkCycle)
        val strideCos = cos(g.walkCycle)
        val isHostile = g.expression == GrannyExpression.MENACING_GRIN || g.expression == GrannyExpression.HOSTILE_SHRIEK
        val dragBlend = g.dragGripBlend.coerceIn(0f, 1f)
        val huntBlend = g.huntBlend.coerceIn(0f, 1f)
        val doorReach = g.doorReachArmBlend.coerceIn(0f, 1f)
        val pullTug = if (g.isDraggingCapture) sin(g.dragPullPhase) else 0f

        val hipBob = kotlin.math.abs(strideCos) * 0.032f * speedNorm + breath * 0.008f - dragBlend * 0.08f
        val stateHunch = when (g.aiState) {
            GrannyAiState.WAITING_AT_DOOR, GrannyAiState.GREETING_DIALOGUE -> 6f
            GrannyAiState.INVESTIGATING_NOISE -> 16f
            else -> 11f
        }
        val baseHunch = stateHunch * (1f - huntBlend) + 21f * huntBlend
        val spineHunch = baseHunch + dragBlend * (34f - pullTug * 5f) +
            (if (g.isLoomingCapture) 14f else 0f) +
            (if (g.isSearchingRoom) g.searchBendDeg else g.searchBendDeg * g.searchBlend) +
            breath * 1.6f

        stack.resetIdentity()
        stack.translate(g.x, g.y, g.z)
        stack.rotate(g.yawDeg, 0f, 1f, 0f)

        // Pelvis Root
        stack.push()
        stack.translate(0f, 0.78f + hipBob, 0f)
        stack.rotate(strideSin * 4f * speedNorm, 0f, 1f, 0f)
        stack.rotate(strideCos * 2.5f * speedNorm, 0f, 0f, 1f)

        // Tattered Victorian skirt
        drawer.draw(stack.current(), meshes.skirtGranny, TextureKind.GRANNY_DRESS, 1f, 1f, 1f, 0.08f, 0f, true)

        // Left & Right Legs (visible beneath tattered hem & during long hunting strides)
        drawLeg(meshes, sideSign = 1f, strideSin = strideSin, strideCos = strideCos, speedNorm = speedNorm, drawer = drawer)
        drawLeg(meshes, sideSign = -1f, strideSin = -strideSin, strideCos = -strideCos, speedNorm = speedNorm, drawer = drawer)

        // Spine -> Chest
        stack.push()
        stack.translate(0f, 0.02f, 0f)
        stack.rotate(spineHunch, 1f, 0f, 0f)
        stack.rotate(-strideSin * 5f * speedNorm, 0f, 1f, 0f)

        drawer.draw(stack.current(), meshes.torsoGranny, TextureKind.GRANNY_DRESS, 1f, 1f, 1f, 0.08f, 0f, false)

        // Shawl around upper shoulders
        stack.push()
        stack.translate(0f, 0.45f, 0.02f)
        drawer.draw(stack.current(), meshes.shawlCollar, TextureKind.GRANNY_DRESS, 0.78f, 0.72f, 0.76f, 0.06f, 0f, true)
        stack.pop()

        // Neck & Head
        stack.push()
        stack.translate(0f, 0.47f, 0.02f)
        stack.rotate(-spineHunch * 0.45f, 1f, 0f, 0f)
        drawer.draw(stack.current(), meshes.neckMesh, TextureKind.GRANNY_SKIN, 1f, 1f, 1f, 0.14f, 0f, false)

        // Head Bone (turns toward player + tilts eerily)
        stack.push()
        stack.translate(0f, 0.16f, 0.01f)
        stack.rotate(g.headLookYawDeg, 0f, 1f, 0f)
        stack.rotate(g.headLookPitchDeg, 1f, 0f, 0f)
        stack.rotate(g.headTiltDeg, 0f, 0f, 1f)

        // Sculpted Cranium & Face
        drawer.draw(stack.current(), meshes.sculptedHeadGranny, TextureKind.GRANNY_SKIN, 1f, 1f, 1f, 0.18f, 0f, false)
        // Silver-gray Hair Bun & Side Strands
        drawer.draw(stack.current(), meshes.hairBun, TextureKind.GRANNY_HAIR, 1f, 1f, 1f, 0.10f, 0f, false)
        stack.push()
        stack.translate(0.095f, -0.01f, 0.02f)
        stack.rotate(10f, 0f, 0f, 1f)
        drawer.draw(stack.current(), meshes.hairStrand, TextureKind.GRANNY_HAIR, 0.92f, 0.92f, 0.95f, 0.10f, 0f, false)
        stack.pop()
        stack.push()
        stack.translate(-0.095f, -0.01f, 0.02f)
        stack.rotate(-10f, 0f, 0f, 1f)
        drawer.draw(stack.current(), meshes.hairStrand, TextureKind.GRANNY_HAIR, 0.92f, 0.92f, 0.95f, 0.10f, 0f, false)
        stack.pop()

        // Hinged Lower Jaw (speech & shriek!)
        stack.push()
        stack.translate(0f, -0.048f, 0.02f)
        stack.rotate(g.jawOpenAmount * 30f, 1f, 0f, 0f)
        drawer.draw(stack.current(), meshes.jawGranny, TextureKind.GRANNY_SKIN, 0.92f, 0.88f, 0.86f, 0.15f, 0f, false)
        stack.pop()

        // Expressive Eyes, Pupils, Eyelids & Eyebrows
        drawGrannyEyesAndBrows(g, meshes, isHostile, drawer)

        stack.pop() // Head
        stack.pop() // Neck

        // Left & Right Arms (welcoming gesture on porch, door reach/slam, ankle grip drag, or smooth hunting claws!)
        val doorGesture = maxOf(g.doorSlamGestureTimer.coerceIn(0f, 1f), doorReach)
        val greetingWave = if (g.aiState == GrannyAiState.GREETING_DIALOGUE) {
            sin(elapsedSec * 2.2f) * 14f - 26f
        } else 0f

        val normalPitchL = -8f - strideSin * 24f * speedNorm
        val huntPitchL = -76f + strideSin * 26f
        val baseBlendPitchL = normalPitchL * (1f - huntBlend) + huntPitchL * huntBlend

        val normalPitchR = -8f + strideSin * 24f * speedNorm
        val huntPitchR = -76f - strideSin * 26f
        val baseBlendPitchR = normalPitchR * (1f - huntBlend) + huntPitchR * huntBlend

        val armPitchL = when {
            g.isDraggingCapture || dragBlend > 0.05f -> -46f * dragBlend + pullTug * 8f
            g.isLoomingCapture -> -28f + sin(elapsedSec * 2.6f) * 6f
            g.isSearchingRoom -> -24f - strideSin * 16f * speedNorm
            doorGesture > 0.05f -> baseBlendPitchL * (1f - doorGesture) + (-66f) * doorGesture
            g.aiState == GrannyAiState.GREETING_DIALOGUE -> greetingWave
            else -> baseBlendPitchL
        }
        val armPitchR = when {
            g.isDraggingCapture || dragBlend > 0.05f -> -46f * dragBlend + pullTug * 8f
            g.isLoomingCapture -> -24f - sin(elapsedSec * 2.6f) * 6f
            g.isSearchingRoom -> -28f + strideSin * 16f * speedNorm
            g.aiState == GrannyAiState.INVESTIGATING_NOISE -> -35f
            else -> baseBlendPitchR
        }
        val baseElbow = -22f * (1f - huntBlend) + (-36f) * huntBlend
        val elbowFlexL = when {
            g.isDraggingCapture || dragBlend > 0.05f -> -16f
            g.isLoomingCapture -> -28f
            g.aiState == GrannyAiState.GREETING_DIALOGUE -> -52f
            else -> baseElbow
        }
        val elbowFlexR = when {
            g.isDraggingCapture || dragBlend > 0.05f -> -16f
            g.isLoomingCapture -> -28f
            else -> baseElbow
        }

        val shoulderRollMag = when {
            g.isDraggingCapture || dragBlend > 0.05f -> -7f
            else -> 8f * (1f - huntBlend) + 12f * huntBlend
        }

        drawArm(
            meshes = meshes,
            sideSign = 1f,
            shoulderPitch = armPitchL,
            shoulderRoll = shoulderRollMag,
            elbowFlex = elbowFlexL,
            clothTex = TextureKind.GRANNY_DRESS,
            skinTex = TextureKind.GRANNY_SKIN,
            drawer = drawer,
        )
        drawArm(
            meshes = meshes,
            sideSign = -1f,
            shoulderPitch = armPitchR,
            shoulderRoll = -shoulderRollMag,
            elbowFlex = elbowFlexR,
            clothTex = TextureKind.GRANNY_DRESS,
            skinTex = TextureKind.GRANNY_SKIN,
            drawer = drawer,
        )

        stack.pop() // Spine
        stack.pop() // Pelvis
    }

    private fun <M> drawGrannyEyesAndBrows(
        g: GrannyState,
        meshes: CharacterMeshProvider<M>,
        isHostile: Boolean,
        drawer: RigDrawCallback<M>,
    ) {
        val browAngle = when (g.expression) {
            GrannyExpression.GENTLE_SMILE -> 10f
            GrannyExpression.CREEPY_STARE -> 2f
            GrannyExpression.SUSPICIOUS -> -18f
            GrannyExpression.MENACING_GRIN -> -26f
            GrannyExpression.HOSTILE_SHRIEK -> -32f
        }
        val browLiftY = when (g.expression) {
            GrannyExpression.GENTLE_SMILE -> 0.008f
            GrannyExpression.HOSTILE_SHRIEK -> 0.012f
            else -> 0f
        }
        val blinkClose = if (g.expression == GrannyExpression.HOSTILE_SHRIEK) 0.1f else g.blinkAmount

        for (sideSign in listOf(-1f, 1f)) {
            val ex = sideSign * 0.038f
            val ey = 0.022f
            val ez = 0.098f

            // Eyeball
            stack.push()
            stack.translate(ex, ey, ez)
            drawer.draw(stack.current(), meshes.eyeball, TextureKind.GRANNY_HAIR, 0.92f, 0.88f, 0.84f, 0.45f, 0f, false)

            // Iris / Pupil (turns toward player + darts nervously; glows pale crimson when hostile)
            stack.push()
            stack.translate(sin(g.headLookYawDeg * 0.017f) * 0.005f + g.eyeDartX, g.eyeDartY, 0.015f)
            if (isHostile) {
                drawer.draw(stack.current(), meshes.irisPupil, TextureKind.LANTERN_CORE, 1.0f, 0.16f, 0.14f, 0.8f, 0.85f, false)
            } else {
                drawer.draw(stack.current(), meshes.irisPupil, TextureKind.METAL_PROP, 0.12f, 0.14f, 0.16f, 0.7f, 0f, false)
            }
            stack.pop()

            // Upper Eyelid (drops smoothly on blink or suspicious squint)
            val squint = if (g.expression == GrannyExpression.SUSPICIOUS) 0.42f else 0.15f
            val lidScale = (squint + blinkClose * 0.85f).coerceIn(0.1f, 1.0f)
            stack.push()
            stack.translate(0f, 0.012f - lidScale * 0.008f, 0.004f)
            stack.scale(1f, lidScale, 1f)
            drawer.draw(stack.current(), meshes.eyelidMesh, TextureKind.GRANNY_SKIN, 0.85f, 0.78f, 0.72f, 0.12f, 0f, false)
            stack.pop()

            stack.pop() // Eye

            // Eyebrow
            stack.push()
            stack.translate(ex, 0.056f + browLiftY, ez + 0.014f)
            stack.rotate(sideSign * -browAngle, 0f, 0f, 1f)
            drawer.draw(stack.current(), meshes.browMesh, TextureKind.GRANNY_HAIR, 0.65f, 0.65f, 0.68f, 0.05f, 0f, false)
            stack.pop()
        }
    }

    private fun <M> drawLeg(
        meshes: CharacterMeshProvider<M>,
        sideSign: Float,
        strideSin: Float,
        strideCos: Float,
        speedNorm: Float,
        drawer: RigDrawCallback<M>,
    ) {
        val hipPitch = strideSin * 26f * speedNorm
        val kneeBend = ((-strideCos + 0.5f).coerceIn(0f, 1.2f) * 30f) * speedNorm + 6f

        stack.push()
        stack.translate(sideSign * 0.105f, -0.02f, 0f)
        stack.rotate(hipPitch, 1f, 0f, 0f)
        drawer.draw(stack.current(), meshes.thighMesh, TextureKind.GRANNY_DRESS, 0.75f, 0.72f, 0.75f, 0.06f, 0f, false)

        // Knee
        stack.translate(0f, -0.38f, 0f)
        drawer.draw(stack.current(), meshes.jointSphere, TextureKind.GRANNY_SKIN, 0.85f, 0.80f, 0.76f, 0.10f, 0f, false)
        stack.rotate(kneeBend, 1f, 0f, 0f)
        drawer.draw(stack.current(), meshes.shinMesh, TextureKind.GRANNY_SKIN, 0.88f, 0.82f, 0.78f, 0.12f, 0f, false)

        // Modeled Old Victorian Boot
        stack.translate(0f, -0.37f, 0f)
        drawer.draw(stack.current(), meshes.footShoeMesh, TextureKind.METAL_PROP, 0.25f, 0.20f, 0.18f, 0.20f, 0f, false)
        stack.pop()
    }

    private fun <M> drawArm(
        meshes: CharacterMeshProvider<M>,
        sideSign: Float,
        shoulderPitch: Float,
        shoulderRoll: Float,
        elbowFlex: Float,
        clothTex: TextureKind,
        skinTex: TextureKind,
        drawer: RigDrawCallback<M>,
    ) {
        stack.push()
        stack.translate(sideSign * 0.195f, 0.43f, 0f)
        stack.rotate(shoulderPitch, 1f, 0f, 0f)
        stack.rotate(shoulderRoll, 0f, 0f, 1f)

        // Shoulder joint, Upper Arm Sleeve & Elbow Cloth Cuff
        drawer.draw(stack.current(), meshes.jointSphere, clothTex, 1f, 1f, 1f, 0.08f, 0f, false)
        drawer.draw(stack.current(), meshes.upperArm, clothTex, 1f, 1f, 1f, 0.08f, 0f, false)

        stack.translate(0f, -0.27f, 0f)
        drawer.draw(stack.current(), meshes.sleeveCuff, clothTex, 0.94f, 0.92f, 0.94f, 0.08f, 0f, false)
        drawer.draw(stack.current(), meshes.jointSphere, skinTex, 0.94f, 0.90f, 0.88f, 0.12f, 0f, false)
        stack.rotate(elbowFlex, 1f, 0f, 0f)
        drawer.draw(stack.current(), meshes.forearm, skinTex, 1f, 1f, 1f, 0.14f, 0f, false)

        // Hand & 2-Joint Articulated Clawed Fingers
        stack.translate(0f, -0.25f, 0f)
        drawer.draw(stack.current(), meshes.palmMesh, skinTex, 1f, 1f, 1f, 0.14f, 0f, false)
        drawArticulatedFingers(meshes, sideSign, curlDeg = 28f, skinTex = skinTex, drawer = drawer)
        stack.pop()
    }

    private fun <M> drawArticulatedFingers(
        meshes: CharacterMeshProvider<M>,
        sideSign: Float,
        curlDeg: Float,
        skinTex: TextureKind,
        drawer: RigDrawCallback<M>,
    ) {
        // 4 fingers with proximal + bent distal phalanges
        for (i in 0 until 4) {
            val spreadX = (i - 1.5f) * 0.017f
            stack.push()
            stack.translate(spreadX, -0.072f, 0.004f)
            stack.rotate(-curlDeg, 1f, 0f, 0f)
            drawer.draw(stack.current(), meshes.fingerMesh, skinTex, 0.96f, 0.92f, 0.90f, 0.12f, 0f, false)
            stack.translate(0f, -0.036f, 0f)
            stack.rotate(-curlDeg * 0.85f, 1f, 0f, 0f)
            drawer.draw(stack.current(), meshes.distalFinger, skinTex, 0.94f, 0.89f, 0.87f, 0.12f, 0f, false)
            stack.pop()
        }
        // Articulated Thumb (proximal + distal tip)
        stack.push()
        stack.translate(-sideSign * 0.032f, -0.035f, 0.015f)
        stack.rotate(-sideSign * 35f, 0f, 0f, 1f)
        stack.rotate(-curlDeg * 0.7f, 1f, 0f, 0f)
        drawer.draw(stack.current(), meshes.fingerMesh, skinTex, 0.96f, 0.92f, 0.90f, 0.12f, 0f, false)
        stack.translate(0f, -0.034f, 0f)
        stack.rotate(-curlDeg * 0.55f, 1f, 0f, 0f)
        drawer.draw(stack.current(), meshes.distalFinger, skinTex, 0.94f, 0.89f, 0.87f, 0.12f, 0f, false)
        stack.pop()
    }
}

/**
 * Renders the Main Player's complete human body:
 * 1) Torso, hips, legs & boots grounded at player's world position (visible when looking down!)
 * 2) First-person arms, forearms, hands & fingers with natural breathing, walk/run swing,
 *    tactical flashlight holding pose, and forward physical reach animation for door/item interaction!
 */
object PlayerRigAnimator {
    private val stack = BoneMatrixStack()

    fun drawPlayerLowerBody(
        engine: HorrorGameEngine,
        meshes: CharacterMeshLibrary,
        drawer: MeshDrawCallback,
    ) {
        drawPlayerLowerBodyGeneric(engine, meshes) { mat, mesh, tex, r, g, b, spec, emit, ds ->
            drawer.draw(mat, mesh, tex, r, g, b, spec, emit, ds)
        }
    }

    fun <M> drawPlayerLowerBodyGeneric(
        engine: HorrorGameEngine,
        meshes: CharacterMeshProvider<M>,
        drawer: RigDrawCallback<M>,
    ) {
        if (engine.isCinematicIntro || engine.isHiding) return

        val strideSin = sin(engine.playerWalkCycle)
        val strideCos = cos(engine.playerWalkCycle)
        val speedNorm = engine.playerMoveSpeedNorm.coerceIn(0f, 1f)
        val crouch = if (engine.isCrouching) 1f else 0f

        val bodyYawDeg = engine.playerBodyYawRad * (180f / PI.toFloat())
        val floorY = WorldMap3D.floorHeightAt(engine.playerX, engine.playerZ)
        // Offset pelvis slightly backward (-Z local) relative to eye camera so looking down frames chest, legs & shoes cleanly
        val pelvisY = floorY + 0.80f - crouch * 0.36f + kotlin.math.abs(strideCos) * 0.025f * speedNorm

        stack.resetIdentity()
        stack.translate(engine.playerX, 0f, engine.playerZ)
        stack.rotate(bodyYawDeg, 0f, 1f, 0f)
        stack.translate(0f, 0f, -0.11f)

        // Pelvis
        stack.push()
        stack.translate(0f, pelvisY, 0f)
        stack.rotate(strideSin * 4f * speedNorm, 0f, 1f, 0f)

        // Left & Right Legs (thighs -> knees -> shins -> boots)
        for (sideSign in listOf(-1f, 1f)) {
            val sSin = if (sideSign > 0) strideSin else -strideSin
            val sCos = if (sideSign > 0) strideCos else -strideCos
            val crouchHip = crouch * -42f
            val crouchKnee = crouch * 68f
            val hipPitch = sSin * 28f * speedNorm + crouchHip
            val kneeBend = ((-sCos + 0.5f).coerceIn(0f, 1.2f) * 28f) * speedNorm + crouchKnee + 4f

            stack.push()
            stack.translate(sideSign * 0.11f, -0.02f, 0f)
            stack.rotate(hipPitch, 1f, 0f, 0f)
            drawer.draw(stack.current(), meshes.thighMesh, TextureKind.PLAYER_JACKET, 0.72f, 0.74f, 0.82f, 0.12f, 0f, false)

            stack.translate(0f, -0.38f, 0f)
            drawer.draw(stack.current(), meshes.jointSphere, TextureKind.PLAYER_JACKET, 0.72f, 0.74f, 0.82f, 0.10f, 0f, false)
            stack.rotate(kneeBend, 1f, 0f, 0f)
            drawer.draw(stack.current(), meshes.shinMesh, TextureKind.PLAYER_JACKET, 0.68f, 0.70f, 0.78f, 0.12f, 0f, false)

            stack.translate(0f, -0.37f, 0f)
            drawer.draw(stack.current(), meshes.footShoeMesh, TextureKind.METAL_PROP, 0.22f, 0.20f, 0.18f, 0.22f, 0f, false)
            stack.pop()
        }

        // Player Torso Jacket & Collar (head/neck omitted so camera never clips inside skull!)
        stack.push()
        stack.translate(0f, 0.02f, 0f)
        stack.rotate(crouch * 16f + speedNorm * 6f, 1f, 0f, 0f)
        drawer.draw(stack.current(), meshes.torsoPlayer, TextureKind.PLAYER_JACKET, 1f, 1f, 1f, 0.14f, 0f, false)
        stack.push()
        stack.translate(0f, 0.51f, 0f)
        drawer.draw(stack.current(), meshes.jacketCollar, TextureKind.PLAYER_JACKET, 0.92f, 0.94f, 0.96f, 0.12f, 0f, false)
        stack.pop()
        stack.pop()

        stack.pop() // Pelvis
    }

    fun drawFirstPersonArms(
        engine: HorrorGameEngine,
        meshes: CharacterMeshLibrary,
        camX: Float,
        camY: Float,
        camZ: Float,
        drawer: MeshDrawCallback,
        armTuckFrac: Float = 0f,
    ) {
        drawFirstPersonArmsGeneric(engine, meshes, camX, camY, camZ, { mat, mesh, tex, r, g, b, spec, emit, ds ->
            drawer.draw(mat, mesh, tex, r, g, b, spec, emit, ds)
        }, armTuckFrac = armTuckFrac)
    }

    fun <M> drawFirstPersonArmsGeneric(
        engine: HorrorGameEngine,
        meshes: CharacterMeshProvider<M>,
        camX: Float,
        camY: Float,
        camZ: Float,
        drawer: RigDrawCallback<M>,
        armTuckFrac: Float = 0f,
    ) {
        if (engine.isHiding) return

        val breath = sin(engine.playerBreathPhase)
        val stride = sin(engine.playerWalkCycle) * engine.playerMoveSpeedNorm
        val strideCos = cos(engine.playerWalkCycle) * engine.playerMoveSpeedNorm
        val interactArc = sin(engine.interactAnimTimer.coerceIn(0f, 1f) * PI.toFloat())
        val tuck = armTuckFrac.coerceIn(0f, 1f)
        val tuckZ = -tuck * 0.16f
        val tuckY = -tuck * 0.06f

        val yawDeg = engine.playerYawRad * (180f / PI.toFloat())
        val pitchDeg = engine.playerPitchRad * (180f / PI.toFloat())

        // Anchor arms in camera view space so they always match look pitch & yaw without clipping into close walls!
        stack.resetIdentity()
        stack.translate(camX, camY, camZ)
        stack.rotate(yawDeg, 0f, 1f, 0f)
        stack.rotate(-pitchDeg, 1f, 0f, 0f)

        // 1. RIGHT ARM (Holds Tactical Flashlight when collected, otherwise swings naturally)
        stack.push()
        if (engine.hasFlashlight) {
            val bobX = strideCos * 0.012f
            val bobY = kotlin.math.abs(stride) * -0.016f + breath * 0.005f
            // Position right shoulder & extend forearm forward into bottom-right view
            stack.translate(-0.20f + bobX + tuck * 0.02f, -0.20f + bobY + tuckY, 0.05f + tuckZ)
            stack.rotate(-72f + tuck * 20f + breath * 1.5f + stride * 6f, 1f, 0f, 0f)
            stack.rotate(-12f, 0f, 1f, 0f)
            drawer.draw(stack.current(), meshes.upperArm, TextureKind.PLAYER_JACKET, 1f, 1f, 1f, 0.14f, 0f, false)

            stack.translate(0f, -0.26f, 0f)
            drawer.draw(stack.current(), meshes.sleeveCuff, TextureKind.PLAYER_JACKET, 0.92f, 0.94f, 0.96f, 0.12f, 0f, false)
            stack.rotate(-34f - tuck * 18f, 1f, 0f, 0f)
            drawer.draw(stack.current(), meshes.forearm, TextureKind.PLAYER_SKIN, 1f, 1f, 1f, 0.16f, 0f, false)

            stack.translate(0f, -0.24f, 0f)
            stack.rotate(14f, 0f, 0f, 1f)
            drawer.draw(stack.current(), meshes.palmMesh, TextureKind.PLAYER_SKIN, 1f, 1f, 1f, 0.16f, 0f, false)
            drawFirstPersonFingers(meshes, sideSign = -1f, curlDeg = 56f, drawer = drawer)

            // Tactical Flashlight gripped cleanly inside right hand pointing forward (+Z in camera)
            stack.push()
            stack.translate(0f, -0.045f, 0.032f)
            stack.rotate(92f, 1f, 0f, 0f)
            drawer.draw(stack.current(), meshes.flashlightBody, TextureKind.METAL_PROP, 0.38f, 0.40f, 0.44f, 0.65f, 0f, false)
            val lensEmit = if (engine.flashlightOn) 1.0f else 0.04f
            drawer.draw(stack.current(), meshes.flashlightLens, TextureKind.LANTERN_CORE, 1f, 0.94f, 0.78f, 0.9f, lensEmit, false)
            stack.pop()
        } else {
            // Unarmed right arm visible at lower-right periphery with walk swing & breathing
            val swing = -stride * 22f
            stack.translate(-0.22f, -0.24f + breath * 0.005f + tuckY, -0.04f + tuckZ)
            stack.rotate(-28f + swing + tuck * 16f, 1f, 0f, 0f)
            drawer.draw(stack.current(), meshes.upperArm, TextureKind.PLAYER_JACKET, 1f, 1f, 1f, 0.14f, 0f, false)
            stack.translate(0f, -0.26f, 0f)
            drawer.draw(stack.current(), meshes.sleeveCuff, TextureKind.PLAYER_JACKET, 0.92f, 0.94f, 0.96f, 0.12f, 0f, false)
            stack.rotate(-38f - tuck * 16f, 1f, 0f, 0f)
            drawer.draw(stack.current(), meshes.forearm, TextureKind.PLAYER_SKIN, 1f, 1f, 1f, 0.16f, 0f, false)
            stack.translate(0f, -0.24f, 0f)
            drawer.draw(stack.current(), meshes.palmMesh, TextureKind.PLAYER_SKIN, 1f, 1f, 1f, 0.16f, 0f, false)
            drawFirstPersonFingers(meshes, sideSign = -1f, curlDeg = 22f, drawer = drawer)
        }
        stack.pop()

        // 2. LEFT ARM (Swings naturally on walk/run & reaches forward physically when interacting!)
        stack.push()
        val leftReachFwd = interactArc * 0.24f * (1f - tuck * 0.7f)
        val leftLiftY = interactArc * 0.09f
        val leftPitch = -32f + stride * 24f - interactArc * 68f + tuck * 16f
        val leftElbow = -36f + interactArc * 26f - tuck * 14f
        stack.translate(0.22f - interactArc * 0.09f, -0.24f + leftLiftY + breath * 0.005f + tuckY, -0.03f + leftReachFwd + tuckZ)
        stack.rotate(leftPitch, 1f, 0f, 0f)
        stack.rotate(10f - interactArc * 16f, 0f, 1f, 0f)
        drawer.draw(stack.current(), meshes.upperArm, TextureKind.PLAYER_JACKET, 1f, 1f, 1f, 0.14f, 0f, false)

        stack.translate(0f, -0.26f, 0f)
        drawer.draw(stack.current(), meshes.sleeveCuff, TextureKind.PLAYER_JACKET, 0.92f, 0.94f, 0.96f, 0.12f, 0f, false)
        stack.rotate(leftElbow, 1f, 0f, 0f)
        drawer.draw(stack.current(), meshes.forearm, TextureKind.PLAYER_SKIN, 1f, 1f, 1f, 0.16f, 0f, false)

        stack.translate(0f, -0.24f, 0f)
        drawer.draw(stack.current(), meshes.palmMesh, TextureKind.PLAYER_SKIN, 1f, 1f, 1f, 0.16f, 0f, false)
        drawFirstPersonFingers(meshes, sideSign = 1f, curlDeg = if (interactArc > 0.1f) 14f else 24f, drawer = drawer)
        stack.pop()
    }

    private fun <M> drawFirstPersonFingers(
        meshes: CharacterMeshProvider<M>,
        sideSign: Float,
        curlDeg: Float,
        drawer: RigDrawCallback<M>,
    ) {
        for (i in 0 until 4) {
            val spreadX = (i - 1.5f) * 0.017f
            stack.push()
            stack.translate(spreadX, -0.072f, 0.004f)
            stack.rotate(-curlDeg, 1f, 0f, 0f)
            drawer.draw(stack.current(), meshes.fingerMesh, TextureKind.PLAYER_SKIN, 0.96f, 0.92f, 0.90f, 0.14f, 0f, false)
            stack.translate(0f, -0.035f, 0f)
            stack.rotate(-curlDeg * 0.85f, 1f, 0f, 0f)
            drawer.draw(stack.current(), meshes.distalFinger, TextureKind.PLAYER_SKIN, 0.94f, 0.88f, 0.86f, 0.14f, 0f, false)
            stack.pop()
        }
        stack.push()
        stack.translate(-sideSign * 0.032f, -0.035f, 0.015f)
        stack.rotate(-sideSign * 35f, 0f, 0f, 1f)
        stack.rotate(-curlDeg * 0.7f, 1f, 0f, 0f)
        drawer.draw(stack.current(), meshes.fingerMesh, TextureKind.PLAYER_SKIN, 0.96f, 0.92f, 0.90f, 0.14f, 0f, false)
        stack.translate(0f, -0.033f, 0f)
        stack.rotate(-curlDeg * 0.55f, 1f, 0f, 0f)
        drawer.draw(stack.current(), meshes.distalFinger, TextureKind.PLAYER_SKIN, 0.94f, 0.88f, 0.86f, 0.14f, 0f, false)
        stack.pop()
    }

    /**
     * Renders the complete 3D human protagonist body (sculpted head, hair, eyes/eyelids, neck,
     * jacketed torso, arms, hands, hips, thighs, knees, shins, boots) for unconscious dragging,
     * waking up on the bed, sitting up, looking around, and crawling beneath the bed.
     */
    fun drawFullProtagonist3D(
        bodyX: Float,
        bodyY: Float,
        bodyZ: Float,
        bodyYawDeg: Float,
        bodyLiePitchDeg: Float,
        dragPhase: Float,
        isBeingDragged: Boolean,
        breathPhase: Float,
        meshes: CharacterMeshLibrary,
        drawer: MeshDrawCallback,
        eyeOpenAmount: Float = 0f,
        jawOpenAmount: Float = 0f,
        headYawDeg: Float = 12f,
        headPitchDeg: Float = -6f,
        hipPitchDeg: Float? = null,
        kneeFlexDeg: Float? = null,
        armReachPitchDeg: Float? = null,
        elbowFlexDeg: Float = -14f,
        isCrawling: Boolean = false,
        walkCycle: Float = 0f,
        moveSpeedNorm: Float = 0f,
    ) {
        drawFullProtagonist3DGeneric(
            bodyX = bodyX,
            bodyY = bodyY,
            bodyZ = bodyZ,
            bodyYawDeg = bodyYawDeg,
            bodyLiePitchDeg = bodyLiePitchDeg,
            dragPhase = dragPhase,
            isBeingDragged = isBeingDragged,
            breathPhase = breathPhase,
            meshes = meshes,
            drawer = { mat, mesh, tex, r, g, b, spec, emit, ds ->
                drawer.draw(mat, mesh, tex, r, g, b, spec, emit, ds)
            },
            eyeOpenAmount = eyeOpenAmount,
            jawOpenAmount = jawOpenAmount,
            headYawDeg = headYawDeg,
            headPitchDeg = headPitchDeg,
            hipPitchDeg = hipPitchDeg,
            kneeFlexDeg = kneeFlexDeg,
            armReachPitchDeg = armReachPitchDeg,
            elbowFlexDeg = elbowFlexDeg,
            isCrawling = isCrawling,
            walkCycle = walkCycle,
            moveSpeedNorm = moveSpeedNorm,
        )
    }

    fun <M> drawFullProtagonist3DGeneric(
        bodyX: Float,
        bodyY: Float,
        bodyZ: Float,
        bodyYawDeg: Float,
        bodyLiePitchDeg: Float,
        dragPhase: Float,
        isBeingDragged: Boolean,
        breathPhase: Float,
        meshes: CharacterMeshProvider<M>,
        drawer: RigDrawCallback<M>,
        eyeOpenAmount: Float = 0f,
        jawOpenAmount: Float = 0f,
        headYawDeg: Float = 12f,
        headPitchDeg: Float = -6f,
        hipPitchDeg: Float? = null,
        kneeFlexDeg: Float? = null,
        armReachPitchDeg: Float? = null,
        elbowFlexDeg: Float = -14f,
        isCrawling: Boolean = false,
        walkCycle: Float = 0f,
        moveSpeedNorm: Float = 0f,
    ) {
        val sway = if (isBeingDragged) sin(dragPhase) else 0f
        val bounce = if (isBeingDragged) kotlin.math.abs(cos(dragPhase)) * 0.016f else 0f
        val breath = sin(breathPhase) * 0.008f
        val strideSin = sin(walkCycle) * moveSpeedNorm

        stack.resetIdentity()
        stack.translate(bodyX + sway * 0.025f, bodyY + bounce, bodyZ)
        stack.rotate(bodyYawDeg, 0f, 1f, 0f)
        stack.rotate(bodyLiePitchDeg, 1f, 0f, 0f)

        // 1. Pelvis & Legs (supports dragged slack, bent sitting knees, walking stride, or crawling)
        val defaultHip = when {
            isCrawling -> 38f
            isBeingDragged -> -14f
            else -> -2f
        }
        val defaultKnee = when {
            isCrawling -> 74f
            isBeingDragged -> 6f
            else -> 10f
        }
        for (sideSign in listOf(-1f, 1f)) {
            val strideLeg = strideSin * sideSign * 26f
            val hipAng = (hipPitchDeg ?: defaultHip) + sway * sideSign * 2.5f + strideLeg
            val kneeAng = (kneeFlexDeg ?: defaultKnee) + kotlin.math.max(0f, -strideLeg * 0.8f)

            stack.push()
            stack.translate(sideSign * 0.105f, -0.02f, 0f)
            stack.rotate(hipAng, 1f, 0f, 0f)
            stack.rotate(sideSign * (if (isBeingDragged) 2f else 5f), 0f, 0f, 1f)
            drawer.draw(stack.current(), meshes.thighMesh, TextureKind.PLAYER_JACKET, 0.72f, 0.74f, 0.82f, 0.12f, 0f, false)

            stack.translate(0f, -0.38f, 0f)
            drawer.draw(stack.current(), meshes.jointSphere, TextureKind.PLAYER_JACKET, 0.72f, 0.74f, 0.82f, 0.10f, 0f, false)
            stack.rotate(kneeAng, 1f, 0f, 0f)
            drawer.draw(stack.current(), meshes.shinMesh, TextureKind.PLAYER_JACKET, 0.68f, 0.70f, 0.78f, 0.12f, 0f, false)

            stack.translate(0f, -0.37f, 0f)
            drawer.draw(stack.current(), meshes.footShoeMesh, TextureKind.METAL_PROP, 0.22f, 0.20f, 0.18f, 0.22f, 0f, false)
            stack.pop()
        }

        // 2. Torso Jacket + Collar
        stack.push()
        stack.translate(0f, 0.02f, breath)
        drawer.draw(stack.current(), meshes.torsoPlayer, TextureKind.PLAYER_JACKET, 1f, 1f, 1f, 0.14f, 0f, false)

        // 3. Collar, Neck & Full Sculpted Young-Man Head + Expressive Eyes & Speech Jaw
        stack.push()
        stack.translate(0f, 0.51f, 0f)
        drawer.draw(stack.current(), meshes.jacketCollar, TextureKind.PLAYER_JACKET, 0.92f, 0.94f, 0.96f, 0.12f, 0f, false)
        drawer.draw(stack.current(), meshes.neckMesh, TextureKind.PLAYER_SKIN, 1f, 1f, 1f, 0.14f, 0f, false)

        stack.push()
        stack.translate(0f, 0.15f, 0.01f)
        stack.rotate(sway * 10f + headYawDeg, 0f, 1f, 0f)
        stack.rotate(headPitchDeg, 1f, 0f, 0f)
        drawer.draw(stack.current(), meshes.sculptedHeadPlayer, TextureKind.PLAYER_SKIN, 1f, 1f, 1f, 0.16f, 0f, false)
        drawer.draw(stack.current(), meshes.shortHairPlayer, TextureKind.PLAYER_HAIR, 1f, 1f, 1f, 0.12f, 0f, false)

        // Subtle chin movement for dialogue speech
        if (jawOpenAmount > 0.02f) {
            stack.push()
            stack.translate(0f, -0.044f - jawOpenAmount * 0.010f, 0.016f)
            drawer.draw(stack.current(), meshes.jawGranny, TextureKind.PLAYER_SKIN, 0.96f, 0.90f, 0.88f, 0.14f, 0f, false)
            stack.pop()
        }

        // Expressive Eyeballs + Irises + Lifting Eyelids
        val eyeOpen = eyeOpenAmount.coerceIn(0f, 1f)
        for (sideSign in listOf(-1f, 1f)) {
            val ex = sideSign * 0.036f
            if (eyeOpen > 0.06f) {
                stack.push()
                stack.translate(ex, 0.020f, 0.095f)
                stack.scale(0.88f, 0.72f * eyeOpen, 0.85f)
                drawer.draw(stack.current(), meshes.eyeball, TextureKind.GRANNY_HAIR, 0.96f, 0.95f, 0.93f, 0.45f, 0f, false)
                stack.push()
                stack.translate(sin(headYawDeg * 0.017f) * 0.004f, 0f, 0.015f)
                drawer.draw(stack.current(), meshes.irisPupil, TextureKind.METAL_PROP, 0.22f, 0.14f, 0.09f, 0.75f, 0f, false)
                stack.pop()
                stack.pop()
            }

            // Eyelid closes fully when eyeOpen == 0f, lifts cleanly upward as eyes open
            val lidScaleY = 0.52f * (1f - eyeOpen * 0.82f)
            stack.push()
            stack.translate(ex, 0.022f + eyeOpen * 0.012f, 0.100f)
            stack.scale(1f, lidScaleY, 0.72f)
            drawer.draw(stack.current(), meshes.eyelidMesh, TextureKind.PLAYER_SKIN, 0.85f, 0.72f, 0.64f, 0.12f, 0f, false)
            stack.pop()

            stack.push()
            stack.translate(ex, 0.048f + eyeOpen * 0.005f, 0.108f)
            drawer.draw(stack.current(), meshes.browMesh, TextureKind.PLAYER_HAIR, 1f, 1f, 1f, 0.08f, 0f, false)
            stack.pop()
        }
        stack.pop() // Head
        stack.pop() // Neck

        // 4. Arms (dragged overhead, waking/bracing on bed, swinging during hurry, or reaching forward while crawling)
        val baseArmPitch = armReachPitchDeg ?: when {
            isCrawling -> -86f
            isBeingDragged -> 152f
            else -> 22f
        }
        for (sideSign in listOf(-1f, 1f)) {
            val strideArm = strideSin * sideSign * 22f
            stack.push()
            stack.translate(sideSign * 0.21f, 0.47f, -0.02f)
            stack.rotate(baseArmPitch + sway * sideSign * 6f - strideArm, 1f, 0f, 0f)
            stack.rotate(sideSign * (if (isCrawling) 8f else 18f), 0f, 0f, 1f)
            drawer.draw(stack.current(), meshes.jointSphere, TextureKind.PLAYER_JACKET, 1f, 1f, 1f, 0.12f, 0f, false)
            drawer.draw(stack.current(), meshes.upperArm, TextureKind.PLAYER_JACKET, 1f, 1f, 1f, 0.14f, 0f, false)

            stack.translate(0f, -0.26f, 0f)
            drawer.draw(stack.current(), meshes.sleeveCuff, TextureKind.PLAYER_JACKET, 0.92f, 0.94f, 0.96f, 0.12f, 0f, false)
            drawer.draw(stack.current(), meshes.jointSphere, TextureKind.PLAYER_SKIN, 1f, 1f, 1f, 0.14f, 0f, false)
            stack.rotate(elbowFlexDeg, 1f, 0f, 0f)
            drawer.draw(stack.current(), meshes.forearm, TextureKind.PLAYER_SKIN, 1f, 1f, 1f, 0.16f, 0f, false)

            stack.translate(0f, -0.24f, 0f)
            drawer.draw(stack.current(), meshes.palmMesh, TextureKind.PLAYER_SKIN, 1f, 1f, 1f, 0.16f, 0f, false)
            drawFirstPersonFingers(meshes, sideSign = sideSign, curlDeg = 20f, drawer = drawer)
            stack.pop()
        }

        stack.pop() // Torso
    }
}
