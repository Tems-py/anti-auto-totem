package org.tems.ttotem.checks.movement

import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType
import org.tems.ttotem.checks.MovementCheck
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.simulation.PhysicsSimulator
import org.tems.ttotem.violation.ViolationManager

class FlightCheck(
    private val config: ACConfig,
    private val violationManager: ViolationManager,
    private val physicsSimulator: PhysicsSimulator
) : MovementCheck {

    override fun check(player: Player, data: PlayerData) {
        val curr = data.positionBuffer.latest() ?: return
        val prev = data.positionBuffer.previous() ?: return

        // Skip if in creative/spectator, in fluid, has elytra, or has levitation/slow falling
        if (data.hasElytra) return
        if (data.isInFluid) return
        if (data.simulatorWorld.isInWater || data.simulatorWorld.isInLava) return

        // Check for levitation or slow falling potions (must be done sync-safe)
        try {
            if (player.hasPotionEffect(PotionEffectType.LEVITATION)) return
            if (player.hasPotionEffect(PotionEffectType.SLOW_FALLING)) return
        } catch (_: Exception) {
            // May fail if accessed off-thread on some versions
        }

        val deltaY = curr.y - prev.y
        val onGround = data.simulatorWorld.isOnGround(prev.x, prev.y, prev.z)

        // If not on ground and not in fluid, Y velocity should be decreasing (gravity)
        if (!onGround && !curr.onGround) {
            // Simulate expected Y
            val result = physicsSimulator.simulate(data)

            // Player is gaining or maintaining altitude without a valid jump
            if (deltaY > 0.0 && data.velocityY <= 0.0 && data.knockbackTick < data.tick - 5) {
                violationManager.flag(
                    player, data, "Flight",
                    amount = 2.0,
                    details = String.format("deltaY=%.3f velY=%.3f", deltaY, data.velocityY)
                )
            }

            // Hovering in air (very small Y movement when should be falling)
            if (Math.abs(deltaY) < 0.01 && data.velocityY < -0.1) {
                violationManager.flag(
                    player, data, "Flight",
                    amount = 1.0,
                    details = String.format("hover deltaY=%.4f expectedVelY=%.3f", deltaY, data.velocityY)
                )
            }
        }
    }
}
