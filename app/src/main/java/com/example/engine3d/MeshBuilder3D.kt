package com.example.engine3d

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Axis-aligned bounding box center and half-extents for renderer culling and Filament Box creation.
 */
data class MeshBounds(
    val centerX: Float,
    val centerY: Float,
    val centerZ: Float,
    val halfExtentX: Float,
    val halfExtentY: Float,
    val halfExtentZ: Float,
)

/**
 * Interleaved vertex layout: [x, y, z, nx, ny, nz, u, v] = 8 floats (32 bytes).
 * Indexed triangle mesh for smooth shading, compact memory, and multi-renderer reuse
 * (OpenGL ES 2.0 VBOs or Google Filament VertexBuffer/IndexBuffer).
 */
data class MeshData(
    val vertices: FloatArray,
    val indices: ShortArray,
) {
    val vertexCount: Int get() = vertices.size / 8
    val indexCount: Int get() = indices.size

    fun computeBounds(): MeshBounds {
        val count = vertexCount
        if (count == 0) return MeshBounds(0f, 0f, 0f, 0.01f, 0.01f, 0.01f)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        for (i in 0 until count) {
            val base = i * 8
            val x = vertices[base]
            val y = vertices[base + 1]
            val z = vertices[base + 2]
            minX = min(minX, x); maxX = max(maxX, x)
            minY = min(minY, y); maxY = max(maxY, y)
            minZ = min(minZ, z); maxZ = max(maxZ, z)
        }
        return MeshBounds(
            centerX = (minX + maxX) * 0.5f,
            centerY = (minY + maxY) * 0.5f,
            centerZ = (minZ + maxZ) * 0.5f,
            halfExtentX = ((maxX - minX) * 0.5f).coerceAtLeast(0.005f),
            halfExtentY = ((maxY - minY) * 0.5f).coerceAtLeast(0.005f),
            halfExtentZ = ((maxZ - minZ) * 0.5f).coerceAtLeast(0.005f),
        )
    }

    /**
     * Computes 4-float tangent-space quaternions (qx, qy, qz, qw) per vertex from stored normals,
     * ready for Filament VertexAttribute.TANGENTS (FLOAT4) without requiring an OpenGL context.
     */
    fun computeTangentQuaternions(): FloatArray {
        val count = vertexCount
        val quats = FloatArray(count * 4)
        for (i in 0 until count) {
            val vBase = i * 8
            var nx = vertices[vBase + 3]
            var ny = vertices[vBase + 4]
            var nz = vertices[vBase + 5]
            val nLen = sqrt(nx * nx + ny * ny + nz * nz)
            if (nLen > 1e-5f) {
                nx /= nLen; ny /= nLen; nz /= nLen
            } else {
                nx = 0f; ny = 1f; nz = 0f
            }

            // Pick a reference up vector not collinear with N
            val refX: Float
            val refY: Float
            val refZ: Float
            if (abs(ny) < 0.9f) {
                refX = 0f; refY = 1f; refZ = 0f
            } else {
                refX = 1f; refY = 0f; refZ = 0f
            }

            // T = normalize(ref x N)
            var tx = refY * nz - refZ * ny
            var ty = refZ * nx - refX * nz
            var tz = refX * ny - refY * nx
            val tLen = sqrt(tx * tx + ty * ty + tz * tz).coerceAtLeast(1e-5f)
            tx /= tLen; ty /= tLen; tz /= tLen

            // B = N x T (orthonormal right-handed basis [T B N])
            val bx = ny * tz - nz * ty
            val by = nz * tx - nx * tz
            val bz = nx * ty - ny * tx

            // Convert 3x3 rotation matrix [T B N] to unit quaternion (qx, qy, qz, qw)
            val trace = tx + by + nz
            var qx: Float
            var qy: Float
            var qz: Float
            var qw: Float
            if (trace > 0f) {
                val s = sqrt(trace + 1f) * 2f
                qw = 0.25f * s
                qx = (bz - ny) / s
                qy = (nx - tz) / s
                qz = (ty - bx) / s
            } else if (tx > by && tx > nz) {
                val s = sqrt((1f + tx - by - nz).coerceAtLeast(1e-6f)) * 2f
                qw = (bz - ny) / s
                qx = 0.25f * s
                qy = (bx + ty) / s
                qz = (nx + tz) / s
            } else if (by > nz) {
                val s = sqrt((1f + by - tx - nz).coerceAtLeast(1e-6f)) * 2f
                qw = (nx - tz) / s
                qx = (bx + ty) / s
                qy = 0.25f * s
                qz = (ny + bz) / s
            } else {
                val s = sqrt((1f + nz - tx - by).coerceAtLeast(1e-6f)) * 2f
                qw = (ty - bx) / s
                qx = (nx + tz) / s
                qy = (ny + bz) / s
                qz = 0.25f * s
            }
            val qLen = sqrt(qx * qx + qy * qy + qz * qz + qw * qw).coerceAtLeast(1e-6f)
            val qBase = i * 4
            quats[qBase] = qx / qLen
            quats[qBase + 1] = qy / qLen
            quats[qBase + 2] = qz / qLen
            quats[qBase + 3] = qw / qLen
        }
        return quats
    }

    fun toGpuMesh(): GpuMesh {
        val vboIds = IntArray(2)
        GLES20.glGenBuffers(2, vboIds, 0)
        val vbo = vboIds[0]
        val ibo = vboIds[1]

        val vBuf: FloatBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

        val iBuf: ShortBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indices)
                position(0)
            }

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.size * 4, vBuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, iBuf, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        return GpuMesh(vbo = vbo, ibo = ibo, indexCount = indices.size)
    }
}

