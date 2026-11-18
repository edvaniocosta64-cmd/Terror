package com.example

import com.example.audio.HorrorAudioEngine
import com.example.data.QualityPreference
import com.example.data.StoryPhase
import com.example.data.TargetFpsMode
import com.example.engine3d.AutoQualityController
import com.example.engine3d.GrannyAiState
import com.example.engine3d.HorrorGameEngine
import com.example.engine3d.QualityTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HorrorGameEngineTest {

    @Test
    fun `opening story sequence triggers exact PT-BR porch dialogues and slams front door on entry`() {
        val engine = HorrorGameEngine(HorrorAudioEngine())
        engine.resetNewStory()

        assertTrue(engine.isCinematicIntro)
        assertEquals(StoryPhase.INTRO_ROAD, engine.storyPhase)

        // Skip cinematic intro -> player placed on dark road facing the house
        engine.skipCinematicIntro()
        assertFalse(engine.isCinematicIntro)
        assertEquals(18.0f, engine.playerZ, 0.01f)

        // Walk forward up to the porch (Z <= 13.6)
        engine.playerZ = 13.2f
        engine.step(0.016f)

        assertEquals(StoryPhase.PORCH_DIALOGUE, engine.storyPhase)
        assertEquals(GrannyAiState.GREETING_DIALOGUE, engine.granny.aiState)

        val line1 = engine.hudSnapshot().activeDialogue
        assertNotNull(line1)
        assertEquals("Vovó", line1?.speaker)
        assertEquals("Olá, meu jovem... Você está perdido?", line1?.text)

        engine.advanceDialogue()
        val line2 = engine.hudSnapshot().activeDialogue
        assertEquals("Jovem", line2?.speaker)
        assertEquals("Estou... Não sei onde estou. Pode me ajudar?", line2?.text)

        engine.advanceDialogue()
        val line3 = engine.hudSnapshot().activeDialogue
        assertEquals("Vovó", line3?.speaker)
        assertEquals("Claro, querido. Entre. Está muito frio lá fora.", line3?.text)

        // Walk inside the house (Z <= 8.85) -> front door slams & locks, mystery begins!
        engine.playerZ = 8.4f
        engine.step(0.016f)

        assertEquals(StoryPhase.INSIDE_MYSTERY, engine.storyPhase)
        val frontDoor = engine.doors.first { it.def.id == "front_door" }
        assertFalse(frontDoor.isOpen)
        assertFalse(frontDoor.isUnlocked)
        assertEquals(GrannyAiState.CLOSING_DOOR_MYSTERY, engine.granny.aiState)
    }

    @Test
    fun `wall collision blocks player from walking through exterior house walls`() {
        val engine = HorrorGameEngine(HorrorAudioEngine())
        engine.resetNewStory()
        engine.skipCinematicIntro()

        // Stand outside solid wall (X = -5.0, Z = 10.6) and try pushing into -Z wall at Z=10
        engine.playerX = -5.0f
        engine.playerZ = 10.65f
        engine.playerYawRad = Math.PI.toFloat() // facing -Z
        engine.moveInputY = 1.0f

        repeat(40) { engine.step(0.03f) }
        assertTrue("Player should be stopped by wall at Z ~10.2, was ${engine.playerZ}", engine.playerZ >= 10.2f)
    }

    @Test
    fun `full escape sequence unlocks front door and completes chapter`() {
        val engine = HorrorGameEngine(HorrorAudioEngine())
        engine.resetNewStory()
        engine.skipCinematicIntro()

        // Trigger porch & entry
        engine.playerZ = 13.2f
        engine.step(0.016f)
        engine.playerZ = 8.4f
        engine.step(0.016f)

        // Give player escape items & insert fuse at fuse_box
        engine.inventoryItems.addAll(listOf("wire_cutters", "fuse", "master_key"))

        // Interact with fuse_box at (2.2, 9.75)
        engine.playerX = 2.0f
        engine.playerZ = 8.8f
        engine.playerYawRad = 0.2f // looking toward +Z/+X
        engine.interactWithTarget()
        val front = engine.doors.first { it.def.id == "front_door" }
        assertTrue(front.frontFuseInserted)

        // Aim at front door (0, 10.0) and interact to cut wire + unlock padlock
        engine.playerX = 0.0f
        engine.playerZ = 8.7f
        engine.playerYawRad = 0f // looking +Z
        engine.interactWithTarget()

        assertTrue(front.frontWireCut)
        assertTrue(front.frontPadlockOpened)
        assertTrue(front.isUnlocked)
        assertTrue(front.isOpen)

        // Save & restore round-trip preserves escape lock states
        val saved = engine.toGameSave("uid_1", "slot_1", "Slot 1")
        val restored = HorrorGameEngine(HorrorAudioEngine())
        restored.restoreFromSave(saved)
        val restoredFront = restored.doors.first { it.def.id == "front_door" }
        assertTrue(restoredFront.frontWireCut)
        assertTrue(restoredFront.frontFuseInserted)
        assertTrue(restoredFront.frontPadlockOpened)

        // Walk outside past Z > 11.2 -> ESCAPED
        engine.playerZ = 11.5f
        engine.step(0.016f)
        assertTrue(engine.isEscaped)
        assertEquals(StoryPhase.ESCAPED, engine.storyPhase)
    }

    @Test
    fun `auto quality controller supports 120 90 60 30 FPS and dynamically steps down on lag`() {
        val perf = AutoQualityController(deviceMaxHz = 120)
        perf.configurePreference(TargetFpsMode.AUTO, QualityPreference.AUTO)
        assertEquals(120, perf.effectiveTargetFps())
        assertEquals(QualityTier.HIGH, perf.activeTier)

        perf.configurePreference(TargetFpsMode.FPS_90, QualityPreference.AUTO)
        assertEquals(90, perf.effectiveTargetFps())

        perf.configurePreference(TargetFpsMode.FPS_60, QualityPreference.AUTO)
        assertEquals(60, perf.effectiveTargetFps())

        perf.configurePreference(TargetFpsMode.FPS_30, QualityPreference.AUTO)
        assertEquals(30, perf.effectiveTargetFps())

        // Simulate 120 FPS target running at ~45 FPS (22ms per frame) for > 1.5s -> should auto step down
        perf.configurePreference(TargetFpsMode.FPS_120, QualityPreference.AUTO)
        var steppedDown = false
        repeat(100) {
            if (perf.recordFrame(0.022)) steppedDown = true
        }
        assertTrue(steppedDown)
        assertEquals(QualityTier.MEDIUM, perf.activeTier)
    }
}
