package com.example.engine3d

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin binary glTF 2.0 (.glb) exporter that builds real, spec-compliant skeletal + PBR
 * character models (`granny.glb` and `protagonist.glb`) directly from [CharacterCpuMeshLibrary]
 * and [ProceduralTextures3D]. Zero dummy chunks: embeds real indexed geometry, smooth normals, UVs,
 * skeletal joints/skins (`JOINTS_0`, `WEIGHTS_0`, `inverseBindMatrices`), keyframe animations, and
 * compressed RGBA PNG PBR albedo maps ready for Google Filament `gltfio`.
 */
object GlbBinaryExporter {

    private data class JointDef(
        val name: String,
        val parentIdx: Int,
        val localTx: Float,
        val localTy: Float,
        val localTz: Float,
    )

    private data class AttachedSubmesh(
        val jointIndex: Int,
        val mesh: MeshData,
        val materialKey: String,
        val localMat: FloatArray,
    )

    private data class MaterialSpec(
        val name: String,
        val textureKind: TextureKind,
        val baseColor: FloatArray,
        val metallic: Float,
        val roughness: Float,
        val emissive: FloatArray = floatArrayOf(0f, 0f, 0f),
        val doubleSided: Boolean = false,
    )

    private data class MergedPrimitive(
        val materialIndex: Int,
        val positions: FloatArray,
        val normals: FloatArray,
        val uvs: FloatArray,
        val joints: ShortArray,
        val weights: FloatArray,
        val indices: ShortArray,
        val minPos: FloatArray,
        val maxPos: FloatArray,
    )

    private data class AnimTrack(
        val nodeOffsetInJoints: Int,
        val path: String, // "rotation" or "translation"
        val times: FloatArray,
        val values: FloatArray, // VEC4 for rotation, VEC3 for translation
    )

    private data class AnimClip(
        val name: String,
        val tracks: List<AnimTrack>,
    )

    private val grannyJoints = listOf(
        JointDef("Pelvis", -1, 0f, 0.78f, 0f),                // 0
        JointDef("Spine", 0, 0f, 0.02f, 0f),                  // 1
        JointDef("Neck", 1, 0f, 0.47f, 0.02f),                // 2
        JointDef("Head", 2, 0f, 0.16f, 0.01f),                // 3
        JointDef("Jaw", 3, 0f, -0.066f, 0.012f),              // 4
        JointDef("UpperArm_L", 1, 0.178f, 0.435f, 0f),        // 5
        JointDef("Forearm_L", 5, 0f, -0.227f, 0f),            // 6
        JointDef("Hand_L", 6, 0f, -0.210f, 0f),               // 7
        JointDef("UpperArm_R", 1, -0.178f, 0.435f, 0f),       // 8
        JointDef("Forearm_R", 8, 0f, -0.227f, 0f),            // 9
        JointDef("Hand_R", 9, 0f, -0.210f, 0f),               // 10
        JointDef("Thigh_L", 0, 0.105f, -0.02f, 0f),           // 11
        JointDef("Shin_L", 11, 0f, -0.38f, 0f),               // 12
        JointDef("Foot_L", 12, 0f, -0.37f, 0f),               // 13
        JointDef("Thigh_R", 0, -0.105f, -0.02f, 0f),          // 14
        JointDef("Shin_R", 14, 0f, -0.38f, 0f),               // 15
        JointDef("Foot_R", 15, 0f, -0.37f, 0f),               // 16
    )

    private val protagonistJoints = listOf(
        JointDef("Pelvis", -1, 0f, 0.80f, 0f),                // 0
        JointDef("Spine", 0, 0f, 0.02f, 0f),                  // 1
        JointDef("Neck", 1, 0f, 0.51f, 0f),                   // 2
        JointDef("Head", 2, 0f, 0.15f, 0.01f),                // 3
        JointDef("Jaw", 3, 0f, -0.044f, 0.016f),              // 4
        JointDef("UpperArm_L", 1, 0.21f, 0.47f, -0.02f),      // 5
        JointDef("Forearm_L", 5, 0f, -0.26f, 0f),             // 6
        JointDef("Hand_L", 6, 0f, -0.24f, 0f),                // 7
        JointDef("UpperArm_R", 1, -0.21f, 0.47f, -0.02f),     // 8
        JointDef("Forearm_R", 8, 0f, -0.26f, 0f),             // 9
        JointDef("Hand_R", 9, 0f, -0.24f, 0f),                // 10
        JointDef("Thigh_L", 0, 0.105f, -0.02f, 0f),           // 11
        JointDef("Shin_L", 11, 0f, -0.38f, 0f),               // 12
        JointDef("Foot_L", 12, 0f, -0.37f, 0f),               // 13
        JointDef("Thigh_R", 0, -0.105f, -0.02f, 0f),          // 14
        JointDef("Shin_R", 14, 0f, -0.38f, 0f),               // 15
        JointDef("Foot_R", 15, 0f, -0.37f, 0f),               // 16
    )

