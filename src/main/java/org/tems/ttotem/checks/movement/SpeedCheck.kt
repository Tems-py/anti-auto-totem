package org.tems.ttotem.checks.movement

import org.bukkit.entity.Player
import org.tems.ttotem.checks.MovementCheck
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.simulation.PhysicsSimulator
import org.tems.ttotem.violation.ViolationManager

class SpeedCheck(
    private val config: ACConfig,
    private val violationManager: ViolationManager,
    private val physicsSimulator: PhysicsSimulator
) : MovementCheck {

    override fun check(player: Player, data: PlayerData) {
        val curr = data.positionBuffer.latest() ?: return
        val prev = data.positionBuffer.previous() ?: return

        val dx = curr.x - prev.x
        val dz = curr.z - prev.z
        val actualDeltaXZ = Math.sqrt(dx * dx + dz * dz)

        // Simulate predicted position
        val result = physicsSimulator.simulate(data)
        val predDx = result.predictedX - prev.x
        val predDz = result.predictedZ - prev.z
        val predictedDeltaXZ = Math.sqrt(predDx * predDx + predDz * predDz)

        val tolerance = config.speedTolerance
        val maxAllowed = predictedDeltaXZ + tolerance

        if (actualDeltaXZ > maxAllowed && actualDeltaXZ > 0.1) {
            val diff = actualDeltaXZ - maxAllowed
            violationManager.flag(
                player, data, "Speed",
                amount = diff * 10.0,
                details = String.format("delta=%.3f predicted=%.3f", actualDeltaXZ, predictedDeltaXZ)
            )
        }
    }
}
