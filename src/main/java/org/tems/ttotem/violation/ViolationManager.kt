package org.tems.ttotem.violation

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.tems.ttotem.alert.AlertManager
import org.tems.ttotem.data.PlayerData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ViolationManager(
    private val plugin: Plugin,
    private val alertManager: AlertManager
) {
    // checkName -> (playerUUID -> violationLevel)
    private val violations = ConcurrentHashMap<String, ConcurrentHashMap<UUID, Double>>()

    // checkName -> list of actions at thresholds
    private val actions = ConcurrentHashMap<String, List<ViolationAction>>()

    // VL decay rate per second when no violations
    private var decayRate = 1.0

    fun configure(checkName: String, checkActions: List<ViolationAction>) {
        actions[checkName] = checkActions
    }

    fun setDecayRate(rate: Double) {
        decayRate = rate
    }

    fun flag(player: Player, playerData: PlayerData, checkName: String, amount: Double = 1.0, details: String = "") {
        val playerVls = violations.computeIfAbsent(checkName) { ConcurrentHashMap() }
        val currentVl = playerVls.getOrDefault(player.uniqueId, 0.0)
        val newVl = currentVl + amount
        playerVls[player.uniqueId] = newVl

        val vlInt = newVl.toInt()

        // Execute actions at thresholds
        val checkActions = actions[checkName] ?: getDefaultActions(checkName)
        for (action in checkActions) {
            if (vlInt >= action.threshold && (vlInt - amount.toInt()) < action.threshold) {
                executeAction(player, playerData, checkName, vlInt, action, details)
            }
        }

        // Always alert on flag
        alertManager.alert(player, checkName, vlInt, details)
    }

    private fun executeAction(
        player: Player,
        playerData: PlayerData,
        checkName: String,
        vl: Int,
        action: ViolationAction,
        details: String
    ) {
        when (action.type) {
            ViolationActionType.ALERT -> {
                alertManager.alert(player, checkName, vl, details)
            }
            ViolationActionType.SETBACK -> {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val safeX = playerData.lastSafeX
                    val safeY = playerData.lastSafeY
                    val safeZ = playerData.lastSafeZ
                    val safeLoc = player.location.clone()
                    safeLoc.x = safeX
                    safeLoc.y = safeY
                    safeLoc.z = safeZ
                    // Validate safe position is not inside a block
                    if (!safeLoc.block.type.isSolid) {
                        player.teleport(safeLoc)
                    }
                })
            }
            ViolationActionType.KICK -> {
                val cmd = action.command?.replace("%player%", player.name)
                    ?.replace("%vl%", vl.toString())
                    ?.replace("%check%", checkName)
                    ?: "kick ${player.name} Cheating detected ($checkName)"
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                })
            }
            ViolationActionType.BAN -> {
                val cmd = action.command?.replace("%player%", player.name)
                    ?.replace("%vl%", vl.toString())
                    ?.replace("%check%", checkName)
                    ?: "ban ${player.name} 7d Cheating ($checkName)"
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                })
            }
        }
    }

    fun getVl(playerUUID: UUID, checkName: String): Double {
        return violations[checkName]?.get(playerUUID) ?: 0.0
    }

    fun decay() {
        for ((_, playerVls) in violations) {
            for ((uuid, vl) in playerVls) {
                val newVl = maxOf(0.0, vl - decayRate)
                if (newVl <= 0.0) {
                    playerVls.remove(uuid)
                } else {
                    playerVls[uuid] = newVl
                }
            }
        }
    }

    fun clearPlayer(uuid: UUID) {
        for ((_, playerVls) in violations) {
            playerVls.remove(uuid)
        }
    }

    private fun getDefaultActions(checkName: String): List<ViolationAction> {
        return listOf(
            ViolationAction(10, ViolationActionType.ALERT),
            ViolationAction(15, ViolationActionType.SETBACK),
            ViolationAction(20, ViolationActionType.KICK, "kick %player% Cheating detected ($checkName)")
        )
    }
}
