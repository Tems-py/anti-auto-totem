package org.tems.ttotem.config

import org.bukkit.GameMode
import org.bukkit.configuration.file.FileConfiguration

class ACConfig(private val config: FileConfiguration) {

    val debug: Boolean get() = config.getBoolean("anticheat.debug", false)
    val prefix: String get() = config.getString("anticheat.prefix", "&8[&cAC&8] ") ?: "&8[&cAC&8] "
    val alertPermission: String get() = config.getString("anticheat.alert-permission", "anticheat.alerts") ?: "anticheat.alerts"
    val threadPoolSize: Int get() = config.getInt("anticheat.thread-pool-size", 4)
    val latencyCompensation: Boolean get() = config.getBoolean("anticheat.latency-compensation", true)
    val maxPingCompensationTicks: Int get() = config.getInt("anticheat.max-ping-compensation-ticks", 6)

    val setbackEnabled: Boolean get() = config.getBoolean("setback.enabled", true)
    val setbackSaveIntervalTicks: Int get() = config.getInt("setback.save-interval-ticks", 40)

    // Movement checks
    val speedEnabled: Boolean get() = config.getBoolean("checks.movement.speed.enabled", true)
    val speedTolerance: Double get() = config.getDouble("checks.movement.speed.tolerance", 0.05)
    val flightEnabled: Boolean get() = config.getBoolean("checks.movement.flight.enabled", true)
    val timerEnabled: Boolean get() = config.getBoolean("checks.movement.timer.enabled", true)
    val timerMaxPacketsPerSecond: Int get() = config.getInt("checks.movement.timer.max-cps", 22)
    val noFallEnabled: Boolean get() = config.getBoolean("checks.movement.nofall.enabled", true)
    val stepEnabled: Boolean get() = config.getBoolean("checks.movement.step.enabled", true)

    // Combat checks
    val reachEnabled: Boolean get() = config.getBoolean("checks.combat.reach.enabled", true)
    val maxReach: Double get() = config.getDouble("checks.combat.reach.max-reach", 3.2)
    val killAuraEnabled: Boolean get() = config.getBoolean("checks.combat.killaura.enabled", true)
    val autoClickerEnabled: Boolean get() = config.getBoolean("checks.combat.autoclicker.enabled", true)
    val autoClickerMaxCps: Int get() = config.getInt("checks.combat.autoclicker.max-cps", 20)
    val autoClickerMinStdDev: Double get() = config.getDouble("checks.combat.autoclicker.min-stddev", 5.0)
    val aimBotEnabled: Boolean get() = config.getBoolean("checks.combat.aimbot.enabled", true)
    val aimBotMaxDeltaPerTick: Double get() = config.getDouble("checks.combat.aimbot.max-delta-per-tick", 180.0)
    val velocityEnabled: Boolean get() = config.getBoolean("checks.combat.velocity.enabled", true)

    // Exemptions
    val exemptGameModes: Set<GameMode> get() {
        val modes = config.getStringList("exemptions.gamemodes")
        return modes.mapNotNull {
            try { GameMode.valueOf(it) } catch (_: Exception) { null }
        }.toSet()
    }
    val exemptPermissions: List<String> get() = config.getStringList("exemptions.permissions")
    val exemptWorlds: List<String> get() = config.getStringList("exemptions.worlds")

    fun reload(newConfig: FileConfiguration) {
        // Config is read live from FileConfiguration, so reloading the plugin config is sufficient
    }
}
