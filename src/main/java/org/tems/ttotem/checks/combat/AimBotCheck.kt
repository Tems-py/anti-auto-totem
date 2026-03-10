package org.tems.ttotem.checks.combat

import org.bukkit.entity.Player
import org.tems.ttotem.checks.CombatCheck
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.violation.ViolationManager

class AimBotCheck(
    private val config: ACConfig,
    private val violationManager: ViolationManager
) : CombatCheck {

    override fun check(player: Player, data: PlayerData, targetEntityId: Int) {
        val curr = data.positionBuffer.latest() ?: return
        val prev = data.positionBuffer.previous() ?: return

        val deltaYaw = Math.abs(normalizeAngle(curr.yaw - prev.yaw))
        val deltaPitch = Math.abs(curr.pitch - prev.pitch)

        // Impossible rotation: yaw delta > 180 in a single tick
        if (deltaYaw > config.aimBotMaxDeltaPerTick) {
            violationManager.flag(
                player, data, "AimBot",
                amount = 3.0,
                details = String.format("impossible rotation deltaYaw=%.1f", deltaYaw)
            )
        }

        // Invalid pitch: outside [-90, 90]
        if (curr.pitch < -90f || curr.pitch > 90f) {
            violationManager.flag(
                player, data, "AimBot",
                amount = 5.0,
                details = String.format("invalid pitch=%.1f", curr.pitch)
            )
        }

        // Aim lock detection: rotation snaps exactly to a consistent pattern while attacking
        // Check if recent attacks show near-zero pitch/yaw variance (snapping to target center)
        val recentAttacks = data.combatTracker.recentAttacks(5)
        if (recentAttacks.size >= 5) {
            val entries = data.positionBuffer.entries()
            if (entries.size >= 5) {
                val recentYawDeltas = mutableListOf<Float>()
                for (i in 1 until minOf(6, entries.size)) {
                    recentYawDeltas.add(Math.abs(normalizeAngle(entries[entries.size - i].yaw - entries[entries.size - i - 1 + 1].yaw)))
                }
                // If all recent yaw deltas are suspiciously similar (aim lock pattern)
                if (recentYawDeltas.size >= 4) {
                    val avgDelta = recentYawDeltas.average()
                    val variance = recentYawDeltas.map { (it - avgDelta) * (it - avgDelta) }.average()
                    if (variance < 0.5 && avgDelta > 5.0) {
                        violationManager.flag(
                            player, data, "AimBot",
                            amount = 2.0,
                            details = String.format("aim-lock variance=%.3f avgDelta=%.1f", variance, avgDelta)
                        )
                    }
                }
            }
        }
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle % 360f
        if (a > 180f) a -= 360f
        if (a < -180f) a += 360f
        return a
    }
}
