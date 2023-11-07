package org.tems.ttotem.Listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.tems.ttotem.Tetotem
import java.util.*
import kotlin.collections.HashMap

class InventoryClickEvent(private val plugin: Tetotem) : Listener {
    private var flags: HashMap<UUID, Int> = HashMap<UUID, Int>()
    private var lastClick: HashMap<UUID, Long> = HashMap<UUID, Long>()

    @EventHandler
    fun inventoryClickEvent(event: InventoryClickEvent) {
        if (autoTotemChecks(event)) return
        if (plugin.config.getBoolean("detectFastTotemEquipInInventory")){
            bindTotemChecks(event)
        }
        val player = event.whoClicked
        lastClick[player.uniqueId] = plugin.i
    }

    private fun bindTotemChecks(event: InventoryClickEvent){
        val player = event.whoClicked
        if (!plugin.config.getBoolean("detectUnknownTypeActionsWhenSwitchingToOffhand")) return
        if (event.click != ClickType.UNKNOWN) return
        if (event.slot != 40) return

        logFlag(player, "detect type - UNKNOWN")
        punishAndRegisterFlag(player)
    }

    private fun autoTotemChecks(event: InventoryClickEvent) : Boolean {
        val item = event.cursor
        val player = event.whoClicked
        val ticks = plugin.config.getInt("maximumAllowedTicksToChangeTotemAfterPop")
        if (item.type != Material.TOTEM_OF_UNDYING || event.slot != 40) return false
        if (plugin.lastTotem[player.uniqueId] == null) return false

        val lastUse = plugin.lastTotem[player.uniqueId] ?: return false
        if (plugin.i.minus(lastUse) > ticks) return false

        logFlag(player, ticks.toString())
        punishAndRegisterFlag(player)
        return true
    }

    private fun logFlag(player: HumanEntity, ticks: String){
        if (plugin.config.getString("adminMessage") == null) {
            println("\"adminMessage\" is missing in config")
            return
        }
        val mm = MiniMessage.miniMessage();
        val message = plugin.config.getString("adminMessage").toString().replace("%player%", player.name)
            .replace("%ticks%", ticks)
        val parsed: Component = mm.deserialize(message)
        val consoleMessage = plugin.config.getString("consoleMessage").toString().replace("%player%", player.name)
            .replace("%ticks%", ticks)
        plugin.logger.info(consoleMessage)

        for (lp in plugin.server.onlinePlayers) {
            if (lp.hasPermission("ttotem.admin")) {
                lp.sendMessage(parsed)
            }
        }
    }

    private fun punishAndRegisterFlag(player: HumanEntity) {
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