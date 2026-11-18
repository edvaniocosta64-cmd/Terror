package com.example.engine3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class CpuMeshBatch(
    val texture: TextureKind,
    val meshData: MeshData,
    val tintR: Float = 1f,
    val tintG: Float = 1f,
    val tintB: Float = 1f,
    val specular: Float = 0.14f,
    val emissive: Float = 0f,
    val transparent: Boolean = false,
    val doubleSided: Boolean = false,
)

data class GpuSceneBatch(
    val texture: TextureKind,
    val gpuMesh: GpuMesh,
    val tintR: Float,
    val tintG: Float,
    val tintB: Float,
    val specular: Float,
    val emissive: Float,
    val transparent: Boolean,
    val doubleSided: Boolean,
)

enum class PropMeshKind {
    DOOR_PANEL,
    DOOR_KNOB,
    KEY,
    NOTE,
    FLASHLIGHT_PICKUP,
    MEDKIT,
    CROWBAR,
    GENERIC_TOOL,
    FOG_PLANE,
    SHADOW_DECAL,
}

/**
 * Pure CPU mesh library for dynamic props, doors, items, fog billboards, and contact shadows.
 * Zero OpenGL calls: can be uploaded to OpenGL ES 2.0 VBOs or Filament VertexBuffers.
 */
class PropCpuMeshLibrary {
    val doorPanelMesh: MeshData = MeshBuilder3D().apply {
        addTexturedBox(0f, -0.5f, -0.5f, 1f, 0.5f, 0.5f, uvTileMeters = 0.8f)
        addTexturedBox(0.10f, 0.06f, -0.58f, 0.90f, 0.43f, 0.58f, uvTileMeters = 0.8f)
        addTexturedBox(0.10f, -0.43f, -0.58f, 0.90f, -0.06f, 0.58f, uvTileMeters = 0.8f)
    }.build()

    val doorKnobMesh: MeshData = MeshBuilder3D().apply {
        addEllipsoid(cx = 0f, cy = 0f, cz = 0f, rx = 0.042f, ry = 0.042f, rz = 0.065f, rings = 8, sectors = 12)
    }.build()

    val keyMesh: MeshData = MeshBuilder3D().apply {
        addEllipsoid(cx = -0.07f, cy = 0f, cz = 0f, rx = 0.042f, ry = 0.015f, rz = 0.042f, rings = 8, sectors = 12)
        addTexturedBox(-0.03f, -0.01f, -0.012f, 0.11f, 0.01f, 0.012f, uvTileMeters = 0.2f)
        addTexturedBox(0.06f, -0.01f, 0.012f, 0.10f, 0.01f, 0.042f, uvTileMeters = 0.2f)
    }.build()

    val noteMesh: MeshData = MeshBuilder3D().apply {
        addTexturedBox(-0.14f, -0.012f, -0.18f, 0.14f, 0.012f, 0.18f, uvTileMeters = 0.35f)
    }.build()

    val flashlightPickupMesh: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.10f, yTop = 0.08f, rxBottom = 0.024f, rzBottom = 0.024f, rxTop = 0.025f, rzTop = 0.025f, rings = 4, sectors = 12)
        addTaperedLimb(yBottom = 0.08f, yTop = 0.13f, rxBottom = 0.026f, rzBottom = 0.026f, rxTop = 0.042f, rzTop = 0.042f, rings = 4, sectors = 14)
    }.build()

    val medkitMesh: MeshData = MeshBuilder3D().apply {
        addTexturedBox(-0.14f, -0.07f, -0.10f, 0.14f, 0.07f, 0.10f, uvTileMeters = 0.3f)
    }.build()

    val crowbarMesh: MeshData = MeshBuilder3D().apply {
        addTexturedBox(-0.24f, -0.016f, -0.02f, 0.20f, 0.016f, 0.02f, uvTileMeters = 0.4f)
        addTexturedBox(0.17f, -0.016f, 0.01f, 0.25f, 0.016f, 0.09f, uvTileMeters = 0.2f)
    }.build()

    val genericToolMesh: MeshData = MeshBuilder3D().apply {
        addTaperedLimb(yBottom = -0.09f, yTop = 0.09f, rxBottom = 0.038f, rzBottom = 0.038f, rxTop = 0.038f, rzTop = 0.038f, rings = 4, sectors = 12)
    }.build()

    val fogPlaneMesh: MeshData = MeshBuilder3D().apply {
        addCenteredQuadXY()
    }.build()

    val shadowDecalMesh: MeshData = MeshBuilder3D().apply {
        addHorizontalQuad(cx = 0f, y = 0.028f, cz = 0f, halfW = 0.55f, halfD = 0.55f)
    }.build()

    fun meshFor(kind: PropMeshKind): MeshData = when (kind) {
        PropMeshKind.DOOR_PANEL -> doorPanelMesh
        PropMeshKind.DOOR_KNOB -> doorKnobMesh
        PropMeshKind.KEY -> keyMesh
        PropMeshKind.NOTE -> noteMesh
        PropMeshKind.FLASHLIGHT_PICKUP -> flashlightPickupMesh
        PropMeshKind.MEDKIT -> medkitMesh
        PropMeshKind.CROWBAR -> crowbarMesh
        PropMeshKind.GENERIC_TOOL -> genericToolMesh
        PropMeshKind.FOG_PLANE -> fogPlaneMesh
        PropMeshKind.SHADOW_DECAL -> shadowDecalMesh
    }

    fun allEntries(): Map<PropMeshKind, MeshData> = PropMeshKind.entries.associateWith { meshFor(it) }
}

class PropMeshLibrary(
    val cpuMeshes: PropCpuMeshLibrary = PropCpuMeshLibrary(),
) {
    val doorPanelMesh: GpuMesh = cpuMeshes.doorPanelMesh.toGpuMesh()
    val doorKnobMesh: GpuMesh = cpuMeshes.doorKnobMesh.toGpuMesh()
    val keyMesh: GpuMesh = cpuMeshes.keyMesh.toGpuMesh()
    val noteMesh: GpuMesh = cpuMeshes.noteMesh.toGpuMesh()
    val flashlightPickupMesh: GpuMesh = cpuMeshes.flashlightPickupMesh.toGpuMesh()
    val medkitMesh: GpuMesh = cpuMeshes.medkitMesh.toGpuMesh()
    val crowbarMesh: GpuMesh = cpuMeshes.crowbarMesh.toGpuMesh()
    val genericToolMesh: GpuMesh = cpuMeshes.genericToolMesh.toGpuMesh()
    val fogPlaneMesh: GpuMesh = cpuMeshes.fogPlaneMesh.toGpuMesh()
    val shadowDecalMesh: GpuMesh = cpuMeshes.shadowDecalMesh.toGpuMesh()

    fun release() {
        listOf(
            doorPanelMesh, doorKnobMesh, keyMesh, noteMesh,
            flashlightPickupMesh, medkitMesh, crowbarMesh,
            genericToolMesh, fogPlaneMesh, shadowDecalMesh,
        ).forEach { it.release() }
    }
}

object SceneBuilder3D {

