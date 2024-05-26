package org.tems.ttotem

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.tems.ttotem.Listeners.EntityResurrectListener
import org.tems.ttotem.Listeners.InventoryClickEvent
import org.tems.ttotem.Listeners.SwapHandListener
import org.tems.ttotem.Punishments.FlagsPunish
import java.util.*


class Tetotem : JavaPlugin() {
    var punish = FlagsPunish(this)
    var i: Long = 0
    override fun onEnable() {
        logger.info("Staring plugin")
        saveDefaultConfig()
        registerListeners()
        countTicks()
    }

    private fun registerListeners() {
        server.pluginManager.registerEvents(EntityResurrectListener(this), this)
        server.pluginManager.registerEvents(InventoryClickEvent(this), this)
        server.pluginManager.registerEvents(SwapHandListener(this), this)
        logger.info("Registered listeners")
    }

    private fun countTicks() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(
            this,
            Runnable {
                i += 1;
                if (i > 20 * 60 * 60){
                    i = 0
                }
            },
            0L,
            1L
        )
    }

    override fun onDisable() {
        logger.info("Stopping plugin")
    }


}
