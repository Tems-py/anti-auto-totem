package org.tems.ttotem.Listeners

import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityResurrectEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.plugin.Plugin
import java.util.*
import kotlin.collections.HashMap

class EntityResurrectListener(private var plugin: Plugin) : Listener {
    private var lastTotem: HashMap<UUID, Long> = HashMap<UUID, Long>()
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
        if (item.type == Material.TOTEM_OF_UNDYING && event.slot == 40) {
            if (lastTotem[player.uniqueId] != null) {
                if ((lastTotem[player.uniqueId]?.minus(plugin.server.tickTimes.last()) ?: 1111) < 10) {
                    plugin.logger.info("Gracz " + player.name + " użył totemu w mniej niż 0.5s")
                }
            }
        }
    }
}