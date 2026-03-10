package org.tems.ttotem.checks.movement

import org.bukkit.entity.Player
import org.tems.ttotem.checks.MovementCheck
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.simulation.PhysicsSimulator
import org.tems.ttotem.violation.ViolationManager

class StepCheck(
    private val config: ACConfig,
    private val violationManager: ViolationManager,
    private val physicsSimulator: PhysicsSimulator
) : MovementCheck {

    override fun check(player: Player, data: PlayerData) {
        val curr = data.positionBuffer.latest() ?: return
        val prev = data.positionBuffer.previous() ?: return

        val deltaY = curr.y - prev.y
        val prevOnGround = data.simulatorWorld.isOnGround(prev.x, prev.y, prev.z)

        // Step detection: Y delta > 0.6 but <= 1.25, and player was on ground
        if (deltaY > 0.6 && deltaY <= 1.25 && prevOnGround) {
            // Simulate to check if step-up is valid
            val result = physicsSimulator.simulate(data)
            val predictedDeltaY = result.predictedY - prev.y

            // If predicted step height is significantly less than actual
            if (predictedDeltaY < deltaY - 0.1) {
                violationManager.flag(
                    player, data, "Step",
                    amount = 2.0,
                    details = String.format("deltaY=%.3f predictedDeltaY=%.3f", deltaY, predictedDeltaY)
                )
            }
        }

        // Head hitter: player jumps higher than possible (> 1.25 blocks from ground)
        if (deltaY > 1.25 && prevOnGround && data.knockbackTick < data.tick - 5) {
            violationManager.flag(
                player, data, "Step",
                amount = 3.0,
                details = String.format("impossibleStep deltaY=%.3f", deltaY)
            )
        }
    }
}