    fun buildStaticGpuBatches(): List<GpuSceneBatch> =
        buildStaticCpuBatches().map { b ->
            GpuSceneBatch(
                texture = b.texture,
                gpuMesh = b.meshData.toGpuMesh(),
                tintR = b.tintR,
                tintG = b.tintG,
                tintB = b.tintB,
                specular = b.specular,
                emissive = b.emissive,
                transparent = b.transparent,
                doubleSided = b.doubleSided,
            )
        }

    fun buildStaticCpuBatches(): List<CpuMeshBatch> {
        val batches = mutableListOf<CpuMeshBatch>()

        val road = MeshBuilder3D()
        val yellowLines = MeshBuilder3D()
        val ground = MeshBuilder3D()
        val bark = MeshBuilder3D()
        val foliage = MeshBuilder3D()
        val walls = MeshBuilder3D()
        val roof = MeshBuilder3D()
        val wood = MeshBuilder3D()
        val oak = MeshBuilder3D()
        val bedding = MeshBuilder3D()
        val stone = MeshBuilder3D()
        val windows = MeshBuilder3D()
        val metal = MeshBuilder3D()
        val lanterns = MeshBuilder3D()
        val portraits = MeshBuilder3D()
        val blood = MeshBuilder3D()
        val shadows = MeshBuilder3D()

        // =========================================================================
        // 1. ISOLATED NIGHT ROAD, SHOULDERS, GUARDRAILS & FOREST TERRAIN
        // =========================================================================
        ground.addTexturedBox(-34f, -0.22f, 9.6f, 34f, -0.01f, 39f, uvTileMeters = 2.8f)
        // Raised grassy embankments beside the road
        ground.addTexturedBox(-18f, -0.05f, 12.2f, -3.4f, 0.14f, 38f, uvTileMeters = 2.5f)
        ground.addTexturedBox(3.4f, -0.05f, 12.2f, 18f, 0.14f, 38f, uvTileMeters = 2.5f)

        // Wet cracked asphalt road surface
        road.addTexturedBox(-2.65f, -0.01f, 11.7f, 2.65f, 0.025f, 36.5f, uvTileMeters = 2.2f)
        // Gravel/asphalt shoulder curbs
        road.addTexturedBox(-3.35f, -0.02f, 11.7f, -2.65f, 0.012f, 36.5f, uvTileMeters = 1.8f)
        road.addTexturedBox(2.65f, -0.02f, 11.7f, 3.35f, 0.012f, 36.5f, uvTileMeters = 1.8f)

        // Faded yellow center road lines
        for (z in 13..34 step 4) {
            yellowLines.addTexturedBox(-0.07f, 0.027f, z.toFloat(), 0.07f, 0.032f, z + 1.85f, uvTileMeters = 1.0f)
        }

        // Wooden roadside guardrail posts & rails
        for (z in 14..32 step 3) {
            for (gx in listOf(-3.45f, 3.45f)) {
                bark.addTexturedBox(gx - 0.07f, 0f, z - 0.07f, gx + 0.07f, 0.72f, z + 0.07f, uvTileMeters = 0.8f)
            }
        }
        bark.addTexturedBox(-3.48f, 0.50f, 13.8f, -3.40f, 0.60f, 32.5f, uvTileMeters = 1.5f)
        bark.addTexturedBox(3.40f, 0.50f, 13.8f, 3.48f, 0.60f, 32.5f, uvTileMeters = 1.5f)

        // Roadside Streetlamp (iron curved post + glowing warm-cold lamp head)
        metal.addTaperedLimbInWorld(cx = -3.25f, cy = 0f, cz = 22.0f, height = 3.85f, rBot = 0.08f, rTop = 0.055f)
        metal.addTexturedBox(-3.25f, 3.72f, 21.94f, -2.35f, 3.86f, 22.06f, uvTileMeters = 0.6f)
        metal.addTexturedBox(-2.52f, 3.55f, 21.84f, -2.20f, 3.74f, 22.16f, uvTileMeters = 0.4f)
        lanterns.addEllipsoid(cx = -2.36f, cy = 3.46f, cz = 22.0f, rx = 0.13f, ry = 0.16f, rz = 0.13f, rings = 8, sectors = 12)

        // =========================================================================
        // 2. DETAILED 3D PINE TREES, BRANCHES, BUSHES & ROCKS
        // =========================================================================
        val trees = listOf(
            Triple(-5.0f, 27.5f, 1.05f), Triple(5.2f, 26.2f, 1.15f),
            Triple(-6.1f, 22.2f, 0.95f), Triple(5.7f, 21.0f, 1.08f),
            Triple(-4.9f, 17.0f, 1.00f), Triple(6.0f, 16.0f, 1.12f),
            Triple(-7.2f, 13.0f, 1.18f), Triple(7.1f, 13.4f, 0.98f),
            Triple(-9.4f, 25.0f, 1.25f), Triple(9.1f, 23.5f, 1.22f),
            Triple(-9.8f, 18.2f, 1.10f), Triple(9.5f, 18.6f, 1.18f),
            Triple(-12.5f, 14.5f, 1.30f), Triple(12.2f, 14.2f, 1.25f),
            Triple(-6.5f, 31.0f, 1.15f), Triple(6.8f, 30.5f, 1.10f),
        )
        for ((tx, tz, scale) in trees) {
            // Tapered trunk with root flare
            bark.addTaperedLimbInWorld(cx = tx, cy = 0f, cz = tz, height = 3.2f * scale, rBot = 0.25f * scale, rTop = 0.09f * scale)
            // Multi-tiered layered conifer canopies
            foliage.addPineConeLayer(tx, 1.85f * scale, tz, radius = 1.65f * scale, height = 2.05f * scale, sectors = 12)
            foliage.addPineConeLayer(tx, 3.05f * scale, tz, radius = 1.30f * scale, height = 1.85f * scale, sectors = 12)
            foliage.addPineConeLayer(tx, 4.15f * scale, tz, radius = 0.92f * scale, height = 1.65f * scale, sectors = 10)
            foliage.addPineConeLayer(tx, 5.10f * scale, tz, radius = 0.52f * scale, height = 1.25f * scale, sectors = 8)
            // Ground contact shadow beneath tree
            shadows.addHorizontalQuad(tx, 0.02f, tz, 1.55f * scale, 1.55f * scale)
        }

        // Forest undergrowth bushes & moss rocks along ditches
        val bushes = listOf(
            -4.1f to 24.5f, 4.2f to 24.0f, -4.3f to 19.2f, 4.4f to 18.4f,
            -4.0f to 14.5f, 4.1f to 14.6f, -8.2f to 12.2f, 8.4f to 12.1f,
            -5.6f to 11.4f, 5.6f to 11.4f,
        )
        for ((bx, bz) in bushes) {
            foliage.addEllipsoid(cx = bx, cy = 0.34f, cz = bz, rx = 0.65f, ry = 0.44f, rz = 0.62f, rings = 7, sectors = 10)
            foliage.addEllipsoid(cx = bx + 0.35f, cy = 0.26f, cz = bz + 0.25f, rx = 0.46f, ry = 0.32f, rz = 0.44f, rings = 6, sectors = 8)
            shadows.addHorizontalQuad(bx, 0.02f, bz, 0.78f, 0.72f)
        }
        val rocks = listOf(-3.7f to 28.5f, 3.8f to 28.2f, -3.9f to 15.8f, 3.9f to 16.5f)
        for ((rx, rz) in rocks) {
            stone.addEllipsoid(cx = rx, cy = 0.18f, cz = rz, rx = 0.45f, ry = 0.24f, rz = 0.38f, rings = 6, sectors = 8)
        }

        // =========================================================================
        // 3. DETAILED VICTORIAN HORROR HOUSE EXTERIOR (PORCH, COLUMNS, ROOF, WINDOWS)
        // =========================================================================
        // Porch wooden deck & 3 descending steps
        wood.addTexturedBox(-2.85f, 0.06f, 10.0f, 2.85f, 0.18f, 11.85f, uvTileMeters = 1.4f)
        wood.addTexturedBox(-1.65f, 0.02f, 11.85f, 1.65f, 0.12f, 12.22f, uvTileMeters = 1.2f)
        wood.addTexturedBox(-1.65f, 0.00f, 12.22f, 1.65f, 0.06f, 12.56f, uvTileMeters = 1.2f)

        // Turned cylindrical porch columns + railings + balusters
        for (px in listOf(-2.55f, -1.55f, 1.55f, 2.55f)) {
            wood.addTaperedLimbInWorld(cx = px, cy = 0.18f, cz = 11.65f, height = 2.62f, rBot = 0.11f, rTop = 0.085f)
            wood.addTexturedBox(px - 0.14f, 0.18f, 11.51f, px + 0.14f, 0.34f, 11.79f, uvTileMeters = 0.6f)
            wood.addTexturedBox(px - 0.13f, 2.66f, 11.52f, px + 0.13f, 2.80f, 11.78f, uvTileMeters = 0.6f)
        }
        // Porch left & right handrails
        wood.addTexturedBox(-2.60f, 0.92f, 11.58f, -1.55f, 1.00f, 11.72f, uvTileMeters = 0.8f)
        wood.addTexturedBox(1.55f, 0.92f, 11.58f, 2.60f, 1.00f, 11.72f, uvTileMeters = 0.8f)
        for (bx in listOf(-2.30f, -2.05f, -1.80f, 1.80f, 2.05f, 2.30f)) {
            wood.addTaperedLimbInWorld(cx = bx, cy = 0.18f, cz = 11.65f, height = 0.74f, rBot = 0.032f, rTop = 0.028f)
        }

        // Porch roof eave & hanging iron lantern
        roof.addGableRoof(x0 = -2.95f, x1 = 2.95f, z0 = 9.9f, z1 = 12.1f, eaveY = 2.80f, peakY = 3.65f, uvTile = 1.4f)
        metal.addTaperedLimbInWorld(cx = 0f, cy = 2.55f, cz = 10.65f, height = 0.30f, rBot = 0.02f, rTop = 0.02f)
        metal.addTexturedBox(-0.14f, 2.34f, 10.51f, 0.14f, 2.58f, 10.79f, uvTileMeters = 0.4f)
        lanterns.addEllipsoid(cx = 0f, cy = 2.45f, cz = 10.65f, rx = 0.10f, ry = 0.12f, rz = 0.10f, rings = 8, sectors = 12)

        // Main House Pitched Gable Roof + Attic Dormer
        val h = WorldMap3D.WALL_H
        roof.addGableRoof(x0 = -12.65f, x1 = 12.65f, z0 = -8.6f, z1 = 10.55f, eaveY = h + 0.18f, peakY = h + 2.55f, uvTile = 2.0f)
        // Attic glowing window in south gable
        windows.addTexturedBox(-0.85f, h + 0.65f, 10.10f, 0.85f, h + 1.85f, 10.24f, uvTileMeters = 1.7f)

        // Framed Glowing Victorian Windows on House Facade & Side Walls (warm interior glow seen from road!)
        val southWinXs = listOf(-8.5f, -4.8f, 4.8f, 8.5f)
        for (wx in southWinXs) {
            windows.addTexturedBox(wx - 0.80f, 1.05f, 10.12f, wx + 0.80f, 2.45f, 10.22f, uvTileMeters = 1.6f)
            // Wooden window sill & shutters
            wood.addTexturedBox(wx - 0.92f, 0.96f, 10.12f, wx + 0.92f, 1.05f, 10.28f, uvTileMeters = 0.8f)
            wood.addTexturedBox(wx - 1.15f, 1.05f, 10.13f, wx - 0.82f, 2.45f, 10.21f, uvTileMeters = 0.8f)
            wood.addTexturedBox(wx + 0.82f, 1.05f, 10.13f, wx + 1.15f, 2.45f, 10.21f, uvTileMeters = 0.8f)
        }

        // Front Door Architrave / Molded Frame
        wood.addTexturedBox(-1.02f, 0f, 9.82f, -0.85f, 2.62f, 10.22f, uvTileMeters = 0.8f)
        wood.addTexturedBox(0.85f, 0f, 9.82f, 1.02f, 2.62f, 10.22f, uvTileMeters = 0.8f)
        wood.addTexturedBox(-1.05f, 2.54f, 9.80f, 1.05f, 2.74f, 10.24f, uvTileMeters = 0.8f)

        // =========================================================================
        // 4. INTERIOR FLOORS, CEILINGS, WALLS, CELLAR & TWO NEW NORTH WINGS
        // =========================================================================
        // Main floor & North wings floor (X: -12.2..12.2, Z: -16.0..10.0)
        wood.addTexturedBox(-12.2f, -0.05f, -8.0f, 12.2f, 0.02f, 10.0f, uvTileMeters = 1.5f)
        wood.addTexturedBox(-12.2f, -0.05f, -16.0f, -6.0f, 0.02f, -8.0f, uvTileMeters = 1.5f)
        wood.addTexturedBox(6.0f, -0.05f, -16.0f, 12.2f, 0.02f, -8.0f, uvTileMeters = 1.5f)

        // Main ceiling + raised ceiling over Staircase Hall (Y = h + 1.25f) so upper landing has headroom!
        walls.addTexturedBox(-12.2f, h, -8.2f, 12.2f, h + 0.22f, 10.2f, uvTileMeters = 2.0f)
        walls.addTexturedBox(-12.2f, h, -16.2f, -5.9f, h + 0.22f, -8.0f, uvTileMeters = 2.0f)
        walls.addTexturedBox(5.9f, h + 1.25f, -16.2f, 12.2f, h + 1.48f, -8.0f, uvTileMeters = 2.0f)
        // Roof over north wings
        roof.addGableRoof(x0 = -12.4f, x1 = 12.4f, z0 = -16.3f, z1 = -8.4f, eaveY = h + 1.48f, peakY = h + 3.1f, uvTile = 2.0f)

        // Exposed carved wooden ceiling beams in hallway, living room & bedrooms
        for (bz in listOf(-6.0f, -2.0f, 2.0f, 6.0f, 9.0f)) {
            oak.addTexturedBox(-12.0f, h - 0.16f, bz - 0.12f, 12.0f, h, bz + 0.12f, uvTileMeters = 1.4f)
        }

        // Cellar stone floor, ceiling & masonry walls
        stone.addTexturedBox(-6.2f, -0.05f, -16.2f, 6.2f, 0.02f, -8.0f, uvTileMeters = 1.6f)
        stone.addTexturedBox(-6.2f, h, -16.2f, 6.2f, h + 0.22f, -8.0f, uvTileMeters = 1.8f)
        stone.addTexturedBox(-6.2f, 0f, -16.2f, -5.9f, h, -8.0f, uvTileMeters = 1.6f)
        stone.addTexturedBox(5.9f, 0f, -16.2f, 6.2f, h, -8.0f, uvTileMeters = 1.6f)
        stone.addTexturedBox(-6.2f, 0f, -16.2f, 6.2f, h, -15.9f, uvTileMeters = 1.6f)

        // House Exterior & Interior Walls (preserving exact doorway openings!)
        walls.addTexturedBox(-12.2f, 0f, 9.85f, -0.85f, h, 10.15f, uvTileMeters = 2.0f)
        walls.addTexturedBox(0.85f, 0f, 9.85f, 12.2f, h, 10.15f, uvTileMeters = 2.0f)
        walls.addTexturedBox(-0.85f, 2.55f, 9.85f, 0.85f, h, 10.15f, uvTileMeters = 2.0f)
        walls.addTexturedBox(-12.2f, 0f, -8.15f, -11.9f, h, 10.15f, uvTileMeters = 2.0f)
        walls.addTexturedBox(11.9f, 0f, -8.15f, 12.2f, h, 10.15f, uvTileMeters = 2.0f)

        // North Wing exterior walls (Z: -16.2..-8.0)
        walls.addTexturedBox(-12.2f, 0f, -16.2f, -11.9f, h, -8.0f, uvTileMeters = 2.0f)
        walls.addTexturedBox(11.9f, 0f, -16.2f, 12.2f, h + 1.25f, -8.0f, uvTileMeters = 2.0f)
        walls.addTexturedBox(-12.2f, 0f, -16.2f, -5.9f, h, -15.9f, uvTileMeters = 2.0f)
        walls.addTexturedBox(5.9f, 0f, -16.2f, 12.2f, h + 1.25f, -15.9f, uvTileMeters = 2.0f)
        walls.addTexturedBox(5.9f, 0f, -16.2f, 6.18f, h + 1.25f, -8.0f, uvTileMeters = 2.0f)

        // North wall at Z = -8 (doorways: NW bedroom [-9.2,-7.75], Cellar [-0.75,0.75], Stair hall [6.85,8.30])
        walls.addTexturedBox(-12.2f, 0f, -8.15f, -9.2f, h, -7.85f, uvTileMeters = 2.0f)
        walls.addTexturedBox(-9.2f, 2.45f, -8.15f, -7.75f, h, -7.85f, uvTileMeters = 2.0f)
        walls.addTexturedBox(-7.75f, 0f, -8.15f, -0.75f, h, -7.85f, uvTileMeters = 2.0f)
        walls.addTexturedBox(-0.75f, 2.45f, -8.15f, 0.75f, h, -7.85f, uvTileMeters = 2.0f)
        walls.addTexturedBox(0.75f, 0f, -8.15f, 6.85f, h, -7.85f, uvTileMeters = 2.0f)
        walls.addTexturedBox(6.85f, 2.45f, -8.15f, 8.30f, h + 1.25f, -7.85f, uvTileMeters = 2.0f)
        walls.addTexturedBox(8.30f, 0f, -8.15f, 12.2f, h + 1.25f, -7.85f, uvTileMeters = 2.0f)

        // Hallway West & East partitions (X = -3 and X = +3)
        walls.addTexturedBox(-3.12f, 0f, 7.55f, -2.88f, h, 10.0f, uvTileMeters = 1.8f)
        walls.addTexturedBox(-3.12f, 2.45f, 6.1f, -2.88f, h, 7.55f, uvTileMeters = 1.8f)
        walls.addTexturedBox(-3.12f, 0f, -0.75f, -2.88f, h, 6.1f, uvTileMeters = 1.8f)
        walls.addTexturedBox(-3.12f, 2.45f, -2.2f, -2.88f, h, -0.75f, uvTileMeters = 1.8f)
        walls.addTexturedBox(-3.12f, 0f, -8.0f, -2.88f, h, -2.2f, uvTileMeters = 1.8f)

        walls.addTexturedBox(2.88f, 0f, 7.55f, 3.12f, h, 10.0f, uvTileMeters = 1.8f)
        walls.addTexturedBox(2.88f, 2.45f, 6.1f, 3.12f, h, 7.55f, uvTileMeters = 1.8f)
        walls.addTexturedBox(2.88f, 0f, -0.75f, 3.12f, h, 6.1f, uvTileMeters = 1.8f)
        walls.addTexturedBox(2.88f, 2.45f, -2.2f, 3.12f, h, -0.75f, uvTileMeters = 1.8f)
        walls.addTexturedBox(2.88f, 0f, -8.0f, 3.12f, h, -2.2f, uvTileMeters = 1.8f)

        // East/West Room Dividers at Z = 2
        walls.addTexturedBox(-12.0f, 0f, 1.88f, -3.0f, h, 2.12f, uvTileMeters = 1.8f)
        walls.addTexturedBox(3.0f, 0f, 1.88f, 12.0f, h, 2.12f, uvTileMeters = 1.8f)

        // Moonlit interior/side windows on East & West outer walls (wind & cold light!)
        for (wz in listOf(6.0f, -4.5f, -12.0f)) {
            windows.addTexturedBox(-11.96f, 1.05f, wz - 0.72f, -11.86f, 2.40f, wz + 0.72f, uvTileMeters = 1.4f)
            oak.addTexturedBox(-11.94f, 0.98f, wz - 0.80f, -11.80f, 1.06f, wz + 0.80f, uvTileMeters = 0.8f)
            windows.addTexturedBox(11.86f, 1.05f, wz - 0.72f, 11.96f, 2.40f, wz + 0.72f, uvTileMeters = 1.4f)
            oak.addTexturedBox(11.80f, 0.98f, wz - 0.80f, 11.94f, 1.06f, wz + 0.80f, uvTileMeters = 0.8f)
        }

        // Architectural wooden baseboards (rodapes) & trim along hallway & bedrooms
        oak.addTexturedBox(-2.88f, 0f, -7.95f, -2.82f, 0.16f, 9.9f, uvTileMeters = 1.2f)
        oak.addTexturedBox(2.82f, 0f, -7.95f, 2.88f, 0.16f, 9.9f, uvTileMeters = 1.2f)
        oak.addTexturedBox(3.12f, 0f, -7.85f, 11.88f, 0.16f, -7.78f, uvTileMeters = 1.2f)
        oak.addTexturedBox(3.12f, 0f, 1.82f, 11.88f, 0.16f, 1.88f, uvTileMeters = 1.2f)

        // =========================================================================
        // 5. INTERIOR FURNITURE, RUGS, BEDS ON LEGS, STAIRCASE, PORTRAITS & PROPS
        // =========================================================================
        // Foyer hallway ornate crimson runner rug
        bedding.addTexturedBox(-1.12f, 0.02f, -7.2f, 1.12f, 0.042f, 8.9f, uvTileMeters = 1.2f)
        // Wainscot wall pillars & hallway arch beams
        for (az in listOf(4.2f, 0.6f, -3.6f, -6.5f)) {
            oak.addTexturedBox(-2.88f, 0f, az - 0.14f, -2.66f, h, az + 0.14f, uvTileMeters = 1.2f)
            oak.addTexturedBox(2.66f, 0f, az - 0.14f, 2.88f, h, az + 0.14f, uvTileMeters = 1.2f)
            oak.addTexturedBox(-2.88f, h - 0.24f, az - 0.14f, 2.88f, h, az + 0.14f, uvTileMeters = 1.2f)
        }
        // Eerie antique Victorian portraits hanging on hallway & bedroom walls
        for (pz in listOf(3.0f, 1.2f, -4.8f)) {
            portraits.addTexturedBox(-2.87f, 1.32f, pz - 0.48f, -2.81f, 2.38f, pz + 0.48f, uvTileMeters = 0.96f)
            portraits.addTexturedBox(2.81f, 1.32f, pz - 0.48f, 2.87f, 2.38f, pz + 0.48f, uvTileMeters = 0.96f)
        }
        portraits.addTexturedBox(5.2f, 1.35f, -7.83f, 6.2f, 2.38f, -7.77f, uvTileMeters = 1.0f)
        portraits.addTexturedBox(11.80f, 2.45f, -15.3f, 11.86f, 3.55f, -14.2f, uvTileMeters = 1.0f)

        // Tall Victorian Grandfather Clock in the hallway corner (X=-2.55, Z=2.3)
        oak.addTexturedBox(-2.84f, 0f, 1.95f, -2.26f, 2.25f, 2.65f, uvTileMeters = 1.0f)
        oak.addTexturedBox(-2.86f, 2.25f, 1.92f, -2.22f, 2.38f, 2.68f, uvTileMeters = 0.8f) // Clock crown
        metal.addEllipsoid(-2.24f, 1.85f, 2.30f, rx = 0.03f, ry = 0.22f, rz = 0.22f, rings = 8, sectors = 12)
        shadows.addHorizontalQuad(-2.55f, 0.025f, 2.30f, 0.42f, 0.48f)

        // Foyer hanging chandelier & sconce fixtures
        metal.addTaperedLimbInWorld(0f, 2.88f, 7.0f, 0.34f, 0.03f, 0.03f)
        lanterns.addEllipsoid(0f, 2.82f, 7.0f, rx = 0.18f, ry = 0.08f, rz = 0.18f, rings = 6, sectors = 12)
        lanterns.addEllipsoid(0f, 2.45f, -1.6f, rx = 0.10f, ry = 0.11f, rz = 0.10f, rings = 6, sectors = 10)
        lanterns.addEllipsoid(0f, 2.45f, -6.2f, rx = 0.09f, ry = 0.10f, rz = 0.09f, rings = 6, sectors = 10)
        metal.addTexturedBox(-2.86f, 2.02f, -1.55f, -2.62f, 2.18f, -1.42f, uvTileMeters = 0.4f)
        metal.addTexturedBox(2.62f, 2.02f, -1.55f, 2.86f, 2.18f, -1.42f, uvTileMeters = 0.4f)

        // Living room (X < -3, Z = 2..10): 4-legged tables, vintage sofa, standing lamp & fireplace
        addDetailedTable(oak, shadows, -7.0f, -5.4f, 7.8f, 9.0f, 0.78f)
        addDetailedTable(oak, shadows, -5.0f, -4.1f, 4.2f, 5.1f, 0.78f)
        addDetailedSofa(oak, bedding, shadows, -10.3f, -7.6f, 3.2f, 4.4f)
        // Tall floor lamp near living room arch
        oak.addTaperedLimbInWorld(-4.6f, 0f, 7.8f, 2.0f, 0.07f, 0.035f)
        bedding.addTaperedLimbInWorld(-4.6f, 1.95f, 7.8f, 0.32f, 0.24f, 0.14f)
        lanterns.addEllipsoid(-4.6f, 2.08f, 7.8f, rx = 0.09f, ry = 0.10f, rz = 0.09f, rings = 6, sectors = 10)
        // Stone fireplace
        stone.addTexturedBox(-11.9f, 0f, 5.0f, -10.8f, 2.25f, 7.0f, uvTileMeters = 1.1f)
        oak.addTexturedBox(-11.9f, 1.35f, 4.85f, -10.65f, 1.48f, 7.15f, uvTileMeters = 0.9f)
        lanterns.addEllipsoid(-11.15f, 0.30f, 6.0f, rx = 0.24f, ry = 0.18f, rz = 0.42f, rings = 6, sectors = 10)

        // Kitchen (X > 3, Z = 2..10): Counters + Butcher Table + Old Dining Chairs
        oak.addTexturedBox(8.8f, 0f, 7.8f, 11.4f, 0.88f, 9.4f, uvTileMeters = 1.0f)
        oak.addTexturedBox(9.6f, 0f, 2.6f, 11.4f, 0.88f, 4.0f, uvTileMeters = 1.0f)
        addDetailedTable(oak, shadows, 6.2f, 8.4f, 4.4f, 6.2f, 0.84f)
        addDetailedChair(oak, shadows, 5.75f, 5.3f, faceDirX = 1f)
        addDetailedChair(oak, shadows, 8.85f, 5.3f, faceDirX = -1f)
        lanterns.addEllipsoid(7.5f, 2.86f, 6.0f, rx = 0.10f, ry = 0.12f, rz = 0.10f, rings = 6, sectors = 10)

        // Study (X < -3, Z = -8..2): Desk, desk lamp, armchair, table & tall bookshelf
        addDetailedTable(oak, shadows, -10.6f, -8.6f, -6.2f, -4.8f, 0.82f)
        addDetailedChair(oak, shadows, -9.6f, -4.25f, faceDirX = 0f, faceDirZ = -1f)
        lanterns.addEllipsoid(-9.2f, 1.05f, -5.4f, rx = 0.10f, ry = 0.12f, rz = 0.10f, rings = 6, sectors = 8)
        addDetailedTable(oak, shadows, -7.8f, -6.6f, -3.9f, -2.9f, 0.82f)
        oak.addTexturedBox(-11.8f, 0f, -7.6f, -11.0f, 2.45f, -3.0f, uvTileMeters = 1.0f)
        for (sy in listOf(0.55f, 1.10f, 1.65f, 2.15f)) {
            oak.addTexturedBox(-11.05f, sy, -7.5f, -10.90f, sy + 0.06f, -3.1f, uvTileMeters = 0.8f)
        }

        // AWAKENING BEDROOM (X: 3..12, Z: -8..2):
        // Raised Carved Bed with 4 legs & open under-bed clearance + Rug + Nightstand + Lamp + Dresser + Tall Wardrobe
        bedding.addTexturedBox(5.2f, 0.022f, -6.9f, 9.8f, 0.038f, -2.5f, uvTileMeters = 1.3f) // Bedroom rug
        addDetailedBed(
            oak = oak,
            bedding = bedding,
            shadows = shadows,
            x0 = 7.4f, x1 = 10.6f,
            z0 = -6.9f, z1 = -4.9f,
            headAtEast = true,
        )
        // Nightstand next to bed (X: 9.4..10.4, Z: -4.6..-3.7) with glowing antique shaded lamp!
        addDetailedNightstand(oak, metal, lanterns, shadows, 9.4f, 10.4f, -4.6f, -3.7f)
        // Dresser/table holding the fuse item (X: 4.2..5.8, Z: -7.8..-6.8)
        addDetailedTable(oak, shadows, 4.2f, 5.8f, -7.8f, -6.8f, 0.84f)
        // Tall paneled Wardrobe (X: 10.3..11.7, Z: -2.9..-1.3)
        addDetailedWardrobe(oak, metal, shadows, 10.3f, 11.7f, -2.9f, -1.3f)
        // Bedroom ceiling light fixture
        lanterns.addEllipsoid(5.4f, 2.68f, -3.5f, rx = 0.11f, ry = 0.08f, rz = 0.11f, rings = 6, sectors = 10)

        // NORTH-WEST MASTER BEDROOM (X: -12..-6, Z: -16..-8):
        bedding.addTexturedBox(-11.2f, 0.022f, -14.8f, -7.6f, 0.038f, -10.6f, uvTileMeters = 1.3f)
        addDetailedBed(
            oak = oak,
            bedding = bedding,
            shadows = shadows,
            x0 = -11.4f, x1 = -8.4f,
            z0 = -15.2f, z1 = -13.2f,
            headAtEast = false,
        )
        addDetailedWardrobe(oak, metal, shadows, -7.4f, -6.2f, -15.4f, -13.8f)
        addDetailedChair(oak, shadows, -10.4f, -10.4f, faceDirX = 1f)
        lanterns.addEllipsoid(-9.0f, 2.55f, -11.5f, rx = 0.11f, ry = 0.09f, rz = 0.11f, rings = 6, sectors = 10)

        // NORTH-EAST GRAND WOODEN STAIRCASE HALL (X: 6..12, Z: -16..-8):
        addWoodenStaircaseHall(oak, wood, lanterns, metal, shadows)

        // Cellar (Z = -16..-8): Wooden crates, table, eerie red bulb
        addDetailedTable(oak, shadows, -0.8f, 0.8f, -14.8f, -13.6f, 0.82f)
        oak.addTexturedBox(-4.6f, 0f, -14.8f, -2.8f, 1.15f, -13.0f, uvTileMeters = 0.9f)
        shadows.addHorizontalQuad(-3.7f, 0.025f, -13.9f, 1.1f, 1.1f)
        lanterns.addEllipsoid(0f, 2.76f, -12.0f, rx = 0.09f, ry = 0.12f, rz = 0.09f, rings = 6, sectors = 10)

        // Fuse box on foyer south wall
        metal.addTexturedBox(1.9f, 1.15f, 9.72f, 2.5f, 1.75f, 9.86f, uvTileMeters = 0.5f)
        lanterns.addEllipsoid(2.2f, 1.62f, 9.71f, rx = 0.03f, ry = 0.03f, rz = 0.02f, rings = 6, sectors = 8)

        // =========================================================================
        // 6. ORGANIC BLOOD POOLS & TRAILS
        // =========================================================================
        blood.addHorizontalQuad(7.05f, 0.846f, 5.20f, 0.55f, 0.50f)
        blood.addHorizontalQuad(7.60f, 0.026f, 5.40f, 0.72f, 0.65f)
        blood.addHorizontalQuad(-0.05f, 0.046f, -5.80f, 0.55f, 0.70f)
        blood.addHorizontalQuad(0.75f, 0.026f, -10.20f, 0.68f, 0.65f)
        blood.addHorizontalQuad(5.55f, 0.026f, -3.95f, 0.62f, 0.52f)

        fun addBatch(
            tex: TextureKind,
            b: MeshBuilder3D,
            r: Float = 1f, g: Float = 1f, bl: Float = 1f,
            spec: Float = 0.12f,
            emit: Float = 0f,
            transp: Boolean = false,
            dbl: Boolean = false,
        ) {
            if (b.currentVertexCount > 0) {
                batches.add(CpuMeshBatch(tex, b.build(), r, g, bl, spec, emit, transp, dbl))
            }
        }

        addBatch(TextureKind.FOREST_GROUND, ground, spec = 0.04f)
        addBatch(TextureKind.ASPHALT_ROAD, road, spec = 0.34f)
        addBatch(TextureKind.ASPHALT_ROAD, yellowLines, r = 1.6f, g = 1.35f, bl = 0.45f, spec = 0.10f)
        addBatch(TextureKind.TREE_BARK, bark, spec = 0.05f)
        addBatch(TextureKind.PINE_FOLIAGE, foliage, spec = 0.05f, dbl = true)
        addBatch(TextureKind.ROOF_SHINGLES, roof, spec = 0.10f, dbl = true)
        addBatch(TextureKind.VICTORIAN_WALLPAPER, walls, spec = 0.07f)
        addBatch(TextureKind.WOOD_FLOOR, wood, spec = 0.16f)
        addBatch(TextureKind.CARVED_OAK, oak, spec = 0.22f)
        addBatch(TextureKind.BED_FABRIC, bedding, spec = 0.06f)
        addBatch(TextureKind.STONE_CELLAR, stone, spec = 0.15f)
        addBatch(TextureKind.WINDOW_GLOW, windows, spec = 0.35f, emit = 0.90f)
        addBatch(TextureKind.METAL_PROP, metal, spec = 0.55f)
        addBatch(TextureKind.LANTERN_CORE, lanterns, spec = 0.8f, emit = 1.15f)
        addBatch(TextureKind.WALL_PORTRAIT, portraits, spec = 0.18f)
        addBatch(TextureKind.BLOOD_SPLATTER, blood, spec = 0.45f, transp = true, dbl = true)
        addBatch(TextureKind.SOFT_SHADOW, shadows, spec = 0.0f, transp = true, dbl = true)

        return batches
    }

