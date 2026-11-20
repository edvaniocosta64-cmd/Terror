package com.example.engine3d

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.filamat.MaterialBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Filament GPU mesh container built from pure-CPU [MeshData] with precomputed bounds and
 * tangent-space quaternions for PBR normal/lighting calculations.
 */
data class FilamentMesh(
    val vertexBuffer: VertexBuffer,
    val indexBuffer: IndexBuffer,
    val indexCount: Int,
    val bounds: MeshBounds,
) {
    fun toBoundingBox(): Box = Box(
        bounds.centerX,
        bounds.centerY,
        bounds.centerZ,
        bounds.halfExtentX,
        bounds.halfExtentY,
        bounds.halfExtentZ,
    )

    fun destroy(engine: Engine) {
        engine.destroyVertexBuffer(vertexBuffer)
        engine.destroyIndexBuffer(indexBuffer)
    }
}

fun MeshData.toFilamentMesh(engine: Engine): FilamentMesh {
    val vCount = vertexCount.coerceAtLeast(1)
    val iCount = indexCount.coerceAtLeast(1)
    val bounds = computeBounds()
    val quats = computeTangentQuaternions()

    val vb = VertexBuffer.Builder()
        .vertexCount(vCount)
        .bufferCount(2)
        .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, 32)
        .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 24, 32)
        .attribute(VertexBuffer.VertexAttribute.TANGENTS, 1, VertexBuffer.AttributeType.FLOAT4, 0, 16)
        .build(engine)

    val interleavedBuf = ByteBuffer.allocateDirect(vertices.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(vertices)
            position(0)
        }
    val tangentBuf = ByteBuffer.allocateDirect(quats.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(quats)
            position(0)
        }

    vb.setBufferAt(engine, 0, interleavedBuf)
    vb.setBufferAt(engine, 1, tangentBuf)

    val ib = IndexBuffer.Builder()
        .indexCount(iCount)
        .bufferType(IndexBuffer.Builder.IndexType.USHORT)
        .build(engine)

    val idxBuf = ByteBuffer.allocateDirect(indices.size * 2)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer()
        .apply {
            put(indices)
            position(0)
        }
    ib.setBuffer(engine, idxBuf)

    return FilamentMesh(
        vertexBuffer = vb,
        indexBuffer = ib,
        indexCount = iCount,
        bounds = bounds,
    )
}

