package com.example.engine3d

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class MaterialKind(val id: Float) {
    ROAD_ASPHALT(0f),
    GRASS_GROUND(1f),
    WOOD_FLOOR(2f),
    OLD_WALL(3f),
    TREE_BARK(4f),
    PINE_FOLIAGE(5f),
    BLOOD_DECAL(6f),
    LANTERN_GLOW(7f),
    STONE_CELLAR(8f),
    METAL_PROP(9f),
    GRANNY_SKIN(10f),
    GRANNY_DRESS(11f),
    GRANNY_HAIR(12f),
    GRANNY_EYE_GLOW(13f),
}

data class BoxCollider(
    val id: String,
    val minX: Float,
    val minZ: Float,
    val maxX: Float,
    val maxZ: Float,
    val blocksMovement: Boolean = true,
)

data class BoxMeshDef(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    val material: MaterialKind,
)

data class DoorDef(
    val id: String,
    val title: String,
    val hingeX: Float,
    val hingeZ: Float,
    val width: Float,
    val height: Float,
    val thickness: Float,
    val closedYawDeg: Float,
    val openDeltaDeg: Float,
    val requiredItemId: String? = null,
    val lockedHint: String = "Trancada.",
    val initiallyOpen: Boolean = false,
)

data class DoorState(
    val def: DoorDef,
    var isOpen: Boolean = def.initiallyOpen,
    var isUnlocked: Boolean = def.requiredItemId == null,
    var currentAngleDeg: Float = if (def.initiallyOpen) def.openDeltaDeg else 0f,
    // Front door multi-lock escape state
    var frontWireCut: Boolean = false,
    var frontFuseInserted: Boolean = false,
    var frontPadlockOpened: Boolean = false,
)

enum class InteractKind {
    ITEM,
    NOTE,
    DOOR,
    HIDE_CLOSET,
    HIDE_BED,
    FUSE_BOX,
}

data class UnderBedSpot(
    val id: String,
    val hideX: Float,
    val hideY: Float,
    val hideZ: Float,
    val lookOutYawRad: Float,
    val exitX: Float,
    val exitZ: Float,
)

data class WorldItemDef(
    val id: String,
    val name: String,
    val description: String,
    val kind: InteractKind,
    val x: Float,
    val y: Float,
    val z: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    val noteBody: String = "",
)

data class PointLightDef(
    val x: Float,
    val y: Float,
    val z: Float,
    val r: Float,
    val g: Float,
    val b: Float,
    val radius: Float,
    val flickerSeed: Float = 0f,
    val slowFlicker: Boolean = false,
)

object WorldMap3D {
    const val WALL_H = 3.2f
    const val PLAYER_EYE_STANDING = 1.65f
    const val PLAYER_EYE_CROUCH = 0.95f
    const val PLAYER_RADIUS = 0.36f

    val staticBoxes: List<BoxMeshDef> by lazy { buildStaticGeometry() }
    val wallColliders: List<BoxCollider> by lazy { buildStaticColliders() }
    val underBedSpots: List<UnderBedSpot> = listOf(
        UnderBedSpot(
            id = "bed_awakening_hide",
            hideX = 8.95f,
            hideY = 0.25f,
            hideZ = -5.85f,
            lookOutYawRad = -1.25f, // faces West-South toward bed legs, floor & doorway!
            exitX = 8.20f,
            exitZ = -4.05f,
        ),
        UnderBedSpot(
            id = "bed_nw_hide",
            hideX = -9.9f,
            hideY = 0.25f,
            hideZ = -14.2f,
            lookOutYawRad = 0.85f,
            exitX = -9.5f,
            exitZ = -12.3f,
        ),
    )

    fun floorHeightAt(x: Float, z: Float): Float {
        // Walkable Grand Wooden Staircase in North-East Stairwell Hall (X: 7.2..11.5, Z: -15.8..-10.2)
        if (x in 7.1f..11.6f && z <= -10.2f && z >= -15.8f) {
            if (z <= -13.6f) return 2.04f
            val t = ((-10.2f - z) / 3.4f).coerceIn(0f, 1f)
            return t * 2.04f
        }
        return 0f
    }

