package org.tems.ttotem.simulation

import org.tems.ttotem.data.PlayerData

data class SimulationResult(
    val predictedX: Double,
    val predictedY: Double,
    val predictedZ: Double,
    val predictedVelX: Double,
    val predictedVelY: Double,
    val predictedVelZ: Double,
    val onGround: Boolean
)

class PhysicsSimulator {

    companion object {
        private const val GRAVITY = 0.08
        private const val AIR_DRAG = 0.98
        private const val WATER_DRAG = 0.8
        private const val LAVA_DRAG = 0.5
        private const val SPRINT_MULTIPLIER = 1.3
        private const val SNEAK_MULTIPLIER = 0.3
        private const val BASE_SPEED = 0.1
        private const val SOUL_SAND_FACTOR = 0.4
        private const val JUMP_VELOCITY = 0.42
    }

    fun simulate(data: PlayerData): SimulationResult {
        val world = data.simulatorWorld
        val pos = data.positionBuffer.latest() ?: return SimulationResult(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, true)

        var velX = data.velocityX
        var velY = data.velocityY
        var velZ = data.velocityZ

        val onGround = world.isOnGround(pos.x, pos.y, pos.z)

        // Apply knockback if pending
        if (data.knockbackTick >= 0 && data.ticksSinceKnockback < 3) {
            velX = data.pendingKnockbackX
            velY = data.pendingKnockbackY
            velZ = data.pendingKnockbackZ
        }

        // Gravity
        if (!onGround && !world.isInWater && !world.isInLava) {
            velY -= GRAVITY
            velY *= AIR_DRAG
        }

        // Fluid drag
        if (world.isInWater) {
            velY -= GRAVITY / 4.0
            velX *= WATER_DRAG
            velY *= WATER_DRAG
            velZ *= WATER_DRAG
        }
        if (world.isInLava) {
            velY -= GRAVITY / 4.0
            velX *= LAVA_DRAG
            velY *= LAVA_DRAG
            velZ *= LAVA_DRAG
        }

        // Ground friction
        if (onGround) {
            val slipperiness = world.getSlipperiness(pos.x, pos.y, pos.z)
            val friction = slipperiness * 0.91
            velX *= friction
            velZ *= friction

            // Max ground speed
            var maxSpeed = BASE_SPEED
            if (data.sprinting) maxSpeed *= SPRINT_MULTIPLIER
            if (data.sneaking) maxSpeed *= SNEAK_MULTIPLIER

            // Soul sand
            val collidingMats = world.getCollidingMaterials(pos.x, pos.y, pos.z)
            if (collidingMats.any { it.name.contains("SOUL") }) {
                maxSpeed *= SOUL_SAND_FACTOR
            }

            // Cap horizontal velocity to max speed (with tolerance)
            val horizSpeed = Math.sqrt(velX * velX + velZ * velZ)
            if (horizSpeed > maxSpeed * 1.5) {
                val scale = maxSpeed * 1.5 / horizSpeed
                velX *= scale
                velZ *= scale
            }
        } else {
            // Air resistance
            velX *= 0.91
            velZ *= 0.91
        }

        val predictedX = pos.x + velX
        val predictedY = pos.y + velY
        val predictedZ = pos.z + velZ
        val predictedOnGround = world.isOnGround(predictedX, predictedY, predictedZ)

        // Clamp to ground if below
        val finalY = if (predictedOnGround && velY < 0) {
            Math.floor(pos.y).let { floorY ->
                if (predictedY < floorY) floorY else predictedY
            }
        } else {
            predictedY
        }

        return SimulationResult(
            predictedX, finalY, predictedZ,
            velX, velY, velZ,
            predictedOnGround
        )
    }

    fun getMaxSpeedXZ(data: PlayerData): Double {
        var maxSpeed = BASE_SPEED * 20.0 // per second → per tick base is 0.1
        if (data.sprinting) maxSpeed *= SPRINT_MULTIPLIER
        if (data.sneaking) maxSpeed *= SNEAK_MULTIPLIER

        val world = data.simulatorWorld
        if (world.isOnIce) maxSpeed *= 2.5
        if (world.isInWater) maxSpeed *= 0.5
        if (world.isInLava) maxSpeed *= 0.25

        return maxSpeed
    }
}
