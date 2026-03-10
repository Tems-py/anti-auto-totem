package org.tems.ttotem.checks.combat

import org.bukkit.entity.Player
import org.tems.ttotem.checks.CombatCheck
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.violation.ViolationManager

class KillAuraCheck(
    private val config: ACConfig,
    private val violationManager: ViolationManager
) : CombatCheck {

    override fun check(player: Player, data: PlayerData, targetEntityId: Int) {
        val tick = data.tick

        // No-swing flag: attack without a preceding swing animation
        if (!data.combatTracker.hadSwingBeforeAttack(tick)) {
            violationManager.flag(
                player, data, "KillAura",
                amount = 3.0,
                details = "no-swing (lastSwing=${data.combatTracker.lastSwingTick} attackTick=$tick)"
            )
        }

        // Multi-target: attacking 3+ distinct entities in the same tick
        val distinctTargets = data.combatTracker.getDistinctTargetsInTick(tick)
        if (distinctTargets >= 3) {
            violationManager.flag(
                player, data, "KillAura",
                amount = 5.0,
                details = "multi-target ($distinctTargets entities in 1 tick)"
            )
        }

        // No-rotation: attacking while yaw/pitch hasn't changed in 3+ ticks
        if (data.rotationUnchangedTicks >= 3) {
            // Check if there are multiple nearby entities (would indicate aura)
            val nearbyEntities = player.getNearbyEntities(5.0, 5.0, 5.0)
                .filter { it != player && it is org.bukkit.entity.LivingEntity }
            if (nearbyEntities.size >= 2) {
                violationManager.flag(
                    player, data, "KillAura",
                    amount = 2.0,
                    details = "no-rotation (unchanged=${data.rotationUnchangedTicks} ticks, nearby=${nearbyEntities.size})"
                )
            }
        }
    }
}
