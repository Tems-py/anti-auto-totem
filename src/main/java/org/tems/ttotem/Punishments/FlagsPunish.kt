package org.tems.ttotem.Punishments

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.tems.ttotem.Tetotem
import java.util.*
import kotlin.collections.HashMap

class FlagsPunish(private var plugin: Tetotem) {
    var lastTotem = HashMap<UUID, Long>()
    var totemsPopped = HashMap<UUID, Int>()
    var totemsFlagged = HashMap<UUID, Int>()
    private var flags = HashMap<UUID, Int>()

    fun addTotem(player: Player) {
        if (totemsPopped[player.uniqueId] == null){
            totemsPopped[player.uniqueId] = 1
        } else {
            totemsPopped[player.uniqueId] = totemsPopped[player.uniqueId]!! + 1
        }
    }

    fun addFlag(player: Player) {
        if (totemsFlagged[player.uniqueId] == null){
            totemsFlagged[player.uniqueId] = 1
        } else {
            totemsFlagged[player.uniqueId] = totemsFlagged[player.uniqueId]!! + 1
        }
    }

    fun logFlag(player: HumanEntity, ticks: String){
        if (player is Player) addFlag(player)
        if (plugin.config.getString("adminMessage") != null) {
            val mm = MiniMessage.miniMessage();
            val message = plugin.config.getString("adminMessage").toString().replace("%player%", player.name)
                .replace("%ticks%", ticks)
            val parsed: Component = mm.deserialize(message)
            for (lp in plugin.server.onlinePlayers) {
                if (lp.hasPermission("ttotem.admin")) {
                    lp.sendMessage(parsed)
                }
            }
        }
        if (plugin.config.getString("consoleMessage") != null) {
            val p = plugin.server.getPlayer(player.uniqueId)
            var ping = -99
            if (p != null) ping = p.ping

            val consoleMessage =
                plugin.config.getString("consoleMessage").toString()
                    .replace("%player%", player.name)
                    .replace("%ticks%", ticks)
                    .replace("%ping%", ping.toString())
                    .replace("%popped%", totemsPopped[player.uniqueId].toString())
                    .replace("%flagged%", totemsFlagged[player.uniqueId].toString())

            plugin.logger.info(consoleMessage)
        }
    }

    fun punishAndRegisterFlag(player: HumanEntity) {
        val tps = plugin.config.getDouble("minimumTpsForFlagsCount")
        if (plugin.server.tps[0] < tps) return
        if (flags[player.uniqueId] == null) {
            flags[player.uniqueId] = 1
        } else {
            flags[player.uniqueId] =flags[player.uniqueId]!! + 1
        }

        val commands = plugin.config.getList("punishments") ?: return
        if (flags[player.uniqueId]!! < plugin.config.getInt("punishAfterFlags")) return

        lastTotem = HashMap<UUID, Long>()
        flags = HashMap<UUID, Int>()
        for (command in commands) {
            plugin.server.dispatchCommand(
                plugin.server.consoleSender,
                command.toString().replace("%player%", player.name)
            )
            plugin.i = 0
        }
    }
}