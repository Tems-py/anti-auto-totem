package org.tems.ttotem

import org.bukkit.plugin.java.JavaPlugin
import org.tems.ttotem.Listeners.EntityResurrectListener
import org.tems.ttotem.Listeners.InventoryClickEvent
import java.util.*
import kotlin.collections.HashMap


class Tetotem : JavaPlugin() {
    var lastTotem: HashMap<UUID, Long> = HashMap<UUID, Long>()

    override fun onEnable() {
        logger.info("Staring plugin")
        saveDefaultConfig()
        registerListeners()

        val config = getConfig()
        logger.info("maximumAllowedTicksToChangeTotem: " + config.getInt("maximumAllowedTicksToChangeTotem").toString())
    }

    private fun registerListeners() {
        server.pluginManager.registerEvents(EntityResurrectListener(this), this)
        server.pluginManager.registerEvents(InventoryClickEvent(this), this)
        logger.info("Registered listeners")
    }

    override fun onDisable() {
        logger.info("Stopping plugin")
    }
}
