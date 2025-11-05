package org.infinite.libs.client.movement

import org.infinite.libs.client.player.ClientInterface

object MovementInterface : ClientInterface() {
    fun speed(): Double = velocity?.length() ?: 0.0

    fun moveTo(
        x: Double,
        adjustTargetY: Double,
        z: Double,
    ) {
    }
}
