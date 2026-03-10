package org.tems.ttotem.checks.movement

import org.bukkit.entity.Player
import org.tems.ttotem.checks.MovementCheck
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.violation.ViolationManager

class TimerCheck(
    private val config: ACConfig,
    private val violationManager: ViolationManager
) : MovementCheck {

    override fun check(player: Player, data: PlayerData) {
        data.packetCount++
        val now = System.currentTimeMillis()
        val elapsed = now - data.lastPacketCountReset

        if (elapsed >= 1000) {
            val packetsPerSecond = data.packetCount.toDouble() / (elapsed / 1000.0)
            val maxAllowed = config.timerMaxPacketsPerSecond

            if (packetsPerSecond > maxAllowed + 2) { // 24+ packets/sec
                data.sustainedHighPacketSeconds++
                if (data.sustainedHighPacketSeconds >= 2) {
                    violationManager.flag(
                        player, data, "Timer",
                        amount = 2.0,
                        details = String.format("packets/s=%.1f (max=%d) sustained=%ds",
                            packetsPerSecond, maxAllowed, data.sustainedHighPacketSeconds)
                    )
                }
            } else {
                data.sustainedHighPacketSeconds = maxOf(0, data.sustainedHighPacketSeconds - 1)
            }

            data.packetCount = 0
            data.lastPacketCountReset = now
        }
    }
}