    val pointLights: List<PointLightDef> = listOf(
        // Warm porch lantern
        PointLightDef(0.0f, 2.55f, 10.6f, 1.0f, 0.66f, 0.28f, 11.5f, 1.3f),
        // Distant roadside streetlight near start
        PointLightDef(-3.1f, 3.6f, 22.0f, 0.55f, 0.65f, 0.85f, 10.0f, 3.7f),
        // Foyer chandelier (warm antique incandescent)
        PointLightDef(0.0f, 2.85f, 7.0f, 1.0f, 0.72f, 0.38f, 11.5f, 2.1f),
        // Living room warm standing lamp + fireplace ember glow
        PointLightDef(-4.6f, 2.25f, 7.8f, 0.96f, 0.68f, 0.34f, 10.5f, 1.8f),
        PointLightDef(-10.8f, 1.0f, 6.0f, 1.0f, 0.44f, 0.16f, 9.5f, 5.2f, slowFlicker = true),
        // Kitchen warm overhead pendant
        PointLightDef(7.5f, 2.85f, 6.0f, 0.94f, 0.80f, 0.54f, 10.5f, 0.7f),
        // Long corridor sconces (dimmer, slowly flickering)
        PointLightDef(0.0f, 2.45f, -1.6f, 0.88f, 0.56f, 0.28f, 9.5f, 4.1f, slowFlicker = true),
        PointLightDef(0.0f, 2.45f, -6.2f, 0.72f, 0.44f, 0.22f, 8.5f, 2.9f, slowFlicker = true),
        // Study warm desk lamp
        PointLightDef(-9.2f, 1.55f, -5.4f, 0.95f, 0.68f, 0.32f, 9.5f, 3.3f),
        // Awakening Bedroom warm antique nightstand lamp + doorway sconce
        PointLightDef(9.8f, 1.38f, -4.15f, 1.0f, 0.70f, 0.32f, 10.0f, 4.8f, slowFlicker = true),
        PointLightDef(5.4f, 2.65f, -3.5f, 0.76f, 0.56f, 0.32f, 9.5f, 1.5f),
        // Staircase Hall chandelier
        PointLightDef(9.2f, 3.55f, -12.4f, 0.94f, 0.66f, 0.32f, 11.5f, 5.9f),
        // North-West Master Bedroom dim lamp
        PointLightDef(-9.0f, 2.55f, -11.5f, 0.86f, 0.56f, 0.28f, 10.0f, 2.4f, slowFlicker = true),
        // Cellar eerie amber-red bulb
        PointLightDef(0.0f, 2.8f, -12.0f, 0.82f, 0.30f, 0.24f, 9.5f, 6.4f, slowFlicker = true),
    )

    val doorDefs: List<DoorDef> = listOf(
        DoorDef(
            id = "front_door",
            title = "Porta da Frente",
            hingeX = -0.85f,
            hingeZ = 10.0f,
            width = 1.70f,
            height = 2.55f,
            thickness = 0.14f,
            closedYawDeg = 0f,
            openDeltaDeg = -85f,
            requiredItemId = "escape_sequence",
            lockedHint = "Trancada por dentro: corte o fio, coloque o fusível e use a Chave Mestra.",
            initiallyOpen = true,
        ),
        DoorDef(
            id = "door_living",
            title = "Porta da Sala",
            hingeX = -3.0f,
            hingeZ = 6.1f,
            width = 1.45f,
            height = 2.45f,
            thickness = 0.12f,
            closedYawDeg = 90f,
            openDeltaDeg = 85f,
            requiredItemId = null,
        ),
        DoorDef(
            id = "door_kitchen",
            title = "Porta da Cozinha",
            hingeX = 3.0f,
            hingeZ = 6.1f,
            width = 1.45f,
            height = 2.45f,
            thickness = 0.12f,
            closedYawDeg = 90f,
            openDeltaDeg = -85f,
            requiredItemId = null,
        ),
        DoorDef(
            id = "door_study",
            title = "Porta do Escritório",
            hingeX = -3.0f,
            hingeZ = -2.2f,
            width = 1.45f,
            height = 2.45f,
            thickness = 0.12f,
            closedYawDeg = 90f,
            openDeltaDeg = 85f,
            requiredItemId = "key_study",
            lockedHint = "Trancada. Precisa da Chave do Escritório.",
        ),
        DoorDef(
            id = "door_granny_room",
            title = "Porta do Quarto",
            hingeX = 3.0f,
            hingeZ = -2.2f,
            width = 1.45f,
            height = 2.45f,
            thickness = 0.12f,
            closedYawDeg = 90f,
            openDeltaDeg = -85f,
            requiredItemId = null,
        ),
        DoorDef(
            id = "door_bedroom_nw",
            title = "Porta do Quarto Antigo",
            hingeX = -9.2f,
            hingeZ = -8.0f,
            width = 1.45f,
            height = 2.45f,
            thickness = 0.12f,
            closedYawDeg = 0f,
            openDeltaDeg = -85f,
            requiredItemId = null,
        ),
        DoorDef(
            id = "door_stairhall",
            title = "Porta da Escadaria",
            hingeX = 6.85f,
            hingeZ = -8.0f,
            width = 1.45f,
            height = 2.45f,
            thickness = 0.12f,
            closedYawDeg = 0f,
            openDeltaDeg = -85f,
            requiredItemId = null,
        ),
        DoorDef(
            id = "door_cellar",
            title = "Porta do Porão",
            hingeX = -0.75f,
            hingeZ = -8.0f,
            width = 1.50f,
            height = 2.45f,
            thickness = 0.14f,
            closedYawDeg = 0f,
            openDeltaDeg = 88f,
            requiredItemId = "crowbar",
            lockedHint = "Tábuas pregadas! Precisa de um Pé de Cabra.",
        ),
    )

