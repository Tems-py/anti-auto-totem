package org.tems.ttotem.checks.combat

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.tems.ttotem.checks.CombatCheck
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.simulation.ReachSimulator
import org.tems.ttotem.violation.ViolationManager

class ReachCheck(
    private val config: ACConfig,
    private val violationManager: ViolationManager,
    private val reachSimulator: ReachSimulator
) : CombatCheck {

    // Track consecutive reach violations per player for threshold flagging
    private val reachViolationCounts = HashMap<java.util.UUID, Int>()
    private val reachAttackCounts = HashMap<java.util.UUID, Int>()

    override fun check(player: Player, data: PlayerData, targetEntityId: Int) {
        val target = Bukkit.getServer().getEntity(
            // entityId -> Entity lookup: iterate online players/entities
            // For simplicity, search nearby entities
            player.world.entities.find { it.entityId == targetEntityId }?.uniqueId ?: return
        ) ?: return

        val pingMs = try { player.ping } catch (_: Exception) { 0 }

        val reach = if (config.latencyCompensation) {
            reachSimulator.calculateReachWithCompensation(
                player, target, pingMs, config.maxPingCompensationTicks
            )
        } else {
            reachSimulator.calculateReach(player, target)
        }

        val maxReach = config.maxReach
        val latencyExtra = if (config.latencyCompensation) {
            minOf(pingMs / 50, config.maxPingCompensationTicks) * 0.05
        } else 0.0

        val effectiveMaxReach = maxReach + latencyExtra

        // Track attacks
        val attackCount = reachAttackCounts.getOrDefault(player.uniqueId, 0) + 1
        reachAttackCounts[player.uniqueId] = attackCount

        if (reach > effectiveMaxReach) {
            val violations = reachViolationCounts.getOrDefault(player.uniqueId, 0) + 1
            reachViolationCounts[player.uniqueId] = violations

            // Flag if reach exceeds threshold consistently (5 out of last 10 attacks)
            if (violations >= 5 && attackCount <= 10) {
                violationManager.flag(
                    player, data, "Reach",
                    amount = 2.0,
                    details = String.format("reach=%.2f max=%.2f ping=%dms", reach, effectiveMaxReach, pingMs)
                )
            } else if (violations >= 5) {
                // Reset tracking window
                reachViolationCounts[player.uniqueId] = 0
                reachAttackCounts[player.uniqueId] = 0
            }
        }

        // Reset window after 10 attacks
        if (attackCount >= 10) {
            reachViolationCounts[player.uniqueId] = 0
            reachAttackCounts[player.uniqueId] = 0
        }
    }
}