    private fun addDetailedBed(
        oak: MeshBuilder3D,
        bedding: MeshBuilder3D,
        shadows: MeshBuilder3D,
        x0: Float, x1: Float,
        z0: Float, z1: Float,
        headAtEast: Boolean,
    ) {
        // 4 turned wooden bed posts raising bed frame 0.42m off the floor (so hiding underneath shows legs & room!)
        val legInset = 0.12f
        val railBottom = 0.42f
        val railTop = 0.54f
        for (lx in listOf(x0 + legInset, x1 - legInset)) {
            for (lz in listOf(z0 + legInset, z1 - legInset)) {
                oak.addTaperedLimbInWorld(lx, 0f, lz, railBottom, 0.065f, 0.055f, sectors = 10)
            }
        }
        // Wooden frame slats & side rails
        oak.addTexturedBox(x0, railBottom, z0, x1, railTop, z1, uvTileMeters = 0.9f)
        // Tall sculpted headboard & low footboard
        if (headAtEast) {
            oak.addTexturedBox(x1 - 0.14f, railBottom, z0, x1, 1.38f, z1, uvTileMeters = 0.8f)
            oak.addTaperedLimbInWorld(x1 - 0.07f, 0f, z0 + 0.08f, 1.48f, 0.06f, 0.05f, sectors = 10)
            oak.addTaperedLimbInWorld(x1 - 0.07f, 0f, z1 - 0.08f, 1.48f, 0.06f, 0.05f, sectors = 10)
            oak.addTexturedBox(x0, railBottom, z0, x0 + 0.10f, 0.78f, z1, uvTileMeters = 0.8f)
        } else {
            oak.addTexturedBox(x0, railBottom, z0, x1, 1.38f, z0 + 0.14f, uvTileMeters = 0.8f)
            oak.addTexturedBox(x0, railBottom, z1 - 0.10f, x1, 0.78f, z1, uvTileMeters = 0.8f)
        }
        // Soft mattress & quilted antique blanket
        bedding.addTexturedBox(x0 + 0.12f, railTop, z0 + 0.08f, x1 - 0.14f, 0.69f, z1 - 0.08f, uvTileMeters = 1.0f)
        // Sculpted pillows near headboard
        val px = if (headAtEast) x1 - 0.44f else (x0 + x1) * 0.5f
        val pz1 = if (headAtEast) z0 + 0.52f else z0 + 0.42f
        val pz2 = if (headAtEast) z1 - 0.52f else z0 + 0.42f
        bedding.addEllipsoid(cx = px, cy = 0.74f, cz = pz1, rx = 0.22f, ry = 0.09f, rz = 0.34f, rings = 8, sectors = 12)
        if (headAtEast) {
            bedding.addEllipsoid(cx = px, cy = 0.74f, cz = pz2, rx = 0.22f, ry = 0.09f, rz = 0.34f, rings = 8, sectors = 12)
        }
        shadows.addHorizontalQuad((x0 + x1) * 0.5f, 0.024f, (z0 + z1) * 0.5f, (x1 - x0) * 0.52f, (z1 - z0) * 0.52f)
    }

