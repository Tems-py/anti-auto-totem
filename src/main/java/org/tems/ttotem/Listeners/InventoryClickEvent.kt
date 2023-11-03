package org.tems.ttotem.Listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.tems.ttotem.Tetotem
import java.util.*
import kotlin.collections.HashMap

class InventoryClickEvent(private val plugin: Tetotem) : Listener {
    private var flags: HashMap<UUID, Int> = HashMap<UUID, Int>()

    @EventHandler
    fun inventoryClickEvent(event: InventoryClickEvent) {
        val item = event.cursor
        val player = event.whoClicked
        val ticks = plugin.config.getInt("maximumAllowedTicksToChangeTotem")
        if (item.type != Material.TOTEM_OF_UNDYING || event.slot != 40) return
        if (plugin.lastTotem[player.uniqueId] == null) return

        val lastUse = plugin.lastTotem[player.uniqueId] ?: return
        if (plugin.i.minus(lastUse) > ticks) return

        val mm = MiniMessage.miniMessage();
        if (plugin.config.getString("adminMessage") == null) {
            return
        }

        val message = plugin.config.getString("adminMessage").toString().replace("%player%", player.name)
            .replace("%ticks%", ticks.toString())
        val parsed: Component = mm.deserialize(message)
        val consoleMessage = plugin.config.getString("consoleMessage").toString().replace("%player%", player.name)
            .replace("%ticks%", ticks.toString())
        plugin.logger.info(consoleMessage)

        for (lp in plugin.server.onlinePlayers) {
            if (lp.hasPermission("ttotem.admin")) {
                lp.sendMessage(parsed)
            }
        }

        val tps = plugin.config.getDouble("minimumTpsForFlagsCount")
        if (plugin.server.tps[0] < tps) return


        if (flags[player.uniqueId] == null) {
            flags[player.uniqueId] = 1
        } else {
            flags[player.uniqueId] = flags[player.uniqueId]!! + 1
        }

        val commands = plugin.config.getList("punishments") ?: return
        if (flags[player.uniqueId]!! < plugin.config.getInt("punishAfterFlags")) return

        plugin.lastTotem = HashMap<UUID, Long>()
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