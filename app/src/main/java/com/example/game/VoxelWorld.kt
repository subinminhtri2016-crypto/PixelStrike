package com.example.game

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

enum class BlockType(val id: Byte, val isSolid: Boolean) {
    AIR(0, false),
    SAND(1, true),
    SANDSTONE(2, true),
    TERRACOTTA(3, true),
    BUNKER_CONCRETE(4, true),
    CRATE_WOOD(5, true),
    SANDBAG(6, true),
    PALM_LOG(7, true),
    PALM_LEAVES(8, true),
    GLOO_BLOCK(9, true);

    companion object {
        private val byId = entries.associateBy { it.id }
        fun fromId(id: Byte): BlockType = byId[id] ?: AIR
    }
}

data class BlockHit(
    val bx: Int,
    val by: Int,
    val bz: Int,
    val distance: Float,
    val blockType: BlockType,
)

class VoxelWorld {
    val sizeX = 32
    val sizeY = 14
    val sizeZ = 44

    private val blocks = ByteArray(sizeX * sizeY * sizeZ)
    private val glooHp = HashMap<Int, Int>()

    @Volatile
    var worldVersion: Int = 1
        private set

    val blueSpawns = listOf(
        Triple(15.5f, 1.0f, 6.5f),
        Triple(11.5f, 1.0f, 6.5f),
        Triple(19.5f, 1.0f, 6.5f),
        Triple(15.5f, 1.0f, 4.5f),
    )

    val redSpawns = listOf(
        Triple(15.5f, 1.0f, 36.5f),
        Triple(11.5f, 1.0f, 36.5f),
        Triple(19.5f, 1.0f, 36.5f),
        Triple(15.5f, 1.0f, 38.5f),
    )

    val tacticalWaypoints = listOf(
        Triple(15.5f, 1.0f, 14.5f),
        Triple(8.5f, 1.0f, 16.5f),
        Triple(23.5f, 1.0f, 16.5f),
        Triple(15.5f, 3.0f, 21.5f),
        Triple(6.5f, 1.0f, 21.5f),
        Triple(25.5f, 1.0f, 21.5f),
        Triple(8.5f, 1.0f, 27.5f),
        Triple(23.5f, 1.0f, 27.5f),
        Triple(15.5f, 1.0f, 29.5f),
    )

    init {
        buildDesertCanyonMap()
    }

    private fun idx(x: Int, y: Int, z: Int): Int = (y * sizeZ + z) * sizeX + x

    fun inBounds(x: Int, y: Int, z: Int): Boolean =
        x in 0 until sizeX && y in 0 until sizeY && z in 0 until sizeZ

    fun getBlock(x: Int, y: Int, z: Int): BlockType {
        if (!inBounds(x, y, z)) return BlockType.TERRACOTTA
        return BlockType.fromId(blocks[idx(x, y, z)])
    }

    fun setBlock(x: Int, y: Int, z: Int, type: BlockType) {
        if (!inBounds(x, y, z)) return
        blocks[idx(x, y, z)] = type.id
    }

    fun isSolid(x: Int, y: Int, z: Int): Boolean {
        if (y < 0) return true
        if (!inBounds(x, y, z)) return y < 8
        return getBlock(x, y, z).isSolid
    }

    private fun fillBox(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int, type: BlockType) {
        for (y in y1..y2) {
            for (z in z1..z2) {
                for (x in x1..x2) {
                    setBlock(x, y, z, type)
                }
            }
        }
    }