    private fun addDetailedNightstand(
        oak: MeshBuilder3D,
        metal: MeshBuilder3D,
        lanterns: MeshBuilder3D,
        shadows: MeshBuilder3D,
        x0: Float, x1: Float, z0: Float, z1: Float,
    ) {
        // Legs + drawer cabinet body + top + antique shaded lamp
        for (lx in listOf(x0 + 0.08f, x1 - 0.08f)) {
            for (lz in listOf(z0 + 0.08f, z1 - 0.08f)) {
                oak.addTaperedLimbInWorld(lx, 0f, lz, 0.20f, 0.04f, 0.035f, sectors = 8)
            }
        }
        oak.addTexturedBox(x0, 0.20f, z0, x1, 0.74f, z1, uvTileMeters = 0.7f)
        oak.addTexturedBox(x0 - 0.03f, 0.74f, z0 - 0.03f, x1 + 0.03f, 0.79f, z1 + 0.03f, uvTileMeters = 0.7f)
        val cx = (x0 + x1) * 0.5f
        val cz = (z0 + z1) * 0.5f
        metal.addEllipsoid(x0 - 0.02f, 0.54f, cz, rx = 0.025f, ry = 0.025f, rz = 0.025f, rings = 6, sectors = 8)
        // Antique bedside lamp
        metal.addTaperedLimbInWorld(cx, 0.79f, cz, 0.28f, 0.06f, 0.028f, sectors = 10)
        lanterns.addTaperedLimbInWorld(cx, 1.02f, cz, 0.24f, 0.18f, 0.09f, sectors = 12)
        shadows.addHorizontalQuad(cx, 0.025f, cz, (x1 - x0) * 0.6f, (z1 - z0) * 0.6f)
    }