data class GpuMesh(
    val vbo: Int,
    val ibo: Int,
    val indexCount: Int,
) {
    fun release() {
        val ids = intArrayOf(vbo, ibo)
        GLES20.glDeleteBuffers(2, ids, 0)
    }
}

class MeshBuilder3D {
    private val verts = ArrayList<Float>(4096)
    private val inds = ArrayList<Short>(4096)

    val currentVertexCount: Int get() = verts.size / 8

    fun addVertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, u: Float, v: Float): Int {
        val idx = currentVertexCount
        val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-5f)
        verts.add(x); verts.add(y); verts.add(z)
        verts.add(nx / len); verts.add(ny / len); verts.add(nz / len)
        verts.add(u); verts.add(v)
        return idx
    }

    fun addTriangle(a: Int, b: Int, c: Int) {
        inds.add(a.toShort())
        inds.add(b.toShort())
        inds.add(c.toShort())
    }

    fun addQuad(a: Int, b: Int, c: Int, d: Int) {
        addTriangle(a, b, c)
        addTriangle(a, c, d)
    }

    fun build(): MeshData = MeshData(
        vertices = verts.toFloatArray(),
        indices = inds.toShortArray(),
    )

    /**
     * Smooth UV-mapped ellipsoid/sphere centered at (cx, cy, cz).
     */
    fun addEllipsoid(
        cx: Float = 0f,
        cy: Float = 0f,
        cz: Float = 0f,
        rx: Float,
        ry: Float,
        rz: Float,
        rings: Int = 12,
        sectors: Int = 18,
        uScale: Float = 1f,
        vScale: Float = 1f,
    ) {
        val base = currentVertexCount
        for (r in 0..rings) {
            val v = r.toFloat() / rings
            val phi = v * PI.toFloat()
            val sinPhi = sin(phi)
            val cosPhi = cos(phi)
            for (s in 0..sectors) {
                val u = s.toFloat() / sectors
                val theta = u * (2f * PI.toFloat())
                val sinTheta = sin(theta)
                val cosTheta = cos(theta)

                val px = sinPhi * cosTheta
                val py = cosPhi
                val pz = sinPhi * sinTheta

                val x = cx + px * rx
                val y = cy + py * ry
                val z = cz + pz * rz

                val nx = px / rx.coerceAtLeast(0.001f)
                val ny = py / ry.coerceAtLeast(0.001f)
                val nz = pz / rz.coerceAtLeast(0.001f)

                addVertex(x, y, z, nx, ny, nz, u * uScale, v * vScale)
            }
        }
        val stride = sectors + 1
        for (r in 0 until rings) {
            for (s in 0 until sectors) {
                val i0 = base + r * stride + s
                val i1 = i0 + 1
                val i2 = i0 + stride + 1
                val i3 = i0 + stride
                addTriangle(i0, i1, i2)
                addTriangle(i0, i2, i3)
            }
        }
    }

    /**
     * Sculpted human head mesh with organic cranium, brow ridge, glabella furrow,
     * sunken eye sockets, sculpted nasal bridge + nostrils, cheekbones, nasolabial folds,
     * parted lips, tapered chin/jawline, and side ears.
     */
    fun addSculptedHead(
        rx: Float = 0.108f,
        ry: Float = 0.132f,
        rz: Float = 0.118f,
        rings: Int = 22,
        sectors: Int = 28,
        gauntness: Float = 0.25f,
    ) {
        val base = currentVertexCount
        for (r in 0..rings) {
            val v = r.toFloat() / rings
            val phi = v * PI.toFloat()
            val sinPhi = sin(phi)
            val cosPhi = cos(phi)

            for (s in 0..sectors) {
                val u = s.toFloat() / sectors
                // theta = PI/2 faces +Z (front of face!)
                val theta = u * (2f * PI.toFloat())
                val sinTheta = sin(theta) // +1 at front (+Z), -1 at back (-Z)
                val cosTheta = cos(theta) // +1 at right (+X), -1 at left (-X)

                var px = sinPhi * cosTheta * rx
                var py = cosPhi * ry
                var pz = sinPhi * sinTheta * rz

                // Narrow lower jaw & chin taper
                if (py < 0f) {
                    val jawT = (-py / ry).coerceIn(0f, 1f)
                    px *= (1f - 0.34f * jawT * jawT)
                    if (pz > 0f) {
                        pz *= (1f - 0.15f * jawT)
                    }
                }
                // Elongate back of cranium & subtle occipital curve
                if (pz < 0f && py > -0.025f) {
                    pz *= 1.12f
                }

                // Front facial sculpting (+Z side)
                if (sinTheta > 0.12f) {
                    val frontWeight = ((sinTheta - 0.12f) / 0.88f).coerceIn(0f, 1f)
                    val latAbs = abs(cosTheta)

                    // Eye socket hollows around y = +0.015..+0.045, x = +-0.036
                    val eyeDx = (latAbs - 0.34f) / 0.22f
                    val eyeDy = (cosPhi - 0.18f) / 0.20f
                    val eyeDistSq = eyeDx * eyeDx + eyeDy * eyeDy
                    if (eyeDistSq < 1f) {
                        val hollow = (1f - eyeDistSq) * (0.015f + 0.007f * gauntness)
                        pz -= hollow * frontWeight
                    }

                    // Brow ridge + angry glabella crease between brows
                    val browDy = (cosPhi - 0.42f) / 0.14f
                    if (browDy * browDy < 1f && latAbs < 0.68f) {
                        val browBump = (1f - browDy * browDy) * (0.011f + 0.004f * gauntness)
                        pz += browBump * frontWeight
                    }
                    if (latAbs < 0.14f && browDy * browDy < 1f) {
                        pz -= (1f - latAbs / 0.14f) * 0.005f * gauntness * frontWeight
                    }

                    // Nose bridge, tip & flared nostrils (alae)
                    val noseLat = (latAbs / 0.19f)
                    val noseVert = ((cosPhi - 0.02f) / 0.26f)
                    if (noseLat < 1f && noseVert > -1f && noseVert < 1f) {
                        val nProfile = (1f - noseLat * noseLat) * (1f - noseVert * noseVert)
                        val tipBoost = if (cosPhi < 0.04f) 1.42f else 0.78f
                        pz += nProfile * 0.030f * tipBoost * frontWeight
                    }
                    // Nostrils flare around cosPhi ~ -0.14, latAbs ~ 0.13..0.24
                    val alaeDx = (latAbs - 0.16f) / 0.09f
                    val alaeDy = (cosPhi + 0.13f) / 0.08f
                    if (alaeDx * alaeDx + alaeDy * alaeDy < 1f) {
                        val alaeBump = (1f - (alaeDx * alaeDx + alaeDy * alaeDy)) * 0.008f
                        pz += alaeBump * frontWeight
                    }

                    // Cheekbones + sunken gaunt cheeks & deep nasolabial folds for elderly realism
                    val cheekDx = (latAbs - 0.48f) / 0.24f
                    val cheekDy = (cosPhi + 0.12f) / 0.17f
                    if (cheekDx * cheekDx + cheekDy * cheekDy < 1f) {
                        pz += (1f - (cheekDx * cheekDx + cheekDy * cheekDy)) * (0.012f + 0.004f * gauntness) * frontWeight
                    }
                    val hollowCheekDy = (cosPhi + 0.34f) / 0.18f
                    if (cheekDx * cheekDx + hollowCheekDy * hollowCheekDy < 1f) {
                        val cHollow = (1f - hollowCheekDy * hollowCheekDy) * (1f - (cheekDx * cheekDx))
                        px *= (1f - 0.17f * gauntness * cHollow)
                        pz -= 0.006f * gauntness * cHollow * frontWeight
                    }

                    // Nasolabial fold crease running diagonally from nose wings to mouth corners
                    if (cosPhi in -0.52f..-0.12f) {
                        val expectedLat = 0.16f + ((-0.12f - cosPhi) / 0.40f) * 0.14f
                        val foldDist = abs(latAbs - expectedLat) / 0.065f
                        if (foldDist < 1f) {
                            pz -= (1f - foldDist * foldDist) * (0.003f + 0.005f * gauntness) * frontWeight
                        }
                    }

                    // Upper & lower lips + parted mouth line at cosPhi ~ -0.39
                    if (latAbs < 0.28f) {
                        val lipArch = 1f - (latAbs / 0.28f) * (latAbs / 0.28f)
                        val upperLipDy = (cosPhi + 0.34f) / 0.055f
                        val lowerLipDy = (cosPhi + 0.44f) / 0.055f
                        val mouthSlitDy = (cosPhi + 0.39f) / 0.032f
                        if (upperLipDy * upperLipDy < 1f) {
                            pz += (1f - upperLipDy * upperLipDy) * 0.007f * lipArch * frontWeight
                        }
                        if (lowerLipDy * lowerLipDy < 1f) {
                            pz += (1f - lowerLipDy * lowerLipDy) * 0.006f * lipArch * frontWeight
                        }
                        if (mouthSlitDy * mouthSlitDy < 1f) {
                            pz -= (1f - mouthSlitDy * mouthSlitDy) * 0.006f * lipArch * frontWeight
                        }
                    }

                    // Chin prominence
                    val chinDy = (cosPhi + 0.72f) / 0.18f
                    if (chinDy * chinDy < 1f && latAbs < 0.35f) {
                        pz += (1f - chinDy * chinDy) * 0.011f * frontWeight
                    }
                }

                // Approximate smooth outward normal
                val nx = px / rx.coerceAtLeast(0.001f)
                val ny = py / ry.coerceAtLeast(0.001f)
                val nz = pz / rz.coerceAtLeast(0.001f)
                addVertex(px, py, pz, nx, ny, nz, u, v)
            }
        }

        val stride = sectors + 1
        for (r in 0 until rings) {
            for (s in 0 until sectors) {
                val i0 = base + r * stride + s
                val i1 = i0 + 1
                val i2 = i0 + stride + 1
                val i3 = i0 + stride
                addTriangle(i0, i1, i2)
                addTriangle(i0, i2, i3)
            }
        }

        // Add subtle sculpted left & right ears along the head sides
        for (side in listOf(-1f, 1f)) {
            addEllipsoid(
                cx = side * (rx * 0.96f),
                cy = 0.002f,
                cz = -0.008f,
                rx = 0.013f,
                ry = 0.030f,
                rz = 0.019f,
                rings = 6,
                sectors = 10,
            )
        }
    }

    /**
     * Tapered organic limb segment extending from yBottom to yTop along local Y axis,
     * with optional elliptical cross section (rx vs rz) and smooth end caps.
     */
    fun addTaperedLimb(
        yBottom: Float,
        yTop: Float,
        rxBottom: Float,
        rzBottom: Float,
        rxTop: Float,
        rzTop: Float,
        rings: Int = 6,
        sectors: Int = 14,
        bulgeFactor: Float = 0f,
        capEnds: Boolean = true,
        uScale: Float = 1f,
        vScale: Float = 1f,
    ) {
        val base = currentVertexCount
        val h = (yTop - yBottom).coerceAtLeast(0.001f)
        for (r in 0..rings) {
            val t = r.toFloat() / rings
            val y = yBottom + t * h
            val muscle = sin(t * PI.toFloat()) * bulgeFactor
            val rx = rxBottom + (rxTop - rxBottom) * t + muscle
            val rz = rzBottom + (rzTop - rzBottom) * t + muscle
            for (s in 0..sectors) {
                val u = s.toFloat() / sectors
                val theta = u * (2f * PI.toFloat())
                val ct = cos(theta)
                val st = sin(theta)
                val x = ct * rx
                val z = st * rz
                addVertex(x, y, z, ct, 0.08f, st, u * uScale, t * vScale)
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

        if (capEnds) {
            val topCenter = addVertex(0f, yTop, 0f, 0f, 1f, 0f, 0.5f, 0.5f)
            val topRingStart = base + rings * stride
            for (s in 0 until sectors) {
                addTriangle(topCenter, topRingStart + s + 1, topRingStart + s)
            }
            val botCenter = addVertex(0f, yBottom, 0f, 0f, -1f, 0f, 0.5f, 0.5f)
            for (s in 0 until sectors) {
                addTriangle(botCenter, base + s, base + s + 1)
            }
        }
    }

    /**
     * Sculpted human torso/ribcage/waist profile from pelvis (yBottom) to shoulders/clavicles (yTop).
     */
    fun addSculptedTorso(
        yBottom: Float = 0f,
        yTop: Float = 0.52f,
        hipRx: Float = 0.145f,
        hipRz: Float = 0.115f,
        waistRx: Float = 0.125f,
        waistRz: Float = 0.095f,
        chestRx: Float = 0.175f,
        chestRz: Float = 0.122f,
        shoulderRx: Float = 0.195f,
        shoulderRz: Float = 0.105f,
        hunchCurveZ: Float = 0.025f,
        sectors: Int = 20,
    ) {
        val profile = listOf(
            Triple(0.00f, hipRx, hipRz),
            Triple(0.18f, waistRx, waistRz),
            Triple(0.46f, chestRx * 0.94f, chestRz * 0.96f),
            Triple(0.68f, chestRx, chestRz),
            Triple(0.86f, shoulderRx, shoulderRz),
            Triple(0.95f, shoulderRx * 0.65f, shoulderRz * 0.72f),
            Triple(1.00f, shoulderRx * 0.36f, shoulderRz * 0.52f),
        )
        val base = currentVertexCount
        val h = yTop - yBottom
        for (spec in profile) {
            val t = spec.first
            val rx = spec.second
            val rz = spec.third
            val y = yBottom + t * h
            val zOff = sin(t * PI.toFloat()) * hunchCurveZ
            for (s in 0..sectors) {
                val u = s.toFloat() / sectors
                val theta = u * (2f * PI.toFloat())
                val ct = cos(theta)
                val st = sin(theta)
                // Subtle chest / spine anatomical shaping
                val frontFold = if (st > 0.3f && t in 0.25f..0.85f) 1.04f else 1.0f
                val spineGroove = if (st < -0.75f && abs(ct) < 0.22f) 0.92f else 1.0f
                addVertex(ct * rx, y, st * rz * frontFold * spineGroove + zOff, ct, 0.14f * t, st, u, t)
            }
        }
        val stride = sectors + 1
        for (r in 0 until profile.size - 1) {
            for (s in 0 until sectors) {
                val i0 = base + r * stride + s
                val i1 = i0 + 1
                val i2 = i0 + stride + 1
                val i3 = i0 + stride
                addTriangle(i0, i2, i1)
                addTriangle(i0, i3, i2)
            }
        }
        val topCenter = addVertex(0f, yTop, 0f, 0f, 1f, 0f, 0.5f, 1f)
        val topRing = base + (profile.size - 1) * stride
        for (s in 0 until sectors) {
            addTriangle(topCenter, topRing + s + 1, topRing + s)
        }
        val botCenter = addVertex(0f, yBottom, 0f, 0f, -1f, 0f, 0.5f, 0f)
        for (s in 0 until sectors) {
            addTriangle(botCenter, base + s, base + s + 1)
        }
    }

    /**
     * Multi-ring ruffled Victorian tattered dress/skirt with rich cloth pleats hanging from waist (yTop) to hem (yBottom).
     */
    fun addFlaredSkirt(
        yBottom: Float = -0.64f,
        yTop: Float = 0.04f,
        topRx: Float = 0.148f,
        topRz: Float = 0.120f,
        botRx: Float = 0.30f,
        botRz: Float = 0.27f,
        rings: Int = 9,
        sectors: Int = 28,
    ) {
        val base = currentVertexCount
        val h = yTop - yBottom
        for (r in 0..rings) {
            val t = r.toFloat() / rings // 0 = hem, 1 = waist
            val yBase = yBottom + t * h
            val flare = (1f - t)
            val rx = topRx + (botRx - topRx) * flare * flare.coerceAtLeast(0.42f)
            val rz = topRz + (botRz - topRz) * flare * flare.coerceAtLeast(0.42f)
            for (s in 0..sectors) {
                val u = s.toFloat() / sectors
                val theta = u * (2f * PI.toFloat())
                // Organic multi-frequency fabric pleats & ragged hemline
                val fold = 1f + flare * (0.09f * sin(theta * 7f) + 0.04f * cos(theta * 13f))
                val tatter = if (r == 0) (sin(theta * 9f) * 0.028f + cos(theta * 15f) * 0.012f) else 0f
                val x = cos(theta) * rx * fold
                val z = sin(theta) * rz * fold
                addVertex(x, yBase + tatter, z, cos(theta), 0.18f, sin(theta), u * 1.5f, 1f - t)
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

    /**
     * Modeled anatomical boot/shoe with distinct heel block, contoured vamp/toe box, and ankle cuff.
     */
    fun addModeledBoot() {
        // Sole & heel block
        addTexturedBox(-0.046f, -0.060f, -0.055f, 0.046f, -0.036f, 0.155f, uvTileMeters = 0.25f)
        addTexturedBox(-0.044f, -0.068f, -0.055f, 0.044f, -0.054f, 0.025f, uvTileMeters = 0.25f)
        // Contoured vamp & toe box
        addEllipsoid(cx = 0f, cy = -0.018f, cz = 0.058f, rx = 0.046f, ry = 0.030f, rz = 0.098f, rings = 8, sectors = 14)
        // Ankle boot cuff
        addTaperedLimb(yBottom = -0.035f, yTop = 0.045f, rxBottom = 0.045f, rzBottom = 0.048f, rxTop = 0.042f, rzTop = 0.045f, rings = 4, sectors = 12)
    }

    /**
     * Continuous, voluminous scalp hair cap conforming to Granny's cranium with a center-parted
     * forehead hairline, swept temple volume, nape taper, and coiled low bun (no stacked spheres).
     */
    fun addSculptedGrannyHairCapAndBun(
        rx: Float = 0.106f,
        ry: Float = 0.130f,
        rz: Float = 0.116f,
        rings: Int = 20,
        sectors: Int = 32,
    ) {
        val base = currentVertexCount
        for (r in 0..rings) {
            val v = r.toFloat() / rings // 0 = crown apex, 1 = hairline/nape edge
            for (s in 0..sectors) {
                val u = s.toFloat() / sectors
                val theta = u * (2f * PI.toFloat())
                val sinTheta = sin(theta) // +1 front (+Z), -1 back (-Z)
                val cosTheta = cos(theta) // +1 right (+X), -1 left (-X)
                val latAbs = abs(cosTheta)

                val frontFactor = sinTheta.coerceAtLeast(0f)
                val backFactor = (-sinTheta).coerceAtLeast(0f)
                val centerPeak = kotlin.math.exp(-(cosTheta * cosTheta) / 0.08f) * (0.042f * PI.toFloat()) * frontFactor
                val phiMax = (0.54f * PI.toFloat()) +
                    backFactor * (0.20f * PI.toFloat()) -
                    frontFactor * (0.24f * PI.toFloat()) +
                    latAbs * (0.04f * PI.toFloat()) +
                    centerPeak

                val phi = v * phiMax
                val sinPhi = sin(phi)
                val cosPhi = cos(phi)

                // Hair volume loft above skull, tapering smoothly at hairline edge (v -> 1)
                val edgeTaper = 1f - v * v * v * 0.72f
                val midLoft = sin(v * PI.toFloat()).coerceAtLeast(0f)
                val partGroove = if (sinTheta > -0.25f) {
                    kotlin.math.exp(-(cosTheta * cosTheta) / 0.016f) * 0.0042f * (1f - v * 0.55f)
                } else 0f
                val sideSwell = kotlin.math.exp(-((latAbs - 0.36f) * (latAbs - 0.36f)) / 0.06f) * 0.0048f * midLoft
                val waveRipple = sin(theta * 18f + v * 3.2f) * 0.0022f * midLoft
                val loft = (0.0025f + 0.0115f * midLoft + 0.0045f * (1f - v) + 0.0035f * backFactor * midLoft + sideSwell + waveRipple - partGroove) * edgeTaper

                var px = sinPhi * cosTheta * (rx + loft)
                var py = cosPhi * (ry + loft * 0.92f)
                var pz = sinPhi * sinTheta * (rz + loft)

                if (py < 0f) {
                    val jawT = (-py / ry).coerceIn(0f, 1f)
                    px *= (1f - 0.28f * jawT * jawT)
                }
                if (pz < 0f && py > -0.025f) {
                    pz *= 1.12f
                }

                val nx = px / (rx + 0.01f)
                val ny = py / (ry + 0.01f)
                val nz = pz / (rz + 0.01f)
                addVertex(px, py, pz, nx, ny, nz, u * 2.2f, v)
            }
        }
        val stride = sectors + 1
        for (r in 0 until rings) {
            for (s in 0 until sectors) {
                val i0 = base + r * stride + s
                val i1 = i0 + 1
                val i2 = i0 + stride + 1
                val i3 = i0 + stride
                addTriangle(i0, i1, i2)
                addTriangle(i0, i2, i3)
            }
        }

        // Sculpted braided/coiled bun tucked snugly against the back of the head (replaces spherical lump)
        val bunBase = currentVertexCount
        val bunRings = 14
        val bunSectors = 22
        val bunCenterY = 0.012f
        val bunCenterZ = -0.124f
        val tiltRad = -0.22f
        val cosTilt = cos(tiltRad)
        val sinTilt = sin(tiltRad)
        for (r in 0..bunRings) {
            val v = r.toFloat() / bunRings
            val phi = v * (2f * PI.toFloat())
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)
            for (s in 0..bunSectors) {
                val u = s.toFloat() / bunSectors
                val theta = u * (2f * PI.toFloat())
                val cosTheta = cos(theta)
                val sinTheta = sin(theta)

                val braidTwist = 1f + 0.14f * sin(theta * 3f + phi * 2f)
                val majorRx = 0.044f
                val majorRy = 0.036f
                val tubeR = 0.024f * braidTwist
                val lx = (majorRx + tubeR * cosPhi) * cosTheta
                val ly = (majorRy + tubeR * cosPhi) * sinTheta
                val lz = tubeR * sinPhi * 0.88f

                // Tilt slightly to conform cleanly to lower occiput
                val ty = ly * cosTilt - lz * sinTilt + bunCenterY
                val tz = ly * sinTilt + lz * cosTilt + bunCenterZ
                val nx = cosPhi * cosTheta
                val ny = cosPhi * sinTheta * cosTilt - sinPhi * sinTilt
                val nz = cosPhi * sinTheta * sinTilt + sinPhi * cosTilt
                addVertex(lx, ty, tz, nx, ny, nz, u * 2f, v)
            }
        }
        val bStride = bunSectors + 1
        for (r in 0 until bunRings) {
            for (s in 0 until bunSectors) {
                val i0 = bunBase + r * bStride + s
                val i1 = i0 + 1
                val i2 = i0 + bStride + 1
                val i3 = i0 + bStride
                addTriangle(i0, i1, i2)
                addTriangle(i0, i2, i3)
            }
        }
        // Core dome inside coil center so the bun has zero hollow gap
        addEllipsoid(cx = 0f, cy = bunCenterY + 0.002f, cz = bunCenterZ + 0.012f, rx = 0.044f, ry = 0.038f, rz = 0.028f, rings = 8, sectors = 12)
    }

    /**
     * Organic, gently curved & ribbon-tapered lock of hair with multi-strand groove ripples.
     */
    fun addSculptedHairLock(
        yTop: Float = 0.025f,
        yBottom: Float = -0.155f,
        rings: Int = 10,
        sectors: Int = 10,
    ) {
        val base = currentVertexCount
        val h = yTop - yBottom
        for (r in 0..rings) {
            val t = r.toFloat() / rings // 0 = bottom tip, 1 = root
            val y = yBottom + t * h
            val taper = sin((t * 0.88f + 0.12f) * PI.toFloat() * 0.5f)
            val rx = 0.0045f + 0.0155f * taper
            val rz = 0.0055f + 0.0185f * taper
            // Gentle S-curve drape outward around cheek/temple and back toward nape
            val swayX = sin((1f - t) * PI.toFloat()) * 0.008f
            val swayZ = sin((1f - t) * PI.toFloat() * 1.4f) * 0.010f - (1f - t) * (1f - t) * 0.018f
            for (s in 0..sectors) {
                val u = s.toFloat() / sectors
                val theta = u * (2f * PI.toFloat())
                val ct = cos(theta)
                val st = sin(theta)
                val strandRib = 1f + 0.14f * sin(theta * 3f + t * 4f)
                val x = swayX + ct * rx * strandRib
                val z = swayZ + st * rz * strandRib
                addVertex(x, y, z, ct, 0.12f, st, u * 1.4f, t)
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
        val topCenter = addVertex(0f, yTop, 0f, 0f, 1f, 0f, 0.5f, 1f)
        val topRing = base + rings * stride
        for (s in 0 until sectors) {
            addTriangle(topCenter, topRing + s + 1, topRing + s)
        }
        val botCenter = addVertex(0f, yBottom, -0.018f, 0f, -1f, 0f, 0.5f, 0f)
        for (s in 0 until sectors) {
            addTriangle(botCenter, base + s, base + s + 1)
        }
    }

    /**
     * Sculpted anatomical palm for Granny with smooth wrist transition, dorsal tendon contour,
     * slight palmar hollow, and arched metacarpal knuckle ridge.
     */
    fun addSculptedGrannyPalm() {
        val profile = listOf(
            // (y, rx, rz, zOffset)
            Triple(0.010f, 0.027f, 0.022f),
            Triple(-0.008f, 0.030f, 0.019f),
            Triple(-0.032f, 0.035f, 0.016f),
            Triple(-0.056f, 0.038f, 0.015f),
            Triple(-0.072f, 0.036f, 0.014f),
            Triple(-0.080f, 0.031f, 0.011f),
        )
        val base = currentVertexCount
        val sectors = 16
        for (r in profile.indices) {
            val (y, rx, rz) = profile[r]
            val t = r.toFloat() / (profile.size - 1)
            for (s in 0..sectors) {
                val u = s.toFloat() / sectors
                val theta = u * (2f * PI.toFloat())
                val ct = cos(theta)
                val st = sin(theta) // +1 palm inside (+Z), -1 dorsal back (-Z)
                // Gentle palmar cup (+Z) and bony dorsal tendons (-Z)
                val palmCup = if (st > 0.25f && t in 0.25f..0.85f) 1f - 0.18f * st * sin(t * PI.toFloat()) else 1f
                val tendonRidge = if (st < -0.35f && t in 0.2f..0.85f) 1f + 0.07f * sin(ct * 4f * PI.toFloat()) else 1f
                // Slight arch across knuckle line
                val archY = if (t > 0.65f) (1f - ct * ct) * -0.003f else 0f
                val x = ct * rx
                val z = st * rz * palmCup * tendonRidge
                addVertex(x, y + archY, z, ct, 0.08f * (0.5f - t), st, u, t)
            }
        }
        val stride = sectors + 1
        for (r in 0 until profile.size - 1) {
            for (s in 0 until sectors) {
                val i0 = base + r * stride + s
                val i1 = i0 + 1
                val i2 = i0 + stride + 1
                val i3 = i0 + stride
                addTriangle(i0, i1, i2)
                addTriangle(i0, i2, i3)
            }
        }
        val topCenter = addVertex(0f, 0.012f, 0f, 0f, 1f, 0f, 0.5f, 0f)
        for (s in 0 until sectors) {
            addTriangle(topCenter, base + s, base + s + 1)
        }
        val botCenter = addVertex(0f, -0.082f, 0f, 0f, -1f, 0f, 0.5f, 1f)
        val botRing = base + (profile.size - 1) * stride
        for (s in 0 until sectors) {
            addTriangle(botCenter, botRing + s + 1, botRing + s)
        }
    }

    /**
     * Sculpted phalanx segment with rounded joint caps at both ends so articulated finger bends stay seamless.
     */
    fun addSculptedFingerSegment(
        length: Float,
        rBase: Float,
        rTip: Float,
        sectors: Int = 10,
    ) {
        addTaperedLimb(
            yBottom = -length,
            yTop = 0f,
            rxBottom = rTip,
            rzBottom = rTip * 0.92f,
            rxTop = rBase,
            rzTop = rBase * 0.96f,
            rings = 4,
            sectors = sectors,
            bulgeFactor = -0.0007f,
            capEnds = false,
        )
        // Rounded proximal & distal joint caps
        addEllipsoid(cx = 0f, cy = 0f, cz = 0f, rx = rBase * 1.02f, ry = rBase * 0.98f, rz = rBase * 0.98f, rings = 4, sectors = sectors)
        addEllipsoid(cx = 0f, cy = -length, cz = 0f, rx = rTip * 1.02f, ry = rTip * 1.08f, rz = rTip * 0.95f, rings = 4, sectors = sectors)
    }

    /**
     * Axis-aligned box with tiled world-metric UVs so wood/wallpaper/stone textures never stretch.
     */
    fun addTexturedBox(
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        uvTileMeters: Float = 1.6f,
    ) {
        val dx = (x1 - x0) / uvTileMeters
        val dy = (y1 - y0) / uvTileMeters
        val dz = (z1 - z0) / uvTileMeters

        fun face(
            ax: Float, ay: Float, az: Float, au: Float, av: Float,
            bx: Float, by: Float, bz: Float, bu: Float, bv: Float,
            cx: Float, cy: Float, cz: Float, cu: Float, cv: Float,
            dxp: Float, dyp: Float, dzp: Float, du: Float, dv: Float,
            nx: Float, ny: Float, nz: Float,
        ) {
            val i0 = addVertex(ax, ay, az, nx, ny, nz, au, av)
            val i1 = addVertex(bx, by, bz, nx, ny, nz, bu, bv)
            val i2 = addVertex(cx, cy, cz, nx, ny, nz, cu, cv)
            val i3 = addVertex(dxp, dyp, dzp, nx, ny, nz, du, dv)
            addQuad(i0, i1, i2, i3)
        }

        // Front (+Z)
        face(x0, y0, z1, 0f, dy, x1, y0, z1, dx, dy, x1, y1, z1, dx, 0f, x0, y1, z1, 0f, 0f, 0f, 0f, 1f)
        // Back (-Z)
        face(x1, y0, z0, 0f, dy, x0, y0, z0, dx, dy, x0, y1, z0, dx, 0f, x1, y1, z0, 0f, 0f, 0f, 0f, -1f)
        // Left (-X)
        face(x0, y0, z0, 0f, dy, x0, y0, z1, dz, dy, x0, y1, z1, dz, 0f, x0, y1, z0, 0f, 0f, -1f, 0f, 0f)
        // Right (+X)
        face(x1, y0, z1, 0f, dy, x1, y0, z0, dz, dy, x1, y1, z0, dz, 0f, x1, y1, z1, 0f, 0f, 1f, 0f, 0f)
        // Top (+Y)
        face(x0, y1, z1, 0f, dz, x1, y1, z1, dx, dz, x1, y1, z0, dx, 0f, x0, y1, z0, 0f, 0f, 0f, 1f, 0f)
        // Bottom (-Y)
        face(x0, y0, z0, 0f, dz, x1, y0, z0, dx, dz, x1, y0, z1, dx, 0f, x0, y0, z1, 0f, 0f, 0f, -1f, 0f)
    }

    /**
     * Sloped gable roof triangle prism along X axis (peak at Z = zCenter, height = peakH).
     */
    fun addGableRoof(
        x0: Float,
        x1: Float,
        z0: Float,
        z1: Float,
        eaveY: Float,
        peakY: Float,
        uvTile: Float = 2.0f,
    ) {
        val zMid = (z0 + z1) * 0.5f
        val halfSpan = (z1 - z0) * 0.5f
        val dy = peakY - eaveY
        val slopeLen = sqrt(halfSpan * halfSpan + dy * dy)
        val dx = (x1 - x0) / uvTile
        val ds = slopeLen / uvTile

        // South slope (+Z facing)
        val nSy = halfSpan / slopeLen
        val nSz = dy / slopeLen
        val s0 = addVertex(x0, eaveY, z1, 0f, nSy, nSz, 0f, ds)
        val s1 = addVertex(x1, eaveY, z1, 0f, nSy, nSz, dx, ds)
        val s2 = addVertex(x1, peakY, zMid, 0f, nSy, nSz, dx, 0f)
        val s3 = addVertex(x0, peakY, zMid, 0f, nSy, nSz, 0f, 0f)
        addQuad(s0, s1, s2, s3)

        // North slope (-Z facing)
        val n0 = addVertex(x1, eaveY, z0, 0f, nSy, -nSz, 0f, ds)
        val n1 = addVertex(x0, eaveY, z0, 0f, nSy, -nSz, dx, ds)
        val n2 = addVertex(x0, peakY, zMid, 0f, nSy, -nSz, dx, 0f)
        val n3 = addVertex(x1, peakY, zMid, 0f, nSy, -nSz, 0f, 0f)
        addQuad(n0, n1, n2, n3)

        // West gable wall triangle (-X)
        val w0 = addVertex(x0, eaveY, z0, -1f, 0f, 0f, 0f, 1f)
        val w1 = addVertex(x0, eaveY, z1, -1f, 0f, 0f, 2f, 1f)
        val w2 = addVertex(x0, peakY, zMid, -1f, 0f, 0f, 1f, 0f)
        addTriangle(w0, w1, w2)

        // East gable wall triangle (+X)
        val e0 = addVertex(x1, eaveY, z1, 1f, 0f, 0f, 0f, 1f)
        val e1 = addVertex(x1, eaveY, z0, 1f, 0f, 0f, 2f, 1f)
        val e2 = addVertex(x1, peakY, zMid, 1f, 0f, 0f, 1f, 0f)
        addTriangle(e0, e1, e2)
    }

    /**
     * Multi-layered tapered pine canopy cone with organic droop & jagged branches.
     */
    fun addPineConeLayer(
        cx: Float,
        baseY: Float,
        cz: Float,
        radius: Float,
        height: Float,
        sectors: Int = 12,
    ) {
        val base = currentVertexCount
        // Outer irregular rim
        for (s in 0..sectors) {
            val u = s.toFloat() / sectors
            val theta = u * (2f * PI.toFloat())
            val jitter = 1f + 0.16f * sin(theta * 5f + cx)
            val r = radius * jitter
            val drop = if (s % 2 == 0) -0.14f else 0.04f
            addVertex(
                cx + cos(theta) * r,
                baseY + drop,
                cz + sin(theta) * r,
                cos(theta) * 0.7f,
                0.45f,
                sin(theta) * 0.7f,
                u * 2f,
                1f,
            )
        }
        // Apex vertex
        val apex = addVertex(cx, baseY + height, cz, 0f, 1f, 0f, 0.5f, 0f)
        // Bottom center for underside shading
        val bottom = addVertex(cx, baseY + 0.05f, cz, 0f, -1f, 0f, 0.5f, 0.5f)
        for (s in 0 until sectors) {
            addTriangle(base + s, base + s + 1, apex)
            addTriangle(base + s + 1, base + s, bottom)
        }
    }

    /**
     * Horizontal ground decal / puddle / contact shadow quad at fixed Y.
     */
    fun addHorizontalQuad(cx: Float, y: Float, cz: Float, halfW: Float, halfD: Float) {
        val i0 = addVertex(cx - halfW, y, cz + halfD, 0f, 1f, 0f, 0f, 1f)
        val i1 = addVertex(cx + halfW, y, cz + halfD, 0f, 1f, 0f, 1f, 1f)
        val i2 = addVertex(cx + halfW, y, cz - halfD, 0f, 1f, 0f, 1f, 0f)
        val i3 = addVertex(cx - halfW, y, cz - halfD, 0f, 1f, 0f, 0f, 0f)
        addQuad(i0, i1, i2, i3)
    }

    /**
     * Centered unit vertical billboard / fog plane in XY [-0.5..0.5].
     */
    fun addCenteredQuadXY() {
        val i0 = addVertex(-0.5f, -0.5f, 0f, 0f, 0f, 1f, 0f, 1f)
        val i1 = addVertex(0.5f, -0.5f, 0f, 0f, 0f, 1f, 1f, 1f)
        val i2 = addVertex(0.5f, 0.5f, 0f, 0f, 0f, 1f, 1f, 0f)
        val i3 = addVertex(-0.5f, 0.5f, 0f, 0f, 0f, 1f, 0f, 0f)
        addQuad(i0, i1, i2, i3)
    }
}
