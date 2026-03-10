package org.tems.ttotem.checks.combat

import org.bukkit.entity.Player
import org.tems.ttotem.checks.CombatCheck
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.violation.ViolationManager

class VelocityCheck(
    private val config: ACConfig,
    private val violationManager: ViolationManager
) : CombatCheck {

    override fun check(player: Player, data: PlayerData, targetEntityId: Int) {
        // This check runs on movement packets, not combat. We include it in combat checks
        // because it's related to anti-knockback which is combat behavior.
        // The actual velocity comparison happens in the movement pipeline.
    }

    /**
     * Called from the packet listener when movement is received after knockback.
     */
    fun checkVelocity(player: Player, data: PlayerData) {
        if (data.knockbackTick < 0) return

        data.ticksSinceKnockback++

        // Check within 3 ticks of knockback being applied
        if (data.ticksSinceKnockback > 3) {
            data.knockbackTick = -1
            data.ticksSinceKnockback = 0
            return
        }

        val curr = data.positionBuffer.latest() ?: return
        val prev = data.positionBuffer.previous() ?: return

        val dx = curr.x - prev.x
        val dz = curr.z - prev.z
        val actualXZ = Math.sqrt(dx * dx + dz * dz)

        val expectedXZ = Math.sqrt(
            data.pendingKnockbackX * data.pendingKnockbackX +
            data.pendingKnockbackZ * data.pendingKnockbackZ
        )

        // If player moved near-zero when they should have been knocked back significantly
        if (expectedXZ > 0.1 && actualXZ < expectedXZ * 0.1) {
            violationManager.flag(
                player, data, "Velocity",
                amount = 3.0,
                details = String.format(
                    "antiKB actual=%.3f expected=%.3f ratio=%.2f tick=%d",
                    actualXZ, expectedXZ, actualXZ / expectedXZ, data.ticksSinceKnockback
                )
            )
        }
    }
}