    private fun addDetailedWardrobe(
        oak: MeshBuilder3D,
        metal: MeshBuilder3D,
        shadows: MeshBuilder3D,
        x0: Float, x1: Float, z0: Float, z1: Float,
    ) {
        oak.addTexturedBox(x0, 0.12f, z0, x1, 2.26f, z1, uvTileMeters = 0.9f)
        oak.addTexturedBox(x0 - 0.05f, 2.26f, z0 - 0.05f, x1 + 0.05f, 2.38f, z1 + 0.05f, uvTileMeters = 0.8f)
        for (lx in listOf(x0 + 0.10f, x1 - 0.10f)) {
            for (lz in listOf(z0 + 0.10f, z1 - 0.10f)) {
                oak.addTaperedLimbInWorld(lx, 0f, lz, 0.12f, 0.05f, 0.04f, sectors = 8)
            }
        }
        val cz = (z0 + z1) * 0.5f
        metal.addEllipsoid(x0 - 0.03f, 1.18f, cz - 0.14f, rx = 0.025f, ry = 0.04f, rz = 0.025f, rings = 6, sectors = 8)
        metal.addEllipsoid(x0 - 0.03f, 1.18f, cz + 0.14f, rx = 0.025f, ry = 0.04f, rz = 0.025f, rings = 6, sectors = 8)
        shadows.addHorizontalQuad((x0 + x1) * 0.5f, 0.025f, cz, (x1 - x0) * 0.58f, (z1 - z0) * 0.58f)
    }

