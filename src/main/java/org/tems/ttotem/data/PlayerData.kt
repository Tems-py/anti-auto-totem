package org.tems.ttotem.data

import org.bukkit.GameMode
import org.tems.ttotem.simulation.SimulatorWorld
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService

class PlayerData(val uuid: UUID) {
    val positionBuffer = PositionBuffer()
    val combatTracker = CombatTracker()
    val simulatorWorld = SimulatorWorld()
    val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "ac-player-${uuid.toString().substring(0, 8)}").apply { isDaemon = true }
    }

    // Movement state
    @Volatile var velocityX: Double = 0.0
    @Volatile var velocityY: Double = 0.0
    @Volatile var velocityZ: Double = 0.0
    @Volatile var onGround: Boolean = true
    @Volatile var fallDistance: Double = 0.0
    @Volatile var lastSafeX: Double = 0.0
    @Volatile var lastSafeY: Double = 0.0
    @Volatile var lastSafeZ: Double = 0.0
    @Volatile var lastSafeTick: Long = 0L
    @Volatile var sprinting: Boolean = false
    @Volatile var sneaking: Boolean = false
    @Volatile var hasElytra: Boolean = false
    @Volatile var isInFluid: Boolean = false

    // Server-sent knockback
    @Volatile var pendingKnockbackX: Double = 0.0
    @Volatile var pendingKnockbackY: Double = 0.0
    @Volatile var pendingKnockbackZ: Double = 0.0
    @Volatile var knockbackTick: Long = -1L
    @Volatile var ticksSinceKnockback: Int = 0

    // Gamemode tracked via packets
    @Volatile var gameMode: GameMode = GameMode.SURVIVAL

    // Timer check
    @Volatile var packetCount: Int = 0
    @Volatile var lastPacketCountReset: Long = System.currentTimeMillis()
    @Volatile var sustainedHighPacketSeconds: Int = 0

    // Rotation tracking
    @Volatile var lastYaw: Float = 0f
    @Volatile var lastPitch: Float = 0f
    @Volatile var rotationUnchangedTicks: Int = 0

    // Tick counter
    @Volatile var tick: Long = 0L

    fun shutdown() {
        executor.shutdownNow()
    }

    fun updateSafePosition(x: Double, y: Double, z: Double, tick: Long) {
        lastSafeX = x
        lastSafeY = y
        lastSafeZ = z
        lastSafeTick = tick
    }
}