class CharacterFilamentLibrary(
    engine: Engine,
    val cpuMeshes: CharacterCpuMeshLibrary = CharacterCpuMeshLibrary(),
) : CharacterMeshProvider<FilamentMesh> {
    override val sculptedHeadGranny: FilamentMesh = cpuMeshes.sculptedHeadGranny.toFilamentMesh(engine)
    override val jawGranny: FilamentMesh = cpuMeshes.jawGranny.toFilamentMesh(engine)
    override val hairBun: FilamentMesh = cpuMeshes.hairBun.toFilamentMesh(engine)
    override val hairStrand: FilamentMesh = cpuMeshes.hairStrand.toFilamentMesh(engine)
    override val neckMesh: FilamentMesh = cpuMeshes.neckMesh.toFilamentMesh(engine)
    override val torsoGranny: FilamentMesh = cpuMeshes.torsoGranny.toFilamentMesh(engine)
    override val skirtGranny: FilamentMesh = cpuMeshes.skirtGranny.toFilamentMesh(engine)
    override val shawlCollar: FilamentMesh = cpuMeshes.shawlCollar.toFilamentMesh(engine)
    override val eyeball: FilamentMesh = cpuMeshes.eyeball.toFilamentMesh(engine)
    override val irisPupil: FilamentMesh = cpuMeshes.irisPupil.toFilamentMesh(engine)
    override val browMesh: FilamentMesh = cpuMeshes.browMesh.toFilamentMesh(engine)
    override val eyelidMesh: FilamentMesh = cpuMeshes.eyelidMesh.toFilamentMesh(engine)
    override val upperArm: FilamentMesh = cpuMeshes.upperArm.toFilamentMesh(engine)
    override val forearm: FilamentMesh = cpuMeshes.forearm.toFilamentMesh(engine)
    override val jointSphere: FilamentMesh = cpuMeshes.jointSphere.toFilamentMesh(engine)
    override val palmMesh: FilamentMesh = cpuMeshes.palmMesh.toFilamentMesh(engine)
    override val fingerMesh: FilamentMesh = cpuMeshes.fingerMesh.toFilamentMesh(engine)
    override val distalFinger: FilamentMesh = cpuMeshes.distalFinger.toFilamentMesh(engine)
    override val palmGranny: FilamentMesh = cpuMeshes.palmGranny.toFilamentMesh(engine)
    override val fingerGranny: FilamentMesh = cpuMeshes.fingerGranny.toFilamentMesh(engine)
    override val distalFingerGranny: FilamentMesh = cpuMeshes.distalFingerGranny.toFilamentMesh(engine)
    override val sleeveCuff: FilamentMesh = cpuMeshes.sleeveCuff.toFilamentMesh(engine)
    override val jacketCollar: FilamentMesh = cpuMeshes.jacketCollar.toFilamentMesh(engine)
    override val thighMesh: FilamentMesh = cpuMeshes.thighMesh.toFilamentMesh(engine)
    override val shinMesh: FilamentMesh = cpuMeshes.shinMesh.toFilamentMesh(engine)
    override val footShoeMesh: FilamentMesh = cpuMeshes.footShoeMesh.toFilamentMesh(engine)
    override val sculptedHeadPlayer: FilamentMesh = cpuMeshes.sculptedHeadPlayer.toFilamentMesh(engine)
    override val shortHairPlayer: FilamentMesh = cpuMeshes.shortHairPlayer.toFilamentMesh(engine)
    override val torsoPlayer: FilamentMesh = cpuMeshes.torsoPlayer.toFilamentMesh(engine)
    override val flashlightBody: FilamentMesh = cpuMeshes.flashlightBody.toFilamentMesh(engine)
    override val flashlightLens: FilamentMesh = cpuMeshes.flashlightLens.toFilamentMesh(engine)

    fun destroy(engine: Engine) {
        listOf(
            sculptedHeadGranny, jawGranny, hairBun, hairStrand, neckMesh,
            torsoGranny, skirtGranny, shawlCollar, eyeball, irisPupil,
            browMesh, eyelidMesh, upperArm, forearm, jointSphere,
            palmMesh, fingerMesh, distalFinger, palmGranny, fingerGranny, distalFingerGranny,
            sleeveCuff, jacketCollar, thighMesh, shinMesh, footShoeMesh,
            sculptedHeadPlayer, shortHairPlayer, torsoPlayer, flashlightBody, flashlightLens,
        ).forEach { it.destroy(engine) }
    }
}

class PropFilamentLibrary(
    engine: Engine,
    val cpuMeshes: PropCpuMeshLibrary = PropCpuMeshLibrary(),
) {
    val doorPanelMesh: FilamentMesh = cpuMeshes.doorPanelMesh.toFilamentMesh(engine)
    val doorKnobMesh: FilamentMesh = cpuMeshes.doorKnobMesh.toFilamentMesh(engine)
    val keyMesh: FilamentMesh = cpuMeshes.keyMesh.toFilamentMesh(engine)
    val noteMesh: FilamentMesh = cpuMeshes.noteMesh.toFilamentMesh(engine)
    val flashlightPickupMesh: FilamentMesh = cpuMeshes.flashlightPickupMesh.toFilamentMesh(engine)
    val medkitMesh: FilamentMesh = cpuMeshes.medkitMesh.toFilamentMesh(engine)
    val crowbarMesh: FilamentMesh = cpuMeshes.crowbarMesh.toFilamentMesh(engine)
    val gasCanMesh: FilamentMesh = cpuMeshes.gasCanMesh.toFilamentMesh(engine)
    val sparkPlugMesh: FilamentMesh = cpuMeshes.sparkPlugMesh.toFilamentMesh(engine)
    val genericToolMesh: FilamentMesh = cpuMeshes.genericToolMesh.toFilamentMesh(engine)
    val fogPlaneMesh: FilamentMesh = cpuMeshes.fogPlaneMesh.toFilamentMesh(engine)
    val shadowDecalMesh: FilamentMesh = cpuMeshes.shadowDecalMesh.toFilamentMesh(engine)

    fun destroy(engine: Engine) {
        listOf(
            doorPanelMesh, doorKnobMesh, keyMesh, noteMesh,
            flashlightPickupMesh, medkitMesh, crowbarMesh,
            gasCanMesh, sparkPlugMesh, genericToolMesh, fogPlaneMesh, shadowDecalMesh,
        ).forEach { it.destroy(engine) }
    }
}

