package org.tems.ttotem.Listeners

import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityResurrectEvent
import org.tems.ttotem.Tetotem


class EntityResurrectListener(private var plugin: Tetotem) : Listener {
    @EventHandler
    fun entityResurrectEvent(event: EntityResurrectEvent) {
        if (!event.isCancelled) {
            if (event.entity.type == EntityType.PLAYER){
                plugin.punish.lastTotem[event.entity.uniqueId] = plugin.i
                val player = event.entity
                if (player is Player) {
                    plugin.punish.addTotem(player)
                }
            }
        }
    }
}