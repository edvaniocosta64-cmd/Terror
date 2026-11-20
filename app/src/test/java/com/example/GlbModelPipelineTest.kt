package com.example

import com.example.engine3d.CharacterCpuMeshLibrary
import com.example.engine3d.GlbBinaryExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GlbModelPipelineTest {

    @Test
    fun `binary GLB exporter produces valid glTF 2_0 skeletal PBR assets on disk`() {
        val modelsDir = File("src/main/assets/models")
        if (System.getProperty("regenGlb") == "true" || !File(modelsDir, "granny.glb").exists()) {
            GlbBinaryExporter.writeModelAssetsToDir(modelsDir, textureSizePx = 128)
        }

        val grannyFile = File(modelsDir, "granny.glb")
        val protagonistFile = File(modelsDir, "protagonist.glb")

        assertTrue("granny.glb must exist on disk", grannyFile.exists() && grannyFile.length() > 50_000L)
        assertTrue("protagonist.glb must exist on disk", protagonistFile.exists() && protagonistFile.length() > 40_000L)

        validateGlbStructure(
            bytes = grannyFile.readBytes(),
            expectedAnimations = listOf("Idle", "Walk", "Hunt"),
            expectedMaterials = listOf("GrannySkin", "GrannyDress", "GrannyHair", "GrannyEyeball", "GrannyCrimsonIris", "GrannyBoots"),
        )
        validateGlbStructure(
            bytes = protagonistFile.readBytes(),
            expectedAnimations = listOf("Idle", "Walk"),
            expectedMaterials = listOf("PlayerSkin", "PlayerJacket", "PlayerHair", "PlayerEyeball", "PlayerBoots"),
        )
    }

    @Test
    fun `exporter byte arrays match sculpted mesh library vertex counts`() {
        val cpu = CharacterCpuMeshLibrary()
        assertTrue(cpu.sculptedHeadGranny.vertexCount > 2000)
        val grannyBytes = GlbBinaryExporter.buildGrannyGlb(cpu, textureSizePx = 64)
        val protagonistBytes = GlbBinaryExporter.buildProtagonistGlb(cpu, textureSizePx = 64)
        validateGlbStructure(
            bytes = grannyBytes,
            expectedAnimations = listOf("Idle", "Walk", "Hunt"),
            expectedMaterials = listOf("GrannySkin", "GrannyDress", "GrannyHair"),
        )
        validateGlbStructure(
            bytes = protagonistBytes,
            expectedAnimations = listOf("Idle", "Walk"),
            expectedMaterials = listOf("PlayerSkin", "PlayerJacket"),
        )
    }

    private fun validateGlbStructure(
        bytes: ByteArray,
        expectedAnimations: List<String>,
        expectedMaterials: List<String>,
    ) {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0x46546C67, bb.getInt()) // "glTF"
        assertEquals(2, bb.getInt())          // version 2
        assertEquals(bytes.size, bb.getInt())

        val jsonLen = bb.getInt()
        assertEquals(0x4E4F534A, bb.getInt()) // "JSON"
        val jsonBytes = ByteArray(jsonLen)
        bb.get(jsonBytes)
        val jsonStr = String(jsonBytes, Charsets.UTF_8)

        val binLen = bb.getInt()
        assertEquals(0x004E4942, bb.getInt()) // "BIN\0"
        assertTrue("BIN chunk must contain non-trivial geometry & textures", binLen > 30_000)
        assertEquals(bytes.size, 12 + 8 + jsonLen + 8 + binLen)

        assertTrue(jsonStr.contains(""""version":"2.0""""))
        assertTrue(jsonStr.contains(""""pbrMetallicRoughness""""))
        assertTrue(jsonStr.contains(""""skins""""))
        assertTrue(jsonStr.contains(""""inverseBindMatrices""""))
        assertTrue(jsonStr.contains(""""JOINTS_0""""))
        assertTrue(jsonStr.contains(""""WEIGHTS_0""""))
        assertTrue(jsonStr.contains(""""mimeType":"image/png""""))

        for (anim in expectedAnimations) {
            assertTrue("Missing animation clip $anim", jsonStr.contains(""""name":"$anim""""))
        }
        for (mat in expectedMaterials) {
            assertTrue("Missing PBR material $mat", jsonStr.contains(""""name":"$mat""""))
        }
    }
}