    fun buildGrannyGlb(
        cpuMeshes: CharacterCpuMeshLibrary = CharacterCpuMeshLibrary(),
        textureSizePx: Int = 128,
    ): ByteArray {
        val materials = listOf(
            MaterialSpec("GrannySkin", TextureKind.GRANNY_SKIN, floatArrayOf(0.84f, 0.80f, 0.77f, 1f), metallic = 0.0f, roughness = 0.78f),
            MaterialSpec("GrannyDress", TextureKind.GRANNY_DRESS, floatArrayOf(1.0f, 0.98f, 1.0f, 1f), metallic = 0.0f, roughness = 0.90f, doubleSided = true),
            MaterialSpec("GrannyHair", TextureKind.GRANNY_HAIR, floatArrayOf(0.50f, 0.49f, 0.54f, 1f), metallic = 0.0f, roughness = 0.84f, doubleSided = true),
            MaterialSpec("GrannyEyeball", TextureKind.GRANNY_HAIR, floatArrayOf(0.92f, 0.88f, 0.84f, 1f), metallic = 0.0f, roughness = 0.22f),
            MaterialSpec("GrannyCrimsonIris", TextureKind.LANTERN_CORE, floatArrayOf(1.0f, 0.22f, 0.18f, 1f), metallic = 0.05f, roughness = 0.18f, emissive = floatArrayOf(0.55f, 0.06f, 0.04f)),
            MaterialSpec("GrannyBoots", TextureKind.METAL_PROP, floatArrayOf(0.28f, 0.22f, 0.20f, 1f), metallic = 0.15f, roughness = 0.62f),
        )

        val parts = mutableListOf<AttachedSubmesh>()
        fun add(joint: Int, mesh: MeshData, mat: String, x: Float = 0f, y: Float = 0f, z: Float = 0f, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f) {
            parts.add(AttachedSubmesh(joint, mesh, mat, composeLocalMat(x, y, z, rx, ry, rz, sx, sy, sz)))
        }

        // Skirt & Spine & Shawl
        add(0, cpuMeshes.skirtGranny, "GrannyDress")
        add(1, cpuMeshes.torsoGranny, "GrannyDress")
        add(1, cpuMeshes.shawlCollar, "GrannyDress", y = 0.44f, z = 0.015f, sx = 1.04f, sy = 1.08f, sz = 1.06f)

        // Neck, Sculpted Head, Continuous Hair Cap + Bun + Side Locks + Wisps
        add(2, cpuMeshes.neckMesh, "GrannySkin")
        add(3, cpuMeshes.sculptedHeadGranny, "GrannySkin")
        add(3, cpuMeshes.hairBun, "GrannyHair")
        add(3, cpuMeshes.hairStrand, "GrannyHair", x = 0.086f, y = 0.006f, z = -0.002f, rx = 10f, rz = -7f, sx = 0.92f, sy = 1.02f, sz = 0.92f)
        add(3, cpuMeshes.hairStrand, "GrannyHair", x = -0.086f, y = 0.006f, z = -0.002f, rx = 10f, rz = 7f, sx = 0.92f, sy = 1.02f, sz = 0.92f)
        add(3, cpuMeshes.hairStrand, "GrannyHair", x = 0.056f, y = 0.070f, z = 0.064f, rx = -14f, ry = 18f, rz = -26f, sx = 0.36f, sy = 0.34f, sz = 0.38f)
        add(3, cpuMeshes.hairStrand, "GrannyHair", x = -0.056f, y = 0.070f, z = 0.064f, rx = -14f, ry = -18f, rz = 26f, sx = 0.36f, sy = 0.34f, sz = 0.38f)
        add(4, cpuMeshes.jawGranny, "GrannySkin")

        // Eyes, Crimson Pupils, Eyelids, Eyebrows
        for (side in floatArrayOf(-1f, 1f)) {
            val ex = side * 0.037f
            add(3, cpuMeshes.eyeball, "GrannyEyeball", x = ex, y = 0.021f, z = 0.091f, sx = 0.90f, sy = 0.86f, sz = 0.88f)
            add(3, cpuMeshes.irisPupil, "GrannyCrimsonIris", x = ex, y = 0.021f, z = 0.104f)
            add(3, cpuMeshes.eyelidMesh, "GrannySkin", x = ex, y = 0.028f, z = 0.094f, sx = 0.96f, sy = 0.24f, sz = 0.92f)
            add(3, cpuMeshes.browMesh, "GrannyHair", x = ex, y = 0.052f, z = 0.099f, rz = side * 12f)
        }

        // Arms & Articulated 5-Fingered Hands
        val segScale = 0.84f
        for ((idx, side) in listOf(0 to 1f, 1 to -1f)) {
            val upperJoint = if (idx == 0) 5 else 8
            val foreJoint = if (idx == 0) 6 else 9
            val handJoint = if (idx == 0) 7 else 10

            add(upperJoint, cpuMeshes.jointSphere, "GrannyDress", sx = 0.86f, sy = 0.86f, sz = 0.86f)
            add(upperJoint, cpuMeshes.upperArm, "GrannyDress", sx = 0.92f, sy = segScale, sz = 0.92f)
            add(foreJoint, cpuMeshes.sleeveCuff, "GrannyDress", sx = 0.92f, sy = 0.92f, sz = 0.92f)
            add(foreJoint, cpuMeshes.jointSphere, "GrannySkin", sx = 0.82f, sy = 0.82f, sz = 0.82f)
            add(foreJoint, cpuMeshes.forearm, "GrannySkin", sx = 0.92f, sy = segScale, sz = 0.92f)

            add(handJoint, cpuMeshes.jointSphere, "GrannySkin", sx = 0.50f, sy = 0.40f, sz = 0.40f)
            add(handJoint, cpuMeshes.palmGranny, "GrannySkin", sx = 0.90f, sy = 0.90f, sz = 0.90f)
            val xOff = floatArrayOf(-0.0255f, -0.0085f, 0.0090f, 0.0255f)
            val yOff = floatArrayOf(-0.072f, -0.075f, -0.073f, -0.067f)
            val lenScales = floatArrayOf(0.96f, 1.08f, 0.98f, 0.78f)
            val wScales = floatArrayOf(0.94f, 0.98f, 0.90f, 0.78f)
            for (f in 0 until 4) {
                val fx = -side * xOff[f] * 0.90f
                val fy = yOff[f] * 0.90f
                add(handJoint, cpuMeshes.fingerGranny, "GrannySkin", x = fx, y = fy, z = 0.003f, rx = -24f, sx = 0.90f * wScales[f], sy = 0.90f * lenScales[f], sz = 0.90f * wScales[f])
                add(handJoint, cpuMeshes.distalFingerGranny, "GrannySkin", x = fx, y = fy - 0.032f * lenScales[f], z = 0.015f, rx = -46f, sx = 0.90f * wScales[f], sy = 0.90f * lenScales[f], sz = 0.90f * wScales[f])
            }
            add(handJoint, cpuMeshes.fingerGranny, "GrannySkin", x = -side * 0.030f, y = -0.032f, z = 0.012f, rx = -16f, ry = side * 20f, rz = -side * 36f, sx = 0.96f, sy = 0.74f, sz = 0.96f)
        }

        // Legs & Victorian Boots
        add(11, cpuMeshes.thighMesh, "GrannyDress")
        add(12, cpuMeshes.jointSphere, "GrannySkin")
        add(12, cpuMeshes.shinMesh, "GrannySkin")
        add(13, cpuMeshes.footShoeMesh, "GrannyBoots")
        add(14, cpuMeshes.thighMesh, "GrannyDress")
        add(15, cpuMeshes.jointSphere, "GrannySkin")
        add(15, cpuMeshes.shinMesh, "GrannySkin")
        add(16, cpuMeshes.footShoeMesh, "GrannyBoots")

        val animations = listOf(
            AnimClip(
                name = "Idle",
                tracks = listOf(
                    AnimTrack(1, "rotation", floatArrayOf(0f, 1.0f, 2.0f), concatQuats(14f, 0f, 0f, 17f, 0f, 0f, 14f, 0f, 0f)),
                    AnimTrack(3, "rotation", floatArrayOf(0f, 1.0f, 2.0f), concatQuats(-6f, 0f, -4f, -4f, 0f, 5f, -6f, 0f, -4f)),
                    AnimTrack(5, "rotation", floatArrayOf(0f, 1.0f, 2.0f), concatQuats(-14f, 0f, 5f, -20f, 0f, 6f, -14f, 0f, 5f)),
                    AnimTrack(8, "rotation", floatArrayOf(0f, 1.0f, 2.0f), concatQuats(-14f, 0f, -5f, -20f, 0f, -6f, -14f, 0f, -5f)),
                ),
            ),
            AnimClip(
                name = "Walk",
                tracks = listOf(
                    AnimTrack(1, "rotation", floatArrayOf(0f, 0.5f, 1.0f), concatQuats(18f, -4f, 0f, 18f, 4f, 0f, 18f, -4f, 0f)),
                    AnimTrack(11, "rotation", floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1.0f), concatQuats(24f, 0f, 0f, 0f, 0f, 0f, -24f, 0f, 0f, 0f, 0f, 0f, 24f, 0f, 0f)),
                    AnimTrack(14, "rotation", floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1.0f), concatQuats(-24f, 0f, 0f, 0f, 0f, 0f, 24f, 0f, 0f, 0f, 0f, 0f, -24f, 0f, 0f)),
                    AnimTrack(5, "rotation", floatArrayOf(0f, 0.5f, 1.0f), concatQuats(-32f, 0f, 6f, 12f, 0f, 6f, -32f, 0f, 6f)),
                    AnimTrack(8, "rotation", floatArrayOf(0f, 0.5f, 1.0f), concatQuats(12f, 0f, -6f, -32f, 0f, -6f, 12f, 0f, -6f)),
                ),
            ),
            AnimClip(
                name = "Hunt",
                tracks = listOf(
                    AnimTrack(1, "rotation", floatArrayOf(0f, 0.35f, 0.7f), concatQuats(26f, -6f, 0f, 26f, 6f, 0f, 26f, -6f, 0f)),
                    AnimTrack(4, "rotation", floatArrayOf(0f, 0.35f, 0.7f), concatQuats(8f, 0f, 0f, 26f, 0f, 0f, 8f, 0f, 0f)),
                    AnimTrack(5, "rotation", floatArrayOf(0f, 0.35f, 0.7f), concatQuats(-74f, 0f, 10f, -52f, 0f, 10f, -74f, 0f, 10f)),
                    AnimTrack(8, "rotation", floatArrayOf(0f, 0.35f, 0.7f), concatQuats(-52f, 0f, -10f, -74f, 0f, -10f, -52f, 0f, -10f)),
                ),
            ),
        )

        return assembleGlb(
            modelName = "Granny",
            joints = grannyJoints,
            materials = materials,
            parts = parts,
            animations = animations,
            textureSizePx = textureSizePx,
        )
    }

    fun buildProtagonistGlb(
        cpuMeshes: CharacterCpuMeshLibrary = CharacterCpuMeshLibrary(),
        textureSizePx: Int = 128,
    ): ByteArray {
        val materials = listOf(
            MaterialSpec("PlayerSkin", TextureKind.PLAYER_SKIN, floatArrayOf(1.0f, 1.0f, 1.0f, 1f), metallic = 0.0f, roughness = 0.68f),
            MaterialSpec("PlayerJacket", TextureKind.PLAYER_JACKET, floatArrayOf(1.0f, 1.0f, 1.0f, 1f), metallic = 0.02f, roughness = 0.85f),
            MaterialSpec("PlayerHair", TextureKind.PLAYER_HAIR, floatArrayOf(1.0f, 1.0f, 1.0f, 1f), metallic = 0.0f, roughness = 0.82f),
            MaterialSpec("PlayerEyeball", TextureKind.GRANNY_HAIR, floatArrayOf(0.96f, 0.95f, 0.93f, 1f), metallic = 0.0f, roughness = 0.20f),
            MaterialSpec("PlayerBoots", TextureKind.METAL_PROP, floatArrayOf(0.22f, 0.20f, 0.18f, 1f), metallic = 0.18f, roughness = 0.58f),
        )

        val parts = mutableListOf<AttachedSubmesh>()
        fun add(joint: Int, mesh: MeshData, mat: String, x: Float = 0f, y: Float = 0f, z: Float = 0f, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f) {
            parts.add(AttachedSubmesh(joint, mesh, mat, composeLocalMat(x, y, z, rx, ry, rz, sx, sy, sz)))
        }

        // Torso, Collar, Neck, Sculpted Head, Hair, Jaw, Eyes
        add(1, cpuMeshes.torsoPlayer, "PlayerJacket")
        add(2, cpuMeshes.jacketCollar, "PlayerJacket")
        add(2, cpuMeshes.neckMesh, "PlayerSkin")
        add(3, cpuMeshes.sculptedHeadPlayer, "PlayerSkin")
        add(3, cpuMeshes.shortHairPlayer, "PlayerHair")
        add(4, cpuMeshes.jawGranny, "PlayerSkin")
        for (side in floatArrayOf(-1f, 1f)) {
            val ex = side * 0.036f
            add(3, cpuMeshes.eyeball, "PlayerEyeball", x = ex, y = 0.020f, z = 0.095f, sx = 0.88f, sy = 0.72f, sz = 0.85f)
            add(3, cpuMeshes.irisPupil, "PlayerBoots", x = ex, y = 0.020f, z = 0.109f)
            add(3, cpuMeshes.browMesh, "PlayerHair", x = ex, y = 0.052f, z = 0.106f)
        }

        // Arms & Hands
        for ((idx, side) in listOf(0 to 1f, 1 to -1f)) {
            val upper = if (idx == 0) 5 else 8
            val fore = if (idx == 0) 6 else 9
            val hand = if (idx == 0) 7 else 10
            add(upper, cpuMeshes.jointSphere, "PlayerJacket", sx = 0.90f, sy = 0.90f, sz = 0.90f)
            add(upper, cpuMeshes.upperArm, "PlayerJacket")
            add(fore, cpuMeshes.sleeveCuff, "PlayerJacket")
            add(fore, cpuMeshes.jointSphere, "PlayerSkin", sx = 0.84f, sy = 0.84f, sz = 0.84f)
            add(fore, cpuMeshes.forearm, "PlayerSkin")
            add(hand, cpuMeshes.palmMesh, "PlayerSkin")
            for (f in 0 until 4) {
                val spreadX = (f - 1.5f) * 0.017f
                add(hand, cpuMeshes.fingerMesh, "PlayerSkin", x = spreadX, y = -0.072f, z = 0.004f, rx = -18f)
                add(hand, cpuMeshes.distalFinger, "PlayerSkin", x = spreadX, y = -0.105f, z = 0.014f, rx = -34f)
            }
            add(hand, cpuMeshes.fingerMesh, "PlayerSkin", x = -side * 0.032f, y = -0.035f, z = 0.015f, rx = -12f, rz = -side * 35f)
        }

        // Legs & Boots
        add(11, cpuMeshes.thighMesh, "PlayerJacket")
        add(12, cpuMeshes.jointSphere, "PlayerJacket", sx = 0.92f, sy = 0.92f, sz = 0.92f)
        add(12, cpuMeshes.shinMesh, "PlayerJacket")
        add(13, cpuMeshes.footShoeMesh, "PlayerBoots")
        add(14, cpuMeshes.thighMesh, "PlayerJacket")
        add(15, cpuMeshes.jointSphere, "PlayerJacket", sx = 0.92f, sy = 0.92f, sz = 0.92f)
        add(15, cpuMeshes.shinMesh, "PlayerJacket")
        add(16, cpuMeshes.footShoeMesh, "PlayerBoots")

        val animations = listOf(
            AnimClip(
                name = "Idle",
                tracks = listOf(
                    AnimTrack(1, "rotation", floatArrayOf(0f, 1.0f, 2.0f), concatQuats(0f, 0f, 0f, 2.5f, 0f, 0f, 0f, 0f, 0f)),
                    AnimTrack(3, "rotation", floatArrayOf(0f, 1.0f, 2.0f), concatQuats(0f, -5f, 0f, 0f, 5f, 0f, 0f, -5f, 0f)),
                ),
            ),
            AnimClip(
                name = "Walk",
                tracks = listOf(
                    AnimTrack(11, "rotation", floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1.0f), concatQuats(26f, 0f, 0f, 0f, 0f, 0f, -26f, 0f, 0f, 0f, 0f, 0f, 26f, 0f, 0f)),
                    AnimTrack(14, "rotation", floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1.0f), concatQuats(-26f, 0f, 0f, 0f, 0f, 0f, 26f, 0f, 0f, 0f, 0f, 0f, -26f, 0f, 0f)),
                    AnimTrack(5, "rotation", floatArrayOf(0f, 0.5f, 1.0f), concatQuats(-20f, 0f, 4f, 18f, 0f, 4f, -20f, 0f, 4f)),
                    AnimTrack(8, "rotation", floatArrayOf(0f, 0.5f, 1.0f), concatQuats(18f, 0f, -4f, -20f, 0f, -4f, 18f, 0f, -4f)),
                ),
            ),
        )

        return assembleGlb(
            modelName = "Protagonist",
            joints = protagonistJoints,
            materials = materials,
            parts = parts,
            animations = animations,
            textureSizePx = textureSizePx,
        )
    }

    fun writeModelAssetsToDir(assetsModelsDir: File, textureSizePx: Int = 128) {
        if (!assetsModelsDir.exists()) {
            assetsModelsDir.mkdirs()
        }
        val cpu = CharacterCpuMeshLibrary()
        File(assetsModelsDir, "granny.glb").writeBytes(buildGrannyGlb(cpu, textureSizePx))
        File(assetsModelsDir, "protagonist.glb").writeBytes(buildProtagonistGlb(cpu, textureSizePx))
    }

    private fun assembleGlb(
        modelName: String,
        joints: List<JointDef>,
        materials: List<MaterialSpec>,
        parts: List<AttachedSubmesh>,
        animations: List<AnimClip>,
        textureSizePx: Int,
    ): ByteArray {
        // 1. Compute bind-pose world matrices & inverse bind matrices for all joints
        val jointWorldMats = Array(joints.size) { FloatArray(16) }
        val invBindMatrices = FloatArray(joints.size * 16)
        for (j in joints.indices) {
            val jd = joints[j]
            val local = composeLocalMat(jd.localTx, jd.localTy, jd.localTz)
            if (jd.parentIdx < 0) {
                jointWorldMats[j] = local
            } else {
                jointWorldMats[j] = mulMat4(jointWorldMats[jd.parentIdx], local)
            }
            val wx = jointWorldMats[j][12]
            val wy = jointWorldMats[j][13]
            val wz = jointWorldMats[j][14]
            val inv = identityMat4()
            inv[12] = -wx
            inv[13] = -wy
            inv[14] = -wz
            System.arraycopy(inv, 0, invBindMatrices, j * 16, 16)
        }

        // 2. Group all submeshes per material into clean skin-bound primitives
        val primitives = mutableListOf<MergedPrimitive>()
        for (matIdx in materials.indices) {
            val matName = materials[matIdx].name
            val matching = parts.filter { it.materialKey == matName }
            if (matching.isEmpty()) continue

            val posList = ArrayList<Float>(16384)
            val normList = ArrayList<Float>(16384)
            val uvList = ArrayList<Float>(10240)
            val jointList = ArrayList<Short>(20480)
            val weightList = ArrayList<Float>(20480)
            val indList = ArrayList<Short>(24576)

            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var minZ = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            var maxZ = Float.NEGATIVE_INFINITY
            var vertCursor = 0

            for (sub in matching) {
                val worldM = mulMat4(jointWorldMats[sub.jointIndex], sub.localMat)
                val v = sub.mesh.vertices
                val vc = sub.mesh.vertexCount
                val jointShort = sub.jointIndex.toShort()

                for (i in 0 until vc) {
                    val b = i * 8
                    val lx = v[b]; val ly = v[b + 1]; val lz = v[b + 2]
                    val nx = v[b + 3]; val ny = v[b + 4]; val nz = v[b + 5]
                    val tu = v[b + 6]; val tv = v[b + 7]

                    val wx = worldM[0] * lx + worldM[4] * ly + worldM[8] * lz + worldM[12]
                    val wy = worldM[1] * lx + worldM[5] * ly + worldM[9] * lz + worldM[13]
                    val wz = worldM[2] * lx + worldM[6] * ly + worldM[10] * lz + worldM[14]

                    var wnx = worldM[0] * nx + worldM[4] * ny + worldM[8] * nz
                    var wny = worldM[1] * nx + worldM[5] * ny + worldM[9] * nz
                    var wnz = worldM[2] * nx + worldM[6] * ny + worldM[10] * nz
                    val nLen = sqrt(wnx * wnx + wny * wny + wnz * wnz).coerceAtLeast(1e-5f)
                    wnx /= nLen; wny /= nLen; wnz /= nLen

                    if (wx < minX) minX = wx; if (wx > maxX) maxX = wx
                    if (wy < minY) minY = wy; if (wy > maxY) maxY = wy
                    if (wz < minZ) minZ = wz; if (wz > maxZ) maxZ = wz

                    posList.add(wx); posList.add(wy); posList.add(wz)
                    normList.add(wnx); normList.add(wny); normList.add(wnz)
                    uvList.add(tu); uvList.add(tv)
                    jointList.add(jointShort); jointList.add(0); jointList.add(0); jointList.add(0)
                    weightList.add(1f); weightList.add(0f); weightList.add(0f); weightList.add(0f)
                }

                for (idxVal in sub.mesh.indices) {
                    indList.add((vertCursor + (idxVal.toInt() and 0xFFFF)).toShort())
                }
                vertCursor += vc
            }

            primitives.add(
                MergedPrimitive(
                    materialIndex = matIdx,
                    positions = posList.toFloatArray(),
                    normals = normList.toFloatArray(),
                    uvs = uvList.toFloatArray(),
                    joints = jointList.toShortArray(),
                    weights = weightList.toFloatArray(),
                    indices = indList.toShortArray(),
                    minPos = floatArrayOf(minX, minY, minZ),
                    maxPos = floatArrayOf(maxX, maxY, maxZ),
                ),
            )
        }

        // 3. Build BIN buffer with strict 4-byte alignment
        val binOut = ByteArrayOutputStream(256 * 1024)
        val bufferViews = mutableListOf<String>()
        val accessors = mutableListOf<String>()

        fun padBinTo4() {
            while (binOut.size() % 4 != 0) binOut.write(0)
        }

        fun appendBytes(bytes: ByteArray, target: Int? = null): Int {
            padBinTo4()
            val offset = binOut.size()
            binOut.write(bytes)
            padBinTo4()
            val bvIdx = bufferViews.size
            val targetStr = if (target != null) ""","target":$target""" else ""
            bufferViews.add("""{"buffer":0,"byteOffset":$offset,"byteLength":${bytes.size}$targetStr}""")
            return bvIdx
        }

        fun appendFloatArray(arr: FloatArray, target: Int? = null): Int {
            val bb = ByteBuffer.allocate(arr.size * 4).order(ByteOrder.LITTLE_ENDIAN)
            bb.asFloatBuffer().put(arr)
            return appendBytes(bb.array(), target)
        }

        fun appendShortArray(arr: ShortArray, target: Int? = null): Int {
            val bb = ByteBuffer.allocate(arr.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            bb.asShortBuffer().put(arr)
            return appendBytes(bb.array(), target)
        }

        fun addAccessor(
            bvIdx: Int,
            componentType: Int,
            count: Int,
            type: String,
            min: FloatArray? = null,
            max: FloatArray? = null,
        ): Int {
            val idx = accessors.size
            val minMax = if (min != null && max != null) {
                val minStr = min.joinToString(",") { fmt(it) }
                val maxStr = max.joinToString(",") { fmt(it) }
                ""","min":[$minStr],"max":[$maxStr]"""
            } else ""
            accessors.add("""{"bufferView":$bvIdx,"componentType":$componentType,"count":$count,"type":"$type"$minMax}""")
            return idx
        }

        // Primitives binary chunks
        val primJsonList = mutableListOf<String>()
        for (p in primitives) {
            val vc = p.positions.size / 3
            val posBv = appendFloatArray(p.positions, target = 34962)
            val normBv = appendFloatArray(p.normals, target = 34962)
            val uvBv = appendFloatArray(p.uvs, target = 34962)
            val jointBv = appendShortArray(p.joints, target = 34962)
            val weightBv = appendFloatArray(p.weights, target = 34962)
            val indBv = appendShortArray(p.indices, target = 34963)

            val posAcc = addAccessor(posBv, 5126, vc, "VEC3", p.minPos, p.maxPos)
            val normAcc = addAccessor(normBv, 5126, vc, "VEC3")
            val uvAcc = addAccessor(uvBv, 5126, vc, "VEC2")
            val jointAcc = addAccessor(jointBv, 5123, vc, "VEC4")
            val weightAcc = addAccessor(weightBv, 5126, vc, "VEC4")
            val indAcc = addAccessor(indBv, 5123, p.indices.size, "SCALAR")

            primJsonList.add(
                """{"attributes":{"POSITION":$posAcc,"NORMAL":$normAcc,"TEXCOORD_0":$uvAcc,"JOINTS_0":$jointAcc,"WEIGHTS_0":$weightAcc},"indices":$indAcc,"material":${p.materialIndex},"mode":4}""",
            )
        }

        // Skin inverseBindMatrices accessor
        val ibmBv = appendFloatArray(invBindMatrices)
        val ibmAcc = addAccessor(ibmBv, 5126, joints.size, "MAT4")

        // Textures -> PNG bytes embedded cleanly into BIN buffer
        val uniqueKinds = materials.map { it.textureKind }.distinct()
        val imageIdxByKind = HashMap<TextureKind, Int>(uniqueKinds.size)
        val imagesJson = mutableListOf<String>()
        val texturesJson = mutableListOf<String>()
        for ((idx, kind) in uniqueKinds.withIndex()) {
            val pixels = ProceduralTextures3D.generatePixels(kind, size = textureSizePx)
            val pngBytes = encodeRgbaPng(pixels, textureSizePx, textureSizePx)
            val imgBv = appendBytes(pngBytes)
            imagesJson.add("""{"bufferView":$imgBv,"mimeType":"image/png","name":"${kind.name}"}""")
            texturesJson.add("""{"sampler":0,"source":$idx}""")
            imageIdxByKind[kind] = idx
        }

        // Materials JSON
        val materialsJson = materials.map { m ->
            val texIdx = imageIdxByKind.getValue(m.textureKind)
            val bcf = m.baseColor.joinToString(",") { fmt(it) }
            val emf = m.emissive.joinToString(",") { fmt(it) }
            """{"name":"${m.name}","doubleSided":${m.doubleSided},"emissiveFactor":[$emf],"pbrMetallicRoughness":{"baseColorFactor":[$bcf],"baseColorTexture":{"index":$texIdx},"metallicFactor":${fmt(m.metallic)},"roughnessFactor":${fmt(m.roughness)}}}"""
        }

        // Nodes hierarchy: Node 0 = Root, Node 1 = SkinnedMeshNode, Nodes 2..(2+joints.size-1) = Skeletal Joints
        val firstJointNode = 2
        val nodesJson = mutableListOf<String>()
        nodesJson.add("""{"name":"${modelName}_Root","children":[1,$firstJointNode]}""")
        nodesJson.add("""{"name":"${modelName}_SkinnedMesh","mesh":0,"skin":0}""")

        val childMap = HashMap<Int, List<Int>>()
        for (j in joints.indices) {
            val children = joints.indices.filter { joints[it].parentIdx == j }.map { firstJointNode + it }
            childMap[j] = children
        }
        for (j in joints.indices) {
            val jd = joints[j]
            val children = childMap[j].orEmpty()
            val childPart = if (children.isNotEmpty()) ""","children":[${children.joinToString(",")}]""" else ""
            val tr = "${fmt(jd.localTx)},${fmt(jd.localTy)},${fmt(jd.localTz)}"
            nodesJson.add("""{"name":"${jd.name}","translation":[$tr]$childPart}""")
        }

        val skinJointNodes = joints.indices.joinToString(",") { "${firstJointNode + it}" }

        // Keyframe Animations
        val animJsonList = mutableListOf<String>()
        for (clip in animations) {
            val samplersJson = mutableListOf<String>()
            val channelsJson = mutableListOf<String>()
            for ((trackIdx, track) in clip.tracks.withIndex()) {
                val timeMin = floatArrayOf(track.times.minOrNull() ?: 0f)
                val timeMax = floatArrayOf(track.times.maxOrNull() ?: 1f)
                val tBv = appendFloatArray(track.times)
                val tAcc = addAccessor(tBv, 5126, track.times.size, "SCALAR", timeMin, timeMax)

                val valType = if (track.path == "rotation") "VEC4" else "VEC3"
                val valCount = track.times.size
                val vBv = appendFloatArray(track.values)
                val vAcc = addAccessor(vBv, 5126, valCount, valType)

                samplersJson.add("""{"input":$tAcc,"interpolation":"LINEAR","output":$vAcc}""")
                val targetNode = firstJointNode + track.nodeOffsetInJoints
                channelsJson.add("""{"sampler":$trackIdx,"target":{"node":$targetNode,"path":"${track.path}"}}""")
            }
            animJsonList.add("""{"name":"${clip.name}","channels":[${channelsJson.joinToString(",")}],"samplers":[${samplersJson.joinToString(",")}]}""")
        }

        val binBytes = binOut.toByteArray()

        val jsonStr = buildString {
            append("""{"asset":{"version":"2.0","generator":"ProjectDarkness-GlbBinaryExporter"},""")
            append(""""scene":0,"scenes":[{"name":"${modelName}Scene","nodes":[0]}],""")
            append(""""nodes":[${nodesJson.joinToString(",")}],""")
            append(""""meshes":[{"name":"${modelName}Mesh","primitives":[${primJsonList.joinToString(",")}]}],""")
            append(""""skins":[{"name":"${modelName}Skeleton","inverseBindMatrices":$ibmAcc,"skeleton":$firstJointNode,"joints":[$skinJointNodes]}],""")
            append(""""animations":[${animJsonList.joinToString(",")}],""")
            append(""""materials":[${materialsJson.joinToString(",")}],""")
            append(""""textures":[${texturesJson.joinToString(",")}],""")
            append(""""images":[${imagesJson.joinToString(",")}],""")
            append(""""samplers":[{"magFilter":9729,"minFilter":9987,"wrapS":10497,"wrapT":10497}],""")
            append(""""accessors":[${accessors.joinToString(",")}],""")
            append(""""bufferViews":[${bufferViews.joinToString(",")}],""")
            append(""""buffers":[{"byteLength":${binBytes.size}}]}""")
        }

        val jsonRaw = jsonStr.toByteArray(Charsets.UTF_8)
        val jsonPad = (4 - (jsonRaw.size % 4)) % 4
        val jsonChunkLen = jsonRaw.size + jsonPad
        val binPad = (4 - (binBytes.size % 4)) % 4
        val binChunkLen = binBytes.size + binPad
        val totalLen = 12 + 8 + jsonChunkLen + 8 + binChunkLen

        val out = ByteBuffer.allocate(totalLen).order(ByteOrder.LITTLE_ENDIAN)
        // GLB Header
        out.putInt(0x46546C67) // "glTF"
        out.putInt(2)          // version 2
        out.putInt(totalLen)
        // JSON chunk
        out.putInt(jsonChunkLen)
        out.putInt(0x4E4F534A) // "JSON"
        out.put(jsonRaw)
        repeat(jsonPad) { out.put(0x20.toByte()) }
        // BIN chunk
        out.putInt(binChunkLen)
        out.putInt(0x004E4942) // "BIN\0"
        out.put(binBytes)
        repeat(binPad) { out.put(0x00.toByte()) }

        return out.array()
    }

    /**
     * Pure-Kotlin RGBA PNG encoder (zero Android dependency) so glTF textures are real embedded PNGs.
     */
    private fun encodeRgbaPng(argbPixels: IntArray, width: Int, height: Int): ByteArray {
        val rawRows = ByteArray(height * (1 + width * 4))
        var dst = 0
        for (y in 0 until height) {
            rawRows[dst++] = 0 // Filter type None
            val rowOff = y * width
            for (x in 0 until width) {
                val c = argbPixels[rowOff + x]
                rawRows[dst++] = ((c shr 16) and 0xFF).toByte() // R
                rawRows[dst++] = ((c shr 8) and 0xFF).toByte()  // G
                rawRows[dst++] = (c and 0xFF).toByte()          // B
                rawRows[dst++] = ((c ushr 24) and 0xFF).toByte() // A
            }
        }

        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION)
        deflater.setInput(rawRows)
        deflater.finish()
        val idatBuf = ByteArrayOutputStream(rawRows.size / 2)
        val tmp = ByteArray(4096)
        while (!deflater.finished()) {
            val n = deflater.deflate(tmp)
            if (n > 0) idatBuf.write(tmp, 0, n)
        }
        deflater.end()

        val png = ByteArrayOutputStream(idatBuf.size() + 128)
        png.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)) // PNG signature

        fun writeChunk(type: String, payload: ByteArray) {
            val header = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(payload.size).array()
            val typeBytes = type.toByteArray(Charsets.US_ASCII)
            val crc = CRC32().apply {
                update(typeBytes)
                update(payload)
            }.value.toInt()
            val crcBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(crc).array()
            png.write(header)
            png.write(typeBytes)
            png.write(payload)
            png.write(crcBytes)
        }

        val ihdr = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(width)
            putInt(height)
            put(8.toByte()) // bit depth
            put(6.toByte()) // RGBA
            put(0.toByte()) // compression
            put(0.toByte()) // filter
            put(0.toByte()) // interlace
        }.array()
        writeChunk("IHDR", ihdr)
        writeChunk("IDAT", idatBuf.toByteArray())
        writeChunk("IEND", ByteArray(0))
        return png.toByteArray()
    }

    private fun composeLocalMat(
        tx: Float = 0f, ty: Float = 0f, tz: Float = 0f,
        rxDeg: Float = 0f, ryDeg: Float = 0f, rzDeg: Float = 0f,
        sx: Float = 1f, sy: Float = 1f, sz: Float = 1f,
    ): FloatArray {
        val stack = BoneMatrixStack(4)
        stack.resetIdentity()
        stack.translate(tx, ty, tz)
        if (ryDeg != 0f) stack.rotate(ryDeg, 0f, 1f, 0f)
        if (rxDeg != 0f) stack.rotate(rxDeg, 1f, 0f, 0f)
        if (rzDeg != 0f) stack.rotate(rzDeg, 0f, 0f, 1f)
        stack.scale(sx, sy, sz)
        return stack.current().copyOf()
    }

    private fun identityMat4(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    private fun mulMat4(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (col in 0 until 4) {
            for (row in 0 until 4) {
                r[col * 4 + row] =
                    a[row] * b[col * 4] +
                    a[4 + row] * b[col * 4 + 1] +
                    a[8 + row] * b[col * 4 + 2] +
                    a[12 + row] * b[col * 4 + 3]
            }
        }
        return r
    }

    private fun eulerXyzDegToQuat(rxDeg: Float, ryDeg: Float, rzDeg: Float): FloatArray {
        val hx = (rxDeg * (PI.toFloat() / 180f)) * 0.5f
        val hy = (ryDeg * (PI.toFloat() / 180f)) * 0.5f
        val hz = (rzDeg * (PI.toFloat() / 180f)) * 0.5f
        val cx = cos(hx); val sx = sin(hx)
        val cy = cos(hy); val sy = sin(hy)
        val cz = cos(hz); val sz = sin(hz)
        val qx = sx * cy * cz + cx * sy * sz
        val qy = cx * sy * cz - sx * cy * sz
        val qz = cx * cy * sz + sx * sy * cz
        val qw = cx * cy * cz - sx * sy * sz
        val len = sqrt(qx * qx + qy * qy + qz * qz + qw * qw).coerceAtLeast(1e-6f)
        return floatArrayOf(qx / len, qy / len, qz / len, qw / len)
    }

    private fun concatQuats(vararg triples: Float): FloatArray {
        val out = FloatArray((triples.size / 3) * 4)
        var i = 0
        var o = 0
        while (i + 2 < triples.size) {
            val q = eulerXyzDegToQuat(triples[i], triples[i + 1], triples[i + 2])
            out[o++] = q[0]; out[o++] = q[1]; out[o++] = q[2]; out[o++] = q[3]
            i += 3
        }
        return out
    }

    private fun fmt(v: Float): String = String.format(Locale.US, "%.6f", if (kotlin.math.abs(v) < 1e-7f) 0f else v)
}
