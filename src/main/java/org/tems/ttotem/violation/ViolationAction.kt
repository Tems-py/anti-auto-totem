package org.tems.ttotem.violation

enum class ViolationActionType {
    ALERT, SETBACK, KICK, BAN
}

data class ViolationAction(
    val threshold: Int,
    val type: ViolationActionType,
    val command: String? = null
)
