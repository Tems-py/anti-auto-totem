package org.tems.ttotem.checks.movement

import org.bukkit.entity.Player
import org.tems.ttotem.checks.MovementCheck
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.violation.ViolationManager

class NoFallCheck(
    private val config: ACConfig,
    private val violationManager: ViolationManager
) : MovementCheck {

    override fun check(player: Player, data: PlayerData) {
        val curr = data.positionBuffer.latest() ?: return
        val prev = data.positionBuffer.previous() ?: return

        val deltaY = curr.y - prev.y

        // Track fall distance server-side
        if (deltaY < 0 && !data.onGround) {
            data.fallDistance += Math.abs(deltaY)
        }

        // Player claims to be on ground via packet
        if (curr.onGround) {
            val serverOnGround = data.simulatorWorld.isOnGround(curr.x, curr.y, curr.z)

            // If player says on ground but server says they're not, and they've been falling
            if (!serverOnGround && data.fallDistance >= 3.0) {
                violationManager.flag(
                    player, data, "NoFall",
                    amount = 2.0,
                    details = String.format("fallDist=%.2f serverGround=%b", data.fallDistance, serverOnGround)
                )
            }

            // Reset fall distance when legitimately on ground
            if (serverOnGround) {
                data.fallDistance = 0.0
            }
        }

        // Update onGround state
        data.onGround = curr.onGround
    }
}
