package com.example.engine3d

import android.graphics.Bitmap
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class TextureKind {
    ASPHALT_ROAD,
    FOREST_GROUND,
    WOOD_FLOOR,
    VICTORIAN_WALLPAPER,
    TREE_BARK,
    PINE_FOLIAGE,
    STONE_CELLAR,
    METAL_PROP,
    GRANNY_SKIN,
    GRANNY_DRESS,
    GRANNY_HAIR,
    PLAYER_JACKET,
    PLAYER_SKIN,
    PLAYER_HAIR,
    WALL_PORTRAIT,
    BED_FABRIC,
    CARVED_OAK,
    WINDOW_GLOW,
    BLOOD_SPLATTER,
    FOG_PUFF,
    SOFT_SHADOW,
    LANTERN_CORE,
    ROOF_SHINGLES,
}

class TextureAtlasSet {
    private val glIds = HashMap<TextureKind, Int>(TextureKind.entries.size)

    fun uploadAll(resolutionPx: Int = 256) {
        releaseAll()
        val size = resolutionPx.coerceIn(128, 512)
        for (kind in TextureKind.entries) {
            val bmp = ProceduralTextures3D.generateBitmap(kind, size)
            val texId = uploadBitmap(bmp)
            bmp.recycle()
            glIds[kind] = texId
        }
    }

    fun idFor(kind: TextureKind): Int = glIds[kind] ?: 0

    fun releaseAll() {
        if (glIds.isEmpty()) return
        val ids = glIds.values.toIntArray()
        GLES20.glDeleteTextures(ids.size, ids, 0)
        glIds.clear()
    }

    private fun uploadBitmap(bitmap: Bitmap): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val texId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        return texId
    }
}

object ProceduralTextures3D {

