package com.example.engine3d

import android.content.Context
import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer

data class LoadedGltfModel(
    val assetPath: String,
    val asset: FilamentAsset,
    val animationNames: List<String>,
    val animationDurations: List<Float>,
) {
    val renderableCount: Int get() = asset.renderableEntities.size
    val boneCount: Int get() = asset.entities.size
}

/**
 * Manages real glTF 2.0 / binary `.glb` assets (`models/granny.glb`, `models/protagonist.glb`)
 * using Google Filament `gltfio` ([UbershaderProvider], [AssetLoader], [ResourceLoader]) and
 * drives keyframe skeletal animations via `FilamentAsset.getInstance().animator`.
 */
class GltfModelPipeline(
    private val context: Context,
    private val engine: Engine,
) {
    private val materialProvider: UbershaderProvider
    private val assetLoader: AssetLoader
    private val resourceLoader: ResourceLoader
    private val loadedModels = mutableListOf<LoadedGltfModel>()

    init {
        Gltfio.init()
        materialProvider = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
        resourceLoader = ResourceLoader(engine, true)
    }

    fun loadModelFromAssets(assetPath: String): LoadedGltfModel? {
        return try {
            val bytes = context.assets.open(assetPath).use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).apply {
                put(bytes)
                rewind()
            }
            val asset = assetLoader.createAsset(buf) ?: run {
                Log.w("GltfModelPipeline", "AssetLoader retornou null para $assetPath")
                return null
            }
            resourceLoader.loadResources(asset)
            asset.releaseSourceData()

            val animator = asset.instance.animator
            val count = animator.animationCount
            val names = (0 until count).map { idx -> animator.getAnimationName(idx) ?: "Clip_$idx" }
            val durations = (0 until count).map { idx -> animator.getAnimationDuration(idx) }

            val model = LoadedGltfModel(
                assetPath = assetPath,
                asset = asset,
                animationNames = names,
                animationDurations = durations,
            )
            loadedModels.add(model)
            Log.i(
                "GltfModelPipeline",
                "Carregado $assetPath: renderables=${model.renderableCount}, ossos=${model.boneCount}, animacoes=$names",
            )
            model
        } catch (t: Throwable) {
            Log.e("GltfModelPipeline", "Erro ao carregar modelo GLB $assetPath", t)
            null
        }
    }

    fun attachToScene(model: LoadedGltfModel, scene: Scene) {
        scene.addEntities(model.asset.entities)
    }

    fun detachFromScene(model: LoadedGltfModel, scene: Scene) {
        scene.removeEntities(model.asset.entities)
    }

    fun applyClip(model: LoadedGltfModel, clipName: String, timeSec: Float) {
        val animator = model.asset.instance.animator
        val idx = model.animationNames.indexOfFirst { it.equals(clipName, ignoreCase = true) }
        if (idx >= 0) {
            val duration = model.animationDurations.getOrElse(idx) { 1f }.coerceAtLeast(0.05f)
            animator.applyAnimation(idx, timeSec % duration)
            animator.updateBoneMatrices()
        }
    }

    fun destroy() {
        loadedModels.forEach { model ->
            assetLoader.destroyAsset(model.asset)
        }
        loadedModels.clear()
        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroyMaterials()
        materialProvider.destroy()
    }
}