    val itemDefs: List<WorldItemDef> = listOf(
        WorldItemDef(
            id = "lantern",
            name = "Lanterna Tática",
            description = "Ilumina os cantos mais escuros da casa.",
            kind = InteractKind.ITEM,
            x = -6.2f, y = 0.85f, z = 8.4f,
            r = 0.95f, g = 0.72f, b = 0.25f,
        ),
        WorldItemDef(
            id = "note_hall",
            name = "Bilhete Rasgado",
            description = "Um aviso rabiscado às pressas.",
            kind = InteractKind.NOTE,
            x = -4.6f, y = 0.85f, z = 4.6f,
            r = 0.88f, g = 0.82f, b = 0.68f,
            noteBody = "\"Se você entrou para se abrigar da chuva ou do frio, FUJA enquanto ainda há tempo. Ela tranca todos os viajantes que param na estrada. Corte o alarme, ligue o disjuntor e encontre a Chave Mestra no porão.\"",
        ),
        WorldItemDef(
            id = "key_study",
            name = "Chave do Escritório",
            description = "Abre a porta do escritório no corredor norte.",
            kind = InteractKind.ITEM,
            x = 9.5f, y = 0.95f, z = 8.5f,
            r = 0.85f, g = 0.65f, b = 0.22f,
        ),
        WorldItemDef(
            id = "medkit",
            name = "Kit Médico",
            description = "Restaura +45 de vida.",
            kind = InteractKind.ITEM,
            x = 10.2f, y = 0.95f, z = 3.2f,
            r = 0.85f, g = 0.18f, b = 0.20f,
        ),
        WorldItemDef(
            id = "wire_cutters",
            name = "Alicate",
            description = "Corta o fio do alarme na porta principal.",
            kind = InteractKind.ITEM,
            x = -9.8f, y = 0.90f, z = -5.6f,
            r = 0.72f, g = 0.76f, b = 0.82f,
        ),
        WorldItemDef(
            id = "note_study",
            name = "Diário da Vovó",
            description = "Páginas manchadas de tinta e sangue seco.",
            kind = InteractKind.NOTE,
            x = -7.2f, y = 0.90f, z = -3.4f,
            r = 0.84f, g = 0.74f, b = 0.64f,
            noteBody = "\"14 de Outubro: Outro jovem perdido bateu à porta hoje. Tão educado... Como os outros, ele nunca mais sairá desta casa. Guardei o Pé de Cabra atrás da mesa e a Chave Mestra no porão.\"",
        ),
        WorldItemDef(
            id = "crowbar",
            name = "Pé de Cabra",
            description = "Remove as tábuas que bloqueiam a porta do porão.",
            kind = InteractKind.ITEM,
            x = -10.4f, y = 0.55f, z = -1.8f,
            r = 0.78f, g = 0.24f, b = 0.22f,
        ),
        WorldItemDef(
            id = "fuse",
            name = "Fusível Elétrico",
            description = "Restaura a energia da trava da porta da frente.",
            kind = InteractKind.ITEM,
            x = 5.0f, y = 0.92f, z = -7.1f,
            r = 0.35f, g = 0.75f, b = 0.92f,
        ),
        WorldItemDef(
            id = "battery",
            name = "Pilha Extra",
            description = "Recarrega +55% da bateria da lanterna.",
            kind = InteractKind.ITEM,
            x = 5.5f, y = 0.85f, z = -0.8f,
            r = 0.32f, g = 0.86f, b = 0.45f,
        ),
        WorldItemDef(
            id = "master_key",
            name = "Chave Mestra",
            description = "Destranca o cadeado pesado da porta principal!",
            kind = InteractKind.ITEM,
            x = 0.0f, y = 0.92f, z = -14.2f,
            r = 0.98f, g = 0.82f, b = 0.32f,
        ),
        WorldItemDef(
            id = "wardrobe_hide",
            name = "Armário Antigo",
            description = "Esconda-se da Vovó aqui dentro.",
            kind = InteractKind.HIDE_CLOSET,
            x = 10.6f, y = 1.15f, z = -2.1f,
            r = 0.34f, g = 0.22f, b = 0.14f,
        ),
        WorldItemDef(
            id = "bed_awakening_hide",
            name = "Debaixo da Cama",
            description = "Abaixe-se e esconda-se sob a cama antiga.",
            kind = InteractKind.HIDE_BED,
            x = 8.4f, y = 0.35f, z = -4.9f,
            r = 0.48f, g = 0.24f, b = 0.18f,
        ),
        WorldItemDef(
            id = "bed_nw_hide",
            name = "Debaixo da Cama",
            description = "Abaixe-se e esconda-se sob a cama do quarto antigo.",
            kind = InteractKind.HIDE_BED,
            x = -9.6f, y = 0.35f, z = -13.2f,
            r = 0.48f, g = 0.24f, b = 0.18f,
        ),
        WorldItemDef(
            id = "fuse_box",
            name = "Caixa de Energia",
            description = "Use o Fusível aqui para liberar a tranca elétrica.",
            kind = InteractKind.FUSE_BOX,
            x = 2.2f, y = 1.45f, z = 9.75f,
            r = 0.42f, g = 0.45f, b = 0.50f,
        ),
    )