    fun generateBitmap(kind: TextureKind, size: Int = 256): Bitmap {
        val pixels = IntArray(size * size)
        val inv = 1f / size.toFloat()

        for (y in 0 until size) {
            val v = y * inv
            for (x in 0 until size) {
                val u = x * inv
                pixels[y * size + x] = samplePixel(kind, u, v, x, y, size)
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private fun samplePixel(kind: TextureKind, u: Float, v: Float, px: Int, py: Int, size: Int): Int {
        val n1 = fbm(u * 8f, v * 8f)
        val n2 = fbm(u * 24f + 7.3f, v * 24f + 2.9f)
        val grain = hash2(px, py) * 0.10f - 0.05f

        return when (kind) {
            TextureKind.ASPHALT_ROAD -> {
                // Wet dark asphalt with cracked tar veins and damp sheen variation
                val puddle = smoothstep(0.62f, 0.78f, n1)
                val speckle = (n2 - 0.5f) * 0.16f + grain * 0.7f
                val centerCrack = abs(u - 0.52f + 0.08f * sin(v * 6.28f))
                val crackDark = if (centerCrack < 0.012f && n1 > 0.45f) 0.55f else 1f
                val base = (0.14f + speckle - puddle * 0.04f) * crackDark
                argb(base, base * 1.02f, base * 1.12f)
            }

            TextureKind.FOREST_GROUND -> {
                // Dark soil, damp moss patches & dead pine needles
                val moss = smoothstep(0.45f, 0.72f, n1)
                val r = 0.08f + moss * 0.04f + n2 * 0.05f + grain * 0.3f
                val g = 0.11f + moss * 0.10f + n2 * 0.05f + grain * 0.3f
                val b = 0.06f + moss * 0.02f + n2 * 0.03f
                argb(r, g, b)
            }

            TextureKind.WOOD_FLOOR -> {
                // Aged floorboards (4 planks across U, long staggered seams across V)
                val plankIdx = (u * 5f).toInt()
                val plankLocalU = (u * 5f) - plankIdx
                val staggerV = (v + plankIdx * 0.37f) % 1f
                val seam = if (plankLocalU < 0.035f || staggerV < 0.018f) 0.35f else 1f
                val woodRings = sin((plankLocalU * 14f + n1 * 4.5f) * PI.toFloat()) * 0.5f + 0.5f
                val plankTone = 0.85f + 0.25f * hash2(plankIdx + 3, 19)
                val r = (0.28f + woodRings * 0.10f + n2 * 0.06f) * plankTone * seam
                val g = (0.17f + woodRings * 0.06f + n2 * 0.04f) * plankTone * seam
                val b = (0.10f + woodRings * 0.04f + n2 * 0.03f) * plankTone * seam
                argb(r, g, b)
            }

            TextureKind.VICTORIAN_WALLPAPER -> {
                // Faded damask/striped Victorian wallpaper with damp mold & peeling base
                val stripe = sin(u * 12f * PI.toFloat() * 2f) * 0.5f + 0.5f
                val mold = smoothstep(0.55f, 0.85f, fbm(u * 5f + 2f, v * 5f + 4f))
                val wainscoting = if (v > 0.72f) 0.65f else 1.0f
                val r = (0.28f + stripe * 0.06f - mold * 0.10f + grain * 0.3f) * wainscoting
                val g = (0.24f + stripe * 0.05f - mold * 0.07f + grain * 0.3f) * wainscoting
                val b = (0.19f + stripe * 0.04f - mold * 0.06f) * wainscoting
                argb(r, g, b)
            }

            TextureKind.TREE_BARK -> {
                // Deep vertical bark furrows
                val furrow = abs(sin((u * 10f + n1 * 1.4f) * PI.toFloat()))
                val ridge = 0.55f + 0.45f * furrow
                val r = (0.17f + n2 * 0.07f) * ridge
                val g = (0.12f + n2 * 0.05f) * ridge
                val b = (0.08f + n2 * 0.03f) * ridge
                argb(r, g, b)
            }

            TextureKind.PINE_FOLIAGE -> {
                // Layered conifer needles
                val needle = abs(sin((u * 22f + v * 16f + n2 * 3f) * PI.toFloat()))
                val r = 0.04f + needle * 0.04f + n1 * 0.03f
                val g = 0.14f + needle * 0.10f + n1 * 0.06f
                val b = 0.06f + needle * 0.04f + n1 * 0.03f
                argb(r, g, b)
            }

            TextureKind.STONE_CELLAR -> {
                // Damp cellar stone masonry with dark mortar joints
                val row = (v * 6f).toInt()
                val offsetU = if (row % 2 == 1) 0.5f else 0f
                val cu = (u * 4f + offsetU) % 1f
                val cv = (v * 6f) % 1f
                val mortar = if (cu < 0.05f || cv < 0.07f) 0.42f else 1f
                val stoneVar = 0.80f + 0.32f * hash2((u * 4f + offsetU).toInt(), row)
                val base = (0.20f + n1 * 0.10f + n2 * 0.05f) * stoneVar * mortar
                argb(base * 0.95f, base * 0.98f, base * 1.06f)
            }

            TextureKind.METAL_PROP -> {
                // Dark brushed iron / weathered brass sheen
                val brush = sin(v * 64f + n1 * 3f) * 0.03f
                val rust = smoothstep(0.65f, 0.88f, n1) * 0.14f
                val base = 0.28f + n2 * 0.10f + brush
                argb(base + rust, base + rust * 0.4f, base * 1.05f)
            }

            TextureKind.GRANNY_SKIN -> {
                // Pale, wrinkled, bloodless aged skin with sunken shadow lines
                val wrinkles = sin(v * 38f + n1 * 6f) * 0.035f + sin(u * 26f + n2 * 4f) * 0.02f
                val ageSpot = smoothstep(0.68f, 0.85f, fbm(u * 12f + 9f, v * 12f)) * 0.08f
                val r = 0.68f + wrinkles - ageSpot + grain * 0.25f
                val g = 0.60f + wrinkles - ageSpot * 1.1f + grain * 0.25f
                val b = 0.54f + wrinkles - ageSpot * 1.2f + grain * 0.20f
                argb(r, g, b)
            }

            TextureKind.GRANNY_DRESS -> {
                // Tattered Victorian mourning wool + apron weave + dried blood stains
                val weave = (sin(u * 90f) * sin(v * 90f)) * 0.025f
                val bloodSpot1 = radialBlob(u, v, 0.45f, 0.42f, 0.16f, n1)
                val bloodSpot2 = radialBlob(u, v, 0.58f, 0.68f, 0.20f, n2)
                val blood = maxOf(bloodSpot1, bloodSpot2).coerceIn(0f, 1f)
                val clothR = 0.17f + n1 * 0.05f + weave
                val clothG = 0.15f + n1 * 0.04f + weave
                val clothB = 0.19f + n1 * 0.05f + weave
                val r = clothR * (1f - blood) + 0.48f * blood
                val g = clothG * (1f - blood) + 0.04f * blood
                val b = clothB * (1f - blood) + 0.05f * blood
                argb(r, g, b)
            }

            TextureKind.GRANNY_HAIR -> {
                // Streaked silver-gray brittle hair strands
                val strand = sin(u * 55f + n1 * 5f) * 0.5f + 0.5f
                val base = 0.46f + strand * 0.26f + n2 * 0.08f
                argb(base * 0.96f, base * 0.97f, base * 1.02f)
            }

            TextureKind.PLAYER_JACKET -> {
                // Dark charcoal field jacket denim/canvas weave for first-person arms & lower body
                val weave = (sin(u * 80f) + cos(v * 80f)) * 0.02f
                val r = 0.14f + n1 * 0.05f + weave
                val g = 0.17f + n1 * 0.05f + weave
                val b = 0.21f + n1 * 0.06f + weave
                argb(r, g, b)
            }

            TextureKind.PLAYER_SKIN -> {
                // Warm natural human skin for player hands, face & forearms
                val r = 0.78f + n1 * 0.05f + grain * 0.2f
                val g = 0.60f + n1 * 0.04f + grain * 0.2f
                val b = 0.48f + n1 * 0.03f + grain * 0.15f
                argb(r, g, b)
            }

            TextureKind.PLAYER_HAIR -> {
                // Dark chestnut brown hair strands for protagonist
                val strand = sin(u * 60f + n1 * 4f) * 0.5f + 0.5f
                val base = 0.09f + strand * 0.08f + n2 * 0.04f
                argb(base * 1.15f, base * 0.85f, base * 0.65f)
            }

            TextureKind.WALL_PORTRAIT -> {
                // Ornate dark wooden picture frame enclosing an eerie shadowy silhouette canvas
                val border = u < 0.10f || u > 0.90f || v < 0.09f || v > 0.91f
                if (border) {
                    val trim = if (u in 0.06f..0.08f || u in 0.92f..0.94f || v in 0.05f..0.07f || v in 0.93f..0.95f) 1.4f else 1f
                    argb(0.16f * trim, 0.10f * trim, 0.06f * trim)
                } else {
                    val headBlob = radialBlob(u, v, 0.5f, 0.36f, 0.18f, n1 * 0.4f)
                    val shoulderBlob = radialBlob(u, v, 0.5f, 0.72f, 0.32f, n2 * 0.4f)
                    val figure = maxOf(headBlob, shoulderBlob)
                    val bg = 0.12f + n1 * 0.06f
                    val col = bg * (1f - figure * 0.75f) + 0.03f * figure
                    argb(col * 1.08f, col * 0.96f, col * 0.88f)
                }
            }

            TextureKind.BED_FABRIC -> {
                // Faded antique crimson/oatmeal quilted blanket weave with stitched diamond pattern
                val diamond = abs(sin((u + v) * 8f * PI.toFloat()) * sin((u - v) * 8f * PI.toFloat()))
                val wear = smoothstep(0.55f, 0.88f, n1) * 0.12f
                val weave = (sin(u * 72f) + cos(v * 72f)) * 0.018f
                val r = 0.34f + diamond * 0.08f - wear + weave
                val g = 0.14f + diamond * 0.04f - wear * 0.4f + weave
                val b = 0.15f + diamond * 0.04f - wear * 0.4f + weave
                argb(r, g, b)
            }

            TextureKind.CARVED_OAK -> {
                // Dark aged mahogany/oak furniture & staircase grain with subtle varnish sheen
                val grainRings = sin((u * 9f + n1 * 3.8f) * PI.toFloat()) * 0.5f + 0.5f
                val bevel = if (u < 0.06f || u > 0.94f || v < 0.06f || v > 0.94f) 0.74f else 1f
                val r = (0.21f + grainRings * 0.09f + n2 * 0.04f) * bevel
                val g = (0.12f + grainRings * 0.05f + n2 * 0.03f) * bevel
                val b = (0.07f + grainRings * 0.03f + n2 * 0.02f) * bevel
                argb(r, g, b)
            }

            TextureKind.WINDOW_GLOW -> {
                // Warm glowing Victorian paned window (wooden mullions + amber panes)
                val frame = u < 0.08f || u > 0.92f || v < 0.08f || v > 0.92f ||
                    abs(u - 0.5f) < 0.04f || abs(v - 0.5f) < 0.04f
                if (frame) {
                    argb(0.11f, 0.08f, 0.06f)
                } else {
                    val glow = 0.72f + 0.28f * (1f - sqrt((u - 0.5f) * (u - 0.5f) + (v - 0.5f) * (v - 0.5f)))
                    argb(0.96f * glow, 0.66f * glow, 0.28f * glow)
                }
            }

            TextureKind.BLOOD_SPLATTER -> {
                // Irregular organic blood pool with alpha cutout/fade
                val dx = u - 0.5f
                val dy = v - 0.5f
                val dist = sqrt(dx * dx + dy * dy) + (n1 - 0.5f) * 0.24f + (n2 - 0.5f) * 0.08f
                val alpha = (1f - smoothstep(0.24f, 0.46f, dist)).coerceIn(0f, 1f)
                val coreDark = 0.24f + 0.22f * smoothstep(0.10f, 0.35f, dist)
                argb(coreDark + 0.18f, 0.02f, 0.03f, alpha * 0.92f)
            }

            TextureKind.FOG_PUFF -> {
                // Soft radial volumetric mist puff for fog layers and light cones
                val dx = u - 0.5f
                val dy = v - 0.5f
                val dist = sqrt(dx * dx + dy * dy) * 2f
                val radial = (1f - smoothstep(0.05f, 0.95f, dist)).coerceIn(0f, 1f)
                val wisp = 0.70f + 0.30f * n1
                val alpha = radial * radial * wisp
                argb(0.58f, 0.66f, 0.78f, alpha)
            }

            TextureKind.SOFT_SHADOW -> {
                // Soft radial dark contact shadow under characters & furniture
                val dx = u - 0.5f
                val dy = v - 0.5f
                val dist = sqrt(dx * dx + dy * dy) * 2f
                val alpha = (1f - smoothstep(0.10f, 0.96f, dist)) * 0.68f
                argb(0.01f, 0.01f, 0.02f, alpha)
            }

            TextureKind.LANTERN_CORE -> {
                // Bright warm lantern glass glow
                val center = 1f - abs(u - 0.5f) * 0.6f
                argb(1.0f * center, 0.78f * center, 0.38f * center)
            }

            TextureKind.ROOF_SHINGLES -> {
                // Overlapping dark slate/cedar roof shingles
                val row = (v * 8f).toInt()
                val localV = (v * 8f) % 1f
                val cu = (u * 6f + (if (row % 2 == 1) 0.5f else 0f)) % 1f
                val edge = if (localV > 0.88f || cu < 0.04f) 0.48f else 1f
                val tone = 0.85f + 0.25f * n1
                argb(0.15f * tone * edge, 0.12f * tone * edge, 0.13f * tone * edge)
            }
        }
    }

    private fun radialBlob(u: Float, v: Float, cx: Float, cy: Float, r: Float, noise: Float): Float {
        val dx = u - cx
        val dy = v - cy
        val d = sqrt(dx * dx + dy * dy) + (noise - 0.5f) * r * 0.9f
        return 1f - smoothstep(r * 0.4f, r, d)
    }

    private fun fbm(x: Float, y: Float): Float {
        return 0.60f * valueNoise(x, y) +
            0.28f * valueNoise(x * 2.1f + 3.7f, y * 2.1f + 1.9f) +
            0.12f * valueNoise(x * 4.3f + 8.1f, y * 4.3f + 5.4f)
    }

    private fun valueNoise(x: Float, y: Float): Float {
        val xi = kotlin.math.floor(x).toInt()
        val yi = kotlin.math.floor(y).toInt()
        val xf = x - xi
        val yf = y - yi
        val ux = xf * xf * (3f - 2f * xf)
        val uy = yf * yf * (3f - 2f * yf)

        val a = hash2(xi, yi)
        val b = hash2(xi + 1, yi)
        val c = hash2(xi, yi + 1)
        val d = hash2(xi + 1, yi + 1)

        val ab = a + (b - a) * ux
        val cd = c + (d - c) * ux
        return ab + (cd - ab) * uy
    }

    private fun hash2(x: Int, y: Int): Float {
        var h = x * 374761393 + y * 668265263
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return ((h and 0x7fffffff) % 10000) / 10000f
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0).coerceAtLeast(1e-5f)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun argb(r: Float, g: Float, b: Float, a: Float = 1.0f): Int {
        val ia = (a.coerceIn(0f, 1f) * 255f).toInt()
        val ir = (r.coerceIn(0f, 1f) * 255f).toInt()
        val ig = (g.coerceIn(0f, 1f) * 255f).toInt()
        val ib = (b.coerceIn(0f, 1f) * 255f).toInt()
        return Color.argb(ia, ir, ig, ib)
    }
}
