package org.tems.ttotem.alert

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.tems.ttotem.config.ACConfig
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date

class AlertManager(
    private val plugin: Plugin,
    private val config: ACConfig
) {
    private val mm = MiniMessage.miniMessage()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
    private var logFile: File? = null
    private var logWriter: PrintWriter? = null

    fun init() {
        val logDir = File(plugin.dataFolder, "logs")
        logDir.mkdirs()
        logFile = File(logDir, "violations.log")
        logWriter = PrintWriter(FileWriter(logFile, true), true)
    }

    fun alert(player: Player, checkName: String, vl: Int, details: String = "") {
        val prefix = config.prefix
        val message = "$prefix<red>${player.name}</red> <gray>failed</gray> <gold>$checkName</gold> " +
            "<gray>(VL: <white>$vl</white>)</gray>" +
            if (details.isNotEmpty()) " <dark_gray>[$details]</dark_gray>" else ""

        val parsed: Component = mm.deserialize(message)

        // Send to staff with permission
        Bukkit.getScheduler().runTask(plugin, Runnable {
            for (online in Bukkit.getOnlinePlayers()) {
                if (online.hasPermission(config.alertPermission)) {
                    online.sendMessage(parsed)
                }
            }
        })

        // Console log
        val consoleMsg = "[AC] ${player.name} failed $checkName (VL: $vl) $details"
        plugin.logger.info(consoleMsg)

        // File log
        val timestamp = dateFormat.format(Date())
        logWriter?.println("[$timestamp] ${player.name} - $checkName - VL:$vl - $details")
    }

    fun shutdown() {
        logWriter?.close()
    }
}