    fun checkCircleCollision(x: Float, z: Float, radius: Float, doors: List<DoorState>): Boolean {
        for (c in wallColliders) {
            if (!c.blocksMovement) continue
            val closestX = x.coerceIn(c.minX, c.maxX)
            val closestZ = z.coerceIn(c.minZ, c.maxZ)
            val dx = x - closestX
            val dz = z - closestZ
            if (dx * dx + dz * dz < radius * radius) return true
        }
        // Check closed doors
        for (d in doors) {
            if (kotlin.math.abs(d.currentAngleDeg) > 45f) continue
            val yawRad = (d.def.closedYawDeg + d.currentAngleDeg) * (PI.toFloat() / 180f)
            val endX = d.def.hingeX + cos(yawRad) * d.def.width
            val endZ = d.def.hingeZ + sin(yawRad) * d.def.width
            val distSq = distPointToSegmentSq(x, z, d.def.hingeX, d.def.hingeZ, endX, endZ)
            val minDist = radius + d.def.thickness * 0.6f
            if (distSq < minDist * minDist) return true
        }
        return false
    }

    private fun distPointToSegmentSq(px: Float, pz: Float, ax: Float, az: Float, bx: Float, bz: Float): Float {
        val vx = bx - ax
        val vz = bz - az
        val lenSq = vx * vx + vz * vz
        if (lenSq < 1e-6f) {
            val dx = px - ax
            val dz = pz - az
            return dx * dx + dz * dz
        }
        val t = (((px - ax) * vx + (pz - az) * vz) / lenSq).coerceIn(0f, 1f)
        val cx = ax + t * vx
        val cz = az + t * vz
        val dx = px - cx
        val dz = pz - cz
        return dx * dx + dz * dz
    }

    fun resolveMovement(
        curX: Float,
        curZ: Float,
        dx: Float,
        dz: Float,
        doors: List<DoorState>,
    ): Pair<Float, Float> {
        var nx = curX
        var nz = curZ
        if (!checkCircleCollision(curX + dx, curZ, PLAYER_RADIUS, doors)) {
            nx = curX + dx
        }
        if (!checkCircleCollision(nx, curZ + dz, PLAYER_RADIUS, doors)) {
            nz = curZ + dz
        }
        return nx to nz
    }

