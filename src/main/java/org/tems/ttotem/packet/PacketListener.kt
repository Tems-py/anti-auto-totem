package org.tems.ttotem.packet

import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketReceiveEvent
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPosition
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerPositionAndRotation
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerRotation
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityVelocity
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.tems.ttotem.checks.CheckManager
import org.tems.ttotem.checks.combat.VelocityCheck
import org.tems.ttotem.config.ACConfig
import org.tems.ttotem.data.PlayerData
import org.tems.ttotem.data.PositionEntry
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PacketListener(
    private val playerDataMap: ConcurrentHashMap<UUID, PlayerData>,
    private val checkManager: CheckManager,
    private val config: ACConfig,
    private val plugin: Plugin,
    private val velocityCheck: VelocityCheck?
) : SimplePacketListenerAbstract(PacketListenerPriority.NORMAL) {

    override fun onPacketReceive(event: PacketReceiveEvent) {
        val user = event.user ?: return
        val uuid = user.uuid ?: return
        val data = playerDataMap[uuid] ?: return

        when (event.packetType) {
            PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION -> {
                val wrapper = WrapperPlayClientPlayerPositionAndRotation(event)
                val x = wrapper.location.x
                val y = wrapper.location.y
                val z = wrapper.location.z
                val yaw = wrapper.location.yaw
                val pitch = wrapper.location.pitch
                val onGround = wrapper.isOnGround

                handleMovement(uuid, data, x, y, z, yaw, pitch, onGround)
            }

            PacketType.Play.Client.PLAYER_POSITION -> {
                val wrapper = WrapperPlayClientPlayerPosition(event)
                val x = wrapper.location.x
                val y = wrapper.location.y
                val z = wrapper.location.z
                val onGround = wrapper.isOnGround

                val last = data.positionBuffer.latest()
                handleMovement(uuid, data, x, y, z, last?.yaw ?: 0f, last?.pitch ?: 0f, onGround)
            }

            PacketType.Play.Client.PLAYER_ROTATION -> {
                val wrapper = WrapperPlayClientPlayerRotation(event)
                val yaw = wrapper.yaw
                val pitch = wrapper.pitch
                val onGround = wrapper.isOnGround

                val last = data.positionBuffer.latest()
                handleMovement(uuid, data, last?.x ?: 0.0, last?.y ?: 0.0, last?.z ?: 0.0, yaw, pitch, onGround)
            }

            PacketType.Play.Client.PLAYER_FLYING -> {
                val wrapper = WrapperPlayClientPlayerFlying(event)
                val last = data.positionBuffer.latest() ?: return
                handleMovement(uuid, data, last.x, last.y, last.z, last.yaw, last.pitch, wrapper.isOnGround)
            }

            PacketType.Play.Client.INTERACT_ENTITY -> {
                val wrapper = WrapperPlayClientInteractEntity(event)
                if (wrapper.action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                    val entityId = wrapper.entityId
                    data.combatTracker.recordAttack(entityId, System.currentTimeMillis(), data.tick)

                    // Run combat checks on player's executor
                    data.executor.submit {
                        val player = Bukkit.getPlayer(uuid) ?: return@submit
                        checkManager.runCombatChecks(player, data, entityId)
                    }
                }
            }

            PacketType.Play.Client.ANIMATION -> {
                // Swing arm - record for KillAura no-swing detection
                data.combatTracker.recordSwing(data.tick)
            }
        }
    }

    override fun onPacketSend(event: PacketSendEvent) {
        val user = event.user ?: return
        val uuid = user.uuid ?: return
        val data = playerDataMap[uuid] ?: return

        if (event.packetType == PacketType.Play.Server.ENTITY_VELOCITY) {
            val wrapper = WrapperPlayServerEntityVelocity(event)
            if (wrapper.entityId == user.entityId) {
                // Server sent knockback — store expected velocity
                data.pendingKnockbackX = wrapper.velocity.x
                data.pendingKnockbackY = wrapper.velocity.y
                data.pendingKnockbackZ = wrapper.velocity.z
                data.knockbackTick = data.tick
                data.ticksSinceKnockback = 0
            }
        }
    }

    private fun handleMovement(
        uuid: UUID, data: PlayerData,
        x: Double, y: Double, z: Double,
        yaw: Float, pitch: Float, onGround: Boolean
    ) {
        data.tick++

        val entry = PositionEntry(x, y, z, yaw, pitch, onGround, System.currentTimeMillis())
        data.positionBuffer.add(entry)

        // Track rotation changes
        if (Math.abs(yaw - data.lastYaw) < 0.01f && Math.abs(pitch - data.lastPitch) < 0.01f) {
            data.rotationUnchangedTicks++
        } else {
            data.rotationUnchangedTicks = 0
        }
        data.lastYaw = yaw
        data.lastPitch = pitch

        // Update safe position periodically
        if (data.tick - data.lastSafeTick >= config.setbackSaveIntervalTicks) {
            if (data.simulatorWorld.isOnGround(x, y, z)) {
                data.updateSafePosition(x, y, z, data.tick)
            }
        }

        // Snapshot world state on main thread when player moves significantly
        val prev = data.positionBuffer.previous()
        if (prev != null) {
            val dx = x - prev.x
            val dy = y - prev.y
            val dz = z - prev.z
            val dist = Math.sqrt(dx * dx + dy * dy + dz * dz)
            if (dist > 1.0 || data.tick % 20 == 0L) {
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    val player = Bukkit.getPlayer(uuid) ?: return@Runnable
                    data.simulatorWorld.snapshot(player)
                    data.isInFluid = player.isInWater
                })
            }
        }

        // Run movement checks on player's dedicated executor
        data.executor.submit {
            val player = Bukkit.getPlayer(uuid) ?: return@submit
            checkManager.runMovementChecks(player, data)

            // Also check velocity (anti-knockback)
            velocityCheck?.checkVelocity(player, data)

            // Update velocity tracking from position deltas
            if (prev != null) {
                data.velocityX = x - prev.x
                data.velocityY = y - prev.y
                data.velocityZ = z - prev.z
            }
        }
    }
}
