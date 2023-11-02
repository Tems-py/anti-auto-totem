package org.tems.ttotem

import org.bukkit.plugin.java.JavaPlugin
import org.tems.ttotem.Listeners.EntityResurrectListener

class Tetotem : JavaPlugin() {
    override fun onEnable() {
        logger.info("Staring plugin")
        // Plugin startup logic
    }

    fun registerListeners() {
        server.pluginManager.registerEvents(EntityResurrectListener(this), this)
    }

    override fun onDisable() {
        logger.info("Stopping plugin")
        // Plugin shutdown logic
    }
}
