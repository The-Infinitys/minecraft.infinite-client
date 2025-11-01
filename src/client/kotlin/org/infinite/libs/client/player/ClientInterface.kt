package org.infinite.libs.client.player

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.network.ClientPlayerInteractionManager
import net.minecraft.client.option.GameOptions
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.player.PlayerInventory

open class ClientInterface {
    protected val client: MinecraftClient
        get() = MinecraftClient.getInstance()
    protected val player: ClientPlayerEntity?
        get() = client.player
    protected val world: ClientWorld?
        get() = client.world
    protected val options: GameOptions
        get() = client.options
    protected val interactionManager: ClientPlayerInteractionManager?
        get() = client.interactionManager
    protected val inventory: PlayerInventory?
        get() = client.player?.inventory
}