data class PbrParams(
    val metallic: Float,
    val roughness: Float,
)

enum class MaterialVariant {
    LIT_OPAQUE,
    LIT_DOUBLE_SIDED,
    LIT_TRANSPARENT,
    UNLIT_TRANSPARENT,
}

/**
 * Compiles runtime Filament PBR materials via `filamat` (`MaterialBuilder`), uploads mipmapped
 * procedural albedo textures for every [TextureKind], and caches parameterized [MaterialInstance]s.
 */
class FilamentPbrLibrary(
    private val engine: Engine,
    textureResolutionPx: Int = 256,
) {
    private val textures = HashMap<TextureKind, Texture>(TextureKind.entries.size)
    private val instances = HashMap<String, MaterialInstance>(64)

    private val repeatSampler = TextureSampler(
        TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
        TextureSampler.MagFilter.LINEAR,
        TextureSampler.WrapMode.REPEAT,
    ).apply { anisotropy = 2.0f }

    private val clampSampler = TextureSampler(
        TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
        TextureSampler.MagFilter.LINEAR,
        TextureSampler.WrapMode.CLAMP_TO_EDGE,
    )

    private val litOpaqueMat: Material
    private val litDoubleSidedMat: Material
    private val litTransparentMat: Material
    private val unlitTransparentMat: Material

    init {
        MaterialBuilder.init()
        litOpaqueMat = compilePbrMaterial("pbr_lit_opaque", lit = true, transparent = false, doubleSided = false)
        litDoubleSidedMat = compilePbrMaterial("pbr_lit_doublesided", lit = true, transparent = false, doubleSided = true)
        litTransparentMat = compilePbrMaterial("pbr_lit_transparent", lit = true, transparent = true, doubleSided = true)
        unlitTransparentMat = compilePbrMaterial("pbr_unlit_transparent", lit = false, transparent = true, doubleSided = true)
        uploadTextures(textureResolutionPx)
    }

    fun pbrFor(kind: TextureKind, legacySpecular: Float = 0.14f): PbrParams = when (kind) {
        TextureKind.METAL_PROP -> PbrParams(metallic = 0.80f, roughness = 0.34f)
        TextureKind.CAR_RUST_PAINT -> PbrParams(metallic = 0.52f, roughness = 0.42f)
        TextureKind.BATHROOM_TILE -> PbrParams(metallic = 0.04f, roughness = 0.24f)
        TextureKind.ASPHALT_ROAD -> PbrParams(metallic = 0.04f, roughness = 0.38f)
        TextureKind.WOOD_FLOOR -> PbrParams(metallic = 0.0f, roughness = 0.56f)
        TextureKind.CARVED_OAK -> PbrParams(metallic = 0.0f, roughness = 0.50f)
        TextureKind.BOOKSHELF_WOOD -> PbrParams(metallic = 0.0f, roughness = 0.68f)
        TextureKind.VICTORIAN_WALLPAPER -> PbrParams(metallic = 0.0f, roughness = 0.86f)
        TextureKind.AGED_PLASTER -> PbrParams(metallic = 0.0f, roughness = 0.88f)
        TextureKind.STONE_CELLAR -> PbrParams(metallic = 0.0f, roughness = 0.80f)
        TextureKind.ROOF_SHINGLES -> PbrParams(metallic = 0.0f, roughness = 0.84f)
        TextureKind.TREE_BARK -> PbrParams(metallic = 0.0f, roughness = 0.92f)
        TextureKind.PINE_FOLIAGE, TextureKind.FOREST_GROUND -> PbrParams(metallic = 0.0f, roughness = 0.90f)
        TextureKind.GRANNY_SKIN -> PbrParams(metallic = 0.0f, roughness = 0.74f)
        TextureKind.PLAYER_SKIN -> PbrParams(metallic = 0.0f, roughness = 0.68f)
        TextureKind.GRANNY_DRESS, TextureKind.PLAYER_JACKET, TextureKind.BED_FABRIC -> PbrParams(metallic = 0.0f, roughness = 0.88f)
        TextureKind.GRANNY_HAIR, TextureKind.PLAYER_HAIR -> PbrParams(metallic = 0.0f, roughness = 0.76f)
        TextureKind.WALL_PORTRAIT -> PbrParams(metallic = 0.0f, roughness = 0.64f)
        TextureKind.WINDOW_GLOW, TextureKind.LANTERN_CORE -> PbrParams(metallic = 0.0f, roughness = 0.22f)
        TextureKind.BLOOD_SPLATTER -> PbrParams(metallic = 0.04f, roughness = 0.16f)
        TextureKind.FOG_PUFF, TextureKind.SOFT_SHADOW, TextureKind.DUST_MOTE -> PbrParams(metallic = 0.0f, roughness = 0.95f)
    }.let { preset ->
        if (legacySpecular >= 0.75f && preset.metallic < 0.2f) {
            preset.copy(roughness = preset.roughness.coerceAtMost(0.28f))
        } else {
            preset
        }
    }

    fun getOrCreateInstance(
        variant: MaterialVariant,
        texture: TextureKind,
        tintR: Float = 1f,
        tintG: Float = 1f,
        tintB: Float = 1f,
        legacySpecular: Float = 0.14f,
        emissive: Float = 0f,
        alphaScale: Float = 1f,
    ): MaterialInstance {
        val qr = (tintR * 50f).roundToInt()
        val qg = (tintG * 50f).roundToInt()
        val qb = (tintB * 50f).roundToInt()
        val qe = (emissive * 25f).roundToInt()
        val qa = (alphaScale * 50f).roundToInt()
        val qs = (legacySpecular * 10f).roundToInt()
        val key = "${variant.ordinal}_${texture.ordinal}_${qr}_${qg}_${qb}_${qe}_${qa}_${qs}"

        return instances.getOrPut(key) {
            val mat = when (variant) {
                MaterialVariant.LIT_OPAQUE -> litOpaqueMat
                MaterialVariant.LIT_DOUBLE_SIDED -> litDoubleSidedMat
                MaterialVariant.LIT_TRANSPARENT -> litTransparentMat
                MaterialVariant.UNLIT_TRANSPARENT -> unlitTransparentMat
            }
            val pbr = pbrFor(texture, legacySpecular)
            val tex = textures.getValue(texture)
            val sampler = when (texture) {
                TextureKind.FOG_PUFF, TextureKind.SOFT_SHADOW, TextureKind.DUST_MOTE, TextureKind.BLOOD_SPLATTER -> clampSampler
                else -> repeatSampler
            }
            mat.createInstance().apply {
                setParameter("albedoMap", tex, sampler)
                setParameter("tintColor", srgbToLinear(tintR), srgbToLinear(tintG), srgbToLinear(tintB))
                setParameter("metallic", pbr.metallic)
                setParameter("roughness", pbr.roughness)
                setParameter("emissiveIntensity", emissive)
                setParameter("alphaScale", alphaScale)
            }
        }
    }

    private fun uploadTextures(resolutionPx: Int) {
        val size = resolutionPx.coerceIn(128, 512)
        val mipLevels = (kotlin.math.log2(size.toFloat()).toInt() + 1).coerceAtLeast(1)
        for (kind in TextureKind.entries) {
            val bmp = ProceduralTextures3D.generateBitmap(kind, size)
            val tex = Texture.Builder()
                .width(size)
                .height(size)
                .levels(mipLevels)
                .sampler(Texture.Sampler.SAMPLER_2D)
                .format(Texture.InternalFormat.SRGB8_A8)
                .usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE)
                .build(engine)
            TextureHelper.setBitmap(engine, tex, 0, bmp)
            tex.generateMipmaps(engine)
            textures[kind] = tex
        }
    }

    private fun compilePbrMaterial(
        matName: String,
        lit: Boolean,
        transparent: Boolean,
        doubleSided: Boolean,
    ): Material {
        val fragCode = if (lit) {
            """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec4 tex = texture(materialParams_albedoMap, getUV0());
                float outAlpha = tex.a * materialParams.alphaScale;
                if (outAlpha < 0.02) discard;
                vec3 base = tex.rgb * materialParams.tintColor;
                material.baseColor = vec4(base, outAlpha);
                material.metallic = materialParams.metallic;
                material.roughness = materialParams.roughness;
                material.emissive = vec4(base * materialParams.emissiveIntensity * 3.5, 1.0);
            }
            """.trimIndent()
        } else {
            """
            void material(inout MaterialInputs material) {
                prepareMaterial(material);
                vec4 tex = texture(materialParams_albedoMap, getUV0());
                float outAlpha = tex.a * materialParams.alphaScale;
                if (outAlpha < 0.02) discard;
                vec3 base = tex.rgb * materialParams.tintColor;
                material.baseColor = vec4(base * (1.0 + materialParams.emissiveIntensity), outAlpha);
            }
            """.trimIndent()
        }

        val builder = MaterialBuilder()
            .name(matName)
            .materialDomain(MaterialBuilder.MaterialDomain.SURFACE)
            .shading(if (lit) MaterialBuilder.Shading.LIT else MaterialBuilder.Shading.UNLIT)
            .blending(if (transparent) MaterialBuilder.BlendingMode.TRANSPARENT else MaterialBuilder.BlendingMode.OPAQUE)
            .require(MaterialBuilder.VertexAttribute.UV0)
            .samplerParameter(
                MaterialBuilder.SamplerType.SAMPLER_2D,
                MaterialBuilder.SamplerFormat.FLOAT,
                MaterialBuilder.ParameterPrecision.DEFAULT,
                "albedoMap",
            )
            .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "tintColor")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "metallic")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughness")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "emissiveIntensity")
            .uniformParameter(MaterialBuilder.UniformType.FLOAT, "alphaScale")
            .doubleSided(doubleSided)
            .culling(if (doubleSided) MaterialBuilder.CullingMode.NONE else MaterialBuilder.CullingMode.BACK)
            .depthWrite(!transparent)
            .platform(MaterialBuilder.Platform.MOBILE)
            .targetApi(MaterialBuilder.TargetApi.OPENGL)
            .optimization(MaterialBuilder.Optimization.PREPROCESSOR)
            .material(fragCode)

        val pkg = builder.build()
        check(pkg.isValid) { "Falha ao compilar material Filament: $matName" }
        val payload = pkg.buffer
        return Material.Builder()
            .payload(payload, payload.remaining())
            .build(engine)
    }

    private fun srgbToLinear(c: Float): Float {
        if (c <= 0f) return 0f
        return if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    fun destroy() {
        instances.values.forEach { engine.destroyMaterialInstance(it) }
        instances.clear()
        textures.values.forEach { engine.destroyTexture(it) }
        textures.clear()
        engine.destroyMaterial(litOpaqueMat)
        engine.destroyMaterial(litDoubleSidedMat)
        engine.destroyMaterial(litTransparentMat)
        engine.destroyMaterial(unlitTransparentMat)
    }
}