    private fun buildDesertCanyonMap() {
        blocks.fill(BlockType.AIR.id)
        glooHp.clear()

        // Base desert floor at y = 0
        fillBox(0, 0, 0, sizeX - 1, 0, sizeZ - 1, BlockType.SAND)

        // Perimeter canyon cliffs (layered terracotta & sandstone)
        for (x in 0 until sizeX) {
            for (z in 0 until sizeZ) {
                if (x <= 1 || x >= sizeX - 2 || z <= 1 || z >= sizeZ - 2) {
                    val cliffHeight = 6 + ((x * 7 + z * 13) % 4)
                    for (y in 1..cliffHeight) {
                        val b = if (y % 2 == 0) BlockType.TERRACOTTA else BlockType.SANDSTONE
                        setBlock(x, y, z, b)
                    }
                }
            }
        }

        // Canyon ledges protruding along sides for natural depth
        fillBox(2, 1, 10, 3, 4, 15, BlockType.TERRACOTTA)
        fillBox(28, 1, 10, 29, 4, 15, BlockType.TERRACOTTA)
        fillBox(2, 1, 28, 3, 4, 33, BlockType.TERRACOTTA)
        fillBox(28, 1, 28, 29, 4, 33, BlockType.TERRACOTTA)

        // Blue Spawn Military Bunker Cover (South)
        fillBox(13, 1, 8, 18, 2, 8, BlockType.BUNKER_CONCRETE)
        fillBox(9, 1, 9, 11, 1, 9, BlockType.SANDBAG)
        fillBox(20, 1, 9, 22, 1, 9, BlockType.SANDBAG)
        fillBox(7, 1, 6, 8, 2, 7, BlockType.CRATE_WOOD)
        fillBox(23, 1, 6, 24, 2, 7, BlockType.CRATE_WOOD)

        // Red Spawn Military Bunker Cover (North)
        fillBox(13, 1, 35, 18, 2, 35, BlockType.BUNKER_CONCRETE)
        fillBox(9, 1, 34, 11, 1, 34, BlockType.SANDBAG)
        fillBox(20, 1, 34, 22, 1, 34, BlockType.SANDBAG)
        fillBox(7, 1, 36, 8, 2, 37, BlockType.CRATE_WOOD)
        fillBox(23, 1, 36, 24, 2, 37, BlockType.CRATE_WOOD)

        // Central Raised Sandstone Fortress Platform + Side Ramps
        fillBox(12, 1, 19, 19, 2, 24, BlockType.SANDSTONE)
        // North & South steps up to central platform
        fillBox(14, 1, 18, 17, 1, 18, BlockType.SANDSTONE)
        fillBox(14, 1, 25, 17, 1, 25, BlockType.SANDSTONE)
        // Low battlements on central platform
        fillBox(12, 3, 19, 13, 3, 20, BlockType.SANDBAG)
        fillBox(18, 3, 19, 19, 3, 20, BlockType.SANDBAG)
        fillBox(12, 3, 23, 13, 3, 24, BlockType.SANDBAG)
        fillBox(18, 3, 23, 19, 3, 24, BlockType.SANDBAG)
        fillBox(15, 3, 21, 16, 4, 22, BlockType.CRATE_WOOD)

        // Mid-lane tactical pillars & crates
        fillBox(8, 1, 14, 9, 3, 15, BlockType.BUNKER_CONCRETE)
        fillBox(22, 1, 14, 23, 3, 15, BlockType.BUNKER_CONCRETE)
        fillBox(8, 1, 28, 9, 3, 29, BlockType.BUNKER_CONCRETE)
        fillBox(22, 1, 28, 23, 3, 29, BlockType.BUNKER_CONCRETE)

        fillBox(11, 1, 13, 12, 1, 13, BlockType.SANDBAG)
        fillBox(19, 1, 13, 20, 1, 13, BlockType.SANDBAG)
        fillBox(11, 1, 30, 12, 1, 30, BlockType.SANDBAG)
        fillBox(19, 1, 30, 20, 1, 30, BlockType.SANDBAG)

        // East & West Flank Watchtowers (climbable / cover)
        buildWatchtower(4, 20)
        buildWatchtower(25, 20)

        // Desert Oasis Palm Trees
        buildPalmTree(5, 11)
        buildPalmTree(26, 32)
        buildPalmTree(5, 32)
        buildPalmTree(26, 11)

        // Extra supply crates in flanking alleys
        fillBox(6, 1, 17, 6, 2, 17, BlockType.CRATE_WOOD)
        fillBox(25, 1, 26, 25, 2, 26, BlockType.CRATE_WOOD)

        worldVersion++
    }

    private fun buildWatchtower(baseX: Int, baseZ: Int) {
        // 3x3 concrete pillars + wooden crate top cover
        fillBox(baseX, 1, baseZ, baseX, 4, baseZ, BlockType.BUNKER_CONCRETE)
        fillBox(baseX + 2, 1, baseZ, baseX + 2, 4, baseZ, BlockType.BUNKER_CONCRETE)
        fillBox(baseX, 1, baseZ + 2, baseX, 4, baseZ + 2, BlockType.BUNKER_CONCRETE)
        fillBox(baseX + 2, 1, baseZ + 2, baseX + 2, 4, baseZ + 2, BlockType.BUNKER_CONCRETE)
        fillBox(baseX, 3, baseZ, baseX + 2, 3, baseZ + 2, BlockType.CRATE_WOOD)
        setBlock(baseX + 1, 3, baseZ + 1, BlockType.BUNKER_CONCRETE)
    }

    private fun buildPalmTree(x: Int, z: Int) {
        for (y in 1..4) {
            setBlock(x, y, z, BlockType.PALM_LOG)
        }
        fillBox(x - 1, 5, z - 1, x + 1, 5, z + 1, BlockType.PALM_LEAVES)
        setBlock(x - 2, 5, z, BlockType.PALM_LEAVES)
        setBlock(x + 2, 5, z, BlockType.PALM_LEAVES)
        setBlock(x, 5, z - 2, BlockType.PALM_LEAVES)
        setBlock(x, 5, z + 2, BlockType.PALM_LEAVES)
        setBlock(x, 6, z, BlockType.PALM_LEAVES)
    }

