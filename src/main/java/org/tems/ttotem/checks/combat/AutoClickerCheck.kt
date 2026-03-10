package org.tems.ttotem.checks.combat

import org.bukkit.entity.Player
import org.tems.ttotem.checks.CombatCheck
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.violation.ViolationManager

class AutoClickerCheck(
    private val config: ACConfig,
    private val violationManager: ViolationManager
) : CombatCheck {

    override fun check(player: Player, data: PlayerData, targetEntityId: Int) {
        val deltas = data.combatTracker.getClickDeltas()

        // Need enough samples
        if (deltas.size < 15) return

        // Check standard deviation - too consistent clicking indicates autoclicker
        val stdDev = data.combatTracker.getClickStdDev()
        if (stdDev < config.autoClickerMinStdDev) {
            violationManager.flag(
                player, data, "AutoClicker",
                amount = 3.0,
                details = String.format("stddev=%.2fms (min=%.1f) samples=%d", stdDev, config.autoClickerMinStdDev, deltas.size)
            )
        }

        // Check CPS over 3 second window
        val cps = data.combatTracker.getCpsOverWindow(3000)
        if (cps > config.autoClickerMaxCps) {
            violationManager.flag(
                player, data, "AutoClicker",
                amount = 2.0,
                details = String.format("cps=%.1f (max=%d) over 3s", cps, config.autoClickerMaxCps)
            )
        }
    }
}
