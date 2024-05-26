package org.tems.ttotem.Listeners

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.tems.ttotem.Tetotem
import java.util.*
import kotlin.collections.HashMap

class InventoryClickEvent(private val plugin: Tetotem) : Listener {
    private var lastClick: HashMap<UUID, Long> = HashMap<UUID, Long>()

    @EventHandler
    fun inventoryClickEvent(event: InventoryClickEvent) {
        if (!autoTotemChecks(event)){
            if (!quickItemMoveChecks(event)){
                if (plugin.config.getBoolean("detectUnknownTypeActionsWhenSwitchingToOffhand")){
                    bindTotemChecks(event)
                }
            }
        }

        val player = event.whoClicked
        lastClick[player.uniqueId] = plugin.i
    }



    private fun quickItemMoveChecks(event: InventoryClickEvent): Boolean {
        val player = event.whoClicked
        val ticks = plugin.config.getInt("maximumAllowedTicksToChangeTotemAfterPop") / 2
        if (event.slot != 40) return false
        if (plugin.punish.lastTotem[player.uniqueId] == null) return false

        val lastUse = plugin.punish.lastTotem[player.uniqueId] ?: return false
        if (plugin.i.minus(lastUse) > ticks) return false

        plugin.punish.logFlag(player, "$ticks B CHECK")
        plugin.punish.punishAndRegisterFlag(player)
        return true
    }

    private fun bindTotemChecks(event: InventoryClickEvent): Boolean {
        val player = event.whoClicked
        if (!plugin.config.getBoolean("detectUnknownTypeActionsWhenSwitchingToOffhand")) return false
        if (event.click != ClickType.UNKNOWN) return false
        if (event.slot != 40) return false

        plugin.punish.logFlag(player, "C CHECK")
        plugin.punish.punishAndRegisterFlag(player)
        return true
    }

    private fun autoTotemChecks(event: InventoryClickEvent): Boolean{
        val item = event.cursor
        val player = event.whoClicked
        val ticks = plugin.config.getInt("maximumAllowedTicksToChangeTotemAfterPop")
        if (item.type != Material.TOTEM_OF_UNDYING || event.slot != 40) return false
        if (plugin.punish.lastTotem[player.uniqueId] == null) return false

        val lastUse = plugin.punish.lastTotem[player.uniqueId] ?: return false
        if (plugin.i.minus(lastUse) > ticks) return false

        plugin.punish.logFlag(player, "$ticks D CHECK")
        plugin.punish.punishAndRegisterFlag(player)
        return true
    }
}