package org.tems.ttotem.data

import java.util.concurrent.ConcurrentLinkedDeque

data class AttackEntry(
    val targetEntityId: Int,
    val timestamp: Long,
    val tick: Long
)

class CombatTracker {
    private val attacks = ConcurrentLinkedDeque<AttackEntry>()
    private val clickDeltas = ConcurrentLinkedDeque<Long>()
    private val maxAttackHistory = 50
    private val maxClickSamples = 20

    @Volatile var lastSwingTick: Long = -1L
    @Volatile var lastAttackTick: Long = -1L

    fun recordAttack(entityId: Int, timestamp: Long, tick: Long) {
        attacks.addLast(AttackEntry(entityId, timestamp, tick))
        while (attacks.size > maxAttackHistory) {
            attacks.pollFirst()
        }

        // Record click delta
        val prev = attacks.toList().let { list ->
            if (list.size >= 2) list[list.size - 2].timestamp else null
        }
        if (prev != null) {
            clickDeltas.addLast(timestamp - prev)
            while (clickDeltas.size > maxClickSamples) {
                clickDeltas.pollFirst()
            }
        }
        lastAttackTick = tick
    }

    fun recordSwing(tick: Long) {
        lastSwingTick = tick
    }

    fun getDistinctTargetsInTick(tick: Long): Int {
        return attacks.filter { it.tick == tick }.map { it.targetEntityId }.distinct().size
    }

    fun getClickDeltas(): List<Long> = clickDeltas.toList()

    fun getClickStdDev(): Double {
        val deltas = clickDeltas.toList().map { it.toDouble() }
        if (deltas.size < 2) return Double.MAX_VALUE
        val mean = deltas.average()
        val variance = deltas.map { (it - mean) * (it - mean) }.average()
        return Math.sqrt(variance)
    }

    fun getCpsOverWindow(windowMs: Long): Double {
        val now = System.currentTimeMillis()
        val count = attacks.count { now - it.timestamp <= windowMs }
        return count.toDouble() / (windowMs / 1000.0)
    }

    fun recentAttacks(count: Int): List<AttackEntry> {
        val list = attacks.toList()
        return list.takeLast(count)
    }

    fun hadSwingBeforeAttack(attackTick: Long): Boolean {
        return lastSwingTick >= attackTick - 1
    }
}
