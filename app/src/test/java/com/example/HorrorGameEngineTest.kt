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

    @Test
    fun `60Hz device never forces 120 FPS even when requested`() {
        val perf = AutoQualityController(deviceMaxHz = 60)
        perf.configurePreference(TargetFpsMode.FPS_120, QualityPreference.HIGH)
        assertEquals(60, perf.effectiveTargetFps())
        assertEquals(QualityTier.HIGH, perf.activeTier)

        perf.configurePreference(TargetFpsMode.AUTO, QualityPreference.LOW)
        assertEquals(60, perf.effectiveTargetFps())
        assertEquals(QualityTier.LOW, perf.activeTier)
        assertFalse(QualityTier.LOW.enableShadows)
        assertEquals(0, QualityTier.LOW.volumetricFogLayers)
    }

    @Test
    fun `player rig and granny skeletal animation states update naturally on movement and interaction`() {
        val engine = HorrorGameEngine(HorrorAudioEngine())
        engine.resetNewStory()

        // Cinematic intro progresses both player walk cycle and Granny porch entrance
        repeat(40) { engine.step(0.05f) }
        assertTrue("Player walk cycle should advance during intro", engine.playerWalkCycle > 0f)
        assertTrue("Player breathing should animate", engine.playerBreathPhase > 0f)
        assertTrue("Granny breathing should animate", engine.granny.breathPhase > 0f)

        engine.skipCinematicIntro()
        // Move player near porch & step to trigger dialogue + head tracking
        engine.playerX = 1.4f
        engine.playerZ = 13.0f
        engine.moveInputY = 1.0f
        repeat(15) { engine.step(0.04f) }
        assertTrue("Player speed norm should rise when moving", engine.playerMoveSpeedNorm > 0.4f)
        assertTrue("Granny head should track player offset", engine.granny.headLookYawDeg != 0f || engine.granny.yawDeg != 0f)

        // Enter house to finish porch greeting -> INSIDE_MYSTERY
        engine.moveInputY = 0f
        engine.playerX = 0.0f
        engine.playerZ = 8.4f
        engine.step(0.016f)

        // Pick up lantern inside living room -> triggers interactAnimTimer (arm reach!)
        engine.playerX = -6.0f
        engine.playerZ = 7.4f
        engine.playerYawRad = 0.0f // facing +Z toward lantern at (-6.2, 8.4)
        engine.interactWithTarget()
        assertTrue("Interacting should trigger physical arm reach animation", engine.interactAnimTimer > 0.8f)
        assertTrue(engine.hasFlashlight)

        // Crouching lowers eye height while keeping body grounded
        engine.toggleCrouch()
        repeat(15) { engine.step(0.05f) }
        assertTrue(engine.playerY < 1.2f)
    }

    @Test
    fun `procedural 3D scene and sculpted human meshes generate valid indexed geometry`() {
        val batches = com.example.engine3d.SceneBuilder3D.buildStaticCpuBatches()
        assertTrue("Scene should produce multiple textured batches", batches.size >= 10)
        for (batch in batches) {
            val md = batch.meshData
            assertTrue("Mesh ${batch.texture} must have vertices", md.vertexCount > 0)
            assertTrue("Mesh ${batch.texture} must have triangles", md.indexCount >= 3)
            for (idx in md.indices) {
                val uIdx = idx.toInt() and 0xFFFF
                assertTrue("Index out of bounds in ${batch.texture}: $uIdx >= ${md.vertexCount}", uIdx < md.vertexCount)
            }
            for (f in md.vertices) {
                assertFalse("Vertex float must not be NaN in ${batch.texture}", f.isNaN())
            }
        }

        // Verify sculpted head, torso, skirt & tapered limb builders produce smooth high-poly anatomy
        val head = com.example.engine3d.MeshBuilder3D().apply { addSculptedHead() }.build()
        val torso = com.example.engine3d.MeshBuilder3D().apply { addSculptedTorso() }.build()
        val skirt = com.example.engine3d.MeshBuilder3D().apply { addFlaredSkirt() }.build()
        assertTrue("Sculpted head should have >300 vertices", head.vertexCount > 300)
        assertTrue("Sculpted torso should have >80 vertices", torso.vertexCount > 80)
        assertTrue("Tattered skirt should have >150 vertices", skirt.vertexCount > 150)
    }

    @Test
    fun `part 2 capture sequence progresses through ambush collapse blackout dragging and aftermath`() {
        val engine = HorrorGameEngine(HorrorAudioEngine())
        engine.resetNewStory()
        engine.skipCinematicIntro()

        // Approach porch & step inside the house
        engine.playerZ = 13.2f
        engine.step(0.016f)
        engine.playerZ = 8.4f
        engine.step(0.016f)
        assertEquals(com.example.engine3d.CaptureStage.NONE, engine.captureState.stage)

        // Advance past the 1.2s ambush delay -> AMBUSH_TURN triggers
        repeat(25) { engine.step(0.05f) } // 1.25s -> enters AMBUSH_TURN (stageTimer ~0.05s)
        assertEquals(com.example.engine3d.CaptureStage.AMBUSH_TURN, engine.captureState.stage)
        assertTrue(engine.hudSnapshot().isCaptureCutscene)

        // Advance past AMBUSH_TURN (2.2s) into mid-COLLAPSE_FALL (~1.2s in, t=0.6)
        repeat(84) { engine.step(0.04f) } // +3.36s
        assertEquals(com.example.engine3d.CaptureStage.COLLAPSE_FALL, engine.captureState.stage)
        assertTrue("Camera should roll during collapse (${engine.captureState.cameraRollRad})", engine.captureState.cameraRollRad > 0.1f)
        assertTrue("Camera should fall toward floor (${engine.captureState.camPosY})", engine.captureState.camPosY < 1.3f)

        // Advance into BLACKOUT_UNCONSCIOUS
        repeat(33) { engine.step(0.04f) } // +1.32s
        assertEquals(com.example.engine3d.CaptureStage.BLACKOUT_UNCONSCIOUS, engine.captureState.stage)
        assertEquals(1.0f, engine.captureState.blackoutAlpha, 0.01f)
        assertTrue(engine.captureState.useThirdPersonCamera)

        // Advance into GRANNY_LOOMING
        repeat(45) { engine.step(0.04f) } // +1.80s
        assertEquals(com.example.engine3d.CaptureStage.GRANNY_LOOMING, engine.captureState.stage)
        assertTrue(engine.granny.isLoomingCapture)

        // Advance into DRAGGING_INSIDE (~1.7s into drag)
        repeat(100) { engine.step(0.04f) } // +4.00s
        assertEquals(com.example.engine3d.CaptureStage.DRAGGING_INSIDE, engine.captureState.stage)
        assertTrue(engine.granny.isDraggingCapture)
        assertTrue(engine.captureState.isDraggingBody)
        val midDragBodyZ = engine.captureState.bodyZ
        assertTrue("Body should be dragged deeper inside house (< 7.5f), was $midDragBodyZ", midDragBodyZ < 7.5f)
        assertEquals(midDragBodyZ - 1.15f, engine.granny.z, 0.05f)

        // Continue dragging -> body moves substantially further north
        repeat(100) { engine.step(0.04f) } // +4.00s
        assertEquals(com.example.engine3d.CaptureStage.DRAGGING_INSIDE, engine.captureState.stage)
        assertTrue("Body should continue moving north toward -3.8f", engine.captureState.bodyZ < midDragBodyZ - 2.0f)

        // Advance into DARK_AFTERMATH
        repeat(88) { engine.step(0.04f) } // +3.52s
        assertEquals(com.example.engine3d.CaptureStage.DARK_AFTERMATH, engine.captureState.stage)
        assertFalse(engine.granny.isDraggingCapture)
        assertFalse(engine.captureState.isDraggingBody)

        // Finish aftermath -> transitions smoothly into Part 3 Awakening sequence in the bedroom
        repeat(100) { engine.step(0.04f) } // +4.00s
        assertEquals(com.example.engine3d.CaptureStage.NONE, engine.captureState.stage)
        assertFalse(engine.hudSnapshot().isCaptureCutscene)
        assertEquals(StoryPhase.INSIDE_MYSTERY, engine.storyPhase)
        assertEquals(com.example.engine3d.AwakeningStage.WAKE_LYING, engine.awakeningState.stage)
        assertTrue(engine.hudSnapshot().isAwakeningCutscene)
    }

    @Test
    fun `skipCaptureCutscene enters awakening and skipAwakeningCutscene lands tucked safely under bed`() {
        val engine = HorrorGameEngine(HorrorAudioEngine())
        engine.resetNewStory()
        engine.skipCinematicIntro()
        engine.playerZ = 13.2f
        engine.step(0.016f)
        engine.playerZ = 8.4f
        repeat(30) { engine.step(0.05f) }

        assertEquals(com.example.engine3d.CaptureStage.AMBUSH_TURN, engine.captureState.stage)
        engine.skipCaptureCutscene()
        assertEquals(com.example.engine3d.CaptureStage.NONE, engine.captureState.stage)
        assertFalse(engine.hudSnapshot().isCaptureCutscene)
        assertEquals(com.example.engine3d.AwakeningStage.WAKE_LYING, engine.awakeningState.stage)

        engine.skipAwakeningCutscene()
        assertEquals(com.example.engine3d.AwakeningStage.NONE, engine.awakeningState.stage)
        assertFalse(engine.hudSnapshot().isAwakeningCutscene)
        assertTrue(engine.isHiding)
        assertTrue(engine.isHidingUnderBed)
        assertEquals(8.95f, engine.playerX, 0.05f)
        assertEquals(-5.85f, engine.playerZ, 0.05f)
        assertEquals(GrannyAiState.PATROLLING_STRANGE, engine.granny.aiState)
    }

    @Test
    fun `part 3 awakening plays bedroom dialogues, spatial Granny voice, under-bed hide, Granny search, and staircase climb`() {
        val engine = HorrorGameEngine(HorrorAudioEngine())
        engine.resetNewStory()
        engine.skipCinematicIntro()
        engine.playerZ = 13.2f
        engine.step(0.016f)
        engine.playerZ = 8.4f
        repeat(30) { engine.step(0.05f) }
        engine.skipCaptureCutscene()

        // 1. WAKE_LYING -> protagonist opens eyes slowly and speaks Line 1
        assertEquals(com.example.engine3d.AwakeningStage.WAKE_LYING, engine.awakeningState.stage)
        repeat(40) { engine.step(0.04f) } // 1.6s into wake
        assertTrue(engine.awakeningState.eyelidOverlayOpen > 0.25f)
        assertEquals("Ugh... Onde eu estou?", engine.hudSnapshot().activeDialogue?.text)

        // 2. SIT_UP_AND_LOOK -> protagonist sits up on bed and speaks Line 2
        repeat(105) { engine.step(0.04f) } // +4.2s -> ~2.0s into SIT_UP_AND_LOOK
        assertEquals(com.example.engine3d.AwakeningStage.SIT_UP_AND_LOOK, engine.awakeningState.stage)
        assertTrue("Torso should sit upright (> -25 deg)", engine.awakeningState.bodyLiePitchDeg > -25f)
        assertEquals("Que lugar é esse?", engine.hudSnapshot().activeDialogue?.text)

        // 3. HEAR_GRANNY_VOICE -> hears both eerie reverberated Granny lines from hallway
        repeat(65) { engine.step(0.04f) } // +2.6s -> inside HEAR_GRANNY_VOICE line 1
        assertEquals(com.example.engine3d.AwakeningStage.HEAR_GRANNY_VOICE, engine.awakeningState.stage)
        assertEquals("Meu querido... Você já acordou?", engine.hudSnapshot().activeDialogue?.text)

        repeat(75) { engine.step(0.04f) } // +3.0s -> inside HEAR_GRANNY_VOICE line 2
        assertEquals(com.example.engine3d.AwakeningStage.HEAR_GRANNY_VOICE, engine.awakeningState.stage)
        assertEquals(
            "Não adianta se esconder... Eu sei que você está aqui.",
            engine.hudSnapshot().activeDialogue?.text,
        )

        // 4. RUN_AND_HIDE_UNDER_BED -> runs off bed & crawls beneath it
        repeat(105) { engine.step(0.04f) } // +4.2s -> 15.6s (1.2s into RUN_AND_HIDE_UNDER_BED)
        assertEquals(com.example.engine3d.AwakeningStage.RUN_AND_HIDE_UNDER_BED, engine.awakeningState.stage)
        repeat(35) { engine.step(0.04f) } // +1.4s -> 17.0s (2.6s into stage, actively crawling under bed!)
        assertEquals(com.example.engine3d.AwakeningStage.RUN_AND_HIDE_UNDER_BED, engine.awakeningState.stage)
        assertTrue(engine.awakeningState.isCrawling)

        // 5. GRANNY_SEARCH_ROOM -> doorknob turns, door opens slowly, Granny enters bedroom & searches near bed
        repeat(45) { engine.step(0.04f) } // +1.8s -> 18.8s (0.8s into GRANNY_SEARCH_ROOM)
        assertEquals(com.example.engine3d.AwakeningStage.GRANNY_SEARCH_ROOM, engine.awakeningState.stage)
        assertTrue(engine.awakeningState.allowPeekLook)

        // Test under-bed camera peek with TouchLook delta
        val yawBeforePeek = engine.playerYawRad
        engine.addLookDelta(dx = 45f, dy = -15f, sensitivity = 1.0f, invertY = false)
        assertTrue(engine.playerYawRad != yawBeforePeek)

        // Advance to ~2.8s into search -> doorknob rattling & turning!
        var sawKnobTurn = false
        repeat(50) {
            engine.step(0.04f)
            if (kotlin.math.abs(engine.doorKnobTurnDeg) > 1.0f) sawKnobTurn = true
        }
        assertTrue("Doorknob should turn while Granny checks handle", sawKnobTurn)

        // Advance to ~6.6s into search -> door open & Granny inside bedroom near bed!
        repeat(95) { engine.step(0.04f) }
        val bedDoor = engine.doors.first { it.def.id == "door_granny_room" }
        assertTrue("Bedroom door should be open during Granny search", bedDoor.isOpen)
        assertTrue("Granny should walk inside the bedroom (> 4.0f)", engine.granny.x > 4.0f)
        assertTrue(engine.granny.isSearchingRoom)

        // Finish Granny search -> playable under bed
        repeat(125) { engine.step(0.04f) }
        assertEquals(com.example.engine3d.AwakeningStage.NONE, engine.awakeningState.stage)
        assertTrue(engine.isHiding)
        assertTrue(engine.isHidingUnderBed)

        // Player exits under bed cleanly into crouch
        engine.interactWithTarget()
        assertFalse(engine.isHiding)
        assertFalse(engine.isHidingUnderBed)
        assertTrue(engine.isCrouching)
        assertEquals(-4.05f, engine.playerZ, 0.05f)

        // Verify re-hiding under the bed works via interactWithTarget()
        engine.playerYawRad = Math.PI.toFloat() // looking -Z toward bed at (8.4, -4.9)
        assertEquals("bed_awakening_hide", engine.findBestInteraction()?.targetId)
        engine.interactWithTarget()
        assertTrue(engine.isHiding)
        assertTrue(engine.isHidingUnderBed)

        // Exit again and walk up the North-East wooden staircase -> playerY climbs > 3.2f!
        engine.toggleCrouch()
        engine.toggleCrouch() // stand up
        engine.playerX = 9.2f
        engine.playerZ = -14.2f
        repeat(20) { engine.step(0.05f) }
        assertTrue("Player standing at top of wooden staircase should elevate > 3.3f, was ${engine.playerY}", engine.playerY > 3.3f)
    }

    @Test
    fun `audio engine synthesizes audible stereo horror music in menu and spatial voices`() {
        val audio = HorrorAudioEngine()
        audio.updateVolumes(master = 0.85f, ambient = 0.80f, sfx = 0.90f)
        audio.setMenuMode(true)
        audio.setPaused(false)

        val menuBuf = ShortArray(2048)
        audio.mixStereoFrame(menuBuf)
        var maxMenuPeak = 0
        for (sample in menuBuf) {
            val absVal = kotlin.math.abs(sample.toInt())
            if (absVal > maxMenuPeak) maxMenuPeak = absVal
        }
        assertTrue("Menu horror music should produce audible PCM (> 500), was $maxMenuPeak", maxMenuPeak > 500)

        audio.setMenuMode(false)
        audio.updateListener(8.95f, -5.85f, 0f)
        audio.updateEnvironment(insideHouse = true, dangerLevel = 0.8f, suspenseLevel = 0.9f)
        audio.playProtagonistVoice(0)
        audio.playGrannyVoice(0, 1.2f, -1.2f)
        audio.playFootstep(stealth = false)

        val sfxBuf = ShortArray(2048)
        audio.mixStereoFrame(sfxBuf)
        var leftPeak = 0
        var rightPeak = 0
        for (i in 0 until sfxBuf.size / 2) {
            leftPeak = maxOf(leftPeak, kotlin.math.abs(sfxBuf[i * 2].toInt()))
            rightPeak = maxOf(rightPeak, kotlin.math.abs(sfxBuf[i * 2 + 1].toInt()))
        }
        assertTrue("Left channel SFX/music mix should be audible (> 500), was $leftPeak", leftPeak > 500)
        assertTrue("Right channel SFX/music mix should be audible (> 500), was $rightPeak", rightPeak > 500)
    }

    @Test
    fun `decoupled CPU mesh libraries and rig animators run cleanly without OpenGL context`() {
        val propsCpu = com.example.engine3d.PropCpuMeshLibrary()
        val propEntries = propsCpu.allEntries()
        assertEquals(com.example.engine3d.PropMeshKind.entries.size, propEntries.size)
        for ((kind, mesh) in propEntries) {
            assertTrue("Prop $kind must have vertices", mesh.vertexCount > 0)
            assertTrue("Prop $kind must have indices", mesh.indexCount >= 3)
            val bounds = mesh.computeBounds()
            assertTrue("Bounds halfExtentX must be positive for $kind", bounds.halfExtentX > 0f)
            assertTrue("Bounds halfExtentY must be positive for $kind", bounds.halfExtentY > 0f)
            assertTrue("Bounds halfExtentZ must be positive for $kind", bounds.halfExtentZ > 0f)
            val quats = mesh.computeTangentQuaternions()
            assertEquals(mesh.vertexCount * 4, quats.size)
            for (v in 0 until mesh.vertexCount) {
                val qx = quats[v * 4]
                val qy = quats[v * 4 + 1]
                val qz = quats[v * 4 + 2]
                val qw = quats[v * 4 + 3]
                val norm = kotlin.math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
                assertFalse("Quaternion norm must not be NaN", norm.isNaN())
                assertEquals("Tangent quaternion must be unit length", 1.0f, norm, 0.01f)
            }
        }

        val charsCpu = com.example.engine3d.CharacterCpuMeshLibrary()
        val charEntries = charsCpu.allEntries()
        assertEquals(com.example.engine3d.CharacterMeshKind.entries.size, charEntries.size)
        for ((kind, mesh) in charEntries) {
            assertTrue("Character part $kind must have vertices", mesh.vertexCount > 0)
            assertTrue("Character part $kind must have indices", mesh.indexCount >= 3)
        }

        val engine = HorrorGameEngine(HorrorAudioEngine())
        engine.resetNewStory()
        var grannyDrawCalls = 0
        com.example.engine3d.GrannyRigAnimator.drawGrannyGeneric(
            g = engine.granny,
            meshes = charsCpu,
            elapsedSec = 1.25f,
        ) { mat, mesh, _, _, _, _, _, _, _ ->
            assertEquals(16, mat.size)
            assertTrue(mesh.vertexCount > 0)
            grannyDrawCalls++
        }
        assertTrue("Granny rig should emit > 25 submesh draws, was $grannyDrawCalls", grannyDrawCalls > 25)

        var playerDrawCalls = 0
        com.example.engine3d.PlayerRigAnimator.drawFullProtagonist3DGeneric(
            bodyX = 8.4f,
            bodyY = 0.72f,
            bodyZ = -4.9f,
            bodyYawDeg = 0f,
            bodyLiePitchDeg = -88f,
            dragPhase = 0f,
            isBeingDragged = false,
            breathPhase = 0.5f,
            meshes = charsCpu,
            drawer = { mat, mesh, _, _, _, _, _, _, _ ->
                assertEquals(16, mat.size)
                assertTrue(mesh.vertexCount > 0)
                playerDrawCalls++
            },
            eyeOpenAmount = 0.8f,
            jawOpenAmount = 0.3f,
        )
        assertTrue("Full protagonist rig should emit > 25 submesh draws, was $playerDrawCalls", playerDrawCalls > 25)
    }
}
