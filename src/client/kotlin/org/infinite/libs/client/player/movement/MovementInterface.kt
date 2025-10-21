package org.infinite.libs.client.player.movement

import net.minecraft.client.MinecraftClient

class MovementInterface(
    private val client: MinecraftClient,
) {
    fun speed(): Double = client.player?.velocity?.length() ?: 0.0
}
