package org.tems.ttotem.checks

import org.bukkit.entity.Player
import org.tems.ttotem.checks.combat.*
import org.tems.ttotem.checks.movement.*
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.simulation.PhysicsSimulator
import org.tems.ttotem.simulation.ReachSimulator
import org.tems.ttotem.violation.ViolationManager

class CheckManager(
    private val config: ACConfig,
    private val violationManager: ViolationManager,
    private val physicsSimulator: PhysicsSimulator,
    private val reachSimulator: ReachSimulator
) {
    private val movementChecks = mutableListOf<MovementCheck>()
    private val combatChecks = mutableListOf<CombatCheck>()

    fun init() {
        // Movement checks
        if (config.speedEnabled) movementChecks.add(SpeedCheck(config, violationManager, physicsSimulator))
        if (config.flightEnabled) movementChecks.add(FlightCheck(config, violationManager, physicsSimulator))
        if (config.timerEnabled) movementChecks.add(TimerCheck(config, violationManager))
        if (config.noFallEnabled) movementChecks.add(NoFallCheck(config, violationManager))
        if (config.stepEnabled) movementChecks.add(StepCheck(config, violationManager, physicsSimulator))

        // Combat checks
        if (config.reachEnabled) combatChecks.add(ReachCheck(config, violationManager, reachSimulator))
        if (config.killAuraEnabled) combatChecks.add(KillAuraCheck(config, violationManager))
        if (config.autoClickerEnabled) combatChecks.add(AutoClickerCheck(config, violationManager))
        if (config.aimBotEnabled) combatChecks.add(AimBotCheck(config, violationManager))
        if (config.velocityEnabled) combatChecks.add(VelocityCheck(config, violationManager))
    }

    fun runMovementChecks(player: Player, data: PlayerData) {
        if (isExempt(player, data)) return
        for (check in movementChecks) {
            check.check(player, data)
        }
    }

    fun runCombatChecks(player: Player, data: PlayerData, targetEntityId: Int) {
        if (isExempt(player, data)) return
        for (check in combatChecks) {
            check.check(player, data, targetEntityId)
        }
    }

    private fun isExempt(player: Player, data: PlayerData): Boolean {
        if (data.gameMode in config.exemptGameModes) return true
        for (perm in config.exemptPermissions) {
            if (player.hasPermission(perm)) return true
        }
        if (config.exemptWorlds.contains(player.world.name)) return true
        return false
    }
}

interface MovementCheck {
    fun check(player: Player, data: PlayerData)
}

interface CombatCheck {
    fun check(player: Player, data: PlayerData, targetEntityId: Int)
}
