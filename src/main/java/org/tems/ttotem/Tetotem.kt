package org.tems.ttotem

import com.github.retrooper.packetevents.PacketEvents
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.tems.ttotem.Listeners.EntityResurrectListener
import org.tems.ttotem.Listeners.InventoryClickEvent
import org.tems.ttotem.Listeners.SwapHandListener
import org.tems.ttotem.Punishments.FlagsPunish
import org.tems.ttotem.alert.AlertManager
import org.tems.ttotem.checks.CheckManager
import org.tems.ttotem.checks.combat.VelocityCheck
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.packet.PacketListener
import org.tems.ttotem.simulation.PhysicsSimulator
import org.tems.ttotem.simulation.ReachSimulator
import org.tems.ttotem.violation.ViolationManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap


class Tetotem : JavaPlugin(), Listener {
    var punish = FlagsPunish(this)
    var i: Long = 0

    // Anticheat components
    private val playerDataMap = ConcurrentHashMap<UUID, PlayerData>()
    private lateinit var acConfig: ACConfig
    private lateinit var alertManager: AlertManager
    private lateinit var violationManager: ViolationManager
    private lateinit var checkManager: CheckManager
    private lateinit var physicsSimulator: PhysicsSimulator
    private lateinit var reachSimulator: ReachSimulator

    override fun onLoad() {
        // PacketEvents must be loaded in onLoad
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this))
        PacketEvents.getAPI().settings
            .reEncodeByDefault(false)
            .checkForUpdates(false)
            .bStats(false)
        PacketEvents.getAPI().load()
    }

    override fun onEnable() {
        logger.info("Starting plugin")
        saveDefaultConfig()

        // Register existing auto-totem listeners
        registerListeners()
        countTicks()

        // Initialize anticheat system
        initAntiCheat()

        logger.info("AntiCheat system initialized")
    }

    private fun initAntiCheat() {
        acConfig = ACConfig(config)
        alertManager = AlertManager(this, acConfig)
        alertManager.init()
        violationManager = ViolationManager(this, alertManager)
        physicsSimulator = PhysicsSimulator()
        reachSimulator = ReachSimulator()
        checkManager = CheckManager(acConfig, violationManager, physicsSimulator, reachSimulator)
        checkManager.init()

        // Extract velocity check for the packet listener
        val velocityCheck = if (acConfig.velocityEnabled) VelocityCheck(acConfig, violationManager) else null

        // Register packet listener
        val packetListener = PacketListener(playerDataMap, checkManager, acConfig, this, velocityCheck)
        PacketEvents.getAPI().eventManager.registerListener(packetListener)
        PacketEvents.getAPI().init()

        // Register player join/quit for data lifecycle
        server.pluginManager.registerEvents(this, this)

        // Initialize data for already-online players
        for (player in Bukkit.getOnlinePlayers()) {
            playerDataMap[player.uniqueId] = PlayerData(player.uniqueId)
        }

        // VL decay task - runs every second
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, Runnable {
            violationManager.decay()
        }, 20L, 20L)
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val data = PlayerData(event.player.uniqueId)
        playerDataMap[event.player.uniqueId] = data

        // Take initial world snapshot
        Bukkit.getScheduler().runTaskLater(this, Runnable {
            data.simulatorWorld.snapshot(event.player)
        }, 5L)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val data = playerDataMap.remove(event.player.uniqueId)
        data?.shutdown()
        violationManager.clearPlayer(event.player.uniqueId)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (command.name.equals("ac", ignoreCase = true) || command.name.equals("anticheat", ignoreCase = true)) {
            if (args.isNotEmpty() && args[0].equals("reload", ignoreCase = true)) {
                if (sender.hasPermission("anticheat.admin")) {
                    reloadConfig()
                    acConfig = ACConfig(config)
                    sender.sendMessage("AntiCheat config reloaded.")
                    return true
                }
            }
        }
        return false
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

        // Shutdown all player executors
        for ((_, data) in playerDataMap) {
            data.shutdown()
        }
        playerDataMap.clear()

        // Shutdown alert manager
        if (::alertManager.isInitialized) {
            alertManager.shutdown()
        }

        // Terminate PacketEvents
        PacketEvents.getAPI().terminate()
    }
}
