package org.theinfinitys.features.movement

import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.util.math.Vec3d
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.settings.InfiniteSetting
import org.theinfinitys.utils.toRadians
import kotlin.math.cos
import kotlin.math.sin

class SuperFly : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    override val settings: List<InfiniteSetting<*>> = listOf()

    override fun tick() {
        val client = MinecraftClient.getInstance()
        if (client.player?.isGliding != true) return
        manageGliding(client)
        controlSpeed(client)
        controlHeight(client)
    }

    private fun manageGliding(client: MinecraftClient) {
        if (client.player?.isTouchingWater == true) {
            val packet =
                ClientCommandC2SPacket(
                    client.player,
                    ClientCommandC2SPacket.Mode.START_FALL_FLYING,
                )
            client.player?.networkHandler?.sendPacket(packet)
        }
    }

    private fun controlSpeed(client: MinecraftClient) {
        val player = client.player ?: return
        val yaw = toRadians(player.yaw)
        val velocity = player.velocity
        val forwardVelocity =
            Vec3d(
                -sin(yaw) * 0.05,
                0.0,
                cos(yaw) * 0.05,
            )
        if (client.options.forwardKey.isPressed) {
            client.player?.velocity = velocity.add(forwardVelocity)
        }
        if (client.options.backKey.isPressed) {
            client.player?.velocity = velocity.subtract(forwardVelocity)
        }
    }

    private fun controlHeight(client: MinecraftClient) {
        val player = client.player ?: return
        val velocity = player.velocity
        if (client.options.jumpKey.isPressed) {
            client.player?.setVelocity(velocity.x, velocity.y + 0.08, velocity.z)
        }
        if (client.options.sneakKey.isPressed) {
            client.player?.setVelocity(velocity.x, velocity.y - 0.04, velocity.z)
        }
    }
}
