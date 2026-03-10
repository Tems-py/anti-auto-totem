package org.tems.ttotem.simulation

import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.Vector

class ReachSimulator {

    /**
     * Calculate the distance from the player's eye position to the target entity's hitbox.
     * Returns the reach distance, or Double.MAX_VALUE if the ray doesn't intersect.
     */
    fun calculateReach(player: Player, target: Entity): Double {
        val eyePos = player.eyeLocation
        val direction = eyePos.direction

        val targetBB = target.boundingBox
        val result = targetBB.rayTrace(
            eyePos.toVector(),
            direction,
            10.0 // max check distance
        )

        return if (result != null) {
            result.hitPosition.distance(eyePos.toVector())
        } else {
            // Fallback: simple center-to-center distance
            eyePos.toVector().distance(target.location.toVector().add(Vector(0.0, target.height / 2.0, 0.0)))
        }
    }

    /**
     * Calculate reach with latency compensation.
     * Expands the hitbox based on ping to account for position desync.
     */
    fun calculateReachWithCompensation(
        player: Player,
        target: Entity,
        pingMs: Int,
        maxCompensationTicks: Int
    ): Double {
        val compensationTicks = minOf(pingMs / 50, maxCompensationTicks)
        val expansion = compensationTicks * 0.1 // expand hitbox by 0.1 per tick of compensation

        val eyePos = player.eyeLocation
        val direction = eyePos.direction

        val targetBB = target.boundingBox.expand(expansion)
        val result = targetBB.rayTrace(
            eyePos.toVector(),
            direction,
            10.0
        )

        return if (result != null) {
            result.hitPosition.distance(eyePos.toVector())
        } else {
            val center = target.location.toVector().add(Vector(0.0, target.height / 2.0, 0.0))
            eyePos.toVector().distance(center)
        }
    }
}