    fun placeGlooWall(px: Float, py: Float, pz: Float, yawDeg: Float): Boolean {
        val rad = Math.toRadians(yawDeg.toDouble())
        val fx = sin(rad).toFloat()
        val fz = cos(rad).toFloat()
        val rx = -cos(rad).toFloat()
        val rz = sin(rad).toFloat()

        val centerX = px + fx * 2.2f
        val centerZ = pz + fz * 2.2f
        val baseY = floor(py.toDouble()).toInt().coerceIn(1, sizeY - 3)

        var placedAny = false
        for (side in -1..1) {
            val bx = floor((centerX + rx * side).toDouble()).toInt()
            val bz = floor((centerZ + rz * side).toDouble()).toInt()
            for (dy in 0..1) {
                val by = baseY + dy
                if (inBounds(bx, by, bz) && getBlock(bx, by, bz) == BlockType.AIR) {
                    setBlock(bx, by, bz, BlockType.GLOO_BLOCK)
                    glooHp[idx(bx, by, bz)] = 180
                    placedAny = true
                }
            }
        }
        if (placedAny) worldVersion++
        return placedAny
    }

    fun damageGlooAt(bx: Int, by: Int, bz: Int, damage: Int) {
        if (!inBounds(bx, by, bz)) return
        if (getBlock(bx, by, bz) != BlockType.GLOO_BLOCK) return
        val key = idx(bx, by, bz)
        val remaining = (glooHp[key] ?: 180) - damage
        if (remaining <= 0) {
            glooHp.remove(key)
            setBlock(bx, by, bz, BlockType.AIR)
            worldVersion++
        } else {
            glooHp[key] = remaining
        }
    }

    fun clearGlooBlocks() {
        if (glooHp.isEmpty()) return
        for (key in glooHp.keys.toList()) {
            blocks[key] = BlockType.AIR.id
        }
        glooHp.clear()
        worldVersion++
    }

    fun raycastBlock(
        ox: Float,
        oy: Float,
        oz: Float,
        dx: Float,
        dy: Float,
        dz: Float,
        maxDist: Float,
    ): BlockHit? {
        val len = sqrt(dx * dx + dy * dy + dz * dz)
        if (len < 1e-6f) return null
        val nx = dx / len
        val ny = dy / len
        val nz = dz / len

        var x = floor(ox.toDouble()).toInt()
        var y = floor(oy.toDouble()).toInt()
        var z = floor(oz.toDouble()).toInt()

        val stepX = if (nx >= 0f) 1 else -1
        val stepY = if (ny >= 0f) 1 else -1
        val stepZ = if (nz >= 0f) 1 else -1

        val tDeltaX = if (abs(nx) < 1e-6f) Float.MAX_VALUE else abs(1f / nx)
        val tDeltaY = if (abs(ny) < 1e-6f) Float.MAX_VALUE else abs(1f / ny)
        val tDeltaZ = if (abs(nz) < 1e-6f) Float.MAX_VALUE else abs(1f / nz)

        var tMaxX = if (abs(nx) < 1e-6f) Float.MAX_VALUE else {
            val nextBoundary = if (stepX > 0) (x + 1).toFloat() else x.toFloat()
            (nextBoundary - ox) / nx
        }
        var tMaxY = if (abs(ny) < 1e-6f) Float.MAX_VALUE else {
            val nextBoundary = if (stepY > 0) (y + 1).toFloat() else y.toFloat()
            (nextBoundary - oy) / ny
        }
        var tMaxZ = if (abs(nz) < 1e-6f) Float.MAX_VALUE else {
            val nextBoundary = if (stepZ > 0) (z + 1).toFloat() else z.toFloat()
            (nextBoundary - oz) / nz
        }

        var dist = 0f
        var steps = 0
        while (dist <= maxDist && steps < 180) {
            steps++
            if (isSolid(x, y, z)) {
                return BlockHit(x, y, z, dist, getBlock(x, y, z))
            }
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    x += stepX
                    dist = tMaxX
                    tMaxX += tDeltaX
                } else {
                    z += stepZ
                    dist = tMaxZ
                    tMaxZ += tDeltaZ
                }
            } else {
                if (tMaxY < tMaxZ) {
                    y += stepY
                    dist = tMaxY
                    tMaxY += tDeltaY
                } else {
                    z += stepZ
                    dist = tMaxZ
                    tMaxZ += tDeltaZ
                }
            }
        }
        return null
    }
}