    private fun addDetailedSofa(
        oak: MeshBuilder3D,
        bedding: MeshBuilder3D,
        shadows: MeshBuilder3D,
        x0: Float, x1: Float, z0: Float, z1: Float,
    ) {
        for (lx in listOf(x0 + 0.12f, x1 - 0.12f)) {
            for (lz in listOf(z0 + 0.12f, z1 - 0.12f)) {
                oak.addTaperedLimbInWorld(lx, 0f, lz, 0.16f, 0.05f, 0.04f, sectors = 8)
            }
        }
        oak.addTexturedBox(x0, 0.16f, z0, x1, 0.36f, z1, uvTileMeters = 0.9f)
        bedding.addTexturedBox(x0 + 0.16f, 0.36f, z0 + 0.18f, x1 - 0.16f, 0.52f, z1 - 0.04f, uvTileMeters = 0.9f)
        bedding.addTexturedBox(x0 + 0.14f, 0.36f, z0, x1 - 0.14f, 1.02f, z0 + 0.24f, uvTileMeters = 0.9f)
        bedding.addTexturedBox(x0, 0.36f, z0, x0 + 0.20f, 0.72f, z1, uvTileMeters = 0.8f)
        bedding.addTexturedBox(x1 - 0.20f, 0.36f, z0, x1, 0.72f, z1, uvTileMeters = 0.8f)
        shadows.addHorizontalQuad((x0 + x1) * 0.5f, 0.025f, (z0 + z1) * 0.5f, (x1 - x0) * 0.56f, (z1 - z0) * 0.56f)
    }