    private fun buildStaticColliders(): List<BoxCollider> {
        val list = mutableListOf<BoxCollider>()
        // Road world bounds (prevent falling off the forest road)
        list.add(BoxCollider("road_west_bound", -14f, 10.2f, -6.5f, 34f))
        list.add(BoxCollider("road_east_bound", 6.5f, 10.2f, 14f, 34f))
        list.add(BoxCollider("road_south_bound", -14f, 31f, 14f, 35f))

        // House exterior walls (Z = +10 with front door opening X in [-0.85, 0.85])
        list.add(BoxCollider("ext_south_w", -12.3f, 9.8f, -0.85f, 10.2f))
        list.add(BoxCollider("ext_south_e", 0.85f, 9.8f, 12.3f, 10.2f))
        list.add(BoxCollider("ext_west", -12.3f, -8.2f, -11.9f, 10.2f))
        list.add(BoxCollider("ext_east", 11.9f, -8.2f, 12.3f, 10.2f))

        // Exterior walls of the two new North wings (X: -12.2..-5.9 and 5.9..12.2, Z: -16.2..-7.8)
        list.add(BoxCollider("ext_west_north", -12.3f, -16.2f, -11.9f, -7.8f))
        list.add(BoxCollider("ext_east_north", 11.9f, -16.2f, 12.3f, -7.8f))
        list.add(BoxCollider("nw_room_north", -12.3f, -16.3f, -5.9f, -15.9f))
        list.add(BoxCollider("ne_stair_north", 5.9f, -16.3f, 12.3f, -15.9f))

        // North wall at Z = -8 (doorways at cellar X in [-0.75,0.75], NW bedroom X in [-9.2,-7.75], Stair hall X in [6.85,8.30])
        list.add(BoxCollider("house_north_w1", -12.2f, -8.2f, -9.2f, -7.8f))
        list.add(BoxCollider("house_north_w2", -7.75f, -8.2f, -0.75f, -7.8f))
        list.add(BoxCollider("house_north_e1", 0.75f, -8.2f, 6.85f, -7.8f))
        list.add(BoxCollider("house_north_e2", 8.30f, -8.2f, 12.2f, -7.8f))

        // Cellar walls (X: -6..6, Z: -16..-8)
        list.add(BoxCollider("cellar_w", -6.3f, -16.2f, -5.9f, -7.8f))
        list.add(BoxCollider("cellar_e", 5.9f, -16.2f, 6.3f, -7.8f))
        list.add(BoxCollider("cellar_n", -6.3f, -16.3f, 6.3f, -15.9f))

        // Interior dividers along X = -3 (openings at Z=6.1..7.55 and Z=-2.2..-0.75)
        list.add(BoxCollider("hall_w1", -3.15f, 7.55f, -2.85f, 10.0f))
        list.add(BoxCollider("hall_w2", -3.15f, -0.75f, -2.85f, 6.1f))
        list.add(BoxCollider("hall_w3", -3.15f, -8.0f, -2.85f, -2.2f))

        // Interior dividers along X = +3 (openings at Z=6.1..7.55 and Z=-2.2..-0.75)
        list.add(BoxCollider("hall_e1", 2.85f, 7.55f, 3.15f, 10.0f))
        list.add(BoxCollider("hall_e2", 2.85f, -0.75f, 3.15f, 6.1f))
        list.add(BoxCollider("hall_e3", 2.85f, -8.0f, 3.15f, -2.2f))

        // East-West room partitions at Z = 2 (between Living/Study and Kitchen/AwakeningBedroom)
        list.add(BoxCollider("div_west_room", -12.0f, 1.85f, -3.0f, 2.15f))
        list.add(BoxCollider("div_east_room", 3.0f, 1.85f, 12.0f, 2.15f))

        // Furniture colliders (carefully placed so corridors & doors never snag!)
        list.add(BoxCollider("furn_living_table", -7.0f, 7.8f, -5.4f, 9.0f))
        list.add(BoxCollider("furn_living_sofa", -10.3f, 3.2f, -7.6f, 4.4f))
        list.add(BoxCollider("furn_fireplace", -11.9f, 5.0f, -10.8f, 7.0f))
        list.add(BoxCollider("furn_kitchen_counter", 8.8f, 7.8f, 11.4f, 9.4f))
        list.add(BoxCollider("furn_butcher_table", 6.2f, 4.4f, 8.4f, 6.2f))
        list.add(BoxCollider("furn_study_desk", -10.6f, -6.2f, -8.6f, -4.8f))
        // Awakening Bedroom furniture
        list.add(BoxCollider("furn_awakening_bed", 7.4f, -6.9f, 10.6f, -4.9f))
        list.add(BoxCollider("furn_awakening_nightstand", 9.4f, -4.6f, 10.4f, -3.7f))
        list.add(BoxCollider("furn_awakening_dresser", 4.2f, -7.8f, 5.8f, -6.8f))
        list.add(BoxCollider("furn_wardrobe", 10.3f, -2.9f, 11.7f, -1.3f))
        // North-West Master Bedroom furniture
        list.add(BoxCollider("furn_nw_bed", -11.4f, -15.2f, -8.4f, -13.2f))
        list.add(BoxCollider("furn_nw_wardrobe", -7.4f, -15.4f, -6.2f, -13.8f))
        // North-East Wooden Staircase side rails (keeps player safely on the steps!)
        list.add(BoxCollider("stair_rail_w", 6.85f, -15.6f, 7.15f, -10.1f))
        list.add(BoxCollider("stair_rail_e", 11.55f, -15.6f, 11.85f, -10.1f))
        // Cellar crates
        list.add(BoxCollider("furn_cellar_crates", -4.6f, -14.8f, -2.8f, -13.0f))
        return list
    }

