package org.tems.ttotem.Listeners

import org.bukkit.entity.EntityType
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityResurrectEvent
import org.tems.ttotem.Tetotem


class EntityResurrectListener(private var plugin: Tetotem) : Listener {
    @EventHandler
    fun entityResurrectEvent(event: EntityResurrectEvent) {
        if (!event.isCancelled) {
            if (event.entity.type == EntityType.PLAYER){
                plugin.lastTotem[event.entity.uniqueId] = plugin.server.tickTimes.last()
            }
        }
    }
}