    private fun addDetailedChair(
        oak: MeshBuilder3D,
        shadows: MeshBuilder3D,
        cx: Float, cz: Float,
        faceDirX: Float = 0f,
        faceDirZ: Float = 0f,
    ) {
        val hw = 0.24f
        val seatY = 0.46f
        for (lx in listOf(cx - hw + 0.04f, cx + hw - 0.04f)) {
            for (lz in listOf(cz - hw + 0.04f, cz + hw - 0.04f)) {
                oak.addTaperedLimbInWorld(lx, 0f, lz, seatY, 0.032f, 0.026f, sectors = 8)
            }
        }
        oak.addTexturedBox(cx - hw, seatY, cz - hw, cx + hw, seatY + 0.05f, cz + hw, uvTileMeters = 0.6f)
        // Backrest placed opposite facing direction
        when {
            faceDirX > 0.5f -> oak.addTexturedBox(cx - hw, seatY + 0.05f, cz - hw, cx - hw + 0.06f, 1.02f, cz + hw, uvTileMeters = 0.6f)
            faceDirX < -0.5f -> oak.addTexturedBox(cx + hw - 0.06f, seatY + 0.05f, cz - hw, cx + hw, 1.02f, cz + hw, uvTileMeters = 0.6f)
            faceDirZ < -0.5f -> oak.addTexturedBox(cx - hw, seatY + 0.05f, cz + hw - 0.06f, cx + hw, 1.02f, cz + hw, uvTileMeters = 0.6f)
            else -> oak.addTexturedBox(cx - hw, seatY + 0.05f, cz - hw, cx + hw, 1.02f, cz - hw + 0.06f, uvTileMeters = 0.6f)
        }
        shadows.addHorizontalQuad(cx, 0.025f, cz, 0.30f, 0.30f)
    }