    private fun buildStaticGeometry(): List<BoxMeshDef> {
        val m = mutableListOf<BoxMeshDef>()

        // 1. Exterior Night Ground & Isolated Dirt Road
        m.add(BoxMeshDef(-30f, -0.15f, 10f, 30f, 0f, 38f, 0.06f, 0.11f, 0.07f, MaterialKind.GRASS_GROUND))
        // Road strip
        m.add(BoxMeshDef(-2.6f, -0.02f, 10.2f, 2.6f, 0.01f, 36f, 0.12f, 0.11f, 0.12f, MaterialKind.ROAD_ASPHALT))
        // Center dashed road markings (faded yellow)
        for (z in 12..32 step 4) {
            m.add(BoxMeshDef(-0.08f, 0.012f, z.toFloat(), 0.08f, 0.02f, z + 1.8f, 0.42f, 0.36f, 0.16f, MaterialKind.ROAD_ASPHALT))
        }
        // Porch steps & wooden deck
        m.add(BoxMeshDef(-2.6f, 0f, 10.0f, 2.6f, 0.12f, 11.8f, 0.24f, 0.16f, 0.11f, MaterialKind.WOOD_FLOOR))
        // Porch roof overhang & pillars
        m.add(BoxMeshDef(-2.7f, 2.75f, 10.0f, 2.7f, 2.95f, 12.0f, 0.18f, 0.13f, 0.10f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(-2.45f, 0.12f, 11.55f, -2.25f, 2.75f, 11.75f, 0.22f, 0.15f, 0.11f, MaterialKind.WOOD_FLOOR))
        m.add(BoxMeshDef(2.25f, 0.12f, 11.55f, 2.45f, 2.75f, 11.75f, 0.22f, 0.15f, 0.11f, MaterialKind.WOOD_FLOOR))
        // Glowing porch lantern
        m.add(BoxMeshDef(-0.14f, 2.35f, 10.50f, 0.14f, 2.68f, 10.78f, 1.0f, 0.68f, 0.30f, MaterialKind.LANTERN_GLOW))
        // Roadside streetlamp post
        m.add(BoxMeshDef(-3.15f, 0f, 21.9f, -2.95f, 3.8f, 22.1f, 0.16f, 0.17f, 0.19f, MaterialKind.METAL_PROP))
        m.add(BoxMeshDef(-3.25f, 3.45f, 21.8f, -2.85f, 3.78f, 22.2f, 0.65f, 0.72f, 0.90f, MaterialKind.LANTERN_GLOW))

        // 2. 3D Pine Trees along the road
        val treePositions = listOf(
            -5.2f to 27f, 5.4f to 26f, -6.2f to 22f, 5.8f to 21f,
            -5.0f to 17f, 6.2f to 16f, -7.2f to 13f, 7.0f to 13.5f,
            -9.5f to 25f, 9.2f to 23f, -9.8f to 18f, 9.5f to 18.5f,
        )
        for ((tx, tz) in treePositions) {
            m.add(BoxMeshDef(tx - 0.22f, 0f, tz - 0.22f, tx + 0.22f, 2.6f, tz + 0.22f, 0.18f, 0.12f, 0.08f, MaterialKind.TREE_BARK))
            m.add(BoxMeshDef(tx - 1.3f, 2.2f, tz - 1.3f, tx + 1.3f, 3.8f, tz + 1.3f, 0.06f, 0.16f, 0.09f, MaterialKind.PINE_FOLIAGE))
            m.add(BoxMeshDef(tx - 0.9f, 3.6f, tz - 0.9f, tx + 0.9f, 5.2f, tz + 0.9f, 0.05f, 0.14f, 0.08f, MaterialKind.PINE_FOLIAGE))
            m.add(BoxMeshDef(tx - 0.45f, 5.0f, tz - 0.45f, tx + 0.45f, 6.4f, tz + 0.45f, 0.04f, 0.12f, 0.07f, MaterialKind.PINE_FOLIAGE))
        }

        // 3. House Floors & Ceilings
        m.add(BoxMeshDef(-12.2f, -0.05f, -8.0f, 12.2f, 0.02f, 10.0f, 0.24f, 0.16f, 0.11f, MaterialKind.WOOD_FLOOR))
        m.add(BoxMeshDef(-12.2f, WALL_H, -8.2f, 12.2f, WALL_H + 0.25f, 10.2f, 0.15f, 0.11f, 0.09f, MaterialKind.OLD_WALL))
        // Pitched gable roof outside
        m.add(BoxMeshDef(-12.5f, WALL_H + 0.25f, -8.5f, 12.5f, WALL_H + 1.4f, 10.5f, 0.12f, 0.09f, 0.09f, MaterialKind.OLD_WALL))
        // Cellar stone floor & ceiling
        m.add(BoxMeshDef(-6.2f, -0.05f, -16.2f, 6.2f, 0.02f, -8.0f, 0.15f, 0.16f, 0.17f, MaterialKind.STONE_CELLAR))
        m.add(BoxMeshDef(-6.2f, WALL_H, -16.2f, 6.2f, WALL_H + 0.25f, -8.0f, 0.12f, 0.12f, 0.13f, MaterialKind.STONE_CELLAR))

        // 4. House Walls (with door headers!)
        // South facade
        m.add(BoxMeshDef(-12.2f, 0f, 9.85f, -0.85f, WALL_H, 10.15f, 0.26f, 0.22f, 0.18f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(0.85f, 0f, 9.85f, 12.2f, WALL_H, 10.15f, 0.26f, 0.22f, 0.18f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(-0.85f, 2.55f, 9.85f, 0.85f, WALL_H, 10.15f, 0.26f, 0.22f, 0.18f, MaterialKind.OLD_WALL))
        // West & East exterior walls
        m.add(BoxMeshDef(-12.2f, 0f, -8.15f, -11.9f, WALL_H, 10.15f, 0.25f, 0.21f, 0.18f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(11.9f, 0f, -8.15f, 12.2f, WALL_H, 10.15f, 0.25f, 0.21f, 0.18f, MaterialKind.OLD_WALL))
        // North wall (cellar entrance)
        m.add(BoxMeshDef(-12.2f, 0f, -8.15f, -0.75f, WALL_H, -7.85f, 0.24f, 0.20f, 0.17f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(0.75f, 0f, -8.15f, 12.2f, WALL_H, -7.85f, 0.24f, 0.20f, 0.17f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(-0.75f, 2.45f, -8.15f, 0.75f, WALL_H, -7.85f, 0.24f, 0.20f, 0.17f, MaterialKind.OLD_WALL))
        // Cellar walls
        m.add(BoxMeshDef(-6.2f, 0f, -16.2f, -5.9f, WALL_H, -8.0f, 0.19f, 0.20f, 0.21f, MaterialKind.STONE_CELLAR))
        m.add(BoxMeshDef(5.9f, 0f, -16.2f, 6.2f, WALL_H, -8.0f, 0.19f, 0.20f, 0.21f, MaterialKind.STONE_CELLAR))
        m.add(BoxMeshDef(-6.2f, 0f, -16.2f, 6.2f, WALL_H, -15.9f, 0.19f, 0.20f, 0.21f, MaterialKind.STONE_CELLAR))

        // Hallway West wall (X=-3) + headers above doors
        m.add(BoxMeshDef(-3.12f, 0f, 7.55f, -2.88f, WALL_H, 10.0f, 0.28f, 0.24f, 0.20f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(-3.12f, 2.45f, 6.1f, -2.88f, WALL_H, 7.55f, 0.28f, 0.24f, 0.20f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(-3.12f, 0f, -0.75f, -2.88f, WALL_H, 6.1f, 0.28f, 0.24f, 0.20f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(-3.12f, 2.45f, -2.2f, -2.88f, WALL_H, -0.75f, 0.28f, 0.24f, 0.20f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(-3.12f, 0f, -8.0f, -2.88f, WALL_H, -2.2f, 0.28f, 0.24f, 0.20f, MaterialKind.OLD_WALL))

        // Hallway East wall (X=+3) + headers above doors
        m.add(BoxMeshDef(2.88f, 0f, 7.55f, 3.12f, WALL_H, 10.0f, 0.28f, 0.24f, 0.20f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(2.88f, 2.45f, 6.1f, 3.12f, WALL_H, 7.55f, 0.28f, 0.24f, 0.20f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(2.88f, 0f, -0.75f, 3.12f, WALL_H, 6.1f, 0.28f, 0.24f, 0.20f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(2.88f, 2.45f, -2.2f, 3.12f, WALL_H, -0.75f, 0.28f, 0.24f, 0.20f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(2.88f, 0f, -8.0f, 3.12f, WALL_H, -2.2f, 0.28f, 0.24f, 0.20f, MaterialKind.OLD_WALL))

        // East/West dividing walls at Z=2
        m.add(BoxMeshDef(-12.0f, 0f, 1.88f, -3.0f, WALL_H, 2.12f, 0.27f, 0.22f, 0.19f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(3.0f, 0f, 1.88f, 12.0f, WALL_H, 2.12f, 0.27f, 0.22f, 0.19f, MaterialKind.OLD_WALL))

        // 5. Furniture & Props
        // Foyer hallway red-brown runner rug
        m.add(BoxMeshDef(-1.1f, 0.02f, -7.2f, 1.1f, 0.04f, 8.8f, 0.34f, 0.10f, 0.11f, MaterialKind.OLD_WALL))
        // Living room side tables & fireplace
        m.add(BoxMeshDef(-7.0f, 0f, 7.8f, -5.4f, 0.78f, 9.0f, 0.32f, 0.20f, 0.13f, MaterialKind.WOOD_FLOOR))
        m.add(BoxMeshDef(-5.0f, 0f, 4.2f, -4.1f, 0.78f, 5.1f, 0.32f, 0.20f, 0.13f, MaterialKind.WOOD_FLOOR))
        m.add(BoxMeshDef(-11.9f, 0f, 5.0f, -10.8f, 2.0f, 7.0f, 0.22f, 0.20f, 0.21f, MaterialKind.STONE_CELLAR))
        m.add(BoxMeshDef(-11.4f, 0.15f, 5.5f, -10.9f, 0.55f, 6.5f, 1.0f, 0.42f, 0.14f, MaterialKind.LANTERN_GLOW))
        // Kitchen counter & butcher table
        m.add(BoxMeshDef(8.8f, 0f, 7.8f, 11.4f, 0.88f, 9.4f, 0.35f, 0.30f, 0.24f, MaterialKind.WOOD_FLOOR))
        m.add(BoxMeshDef(9.6f, 0f, 2.6f, 11.4f, 0.88f, 4.0f, 0.35f, 0.30f, 0.24f, MaterialKind.WOOD_FLOOR))
        m.add(BoxMeshDef(6.2f, 0f, 4.4f, 8.4f, 0.84f, 6.2f, 0.38f, 0.25f, 0.17f, MaterialKind.WOOD_FLOOR))
        // Study desk & bookshelves
        m.add(BoxMeshDef(-10.6f, 0f, -6.2f, -8.6f, 0.82f, -4.8f, 0.28f, 0.17f, 0.11f, MaterialKind.WOOD_FLOOR))
        m.add(BoxMeshDef(-7.8f, 0f, -3.9f, -6.6f, 0.82f, -2.9f, 0.28f, 0.17f, 0.11f, MaterialKind.WOOD_FLOOR))
        m.add(BoxMeshDef(-11.8f, 0f, -7.6f, -11.0f, 2.4f, -3.0f, 0.22f, 0.14f, 0.09f, MaterialKind.WOOD_FLOOR))
        // Granny's bed, dresser & hiding wardrobe
        m.add(BoxMeshDef(7.8f, 0f, -7.2f, 11.2f, 0.62f, -4.8f, 0.32f, 0.14f, 0.14f, MaterialKind.OLD_WALL))
        m.add(BoxMeshDef(8.6f, 0f, -6.3f, 9.8f, 0.86f, -5.3f, 0.28f, 0.18f, 0.12f, MaterialKind.WOOD_FLOOR))
        m.add(BoxMeshDef(10.2f, 0f, -3.0f, 11.6f, 2.3f, -1.4f, 0.28f, 0.17f, 0.11f, MaterialKind.WOOD_FLOOR))
        // Cellar table & crates
        m.add(BoxMeshDef(-0.8f, 0f, -14.8f, 0.8f, 0.82f, -13.6f, 0.24f, 0.18f, 0.13f, MaterialKind.WOOD_FLOOR))
        m.add(BoxMeshDef(-4.6f, 0f, -14.8f, -2.8f, 1.15f, -13.0f, 0.28f, 0.20f, 0.12f, MaterialKind.WOOD_FLOOR))
        // Fuse box on foyer wall
        m.add(BoxMeshDef(1.9f, 1.15f, 9.72f, 2.5f, 1.75f, 9.86f, 0.28f, 0.31f, 0.35f, MaterialKind.METAL_PROP))

        // 6. Stylized Crimson Blood Decals (kitchen table, hallway trail, cellar floor, Granny's room)
        m.add(BoxMeshDef(6.5f, 0.845f, 4.7f, 7.6f, 0.855f, 5.7f, 0.45f, 0.04f, 0.05f, MaterialKind.BLOOD_DECAL))
        m.add(BoxMeshDef(7.0f, 0.025f, 4.9f, 8.2f, 0.035f, 5.9f, 0.42f, 0.03f, 0.04f, MaterialKind.BLOOD_DECAL))
        m.add(BoxMeshDef(-0.5f, 0.045f, -6.4f, 0.4f, 0.052f, -5.2f, 0.44f, 0.04f, 0.05f, MaterialKind.BLOOD_DECAL))
        m.add(BoxMeshDef(0.2f, 0.025f, -10.8f, 1.3f, 0.035f, -9.6f, 0.42f, 0.03f, 0.04f, MaterialKind.BLOOD_DECAL))
        m.add(BoxMeshDef(5.0f, 0.025f, -4.4f, 6.1f, 0.035f, -3.5f, 0.40f, 0.03f, 0.04f, MaterialKind.BLOOD_DECAL))

        return m
    }
}
