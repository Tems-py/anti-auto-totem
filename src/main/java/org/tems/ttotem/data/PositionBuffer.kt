package org.tems.ttotem.data

import java.util.concurrent.ConcurrentLinkedDeque

data class PositionEntry(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val onGround: Boolean,
    val timestamp: Long
)

class PositionBuffer(private val maxSize: Int = 20) {
    private val buffer = ConcurrentLinkedDeque<PositionEntry>()

    fun add(entry: PositionEntry) {
        buffer.addLast(entry)
        while (buffer.size > maxSize) {
            buffer.pollFirst()
        }
    }

    fun latest(): PositionEntry? = buffer.peekLast()
    fun previous(): PositionEntry? {
        val iter = buffer.descendingIterator()
        if (iter.hasNext()) iter.next() // skip latest
        return if (iter.hasNext()) iter.next() else null
    }

    fun entries(): List<PositionEntry> = buffer.toList()
    fun size(): Int = buffer.size

    fun deltaXZ(): Double {
        val curr = latest() ?: return 0.0
        val prev = previous() ?: return 0.0
        val dx = curr.x - prev.x
        val dz = curr.z - prev.z
        return Math.sqrt(dx * dx + dz * dz)
    }

    fun deltaY(): Double {
        val curr = latest() ?: return 0.0
        val prev = previous() ?: return 0.0
        return curr.y - prev.y
    }
}
