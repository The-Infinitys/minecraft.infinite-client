package org.infinite.libs.client

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.option.GameOptions
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.Vec3d
import org.infinite.libs.client.player.ai.AISystem
import org.infinite.libs.client.player.inventory.InventoryManager
import org.infinite.libs.client.player.movement.MovementInterface

class PlayerInterface {
    val client: MinecraftClient
        get() = MinecraftClient.getInstance()
    val player: ClientPlayerEntity?
        get() = client.player
    val world: ClientWorld?
        get() = client.world
    val options: GameOptions
        get() = client.options
    val pos: Vec3d?
        get() {
            val x = player?.x ?: return null
            val y = player?.y ?: return null
            val z = player?.z ?: return null
            return Vec3d(x, y, z)
        }
    val velocity: Vec3d?
        get() = player?.velocity
    val movementInterface: MovementInterface = MovementInterface(client)
    val inventory: InventoryManager = InventoryManager(client)
    val ai: AISystem = AISystem(client)
}
