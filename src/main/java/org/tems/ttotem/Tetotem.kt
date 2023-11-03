package org.tems.ttotem

import org.bukkit.plugin.java.JavaPlugin
import org.tems.ttotem.Listeners.EntityResurrectListener


class Tetotem : JavaPlugin() {
    override fun onEnable() {
        logger.info("Staring plugin")
        saveDefaultConfig()
        registerListeners()

        val config = getConfig()
        logger.info("maximumAllowedTicksToChangeTotem: " + config.getInt("maximumAllowedTicksToChangeTotem").toString())
    }

    private fun registerListeners() {
        server.pluginManager.registerEvents(EntityResurrectListener(this), this)
        logger.info("Registered listeners")
    }

    override fun onDisable() {
        logger.info("Stopping plugin")
    }
}
