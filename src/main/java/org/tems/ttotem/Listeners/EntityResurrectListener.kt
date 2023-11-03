package org.tems.ttotem.Listeners

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.plugin.Plugin
import org.checkerframework.checker.units.qual.mm
import java.util.*
import kotlin.collections.HashMap


class EntityResurrectListener(private var plugin: Plugin) : Listener {
    private var lastTotem: HashMap<UUID, Long> = HashMap<UUID, Long>()
    private var flags: HashMap<UUID, Int> = HashMap<UUID, Int>()
    @EventHandler
    fun entityResurrectEvent(event: EntityResurrectEvent) {
        if (!event.isCancelled) {
            if (event.entity.type == EntityType.PLAYER){
                lastTotem[event.entity.uniqueId] = plugin.server.tickTimes.last()
            }
        }
    }

    @EventHandler
    fun inventoryClickEvent(event: InventoryClickEvent) {
        val item = event.cursor
        val player = event.whoClicked
        val ticks = plugin.config.getInt("maximumAllowedTicksToChangeTotem")
        if (item.type == Material.TOTEM_OF_UNDYING && event.slot == 40) {
            if (lastTotem[player.uniqueId] != null) {
                val lastUse = lastTotem[player.uniqueId]
                if (plugin.server.tickTimes.last().minus(lastUse!!) < ticks) {
                    val mm = MiniMessage.miniMessage();
                    if (plugin.config.getString("adminMessage") == null){
                        return
                    }
                    val message = plugin.config.getString("adminMessage").toString().replace("%player%", player.name).replace("%ticks%", ticks.toString())
                    val parsed: Component = mm.deserialize(message)
                    plugin.logger.info(message)
                    if (flags[player.uniqueId] == null){
                        flags[player.uniqueId] = 1
                    } else  {
                        flags[player.uniqueId] = flags[player.uniqueId]!! + 1
                    }

                    if (flags[player.uniqueId]!! >= plugin.config.getInt("punishAfterFlags")){
                        val commands = plugin.config.getList("punishments")
                        if (commands != null){
                            for (command in commands){
                                plugin.server.dispatchCommand(plugin.server.consoleSender, command.toString().replace("%player%", player.name))
                            }
                        }
                    }

                    for (lp in plugin.server.onlinePlayers){
                        if (lp.hasPermission("ttotem.admin")){
                            lp.sendMessage(parsed)
                        }
                    }
                }
            }
        }
    }
}