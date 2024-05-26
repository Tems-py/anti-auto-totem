package org.tems.ttotem.Listeners

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.tems.ttotem.Punishments.FlagsPunish
import org.tems.ttotem.Tetotem

class SwapHandListener(private val plugin: Tetotem) : Listener {
    @EventHandler
    fun swapHandListener(event: PlayerSwapHandItemsEvent){
        val item = event.offHandItem ?: return
        val player = event.player
        val ticks = (plugin.config.getInt("maximumAllowedTicksToChangeTotemAfterPop") / 2)
        if (item.type != Material.TOTEM_OF_UNDYING) return
        if (plugin.punish.lastTotem[player.uniqueId] == null) return
        val lastUse = plugin.punish.lastTotem[player.uniqueId] ?: return
        if (plugin.i.minus(lastUse) > ticks) return

        plugin.punish.logFlag(player, "$ticks A CHECK")
        plugin.punish.punishAndRegisterFlag(player)
    }
}