    private fun addWoodenStaircaseHall(
        oak: MeshBuilder3D,
        wood: MeshBuilder3D,
        lanterns: MeshBuilder3D,
        metal: MeshBuilder3D,
        shadows: MeshBuilder3D,
    ) {
        // 12 solid wooden steps rising from Z=-10.2 (Y=0) to Z=-13.6 (Y=2.04)
        val steps = 12
        val zStart = -10.2f
        val zEnd = -13.6f
        val topY = 2.04f
        val dz = (zEnd - zStart) / steps
        val dy = topY / steps
        for (i in 0 until steps) {
            val sz0 = zStart + i * dz
            val sz1 = sz0 + dz
            val sy1 = (i + 1) * dy
            oak.addTexturedBox(7.15f, (sy1 - dy - 0.06f).coerceAtLeast(0f), sz1, 11.55f, sy1, sz0, uvTileMeters = 1.1f)
            // Nose trim tread
            wood.addTexturedBox(7.12f, sy1 - 0.04f, sz1 - 0.03f, 11.58f, sy1 + 0.005f, sz0 + 0.03f, uvTileMeters = 1.0f)
            // Turned vertical balusters along both stringers
            for (rx in listOf(7.04f, 11.66f)) {
                oak.addTaperedLimbInWorld(rx, sy1, (sz0 + sz1) * 0.5f, 0.84f, 0.032f, 0.026f, sectors = 8)
            }
        }
        // Upper wooden gallery landing (Z: -15.88..-13.6, Y = 2.04)
        wood.addTexturedBox(6.20f, topY - 0.14f, -15.88f, 11.88f, topY, -13.6f, uvTileMeters = 1.4f)
        // Newel posts at bottom & top
        for (px in listOf(7.04f, 11.66f)) {
            oak.addTaperedLimbInWorld(px, 0f, zStart + 0.05f, 1.05f, 0.075f, 0.065f, sectors = 10)
            oak.addTaperedLimbInWorld(px, topY, zEnd, 0.98f, 0.075f, 0.065f, sectors = 10)
            // Angled handrail segments stepping cleanly up the flight
            for (i in 0 until 6) {
                val t0 = i / 6f
                val t1 = (i + 1) / 6f
                val hz0 = zStart + (zEnd - zStart) * t0
                val hz1 = zStart + (zEnd - zStart) * t1
                val hy0 = 0.86f + topY * t0
                val hy1 = 0.86f + topY * t1
                oak.addTexturedBox(px - 0.055f, hy0, hz1, px + 0.055f, hy1 + 0.07f, hz0, uvTileMeters = 0.8f)
            }
        }
        // Upper boarded-up attic door & stairway chandelier
        oak.addTexturedBox(8.4f, topY, -15.86f, 9.9f, topY + 2.15f, -15.72f, uvTileMeters = 0.9f)
        metal.addTaperedLimbInWorld(9.2f, 3.55f, -12.4f, 0.95f, 0.028f, 0.028f, sectors = 8)
        lanterns.addEllipsoid(9.2f, 3.55f, -12.4f, rx = 0.16f, ry = 0.10f, rz = 0.16f, rings = 6, sectors = 12)
        shadows.addHorizontalQuad(9.35f, 0.025f, -11.9f, 2.2f, 1.7f)
    }

    private fun addDetailedTable(
        wood: MeshBuilder3D,
        shadows: MeshBuilder3D,
        x0: Float, x1: Float,
        z0: Float, z1: Float,
        topY: Float,
    ) {
        val thick = 0.06f
        wood.addTexturedBox(x0, topY - thick, z0, x1, topY, z1, uvTileMeters = 0.9f)
        val inset = 0.09f
        val legR = 0.042f
        for (lx in listOf(x0 + inset, x1 - inset)) {
            for (lz in listOf(z0 + inset, z1 - inset)) {
                wood.addTexturedBox(lx - legR, 0f, lz - legR, lx + legR, topY - thick, lz + legR, uvTileMeters = 0.6f)
            }
        }
        shadows.addHorizontalQuad((x0 + x1) * 0.5f, 0.025f, (z0 + z1) * 0.5f, (x1 - x0) * 0.55f, (z1 - z0) * 0.55f)
    }

    private fun MeshBuilder3D.taperedSegment(
        cx: Float, cy: Float, cz: Float,
        height: Float, rBot: Float, rTop: Float,
    ) = addTaperedLimbInWorld(cx, cy, cz, height, rBot, rTop)

    private fun MeshBuilder3D.addTaperedLimbInWorld(
        cx: Float,
        cy: Float,
        cz: Float,
        height: Float,
        rBot: Float,
        rTop: Float,
        sectors: Int = 10,
    ) {
        val base = currentVertexCount
        val rings = 3
        for (r in 0..rings) {
            val t = r.toFloat() / rings
            val y = cy + t * height
            val rad = rBot + (rTop - rBot) * t
            for (s in 0..sectors) {
                val u = s.toFloat() / sectors
                val theta = u * (2f * PI.toFloat())
                val ct = cos(theta)
                val st = sin(theta)
                addVertex(cx + ct * rad, y, cz + st * rad, ct, 0.06f, st, u * 1.5f, t * (height * 0.6f))
            }
        }
        val stride = sectors + 1
        for (r in 0 until rings) {
            for (s in 0 until sectors) {
                val i0 = base + r * stride + s
                val i1 = i0 + 1
                val i2 = i0 + stride + 1
                val i3 = i0 + stride
                addTriangle(i0, i2, i1)
                addTriangle(i0, i3, i2)
            }
        }
    }
}
