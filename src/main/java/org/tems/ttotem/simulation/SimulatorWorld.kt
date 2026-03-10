package org.tems.ttotem.simulation

import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox

data class BlockPos(val x: Int, val y: Int, val z: Int)

class SimulatorWorld {
    private val blockSnapshot = HashMap<BlockPos, Material>()
    private val blockDataSnapshot = HashMap<BlockPos, BlockData>()

    @Volatile var gravity: Double = 0.08
    @Volatile var isInWater: Boolean = false
    @Volatile var isInLava: Boolean = false
    @Volatile var isOnIce: Boolean = false
    @Volatile var isOnSlime: Boolean = false

    /**
     * Must be called on the main thread. Captures a 7x7x7 block region around the player.
     */
    fun snapshot(player: Player) {
        val loc = player.location
        val cx = loc.blockX
        val cy = loc.blockY
        val cz = loc.blockZ
        val world = loc.world ?: return

        blockSnapshot.clear()
        blockDataSnapshot.clear()

        for (dx in -3..3) {
            for (dy in -3..3) {
                for (dz in -3..3) {
                    val bx = cx + dx
                    val by = cy + dy
                    val bz = cz + dz
                    val block = world.getBlockAt(bx, by, bz)
                    val pos = BlockPos(bx, by, bz)
                    blockSnapshot[pos] = block.type
                    blockDataSnapshot[pos] = block.blockData
                }
            }
        }

        // Detect fluid and surface types
        isInWater = player.isInWater
        isInLava = false // checked via block snapshot below

        val belowPos = BlockPos(cx, cy - 1, cz)
        val belowMat = blockSnapshot[belowPos]
        isOnIce = belowMat == Material.ICE || belowMat == Material.PACKED_ICE || belowMat == Material.BLUE_ICE || belowMat == Material.FROSTED_ICE
        isOnSlime = belowMat == Material.SLIME_BLOCK

        // Check lava
        val playerPos = BlockPos(cx, cy, cz)
        if (blockSnapshot[playerPos] == Material.LAVA) {
            isInLava = true
        }
    }

    fun isOnGround(x: Double, y: Double, z: Double, width: Double = 0.6, height: Double = 1.8): Boolean {
        val halfW = width / 2.0
        val minX = (x - halfW).toInt()
        val maxX = (x + halfW).toInt()
        val minZ = (z - halfW).toInt()
        val maxZ = (z + halfW).toInt()
        val belowY = (y - 0.001).toInt()

        for (bx in minX..maxX) {
            for (bz in minZ..maxZ) {
                val pos = BlockPos(bx, belowY, bz)
                val mat = blockSnapshot[pos] ?: continue
                if (mat.isSolid) return true
            }
        }
        return false
    }

    fun getCollidingMaterials(x: Double, y: Double, z: Double, width: Double = 0.6, height: Double = 1.8): Set<Material> {
        val halfW = width / 2.0
        val minX = (x - halfW).toInt()
        val maxX = (x + halfW).toInt()
        val minY = y.toInt()
        val maxY = (y + height).toInt()
        val minZ = (z - halfW).toInt()
        val maxZ = (z + halfW).toInt()

        val materials = mutableSetOf<Material>()
        for (bx in minX..maxX) {
            for (by in minY..maxY) {
                for (bz in minZ..maxZ) {
                    val pos = BlockPos(bx, by, bz)
                    val mat = blockSnapshot[pos] ?: continue
                    if (mat != Material.AIR) {
                        materials.add(mat)
                    }
                }
            }
        }
        return materials
    }

    fun isSolidAt(pos: BlockPos): Boolean {
        return blockSnapshot[pos]?.isSolid == true
    }

    fun getMaterialAt(pos: BlockPos): Material? {
        return blockSnapshot[pos]
    }

    fun getSlipperiness(x: Double, y: Double, z: Double): Double {
        val belowPos = BlockPos(x.toInt(), (y - 1).toInt(), z.toInt())
        val mat = blockSnapshot[belowPos] ?: return 0.6
        return when (mat) {
            Material.ICE, Material.PACKED_ICE, Material.FROSTED_ICE -> 0.98
            Material.BLUE_ICE -> 0.989
            Material.SLIME_BLOCK -> 0.8
            else -> 0.6
        }
    }
}
