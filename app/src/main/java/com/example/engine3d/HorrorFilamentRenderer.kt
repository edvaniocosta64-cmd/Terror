package com.example.engine3d

import android.content.Context
import android.opengl.Matrix
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.Entity
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Google Filament 1.75.1 PBR 3D renderer for Project: Darkness.
 * Replaces fixed-function shading with real PBR metallic-roughness materials, ACES tone mapping,
 * directional moonlight shadow mapping, pooled dynamic point lights, a dynamic first-person flashlight
 * spotlight, indirect spherical-harmonics IBL, atmospheric distance/height fog, and glTF 2.0 (.glb)
 * asset loading via [GltfModelPipeline], while preserving 100% of game logic, skeletal expressions,
 * Moto E40 dynamic quality scaling, and OpenGL ES 2.0 fallback compatibility.
 */
class HorrorFilamentRenderer(
    private val context: Context,
    private val surfaceView: SurfaceView,
    private val gameEngine: HorrorGameEngine,
    private val performanceManager: AutoQualityController,
    private val onTelemetryUpdate: (PerformanceTelemetry) -> Unit,
    private val onQualityScaleChanged: (QualityTier) -> Unit,
    private val onFallbackToOpenGl: (Throwable) -> Unit,
) : Choreographer.FrameCallback {

    private data class StaticBatchEntity(
        @param:Entity val entity: Int,
        val mesh: FilamentMesh,
        val isShadowDecal: Boolean,
        val isTransparent: Boolean,
    )

    private data class DoorEntityPair(
        @param:Entity val panelEntity: Int,
        @param:Entity val knobEntity: Int,
    )

    private data class ItemEntityEntry(
        val def: WorldItemDef,
        @param:Entity val entity: Int,
        val mesh: FilamentMesh,
        val baseTexture: TextureKind,
    )

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var filamentView: View? = null
    private var camera: Camera? = null
    private var swapChain: SwapChain? = null
    private var colorGrading: ColorGrading? = null
    private var indirectLight: IndirectLight? = null

    private var uiHelper: UiHelper? = null
    private var pbrLibrary: FilamentPbrLibrary? = null
    private var charMeshes: CharacterFilamentLibrary? = null
    private var propMeshes: PropFilamentLibrary? = null
    private var gltfPipeline: GltfModelPipeline? = null
    private var grannyGlbModel: LoadedGltfModel? = null
    private var protagonistGlbModel: LoadedGltfModel? = null

    // Lighting Entities
    @Entity private var moonLightEntity: Int = 0
    @Entity private var flashlightEntity: Int = 0
    private val pointLightEntities = IntArray(6)

    // Scene Entities
    private val staticEntities = mutableListOf<StaticBatchEntity>()
    private val doorEntities = mutableListOf<DoorEntityPair>()
    private val itemEntities = mutableListOf<ItemEntityEntry>()
    private val dynamicPoolEntities = IntArray(192)
    private var dynamicPoolCursor = 0
    private var poolInitialized = false
    private var fallbackTriggered = false

    // Scratch arrays (zero per-frame heap allocations)
    private val identityMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val tmpMatrix = FloatArray(16)
    private val fogOptions = View.FogOptions()
    private val clearOptions = Renderer.ClearOptions()

    private var viewportWidth = 1280
    private var viewportHeight = 720
    private var aspectRatio = 16f / 9f
    private var lastFrameNano = 0L
    private var elapsedSec = 0f
    private var telemetryEmitCounter = 0
    private var lastAppliedTier: QualityTier? = null

    private var isRunning = false
    private var isDestroyed = false
    private var initialized = false

    private val rigPoolDrawer = RigDrawCallback<FilamentMesh> { mat, mesh, tex, r, g, b, spec, emit, doubleSided ->
        emitPooledRenderable(
            mat = mat,
            mesh = mesh,
            tex = tex,
            tintR = r,
            tintG = g,
            tintB = b,
            specular = spec,
            emissive = emit,
            alphaScale = 1f,
            doubleSided = doubleSided,
            unlit = false,
            castShadows = performanceManager.activeTier.enableShadows,
            receiveShadows = true,
        )
    }

    fun initialize(): Boolean {
        return try {
            Filament.init()
            val eng = Engine.create()
            engine = eng
            renderer = eng.createRenderer()
            scene = eng.createScene()
            filamentView = eng.createView()
            val camEntity = EntityManager.get().create()
            camera = eng.createCamera(camEntity).apply {
                setExposure(1.0f, 1f / 4f, 400f)
            }

            val grading = ColorGrading.Builder()
                .toneMapper(ToneMapper.ACES())
                .contrast(1.06f)
                .saturation(0.96f)
                .build(eng)
            colorGrading = grading

            val view = filamentView!!
            view.scene = scene
            view.camera = camera
            view.colorGrading = grading
            view.dithering = View.Dithering.TEMPORAL
            view.setShadowType(View.ShadowType.PCF)
            applyQualityOptionsToView(view, performanceManager.activeTier)

            clearOptions.clear = true
            clearOptions.discard = false
            clearOptions.clearColor = doubleArrayOf(0.024, 0.028, 0.044, 1.0)
            renderer?.clearOptions = clearOptions

            val texRes = performanceManager.activeTier.textureSizePx
            pbrLibrary = FilamentPbrLibrary(eng, textureResolutionPx = texRes)
            charMeshes = CharacterFilamentLibrary(eng)
            propMeshes = PropFilamentLibrary(eng)

            // Load and verify real GLB / glTF 2.0 character assets
            gltfPipeline = GltfModelPipeline(context, eng).also { pipeline ->
                grannyGlbModel = pipeline.loadModelFromAssets("models/granny.glb")
                protagonistGlbModel = pipeline.loadModelFromAssets("models/protagonist.glb")
            }

            setupLightingAndIbl(eng, scene!!, performanceManager.activeTier)
            buildStaticWorldEntities(eng, scene!!, pbrLibrary!!)
            buildDynamicDoorAndItemEntities(eng, scene!!, pbrLibrary!!, propMeshes!!)
            allocateDynamicPool(eng, scene!!, propMeshes!!.fogPlaneMesh, pbrLibrary!!)

            val helper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
                isOpaque = true
                renderCallback = object : UiHelper.RendererCallback {
                    override fun onNativeWindowChanged(surface: Surface) {
                        val e = engine ?: return
                        try {
                            swapChain?.let { sc ->
                                e.destroySwapChain(sc)
                                swapChain = null
                            }
                            swapChain = e.createSwapChain(surface)
                        } catch (t: Throwable) {
                            triggerFallback(t)
                        }
                    }

                    override fun onDetachedFromSurface() {
                        val e = engine ?: return
                        swapChain?.let { sc ->
                            e.destroySwapChain(sc)
                            swapChain = null
                        }
                        e.flushAndWait()
                    }

                    override fun onResized(width: Int, height: Int) {
                        viewportWidth = width.coerceAtLeast(1)
                        viewportHeight = height.coerceAtLeast(1)
                        aspectRatio = viewportWidth.toFloat() / viewportHeight.toFloat()
                        filamentView?.viewport = Viewport(0, 0, viewportWidth, viewportHeight)
                        updateCameraProjection(performanceManager.activeTier.drawDistance)
                    }
                }
                attachTo(surfaceView)
            }
            uiHelper = helper

            lastFrameNano = System.nanoTime()
            initialized = true
            true
        } catch (t: Throwable) {
            triggerFallback(t)
            false
        }
    }

    fun applyRenderScale(baseWidth: Int, baseHeight: Int, tier: QualityTier) {
        if (isDestroyed || baseWidth <= 0 || baseHeight <= 0) return
        val scaledW = (baseWidth * tier.renderScale).toInt().coerceAtLeast(360)
        val scaledH = (baseHeight * tier.renderScale).toInt().coerceAtLeast(240)
        uiHelper?.setDesiredSize(scaledW, scaledH)
    }

    private fun triggerFallback(t: Throwable) {
        if (fallbackTriggered) return
        fallbackTriggered = true
        Log.e("HorrorFilamentRenderer", "Falha no Google Filament; revertendo para OpenGL ES 2.0", t)
        destroy()
        onFallbackToOpenGl(t)
    }

    fun onResume() {
        if (isDestroyed || !initialized) return
        if (!isRunning) {
            isRunning = true
            lastFrameNano = System.nanoTime()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun onPause() {
        isRunning = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    fun destroy() {
        if (isDestroyed) return
        isDestroyed = true
        onPause()

        try {
            uiHelper?.detach()
        } catch (_: Throwable) {
        }
        uiHelper = null

        val eng = engine ?: return
        val sc = scene
        val rm = eng.renderableManager
        val lm = eng.lightManager
        val tm = eng.transformManager
        val em = EntityManager.get()

        fun destroyEntityFull(@Entity e: Int) {
            if (e == 0) return
            sc?.removeEntity(e)
            if (rm.hasComponent(e)) rm.destroy(e)
            if (lm.hasComponent(e)) lm.destroy(e)
            if (tm.hasComponent(e)) tm.destroy(e)
            eng.destroyEntity(e)
            em.destroy(e)
        }

        staticEntities.forEach {
            destroyEntityFull(it.entity)
            it.mesh.destroy(eng)
        }
        staticEntities.clear()

        doorEntities.forEach {
            destroyEntityFull(it.panelEntity)
            destroyEntityFull(it.knobEntity)
        }
        doorEntities.clear()

        itemEntities.forEach { destroyEntityFull(it.entity) }
        itemEntities.clear()

        if (poolInitialized) {
            for (e in dynamicPoolEntities) {
                destroyEntityFull(e)
            }
            poolInitialized = false
        }

        destroyEntityFull(moonLightEntity)
        destroyEntityFull(flashlightEntity)
        for (i in pointLightEntities.indices) {
            destroyEntityFull(pointLightEntities[i])
            pointLightEntities[i] = 0
        }

        gltfPipeline?.destroy()
        gltfPipeline = null
        charMeshes?.destroy(eng)
        charMeshes = null
        propMeshes?.destroy(eng)
        propMeshes = null
        pbrLibrary?.destroy()
        pbrLibrary = null

        indirectLight?.let { eng.destroyIndirectLight(it) }
        indirectLight = null
        colorGrading?.let { eng.destroyColorGrading(it) }
        colorGrading = null
        camera?.let {
            val ce = it.entity
            eng.destroyCameraComponent(ce)
            em.destroy(ce)
        }
        camera = null
        filamentView?.let { eng.destroyView(it) }
        filamentView = null
        sc?.let { eng.destroyScene(it) }
        scene = null
        renderer?.let { eng.destroyRenderer(it) }
        renderer = null
        swapChain?.let { eng.destroySwapChain(it) }
        swapChain = null

        eng.flushAndWait()
        eng.destroy()
        engine = null
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning || isDestroyed) return
        Choreographer.getInstance().postFrameCallback(this)

        val helper = uiHelper ?: return
        val sc = swapChain ?: return
        val rend = renderer ?: return
        val view = filamentView ?: return
        val eng = engine ?: return
        if (!helper.isReadyToRender) return

        val rawDt = (frameTimeNanos - lastFrameNano) / 1_000_000_000.0
        val targetFps = performanceManager.effectiveTargetFps()
        val minIntervalSec = 1.0 / targetFps.coerceAtLeast(30)
        if (lastFrameNano != 0L && rawDt < minIntervalSec * 0.72) {
            return
        }
        val dtSec = rawDt.coerceIn(0.001, 0.1)
        lastFrameNano = frameTimeNanos
        elapsedSec += dtSec.toFloat()

        try {
            gameEngine.step(dtSec.toFloat())

            val tierChanged = performanceManager.recordFrame(dtSec)
            val activeTierNow = performanceManager.activeTier
            if (tierChanged || activeTierNow != lastAppliedTier) {
                lastAppliedTier = activeTierNow
                applyQualityOptionsToView(view, activeTierNow)
                updateCameraProjection(activeTierNow.drawDistance)
                if (tierChanged) {
                    onQualityScaleChanged(activeTierNow)
                }
            }
            telemetryEmitCounter++
            if (telemetryEmitCounter >= 12) {
                telemetryEmitCounter = 0
                onTelemetryUpdate(performanceManager.snapshot())
            }

            updateSceneFrame(eng, view, activeTierNow)

            if (rend.beginFrame(sc, frameTimeNanos)) {
                rend.render(view)
                rend.endFrame()
            }
        } catch (t: Throwable) {
            triggerFallback(t)
        }
    }

    private fun updateSceneFrame(eng: Engine, view: View, tier: QualityTier) {
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
        var upX = 0f
        var upY = 1f
        var upZ = 0f

        if (inCapture) {
            camX = cap.camPosX + shakeX
            camY = cap.camPosY + shakeY
            camZ = cap.camPosZ
            val dx = cap.camLookX - camX
            val dy = cap.camLookY - camY
            val dz = cap.camLookZ - camZ
            val invLen = 1f / sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001f)
            lookX = dx * invLen
            lookY = dy * invLen
            lookZ = dz * invLen
            if (abs(cap.cameraRollRad) > 0.001f) {
                val rx = lookY * 0f - lookZ * 1f
                val ry = lookZ * 0f - lookX * 0f
                val rz = lookX * 1f - lookY * 0f
                val rLen = sqrt(rx * rx + ry * ry + rz * rz).coerceAtLeast(0.001f)
                val cr = cos(cap.cameraRollRad)
                val sr = sin(cap.cameraRollRad)
                upX = (rx / rLen) * sr
                upY = cr
                upZ = (rz / rLen) * sr
            }
        } else if (inAwakening && awake.useThirdPersonCamera) {
            camX = awake.camPosX + shakeX
            camY = awake.camPosY + shakeY
            camZ = awake.camPosZ
            val dx = awake.camLookX - camX
            val dy = awake.camLookY - camY
            val dz = awake.camLookZ - camZ
            val invLen = 1f / sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001f)
            lookX = dx * invLen
            lookY = dy * invLen
            lookZ = dz * invLen
        } else {
            camX = (if (inAwakening) awake.camPosX else gameEngine.playerX) + shakeX
            camY = (if (inAwakening) awake.camPosY else gameEngine.playerY) + shakeY
            camZ = if (inAwakening) awake.camPosZ else gameEngine.playerZ
            val cosPitch = cos(gameEngine.playerPitchRad)
            lookX = sin(gameEngine.playerYawRad) * cosPitch
            lookY = sin(gameEngine.playerPitchRad)
            lookZ = cos(gameEngine.playerYawRad) * cosPitch
        }

        camera?.lookAt(
            camX.toDouble(), camY.toDouble(), camZ.toDouble(),
            (camX + lookX).toDouble(), (camY + lookY).toDouble(), (camZ + lookZ).toDouble(),
            upX.toDouble(), upY.toDouble(), upZ.toDouble(),
        )

        // Atmosphere, IBL, Fog & Clear color
        val lightScale = if (inCapture) cap.interiorLightScale.coerceIn(0.35f, 1.0f) else 1.0f
        indirectLight?.intensity = if (insideHouse) 380f * lightScale else 240f

        val fogR = if (insideHouse) 0.020f else 0.028f
        val fogG = if (insideHouse) 0.017f else 0.036f
        val fogB = if (insideHouse) 0.024f else 0.062f
        clearOptions.clearColor[0] = fogR.toDouble()
        clearOptions.clearColor[1] = fogG.toDouble()
        clearOptions.clearColor[2] = fogB.toDouble()
        clearOptions.clearColor[3] = 1.0
        renderer?.clearOptions = clearOptions

        fogOptions.enabled = true
        fogOptions.distance = 2.2f
        fogOptions.cutOffDistance = tier.drawDistance * 1.25f
        fogOptions.maximumOpacity = 0.86f
        fogOptions.density = when {
            inCapture && cap.useThirdPersonCamera -> 0.065f
            insideHouse -> 0.024f
            else -> 0.032f
        }
        fogOptions.height = 0.7f
        fogOptions.heightFalloff = 0.35f
        fogOptions.color = floatArrayOf(fogR, fogG, fogB)
        fogOptions.inScatteringSize = 0f
        view.fogOptions = fogOptions

        // Dynamic Lights + Flashlight
        updateLights(eng, camX, camY, camZ, lookX, lookY, lookZ, inCapture, inAwakening, lightScale, tier)

        // Visibility toggles for static shadow decals based on QualityTier
        val rm = eng.renderableManager
        for (entry in staticEntities) {
            if (entry.isShadowDecal) {
                val inst = rm.getInstance(entry.entity)
                if (inst != 0) rm.setLayerMask(inst, 0xFF, if (tier.enableShadows) 0x01 else 0x00)
            }
        }

        // Dynamic Doors & Collectibles
        updateDoorsAndItems(eng, tier)

        // Advance GLB animator clip metadata silently in sync with Granny's AI state
        grannyGlbModel?.let { model ->
            val clipName = when {
                gameEngine.granny.huntBlend > 0.55f -> "Hunt"
                gameEngine.granny.currentMoveSpeedNorm > 0.15f -> "Walk"
                else -> "Idle"
            }
            gltfPipeline?.applyClip(model, clipName, elapsedSec)
        }

        // Populate Dynamic Skeletal Characters, Contact Shadows & Fog Layers via Zero-Alloc Pool
        dynamicPoolCursor = 0
        val tm = eng.transformManager
        tm.openLocalTransformTransaction()

        val props = propMeshes
        if (props != null && tier.enableShadows) {
            val grannyFloorY = WorldMap3D.floorHeightAt(gameEngine.granny.x, gameEngine.granny.z)
            Matrix.setIdentityM(tmpMatrix, 0)
            Matrix.translateM(tmpMatrix, 0, gameEngine.granny.x, grannyFloorY + 0.01f, gameEngine.granny.z)
            Matrix.scaleM(tmpMatrix, 0, 1.05f, 1f, 1.05f)
            emitPooledRenderable(tmpMatrix, props.shadowDecalMesh, TextureKind.SOFT_SHADOW, 1f, 1f, 1f, 0f, 0f, 0.85f, doubleSided = true, unlit = true, castShadows = false, receiveShadows = false)

            if (inCapture && cap.useThirdPersonCamera) {
                Matrix.setIdentityM(tmpMatrix, 0)
                Matrix.translateM(tmpMatrix, 0, cap.bodyX, 0.015f, cap.bodyZ + 0.15f)
                Matrix.scaleM(tmpMatrix, 0, 0.85f, 1f, 1.45f)
                emitPooledRenderable(tmpMatrix, props.shadowDecalMesh, TextureKind.SOFT_SHADOW, 1f, 1f, 1f, 0f, 0f, 0.78f, doubleSided = true, unlit = true, castShadows = false, receiveShadows = false)
            } else if (inAwakening && awake.useThirdPersonCamera) {
                Matrix.setIdentityM(tmpMatrix, 0)
                Matrix.translateM(tmpMatrix, 0, awake.bodyX, awake.bodyY - 0.03f, awake.bodyZ)
                Matrix.scaleM(tmpMatrix, 0, 0.85f, 1f, 1.15f)
                emitPooledRenderable(tmpMatrix, props.shadowDecalMesh, TextureKind.SOFT_SHADOW, 1f, 1f, 1f, 0f, 0f, 0.55f, doubleSided = true, unlit = true, castShadows = false, receiveShadows = false)
            } else if (!gameEngine.isCinematicIntro && !gameEngine.isHiding && !inCapture && !inAwakening) {
                val floorY = WorldMap3D.floorHeightAt(gameEngine.playerX, gameEngine.playerZ)
                Matrix.setIdentityM(tmpMatrix, 0)
                Matrix.translateM(tmpMatrix, 0, gameEngine.playerX, floorY + 0.01f, gameEngine.playerZ)
                Matrix.scaleM(tmpMatrix, 0, 0.90f, 1f, 0.90f)
                emitPooledRenderable(tmpMatrix, props.shadowDecalMesh, TextureKind.SOFT_SHADOW, 1f, 1f, 1f, 0f, 0f, 0.70f, doubleSided = true, unlit = true, castShadows = false, receiveShadows = false)
            }
        }

        val chars = charMeshes
        if (chars != null) {
            GrannyRigAnimator.drawGrannyGeneric(gameEngine.granny, chars, elapsedSec, rigPoolDrawer)
            if (inCapture && cap.useThirdPersonCamera) {
                PlayerRigAnimator.drawFullProtagonist3DGeneric(
                    bodyX = cap.bodyX,
                    bodyY = cap.bodyY,
                    bodyZ = cap.bodyZ,
                    bodyYawDeg = cap.bodyYawDeg,
                    bodyLiePitchDeg = cap.bodyLiePitchDeg,
                    dragPhase = cap.dragPullPhase,
                    isBeingDragged = cap.isDraggingBody,
                    breathPhase = gameEngine.playerBreathPhase,
                    meshes = chars,
                    drawer = rigPoolDrawer,
                )
            } else if (inAwakening && awake.useThirdPersonCamera) {
                PlayerRigAnimator.drawFullProtagonist3DGeneric(
                    bodyX = awake.bodyX,
                    bodyY = awake.bodyY,
                    bodyZ = awake.bodyZ,
                    bodyYawDeg = awake.bodyYawDeg,
                    bodyLiePitchDeg = awake.bodyLiePitchDeg,
                    dragPhase = 0f,
                    isBeingDragged = false,
                    breathPhase = gameEngine.playerBreathPhase,
                    meshes = chars,
                    drawer = rigPoolDrawer,
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
                PlayerRigAnimator.drawPlayerLowerBodyGeneric(gameEngine, chars, rigPoolDrawer)
                PlayerRigAnimator.drawFirstPersonArmsGeneric(gameEngine, chars, camX, camY, camZ, rigPoolDrawer, armTuckFrac = armTuckFrac)
            }
        }

        if (props != null && tier.volumetricFogLayers > 0) {
            emitVolumetricMistLayers(props, camX, camY, camZ, tier)
        }

        // Hide unused pool slots
        for (i in dynamicPoolCursor until dynamicPoolEntities.size) {
            val inst = rm.getInstance(dynamicPoolEntities[i])
            if (inst != 0) rm.setLayerMask(inst, 0xFF, 0x00)
        }
        tm.commitLocalTransformTransaction()
    }

    private fun setupLightingAndIbl(eng: Engine, scene: Scene, tier: QualityTier) {
        val irr = FloatArray(27).apply {
            // 3-band spherical harmonics ambient irradiance (balanced atmospheric bounce)
            this[0] = 1.15f; this[1] = 1.08f; this[2] = 1.12f
            this[3] = 0.14f; this[4] = 0.16f; this[5] = 0.22f
        }
        indirectLight = IndirectLight.Builder()
            .irradiance(3, irr)
            .intensity(320f)
            .build(eng)
        scene.indirectLight = indirectLight

        val shadowOpts = LightManager.ShadowOptions().apply {
            mapSize = if (tier == QualityTier.ULTRA || tier == QualityTier.HIGH) 1024 else 512
            shadowCascades = 1
            shadowFar = 28f
            constantBias = 0.002f
            normalBias = 0.01f
        }

        moonLightEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(0.68f, 0.78f, 1.0f)
            .intensity(3600f)
            .direction(-0.32f, -0.85f, -0.42f)
            .castShadows(tier.enableShadows)
            .shadowOptions(shadowOpts)
            .build(eng, moonLightEntity)
        scene.addEntity(moonLightEntity)

        flashlightEntity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SPOT)
            .color(1.0f, 0.94f, 0.84f)
            .intensity(0f)
            .falloff(22.0f)
            .spotLightCone(0.34f, 0.62f)
            .castShadows(tier.enableShadows)
            .shadowOptions(shadowOpts)
            .build(eng, flashlightEntity)
        scene.addEntity(flashlightEntity)

        for (i in pointLightEntities.indices) {
            val e = EntityManager.get().create()
            LightManager.Builder(LightManager.Type.POINT)
                .color(1.0f, 0.70f, 0.36f)
                .intensity(0f)
                .falloff(12.5f)
                .castShadows(false)
                .build(eng, e)
            pointLightEntities[i] = e
            scene.addEntity(e)
        }
    }

    private fun updateLights(
        eng: Engine,
        camX: Float, camY: Float, camZ: Float,
        lookX: Float, lookY: Float, lookZ: Float,
        inCapture: Boolean,
        inAwakening: Boolean,
        lightScale: Float,
        tier: QualityTier,
    ) {
        val lm = eng.lightManager

        val allowFlashlight = gameEngine.flashlightOn && !inCapture && !inAwakening && !gameEngine.isHiding
        val flashInst = lm.getInstance(flashlightEntity)
        if (flashInst != 0) {
            if (allowFlashlight) {
                val batFrac = (gameEngine.battery / 100f).coerceIn(0f, 1f)
                val flicker = if (batFrac < 0.22f) (0.62f + 0.38f * sin(elapsedSec * 29f)) else 1f
                lm.setPosition(flashInst, camX, camY - 0.05f, camZ)
                lm.setDirection(flashInst, lookX, lookY, lookZ)
                lm.setIntensity(flashInst, 6800f * flicker)
            } else {
                lm.setIntensity(flashInst, 0f)
            }
        }

        val maxLights = (tier.maxDynamicLights + 2).coerceIn(2, 6)
        val sorted = WorldMap3D.pointLights.sortedBy { l ->
            val dx = l.x - camX
            val dy = l.y - camY
            val dz = l.z - camZ
            dx * dx + dy * dy * 2.2f + dz * dz
        }
        val count = minOf(maxLights, sorted.size)
        for (i in pointLightEntities.indices) {
            val inst = lm.getInstance(pointLightEntities[i])
            if (inst == 0) continue
            if (i < count) {
                val l = sorted[i]
                val flicker = if (l.slowFlicker) {
                    val slowWave = 0.76f + 0.24f * sin(elapsedSec * 2.1f + l.flickerSeed) * cos(elapsedSec * 3.7f + l.flickerSeed)
                    val dip = if (sin(elapsedSec * 0.85f + l.flickerSeed * 2.3f) > 0.94f) 0.35f else 1.0f
                    slowWave * dip * lightScale
                } else {
                    (0.92f + 0.08f * sin(elapsedSec * 6.4f + l.flickerSeed) * cos(elapsedSec * 11.2f + l.flickerSeed)) * lightScale
                }
                lm.setPosition(inst, l.x, l.y, l.z)
                lm.setColor(inst, l.r, l.g, l.b)
                lm.setFalloff(inst, l.radius.coerceAtLeast(10f))
                lm.setIntensity(inst, 1850f * flicker)
            } else {
                lm.setIntensity(inst, 0f)
            }
        }
    }

    private fun buildStaticWorldEntities(eng: Engine, scene: Scene, pbr: FilamentPbrLibrary) {
        val batches = SceneBuilder3D.buildStaticCpuBatches()
        for (b in batches) {
            val fMesh = b.meshData.toFilamentMesh(eng)
            val variant = when {
                b.transparent -> MaterialVariant.LIT_TRANSPARENT
                b.doubleSided -> MaterialVariant.LIT_DOUBLE_SIDED
                else -> MaterialVariant.LIT_OPAQUE
            }
            val mi = pbr.getOrCreateInstance(
                variant = variant,
                texture = b.texture,
                tintR = b.tintR,
                tintG = b.tintG,
                tintB = b.tintB,
                legacySpecular = b.specular,
                emissive = b.emissive,
                alphaScale = 1f,
            )
            val entity = EntityManager.get().create()
            eng.transformManager.create(entity, 0, identityMatrix)
            RenderableManager.Builder(1)
                .boundingBox(fMesh.toBoundingBox())
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, fMesh.vertexBuffer, fMesh.indexBuffer)
                .material(0, mi)
                .culling(!b.transparent)
                .castShadows(!b.transparent && b.emissive < 0.1f)
                .receiveShadows(!b.transparent || b.texture == TextureKind.BLOOD_SPLATTER)
                .build(eng, entity)
            scene.addEntity(entity)
            staticEntities.add(
                StaticBatchEntity(
                    entity = entity,
                    mesh = fMesh,
                    isShadowDecal = b.texture == TextureKind.SOFT_SHADOW,
                    isTransparent = b.transparent,
                ),
            )
        }
    }

    private fun buildDynamicDoorAndItemEntities(
        eng: Engine,
        scene: Scene,
        pbr: FilamentPbrLibrary,
        props: PropFilamentLibrary,
    ) {
        for (door in gameEngine.doors) {
            val isSecret = door.def.isSecretBookshelf
            val panelTex = if (isSecret) TextureKind.BOOKSHELF_WOOD else TextureKind.WOOD_FLOOR
            val panelMi = pbr.getOrCreateInstance(
                variant = MaterialVariant.LIT_OPAQUE,
                texture = panelTex,
                tintR = if (isSecret) 0.95f else 0.88f,
                tintG = if (isSecret) 0.90f else 0.78f,
                tintB = if (isSecret) 0.85f else 0.70f,
                legacySpecular = if (isSecret) 0.12f else 0.18f,
            )
            val panelEntity = EntityManager.get().create()
            eng.transformManager.create(panelEntity, 0, identityMatrix)
            RenderableManager.Builder(1)
                .boundingBox(props.doorPanelMesh.toBoundingBox())
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, props.doorPanelMesh.vertexBuffer, props.doorPanelMesh.indexBuffer)
                .material(0, panelMi)
                .castShadows(true)
                .receiveShadows(true)
                .build(eng, panelEntity)
            scene.addEntity(panelEntity)

            val knobMi = pbr.getOrCreateInstance(
                variant = MaterialVariant.LIT_OPAQUE,
                texture = TextureKind.METAL_PROP,
                tintR = 1.0f,
                tintG = 0.82f,
                tintB = 0.38f,
                legacySpecular = 0.85f,
            )
            val knobEntity = EntityManager.get().create()
            eng.transformManager.create(knobEntity, 0, identityMatrix)
            RenderableManager.Builder(1)
                .boundingBox(props.doorKnobMesh.toBoundingBox())
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, props.doorKnobMesh.vertexBuffer, props.doorKnobMesh.indexBuffer)
                .material(0, knobMi)
                .castShadows(true)
                .receiveShadows(true)
                .build(eng, knobEntity)
            scene.addEntity(knobEntity)
            if (isSecret) {
                val ki = eng.renderableManager.getInstance(knobEntity)
                if (ki != 0) eng.renderableManager.setLayerMask(ki, 0xFF, 0x00)
            }
            doorEntities.add(DoorEntityPair(panelEntity, knobEntity))
        }

        for (item in WorldMap3D.itemDefs) {
            if (item.kind == InteractKind.HIDE_CLOSET ||
                item.kind == InteractKind.HIDE_BED ||
                item.kind == InteractKind.FUSE_BOX ||
                item.kind == InteractKind.DRAWER ||
                item.kind == InteractKind.ESCAPE_CAR
            ) continue

            val (mesh, tex) = when {
                item.kind == InteractKind.SECRET_LEVER -> props.noteMesh to TextureKind.BED_FABRIC
                item.id == "lantern" -> props.flashlightPickupMesh to TextureKind.METAL_PROP
                item.id == "key_study" || item.id == "master_key" || item.id == "car_key" -> props.keyMesh to TextureKind.METAL_PROP
                item.id == "medkit" -> props.medkitMesh to TextureKind.METAL_PROP
                item.id == "crowbar" -> props.crowbarMesh to TextureKind.METAL_PROP
                item.id == "gas_can" -> props.gasCanMesh to TextureKind.METAL_PROP
                item.id == "spark_plug" -> props.sparkPlugMesh to TextureKind.METAL_PROP
                item.kind == InteractKind.NOTE -> props.noteMesh to TextureKind.VICTORIAN_WALLPAPER
                else -> props.genericToolMesh to TextureKind.METAL_PROP
            }
            val mi = pbr.getOrCreateInstance(
                variant = MaterialVariant.LIT_OPAQUE,
                texture = tex,
                tintR = item.r * 1.15f,
                tintG = item.g * 1.15f,
                tintB = item.b * 1.15f,
                legacySpecular = 0.65f,
                emissive = if (item.kind == InteractKind.SECRET_LEVER) 0.18f else 0.25f,
            )
            val entity = EntityManager.get().create()
            eng.transformManager.create(entity, 0, identityMatrix)
            RenderableManager.Builder(1)
                .boundingBox(mesh.toBoundingBox())
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, mesh.vertexBuffer, mesh.indexBuffer)
                .material(0, mi)
                .castShadows(true)
                .receiveShadows(true)
                .build(eng, entity)
            scene.addEntity(entity)
            itemEntities.add(ItemEntityEntry(item, entity, mesh, tex))
        }
    }

    private fun updateDoorsAndItems(eng: Engine, tier: QualityTier) {
        val tm = eng.transformManager
        val rm = eng.renderableManager
        tm.openLocalTransformTransaction()

        for (i in gameEngine.doors.indices) {
            if (i >= doorEntities.size) break
            val door = gameEngine.doors[i]
            val pair = doorEntities[i]
            val yawDeg = door.def.closedYawDeg + door.currentAngleDeg

            Matrix.setIdentityM(tmpMatrix, 0)
            Matrix.translateM(tmpMatrix, 0, door.def.hingeX, door.def.baseY + door.def.height * 0.5f, door.def.hingeZ)
            Matrix.rotateM(tmpMatrix, 0, -yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(tmpMatrix, 0, door.def.width, door.def.height, door.def.thickness)
            val pTm = tm.getInstance(pair.panelEntity)
            if (pTm != 0) tm.setTransform(pTm, tmpMatrix)
            val pRm = rm.getInstance(pair.panelEntity)
            if (pRm != 0) rm.setCastShadows(pRm, tier.enableShadows)

            if (!door.def.isSecretBookshelf) {
                Matrix.setIdentityM(tmpMatrix, 0)
                Matrix.translateM(tmpMatrix, 0, door.def.hingeX, door.def.baseY + 1.08f, door.def.hingeZ)
                Matrix.rotateM(tmpMatrix, 0, -yawDeg, 0f, 1f, 0f)
                Matrix.translateM(tmpMatrix, 0, door.def.width - 0.14f, 0f, 0f)
                if (door.def.id == "door_granny_room" && abs(gameEngine.doorKnobTurnDeg) > 0.1f) {
                    Matrix.rotateM(tmpMatrix, 0, gameEngine.doorKnobTurnDeg, 0f, 0f, 1f)
                }
                val kTm = tm.getInstance(pair.knobEntity)
                if (kTm != 0) tm.setTransform(kTm, tmpMatrix)
            }
        }

        for (entry in itemEntities) {
            val item = entry.def
            val rInst = rm.getInstance(entry.entity)
            if (item.kind == InteractKind.ITEM && gameEngine.collectedItems.contains(item.id)) {
                if (rInst != 0) rm.setLayerMask(rInst, 0xFF, 0x00)
                continue
            }
            if (rInst != 0) rm.setLayerMask(rInst, 0xFF, 0x01)

            Matrix.setIdentityM(tmpMatrix, 0)
            if (item.kind == InteractKind.SECRET_LEVER) {
                val secretUnlocked = gameEngine.doors.firstOrNull { it.def.id == "secret_bookshelf_door" }?.isUnlocked == true
                Matrix.translateM(tmpMatrix, 0, item.x, item.y, item.z)
                Matrix.rotateM(tmpMatrix, 0, 90f, 0f, 0f, 1f)
                Matrix.rotateM(tmpMatrix, 0, if (secretUnlocked) -36f else -14f, 1f, 0f, 0f)
            } else {
                val bob = sin(elapsedSec * 2.3f + item.x) * 0.025f
                Matrix.translateM(tmpMatrix, 0, item.x, item.y + bob, item.z)
                Matrix.rotateM(tmpMatrix, 0, elapsedSec * 36f, 0f, 1f, 0f)
            }
            val tInst = tm.getInstance(entry.entity)
            if (tInst != 0) tm.setTransform(tInst, tmpMatrix)
        }

        tm.commitLocalTransformTransaction()
    }

    private fun allocateDynamicPool(
        eng: Engine,
        scene: Scene,
        placeholderMesh: FilamentMesh,
        pbr: FilamentPbrLibrary,
    ) {
        val defaultMi = pbr.getOrCreateInstance(MaterialVariant.LIT_OPAQUE, TextureKind.GRANNY_SKIN)
        for (i in dynamicPoolEntities.indices) {
            val e = EntityManager.get().create()
            eng.transformManager.create(e, 0, identityMatrix)
            RenderableManager.Builder(1)
                .boundingBox(placeholderMesh.toBoundingBox())
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, placeholderMesh.vertexBuffer, placeholderMesh.indexBuffer)
                .material(0, defaultMi)
                .culling(true)
                .castShadows(false)
                .receiveShadows(true)
                .layerMask(0xFF, 0x00)
                .build(eng, e)
            scene.addEntity(e)
            dynamicPoolEntities[i] = e
        }
        poolInitialized = true
    }

    private fun emitVolumetricMistLayers(
        props: PropFilamentLibrary,
        camX: Float,
        camY: Float,
        camZ: Float,
        tier: QualityTier,
    ) {
        val yawDeg = gameEngine.playerYawRad * (180f / PI.toFloat())
        val layerCount = tier.volumetricFogLayers

        for (i in 0 until layerCount) {
            val seed = i * 1.37f
            val driftX = sin(elapsedSec * 0.22f + seed) * 2.2f + ((i % 5) - 2) * 1.85f
            val fogZ = 10.8f + ((i * 2.4f + elapsedSec * 0.18f) % 21.0f)
            val fogY = 0.65f + (i % 3) * 0.42f
            val scale = 2.8f + (i % 4) * 0.75f

            Matrix.setIdentityM(tmpMatrix, 0)
            Matrix.translateM(tmpMatrix, 0, driftX, fogY, fogZ)
            Matrix.rotateM(tmpMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(tmpMatrix, 0, scale * 1.6f, scale * 0.85f, 1f)
            emitPooledRenderable(tmpMatrix, props.fogPlaneMesh, TextureKind.FOG_PUFF, 0.78f, 0.84f, 0.96f, 0f, 0.15f, 0.17f, doubleSided = true, unlit = true, castShadows = false, receiveShadows = false)
        }

        if (camZ > 6.0f && camX < 14.0f) {
            Matrix.setIdentityM(tmpMatrix, 0)
            Matrix.translateM(tmpMatrix, 0, 0f, 1.85f, 10.65f)
            Matrix.rotateM(tmpMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(tmpMatrix, 0, 2.5f, 2.2f, 1f)
            emitPooledRenderable(tmpMatrix, props.fogPlaneMesh, TextureKind.FOG_PUFF, 1.0f, 0.66f, 0.28f, 0f, 0.85f, 0.22f, doubleSided = true, unlit = true, castShadows = false, receiveShadows = false)

            Matrix.setIdentityM(tmpMatrix, 0)
            Matrix.translateM(tmpMatrix, 0, -2.36f, 2.65f, 22.0f)
            Matrix.rotateM(tmpMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(tmpMatrix, 0, 3.2f, 2.8f, 1f)
            emitPooledRenderable(tmpMatrix, props.fogPlaneMesh, TextureKind.FOG_PUFF, 0.62f, 0.76f, 1.0f, 0f, 0.75f, 0.18f, doubleSided = true, unlit = true, castShadows = false, receiveShadows = false)
        } else if (camX > 13.0f && camZ < 10.5f) {
            Matrix.setIdentityM(tmpMatrix, 0)
            Matrix.translateM(tmpMatrix, 0, 19.5f, 2.25f, 5.8f)
            Matrix.rotateM(tmpMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(tmpMatrix, 0, 2.8f, 2.2f, 1f)
            emitPooledRenderable(tmpMatrix, props.fogPlaneMesh, TextureKind.FOG_PUFF, 0.96f, 0.68f, 0.36f, 0f, 0.72f, 0.20f, doubleSided = true, unlit = true, castShadows = false, receiveShadows = false)
        } else {
            Matrix.setIdentityM(tmpMatrix, 0)
            Matrix.translateM(tmpMatrix, 0, camX + sin(elapsedSec * 0.4f) * 1.4f, camY - 0.15f, camZ - 3.2f)
            Matrix.rotateM(tmpMatrix, 0, yawDeg, 0f, 1f, 0f)
            Matrix.scaleM(tmpMatrix, 0, 3.4f, 1.9f, 1f)
            emitPooledRenderable(tmpMatrix, props.fogPlaneMesh, TextureKind.FOG_PUFF, 0.55f, 0.52f, 0.58f, 0f, 0.10f, 0.14f, doubleSided = true, unlit = true, castShadows = false, receiveShadows = false)

            if (tier.enableParticles) {
                for (m in 0 until 7) {
                    val mx = camX + sin(elapsedSec * 0.45f + m * 2.1f) * 2.4f
                    val my = (camY - 0.35f) + ((m * 0.27f + elapsedSec * 0.08f) % 1.35f)
                    val mz = camZ + cos(elapsedSec * 0.38f + m * 1.7f) * 2.4f
                    val sz = 0.16f + (m % 3) * 0.05f
                    Matrix.setIdentityM(tmpMatrix, 0)
                    Matrix.translateM(tmpMatrix, 0, mx, my, mz)
                    Matrix.rotateM(tmpMatrix, 0, yawDeg, 0f, 1f, 0f)
                    Matrix.scaleM(tmpMatrix, 0, sz, sz, 1f)
                    emitPooledRenderable(tmpMatrix, props.fogPlaneMesh, TextureKind.DUST_MOTE, 0.92f, 0.88f, 0.78f, 0f, 0.35f, 0.45f, doubleSided = true, unlit = true, castShadows = false, receiveShadows = false)
                }
            }
        }
    }

    private fun emitPooledRenderable(
        mat: FloatArray,
        mesh: FilamentMesh,
        tex: TextureKind,
        tintR: Float,
        tintG: Float,
        tintB: Float,
        specular: Float,
        emissive: Float,
        alphaScale: Float,
        doubleSided: Boolean,
        unlit: Boolean,
        castShadows: Boolean,
        receiveShadows: Boolean,
    ) {
        if (dynamicPoolCursor >= dynamicPoolEntities.size) return
        val eng = engine ?: return
        val pbr = pbrLibrary ?: return
        val entity = dynamicPoolEntities[dynamicPoolCursor++]

        val variant = when {
            unlit -> MaterialVariant.UNLIT_TRANSPARENT
            alphaScale < 0.99f || tex == TextureKind.SOFT_SHADOW || tex == TextureKind.FOG_PUFF || tex == TextureKind.DUST_MOTE || tex == TextureKind.BLOOD_SPLATTER ->
                MaterialVariant.LIT_TRANSPARENT
            doubleSided -> MaterialVariant.LIT_DOUBLE_SIDED
            else -> MaterialVariant.LIT_OPAQUE
        }

        val mi = pbr.getOrCreateInstance(
            variant = variant,
            texture = tex,
            tintR = tintR,
            tintG = tintG,
            tintB = tintB,
            legacySpecular = specular,
            emissive = emissive,
            alphaScale = alphaScale,
        )

        val tm = eng.transformManager
        val tInst = tm.getInstance(entity)
        if (tInst != 0) tm.setTransform(tInst, mat)

        val rm = eng.renderableManager
        val rInst = rm.getInstance(entity)
        if (rInst != 0) {
            rm.setGeometryAt(rInst, 0, RenderableManager.PrimitiveType.TRIANGLES, mesh.vertexBuffer, mesh.indexBuffer)
            rm.setMaterialInstanceAt(rInst, 0, mi)
            rm.setAxisAlignedBoundingBox(rInst, mesh.toBoundingBox())
            rm.setCastShadows(rInst, castShadows)
            rm.setReceiveShadows(rInst, receiveShadows)
            rm.setLayerMask(rInst, 0xFF, 0x01)
        }
    }

    private fun applyQualityOptionsToView(view: View, tier: QualityTier) {
        view.setShadowingEnabled(tier.enableShadows)
        view.antiAliasing = if (tier == QualityTier.LOW) View.AntiAliasing.NONE else View.AntiAliasing.FXAA
    }

    private fun updateCameraProjection(drawDist: Float) {
        camera?.setProjection(
            68.0,
            aspectRatio.toDouble().coerceAtLeast(0.5),
            0.08,
            drawDist.toDouble(),
            Camera.Fov.VERTICAL,
        )
    }

    fun loadedGlbSummary(): Map<String, List<String>> {
        val map = LinkedHashMap<String, List<String>>()
        grannyGlbModel?.let { map[it.assetPath] = it.animationNames }
        protagonistGlbModel?.let { map[it.assetPath] = it.animationNames }
        return map
    }
}
