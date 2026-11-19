package com.example.engine3d

import kotlin.math.PI
import kotlin.math.abs
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
    val minY: Float = 0f,
    val maxY: Float = 6.5f,
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
    val baseY: Float = 0f,
    val isSecretBookshelf: Boolean = false,
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
    DRAWER,
    SECRET_LEVER,
    ESCAPE_CAR,
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

data class NavNode(
    val id: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val neighbors: List<String>,
    val gateDoorId: String? = null,
    val nearbyHideSpotId: String? = null,
)

object WorldMap3D {
    const val WALL_H = 3.2f
    const val ATTIC_WALL_TOP = 4.65f
    const val UPPER_FLOOR_Y = 2.04f
    const val PLAYER_EYE_STANDING = 1.65f
    const val PLAYER_EYE_CROUCH = 0.95f
    const val PLAYER_RADIUS = 0.36f

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
        // 1. Walkable Grand Wooden Staircase in North-East Stairwell Hall (X: 7.1..11.6, Z: -13.6..-10.2)
        if (x in 7.1f..11.6f && z <= -10.2f && z >= -13.6f) {
            val t = ((-10.2f - z) / 3.4f).coerceIn(0f, 1f)
            return t * UPPER_FLOOR_Y
        }
        // 2. Upper Staircase Landing (X: 6.0..12.0, Z: -16.0..-13.6)
        if (x in 6.0f..12.0f && z < -13.6f && z >= -16.0f) {
            return UPPER_FLOOR_Y
        }
        // 3. Attic Main & Upper Archive Bedroom (X: 0.0..12.0, Z: -22.2..-16.0)
        if (x in 0.0f..12.0f && z < -16.0f && z >= -22.2f) {
            return UPPER_FLOOR_Y
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
        // Living room warm standing lamp + fireplace ember glow (expanded room!)
        PointLightDef(-4.6f, 2.25f, 7.8f, 0.96f, 0.68f, 0.34f, 10.5f, 1.8f),
        PointLightDef(-14.6f, 1.0f, 6.0f, 1.0f, 0.46f, 0.17f, 10.5f, 5.2f, slowFlicker = true),
        // Kitchen warm overhead pendant + pantry light
        PointLightDef(7.5f, 2.85f, 6.0f, 0.94f, 0.80f, 0.54f, 10.5f, 0.7f),
        PointLightDef(13.8f, 2.65f, 6.0f, 0.86f, 0.70f, 0.44f, 9.5f, 2.3f, slowFlicker = true),
        // Carport exterior amber floodlight illuminating the Getaway Sedan
        PointLightDef(19.5f, 3.05f, 5.8f, 0.92f, 0.64f, 0.34f, 12.0f, 4.1f),
        // Long corridor sconces (dimmer, slowly flickering)
        PointLightDef(0.0f, 2.45f, -1.6f, 0.88f, 0.56f, 0.28f, 9.5f, 4.1f, slowFlicker = true),
        PointLightDef(0.0f, 2.45f, -6.2f, 0.72f, 0.44f, 0.22f, 8.5f, 2.9f, slowFlicker = true),
        // Study warm desk lamp & library corner sconce
        PointLightDef(-9.2f, 1.55f, -5.4f, 0.95f, 0.68f, 0.32f, 9.5f, 3.3f),
        PointLightDef(-13.8f, 2.35f, -2.5f, 0.78f, 0.54f, 0.28f, 8.5f, 1.9f, slowFlicker = true),
        // Bathroom cold pale flickering bulb
        PointLightDef(-14.0f, 2.75f, -12.0f, 0.62f, 0.74f, 0.68f, 8.5f, 6.1f, slowFlicker = true),
        // Awakening Bedroom warm antique nightstand lamp + doorway sconce
        PointLightDef(9.8f, 1.38f, -4.15f, 1.0f, 0.70f, 0.32f, 10.0f, 4.8f, slowFlicker = true),
        PointLightDef(5.4f, 2.65f, -3.5f, 0.76f, 0.56f, 0.32f, 9.5f, 1.5f),
        // Staircase Hall chandelier
        PointLightDef(9.2f, 3.55f, -12.4f, 0.94f, 0.66f, 0.32f, 11.5f, 5.9f),
        // Attic & Upper Archive Room dim dusty bulbs
        PointLightDef(8.8f, 4.25f, -19.2f, 0.88f, 0.58f, 0.26f, 9.5f, 3.8f, slowFlicker = true),
        PointLightDef(3.0f, 4.10f, -19.0f, 0.76f, 0.48f, 0.26f, 8.5f, 5.1f, slowFlicker = true),
        // North-West Master Bedroom dim lamp
        PointLightDef(-9.0f, 2.55f, -11.5f, 0.86f, 0.56f, 0.28f, 10.0f, 2.4f, slowFlicker = true),
        // Cellar eerie amber-red bulb & deep vault boiler glow
        PointLightDef(0.0f, 2.8f, -12.0f, 0.82f, 0.30f, 0.24f, 9.5f, 6.4f, slowFlicker = true),
        PointLightDef(-7.5f, 1.6f, -19.5f, 0.95f, 0.34f, 0.14f, 9.0f, 4.4f, slowFlicker = true),
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
            id = "door_living_study",
            title = "Porta Sala-Escritório",
            hingeX = -7.50f,
            hingeZ = 2.0f,
            width = 1.45f,
            height = 2.45f,
            thickness = 0.12f,
            closedYawDeg = 0f,
            openDeltaDeg = -85f,
            requiredItemId = null,
        ),
        DoorDef(
            id = "door_kitchen_bedroom",
            title = "Porta da Despensa Interna",
            hingeX = 5.20f,
            hingeZ = 2.0f,
            width = 1.45f,
            height = 2.45f,
            thickness = 0.12f,
            closedYawDeg = 0f,
            openDeltaDeg = 85f,
            requiredItemId = null,
        ),
        DoorDef(
            id = "door_back_yard",
            title = "Porta dos Fundos",
            hingeX = 16.5f,
            hingeZ = 5.20f,
            width = 1.45f,
            height = 2.45f,
            thickness = 0.14f,
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
            id = "door_bathroom",
            title = "Porta do Banheiro",
            hingeX = -14.60f,
            hingeZ = -8.0f,
            width = 1.45f,
            height = 2.45f,
            thickness = 0.12f,
            closedYawDeg = 0f,
            openDeltaDeg = -85f,
            requiredItemId = null,
        ),
        DoorDef(
            id = "door_bath_nw",
            title = "Porta Lateral do Banheiro",
            hingeX = -12.0f,
            hingeZ = -11.20f,
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
            id = "secret_bookshelf_door",
            title = "Estante Secreta",
            hingeX = -4.80f,
            hingeZ = -8.0f,
            width = 1.45f,
            height = 2.45f,
            thickness = 0.28f,
            closedYawDeg = 0f,
            openDeltaDeg = -85f,
            requiredItemId = "secret_lever_unlocked",
            lockedHint = "Esta estante parece oca... Procure um livro falso ou alavanca no escritório.",
            isSecretBookshelf = true,
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
        DoorDef(
            id = "door_attic",
            title = "Porta do Sótão",
            hingeX = 8.40f,
            hingeZ = -16.0f,
            width = 1.45f,
            height = 2.30f,
            thickness = 0.12f,
            closedYawDeg = 0f,
            openDeltaDeg = -85f,
            requiredItemId = null,
            baseY = UPPER_FLOOR_Y,
        ),
        DoorDef(
            id = "door_upper_room",
            title = "Porta do Quarto Superior",
            hingeX = 6.0f,
            hingeZ = -19.80f,
            width = 1.45f,
            height = 2.30f,
            thickness = 0.12f,
            closedYawDeg = 90f,
            openDeltaDeg = 85f,
            requiredItemId = null,
            baseY = UPPER_FLOOR_Y,
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
            noteBody = "\"Se você entrou para se abrigar da chuva ou do frio, FUJA enquanto ainda há tempo. Há duas saídas: destranque a porta da frente (fio, fusível e Chave Mestra) ou conserte o carro velho no pátio atrás da cozinha (chave do carro, gasolina e vela de ignição).\"",
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
            noteBody = "\"14 de Outubro: Outro jovem perdido bateu à porta hoje. Tirei a vela de ignição do meu carro velho e a escondi lá em cima no quarto do sótão. O galão de gasolina deixei no fundo do porão, ao lado da caldeira.\"",
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
            id = "lever_study",
            name = "Livro Oculto na Estante",
            description = "Um volume antigo fora do lugar... Puxe para abrir uma passagem.",
            kind = InteractKind.SECRET_LEVER,
            x = -14.85f, y = 1.32f, z = -6.2f,
            r = 0.65f, g = 0.18f, b = 0.16f,
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
        // Car escape route items across Bathroom, Cellar Vault & Upper Archive Bedroom
        WorldItemDef(
            id = "car_key",
            name = "Chave do Carro",
            description = "Chave enferrujada do sedã estacionado nos fundos.",
            kind = InteractKind.ITEM,
            x = -14.8f, y = 0.92f, z = -14.2f,
            r = 0.88f, g = 0.80f, b = 0.36f,
        ),
        WorldItemDef(
            id = "gas_can",
            name = "Galão de Gasolina",
            description = "Combustível para o carro de fuga no pátio.",
            kind = InteractKind.ITEM,
            x = -9.2f, y = 0.48f, z = -19.5f,
            r = 0.88f, g = 0.18f, b = 0.14f,
        ),
        WorldItemDef(
            id = "spark_plug",
            name = "Vela de Ignição",
            description = "Peça essencial para dar partida no carro velho.",
            kind = InteractKind.ITEM,
            x = 2.4f, y = UPPER_FLOOR_Y + 0.86f, z = -19.8f,
            r = 0.78f, g = 0.86f, b = 0.95f,
        ),
        WorldItemDef(
            id = "battery_attic",
            name = "Pilha de Reserva",
            description = "Recarrega +55% da bateria da lanterna.",
            kind = InteractKind.ITEM,
            x = 9.8f, y = UPPER_FLOOR_Y + 0.72f, z = -19.8f,
            r = 0.32f, g = 0.86f, b = 0.45f,
        ),
        WorldItemDef(
            id = "note_attic",
            name = "Carta do Pátio",
            description = "Um recado esquecido no sótão.",
            kind = InteractKind.NOTE,
            x = 7.4f, y = UPPER_FLOOR_Y + 0.72f, z = -18.2f,
            r = 0.86f, g = 0.80f, b = 0.66f,
            noteBody = "\"O carro ainda pega se tiver gasolina e a vela de ignição no motor. A chave caiu perto da pia do banheiro antes da velha me trancar lá em cima...\"",
        ),
        // Inspectable furniture drawers
        WorldItemDef(
            id = "drawer_living",
            name = "Gaveta da Sala",
            description = "Gaveta antiga de madeira rangente.",
            kind = InteractKind.DRAWER,
            x = -13.6f, y = 0.75f, z = 8.8f,
            r = 0.52f, g = 0.38f, b = 0.24f,
        ),
        WorldItemDef(
            id = "drawer_kitchen",
            name = "Armário da Despensa",
            description = "Gavetas antigas cheias de talheres enferrujados.",
            kind = InteractKind.DRAWER,
            x = 14.5f, y = 0.85f, z = 8.8f,
            r = 0.52f, g = 0.38f, b = 0.24f,
        ),
        WorldItemDef(
            id = "drawer_attic",
            name = "Baú Empoeirado",
            description = "Um baú velho no sótão.",
            kind = InteractKind.DRAWER,
            x = 10.6f, y = UPPER_FLOOR_Y + 0.65f, z = -20.8f,
            r = 0.46f, g = 0.34f, b = 0.22f,
        ),
        // Hiding Spots
        WorldItemDef(
            id = "wardrobe_hide",
            name = "Armário Antigo",
            description = "Esconda-se da Vovó aqui dentro.",
            kind = InteractKind.HIDE_CLOSET,
            x = 10.6f, y = 1.15f, z = -2.1f,
            r = 0.34f, g = 0.22f, b = 0.14f,
        ),
        WorldItemDef(
            id = "closet_bath_hide",
            name = "Armário do Banheiro",
            description = "Esconda-se dentro do armário de toalhas.",
            kind = InteractKind.HIDE_CLOSET,
            x = -15.2f, y = 1.15f, z = -10.0f,
            r = 0.34f, g = 0.22f, b = 0.14f,
        ),
        WorldItemDef(
            id = "wardrobe_upper_hide",
            name = "Guarda-Roupa do Sótão",
            description = "Esconda-se aqui em cima longe dos passos dela.",
            kind = InteractKind.HIDE_CLOSET,
            x = 1.0f, y = UPPER_FLOOR_Y + 1.15f, z = -17.3f,
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
        WorldItemDef(
            id = "escape_car",
            name = "Carro de Fuga",
            description = "Instale Gasolina, Vela de Ignição e use a Chave do Carro para escapar!",
            kind = InteractKind.ESCAPE_CAR,
            x = 20.6f, y = 1.05f, z = 2.6f,
            r = 0.30f, g = 0.34f, b = 0.42f,
        ),
    )

    val navNodes: List<NavNode> = listOf(
        NavNode("foyer", 0.0f, 0f, 7.2f, listOf("hall_mid", "living_e", "kitchen_w")),
        NavNode("hall_mid", 0.0f, 0f, 2.5f, listOf("foyer", "hall_n")),
        NavNode("hall_n", 0.0f, 0f, -1.5f, listOf("hall_mid", "hall_far_n", "study_e", "awake_room")),
        NavNode("hall_far_n", 0.0f, 0f, -6.5f, listOf("hall_n", "cellar_s"), gateDoorId = null),

        NavNode("living_e", -5.2f, 0f, 6.6f, listOf("foyer", "living_w", "study_e"), gateDoorId = null),
        NavNode("living_w", -11.5f, 0f, 6.2f, listOf("living_e")),

        NavNode("study_e", -5.5f, 0f, -2.5f, listOf("hall_n", "living_e", "study_w", "nw_bed_s", "cellar_secret")),
        NavNode("study_w", -12.8f, 0f, -4.5f, listOf("study_e", "bathroom")),

        NavNode("kitchen_w", 5.4f, 0f, 6.6f, listOf("foyer", "kitchen_e", "awake_room")),
        NavNode("kitchen_e", 13.8f, 0f, 5.9f, listOf("kitchen_w", "carport")),
        NavNode("carport", 18.2f, 0f, 5.9f, listOf("kitchen_e")),

        NavNode("awake_room", 6.2f, 0f, -2.8f, listOf("hall_n", "kitchen_w", "stair_bot"), nearbyHideSpotId = "bed_awakening_hide"),

        NavNode("bathroom", -13.8f, 0f, -11.5f, listOf("study_w", "nw_bed_s"), nearbyHideSpotId = "closet_bath_hide"),
        NavNode("nw_bed_s", -8.5f, 0f, -10.5f, listOf("study_e", "bathroom"), nearbyHideSpotId = "bed_nw_hide"),

        NavNode("cellar_s", 0.0f, 0f, -10.5f, listOf("hall_far_n", "cellar_secret", "cellar_nw")),
        NavNode("cellar_secret", -4.1f, 0f, -10.0f, listOf("cellar_s", "study_e")),
        NavNode("cellar_nw", -4.0f, 0f, -15.2f, listOf("cellar_s", "cellar_vault")),
        NavNode("cellar_vault", -6.5f, 0f, -18.5f, listOf("cellar_nw")),

        NavNode("stair_bot", 8.6f, 0f, -9.6f, listOf("awake_room", "stair_top")),
        NavNode("stair_top", 9.1f, UPPER_FLOOR_Y, -14.8f, listOf("stair_bot", "attic_main")),
        NavNode("attic_main", 9.0f, UPPER_FLOOR_Y, -18.8f, listOf("stair_top", "upper_room")),
        NavNode("upper_room", 3.5f, UPPER_FLOOR_Y, -18.8f, listOf("attic_main"), nearbyHideSpotId = "wardrobe_upper_hide"),
    )

    private val navById: Map<String, NavNode> = navNodes.associateBy { it.id }

    fun nearestNavNode(x: Float, y: Float, z: Float): NavNode {
        var best = navNodes.first()
        var bestScore = Float.MAX_VALUE
        for (n in navNodes) {
            val dy = (n.y - y) * 3.5f
            val dx = n.x - x
            val dz = n.z - z
            val score = dx * dx + dz * dz + dy * dy
            if (score < bestScore) {
                bestScore = score
                best = n
            }
        }
        return best
    }

    private fun canTraverseNavEdge(a: NavNode, b: NavNode, doors: List<DoorState>): Boolean {
        val pair = setOf(a.id, b.id)
        val blockingDoorId = when (pair) {
            setOf("foyer", "living_e") -> "door_living"
            setOf("foyer", "kitchen_w") -> "door_kitchen"
            setOf("living_e", "study_e") -> "door_living_study"
            setOf("kitchen_w", "awake_room") -> "door_kitchen_bedroom"
            setOf("kitchen_e", "carport") -> "door_back_yard"
            setOf("hall_n", "study_e") -> "door_study"
            setOf("hall_n", "awake_room") -> "door_granny_room"
            setOf("study_w", "bathroom") -> "door_bathroom"
            setOf("bathroom", "nw_bed_s") -> "door_bath_nw"
            setOf("study_e", "nw_bed_s") -> "door_bedroom_nw"
            setOf("study_e", "cellar_secret") -> "secret_bookshelf_door"
            setOf("awake_room", "stair_bot") -> "door_stairhall"
            setOf("hall_far_n", "cellar_s") -> "door_cellar"
            setOf("stair_top", "attic_main") -> "door_attic"
            setOf("attic_main", "upper_room") -> "door_upper_room"
            else -> null
        } ?: return true

        val door = doors.firstOrNull { it.def.id == blockingDoorId } ?: return true
        if (door.def.isSecretBookshelf) {
            return door.isUnlocked && door.isOpen
        }
        return door.isUnlocked
    }

    fun findNavPath(
        startX: Float,
        startY: Float,
        startZ: Float,
        targetX: Float,
        targetY: Float,
        targetZ: Float,
        doors: List<DoorState>,
    ): List<NavNode> {
        val start = nearestNavNode(startX, startY, startZ)
        val goal = nearestNavNode(targetX, targetY, targetZ)
        if (start.id == goal.id) return listOf(goal)

        val queue = ArrayDeque<String>()
        val parent = HashMap<String, String?>()
        queue.addLast(start.id)
        parent[start.id] = null

        var found = false
        while (queue.isNotEmpty()) {
            val curId = queue.removeFirst()
            if (curId == goal.id) {
                found = true
                break
            }
            val cur = navById[curId] ?: continue
            for (nextId in cur.neighbors) {
                if (parent.containsKey(nextId)) continue
                val next = navById[nextId] ?: continue
                if (!canTraverseNavEdge(cur, next, doors)) continue
                parent[nextId] = curId
                queue.addLast(nextId)
            }
        }
        if (!found) return listOf(goal)

        val reversed = ArrayList<NavNode>()
        var trace: String? = goal.id
        while (trace != null) {
            val node = navById[trace]
            if (node != null) reversed.add(node)
            trace = parent[trace]
        }
        reversed.reverse()
        return if (reversed.size > 1) reversed.drop(1) else reversed
    }

    fun checkCircleCollision(
        x: Float,
        z: Float,
        radius: Float,
        doors: List<DoorState>,
        entityFloorY: Float = 0f,
    ): Boolean {
        for (c in wallColliders) {
            if (!c.blocksMovement) continue
            if (entityFloorY + 1.55f <= c.minY || entityFloorY >= c.maxY) continue
            val closestX = x.coerceIn(c.minX, c.maxX)
            val closestZ = z.coerceIn(c.minZ, c.maxZ)
            val dx = x - closestX
            val dz = z - closestZ
            if (dx * dx + dz * dz < radius * radius) return true
        }
        // Check closed doors at entity's vertical floor
        for (d in doors) {
            if (entityFloorY + 1.55f <= d.def.baseY || entityFloorY >= d.def.baseY + d.def.height) continue
            if (abs(d.currentAngleDeg) > 45f) continue
            val yawRad = (d.def.closedYawDeg + d.currentAngleDeg) * (PI.toFloat() / 180f)
            val endX = d.def.hingeX + cos(yawRad) * d.def.width
            val endZ = d.def.hingeZ + sin(yawRad) * d.def.width
            val distSq = distPointToSegmentSq(x, z, d.def.hingeX, d.def.hingeZ, endX, endZ)
            val minDist = radius + d.def.thickness * 0.6f
            if (distSq < minDist * minDist) return true
        }
        return false
    }

    /**
     * Probes forward along view yaw to estimate distance to nearest wall/door for first-person hand tucking.
     */
    fun rayDistToSolidAhead(
        camX: Float,
        camZ: Float,
        yawRad: Float,
        doors: List<DoorState>,
        entityFloorY: Float = 0f,
        maxDist: Float = 0.60f,
    ): Float {
        val dirX = sin(yawRad)
        val dirZ = cos(yawRad)
        val steps = 5
        for (i in 1..steps) {
            val d = (i.toFloat() / steps) * maxDist
            if (checkCircleCollision(camX + dirX * d, camZ + dirZ * d, 0.14f, doors, entityFloorY)) {
                return d
            }
        }
        return maxDist
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
        entityFloorY: Float = floorHeightAt(curX, curZ),
    ): Pair<Float, Float> {
        var nx = curX
        var nz = curZ
        if (!checkCircleCollision(curX + dx, curZ, PLAYER_RADIUS, doors, entityFloorY)) {
            nx = curX + dx
        }
        if (!checkCircleCollision(nx, curZ + dz, PLAYER_RADIUS, doors, entityFloorY)) {
            nz = curZ + dz
        }
        return nx to nz
    }

    private fun buildStaticColliders(): List<BoxCollider> {
        val list = mutableListOf<BoxCollider>()
        // Road world bounds (prevent falling off the forest road)
        list.add(BoxCollider("road_west_bound", -16.5f, 10.2f, -6.5f, 34f))
        list.add(BoxCollider("road_east_bound", 6.5f, 10.2f, 16.5f, 34f))
        list.add(BoxCollider("road_south_bound", -16.5f, 31f, 16.5f, 35f))

        // House South exterior walls (Z = +10 with front door opening X in [-0.85, 0.85])
        list.add(BoxCollider("ext_south_w", -16.2f, 9.8f, -0.85f, 10.2f))
        list.add(BoxCollider("ext_south_e", 0.85f, 9.8f, 16.7f, 10.2f))

        // West exterior walls (expanded Living + Study + Bathroom at X = -16)
        list.add(BoxCollider("ext_west", -16.25f, -16.2f, -15.85f, 10.2f))
        // East exterior walls: Kitchen (X=16.5, Z=2..10 with back door at Z=5.20..6.65)
        list.add(BoxCollider("ext_east_kitchen_s", 16.35f, 6.65f, 16.70f, 10.2f))
        list.add(BoxCollider("ext_east_kitchen_n", 16.35f, 1.85f, 16.70f, 5.20f))
        // Kitchen north exterior wall segment facing Carport yard (X: 12..16.5, Z=2.0)
        list.add(BoxCollider("ext_kitchen_north_yard", 12.0f, 1.85f, 16.7f, 2.15f))
        // Awakening Bedroom & Stairhall East wall (X = 12.0, Z = -16.2..2.0)
        list.add(BoxCollider("ext_east_bedroom_stair", 11.88f, -16.2f, 12.22f, 2.15f))

        // Carport / Exterior Yard fences & Escape Car collision box
        list.add(BoxCollider("yard_fence_s", 16.5f, 10.2f, 24.8f, 10.6f))
        list.add(BoxCollider("yard_fence_e", 24.4f, -6.2f, 24.8f, 10.6f))
        list.add(BoxCollider("yard_fence_n", 12.0f, -6.2f, 24.8f, -5.8f))
        list.add(BoxCollider("escape_car_collider", 19.0f, 1.3f, 23.4f, 3.9f))

        // Exterior & North Wing Ground Walls
        list.add(BoxCollider("bath_north", -16.2f, -16.2f, -11.9f, -15.85f))
        list.add(BoxCollider("nw_room_north", -12.2f, -16.2f, -5.9f, -15.85f))

        // Cellar Vault (X: -12..0, Z: -22..-16)
        list.add(BoxCollider("cellar_vault_w", -12.2f, -22.2f, -11.85f, -16.0f))
        list.add(BoxCollider("cellar_vault_n", -12.2f, -22.2f, 0.15f, -21.85f))
        list.add(BoxCollider("cellar_vault_e", -0.15f, -22.2f, 0.15f, -16.0f))
        // Divider between Cellar Main & Cellar Vault at Z=-16 (archway X=-4.65..-3.15)
        list.add(BoxCollider("cellar_arch_w", -6.2f, -16.2f, -4.65f, -15.85f, minY = 0f, maxY = 3.3f))
        list.add(BoxCollider("cellar_arch_e", -3.15f, -16.2f, 6.2f, -15.85f, minY = 0f, maxY = 3.3f))

        // North wall at Z = -8 (doorways: Bath [-14.60,-13.15], NW [-9.20,-7.75], Secret [-4.80,-3.35], Cellar [-0.75,0.75], Stair [6.85,8.30])
        list.add(BoxCollider("house_north_1", -16.2f, -8.15f, -14.60f, -7.85f))
        list.add(BoxCollider("house_north_2", -13.15f, -8.15f, -9.20f, -7.85f))
        list.add(BoxCollider("house_north_3", -7.75f, -8.15f, -4.80f, -7.85f))
        list.add(BoxCollider("house_north_4", -3.35f, -8.15f, -0.75f, -7.85f))
        list.add(BoxCollider("house_north_5", 0.75f, -8.15f, 6.85f, -7.85f))
        list.add(BoxCollider("house_north_6", 8.30f, -8.15f, 12.2f, -7.85f))

        // North Wing Interior Dividers (Z = -16..-8)
        // Bathroom / NW Bedroom at X = -12 (doorway Z = -11.20..-9.75)
        list.add(BoxCollider("bath_nw_1", -12.15f, -16.0f, -11.85f, -11.20f))
        list.add(BoxCollider("bath_nw_2", -12.15f, -9.75f, -11.85f, -8.0f))
        // NW Bedroom / Cellar at X = -6
        list.add(BoxCollider("cellar_w", -6.2f, -16.2f, -5.85f, -7.85f))
        // Cellar / Stairhall at X = +6 (ground floor only: Y=0..3.3)
        list.add(BoxCollider("cellar_e", 5.85f, -16.2f, 6.2f, -7.85f, minY = 0f, maxY = 4.7f))

        // Hallway partitions along X = -3 and X = +3
        list.add(BoxCollider("hall_w1", -3.15f, 7.55f, -2.85f, 10.0f))
        list.add(BoxCollider("hall_w2", -3.15f, -0.75f, -2.85f, 6.1f))
        list.add(BoxCollider("hall_w3", -3.15f, -8.0f, -2.85f, -2.2f))

        list.add(BoxCollider("hall_e1", 2.85f, 7.55f, 3.15f, 10.0f))
        list.add(BoxCollider("hall_e2", 2.85f, -0.75f, 3.15f, 6.1f))
        list.add(BoxCollider("hall_e3", 2.85f, -8.0f, 3.15f, -2.2f))

        // East-West room partitions at Z = 2 with connecting doorways
        list.add(BoxCollider("div_west_1", -16.0f, 1.85f, -7.50f, 2.15f))
        list.add(BoxCollider("div_west_2", -6.05f, 1.85f, -3.0f, 2.15f))
        list.add(BoxCollider("div_east_1", 3.0f, 1.85f, 5.20f, 2.15f))
        list.add(BoxCollider("div_east_2", 6.65f, 1.85f, 12.0f, 2.15f))

        // Upper Floor & Attic Walls (Y >= 1.90f, X: 0..12, Z: -22.2..-16.0)
        // Attic South Wall at Z = -16.0 (door_attic opening at X = 8.40..9.85, minY = 1.90f)
        list.add(BoxCollider("attic_south_1", 6.0f, -16.15f, 8.40f, -15.85f, minY = 1.90f, maxY = 5.0f))
        list.add(BoxCollider("attic_south_2", 9.85f, -16.15f, 12.2f, -15.85f, minY = 1.90f, maxY = 5.0f))
        list.add(BoxCollider("attic_east", 11.85f, -22.2f, 12.2f, -15.8f, minY = 1.90f, maxY = 5.0f))
        list.add(BoxCollider("attic_north", -0.15f, -22.2f, 12.2f, -21.85f, minY = 1.90f, maxY = 5.0f))
        list.add(BoxCollider("attic_west", -0.15f, -22.2f, 0.15f, -15.8f, minY = 1.90f, maxY = 5.0f))
        list.add(BoxCollider("upper_room_south", -0.15f, -16.15f, 6.0f, -15.85f, minY = 1.90f, maxY = 5.0f))
        // Attic / Upper Room interior divider at X = 6.0 (door_upper_room at Z = -19.80..-18.35)
        list.add(BoxCollider("upper_div_n", 5.85f, -22.0f, 6.15f, -19.80f, minY = 1.90f, maxY = 5.0f))
        list.add(BoxCollider("upper_div_s", 5.85f, -18.35f, 6.15f, -16.0f, minY = 1.90f, maxY = 5.0f))

        // Furniture colliders (carefully spaced so all doorways & loops stay open!)
        list.add(BoxCollider("furn_living_table", -7.0f, 7.8f, -5.4f, 9.0f, maxY = 1.8f))
        list.add(BoxCollider("furn_living_sofa", -10.3f, 3.2f, -7.6f, 4.4f, maxY = 1.8f))
        list.add(BoxCollider("furn_living_piano", -14.8f, 8.2f, -12.6f, 9.6f, maxY = 2.0f))
        list.add(BoxCollider("furn_fireplace", -15.9f, 5.0f, -14.8f, 7.0f, maxY = 2.6f))
        list.add(BoxCollider("furn_kitchen_counter", 8.8f, 7.8f, 11.4f, 9.4f, maxY = 1.8f))
        list.add(BoxCollider("furn_kitchen_pantry", 13.4f, 8.2f, 15.8f, 9.5f, maxY = 2.4f))
        list.add(BoxCollider("furn_butcher_table", 6.2f, 4.4f, 8.4f, 6.2f, maxY = 1.8f))
        list.add(BoxCollider("furn_study_desk", -10.6f, -6.2f, -8.6f, -4.8f, maxY = 1.8f))
        list.add(BoxCollider("furn_study_bookshelf", -15.8f, -7.6f, -14.8f, -3.0f, maxY = 2.6f))
        // Bathroom fixtures
        list.add(BoxCollider("furn_bath_tub", -15.6f, -15.4f, -13.6f, -13.4f, maxY = 1.8f))
        list.add(BoxCollider("furn_bath_closet", -15.8f, -11.0f, -14.8f, -9.2f, maxY = 2.4f))
        // Awakening Bedroom furniture
        list.add(BoxCollider("furn_awakening_bed", 7.4f, -6.9f, 10.6f, -4.9f, maxY = 1.8f))
        list.add(BoxCollider("furn_awakening_nightstand", 9.4f, -4.6f, 10.4f, -3.7f, maxY = 1.8f))
        list.add(BoxCollider("furn_awakening_dresser", 4.2f, -7.8f, 5.8f, -6.8f, maxY = 1.8f))
        list.add(BoxCollider("furn_wardrobe", 10.3f, -2.9f, 11.7f, -1.3f, maxY = 2.5f))
        // NW Master Bedroom furniture
        list.add(BoxCollider("furn_nw_bed", -11.4f, -15.2f, -8.4f, -13.2f, maxY = 1.8f))
        list.add(BoxCollider("furn_nw_wardrobe", -7.4f, -15.4f, -6.2f, -13.8f, maxY = 2.5f))
        // North-East Wooden Staircase side rails
        list.add(BoxCollider("stair_rail_w", 6.85f, -15.6f, 7.15f, -10.1f))
        list.add(BoxCollider("stair_rail_e", 11.55f, -15.6f, 11.85f, -10.1f))
        // Cellar & Vault props
        list.add(BoxCollider("furn_cellar_crates", -4.6f, -14.8f, -2.8f, -13.0f, maxY = 1.8f))
        list.add(BoxCollider("furn_cellar_boiler", -11.5f, -21.4f, -9.6f, -19.2f, maxY = 2.6f))
        // Attic & Upper Bedroom props (minY = 1.90f)
        list.add(BoxCollider("furn_attic_trunk", 10.0f, -21.4f, 11.4f, -20.0f, minY = 1.90f, maxY = 4.2f))
        list.add(BoxCollider("furn_upper_wardrobe", 0.4f, -18.1f, 1.5f, -16.6f, minY = 1.90f, maxY = 4.4f))
        list.add(BoxCollider("furn_upper_table", 1.8f, -20.6f, 3.2f, -19.2f, minY = 1.90f, maxY = 3.8f))
        return list
    }
}
