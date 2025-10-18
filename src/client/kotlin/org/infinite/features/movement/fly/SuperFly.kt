package org.infinite.features.movement.fly

import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.util.math.Vec3d
import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.settings.FeatureSetting
import org.infinite.utils.toRadians
import kotlin.math.cos
import kotlin.math.sin

class SuperFly : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BooleanSetting(
                "HyperBoost",
                "feature.movement.superfly.hyperboost.description",
                false,
            ),
        )

    private fun isHyperBoostMode(): Boolean = getSetting("HyperBoost")?.value as? Boolean ?: false

    override fun tick() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return

        // Check if player is gliding
        if (!player.isGliding) return

        // Manage gliding (e.g., start fall flying if in water)
        manageGliding(client)

        // Check HyperBoost conditions: HyperBoost enabled, forward key, jump key, and sneak key pressed
        val isHyperBoostActive =
            isHyperBoostMode() &&
                client.options.forwardKey.isPressed &&
                client.options.jumpKey.isPressed &&
                client.options.sneakKey.isPressed

        if (isHyperBoostActive) {
            // Apply HyperBoost effects
            applyHyperBoost(client)
        } else {
            // Apply normal speed and height controls
            controlSpeed(client)
            controlHeight(client)
        }
    }

    private fun manageGliding(client: MinecraftClient) {
        val player = client.player ?: return
        if (player.isTouchingWater) {
            val packet =
                ClientCommandC2SPacket(
                    player,
                    ClientCommandC2SPacket.Mode.START_FALL_FLYING,
                )
            player.networkHandler?.sendPacket(packet)
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
            player.velocity = velocity.add(forwardVelocity)
        }
        if (client.options.backKey.isPressed) {
            player.velocity = velocity.subtract(forwardVelocity)
        }
    }

    private fun controlHeight(client: MinecraftClient) {
        val player = client.player ?: return
        val velocity = player.velocity
        if (client.options.jumpKey.isPressed) {
            player.setVelocity(velocity.x, velocity.y + 0.08, velocity.z)
        }
        if (client.options.sneakKey.isPressed) {
            player.setVelocity(velocity.x, velocity.y - 0.04, velocity.z)
        }
    }

    private fun applyHyperBoost(client: MinecraftClient) {
        val player = client.player ?: return
        val yaw = toRadians(player.yaw)
        val velocity = player.velocity

        // HyperBoost: Significantly increase forward speed and add slight upward boost
        val hyperBoostVelocity =
            Vec3d(
                -sin(yaw) * 0.3, // Increased speed (0.05 -> 0.3)
                0.1, // Slight upward boost
                cos(yaw) * 0.3, // Increased speed (0.05 -> 0.3)
            )
        player.velocity = velocity.add(hyperBoostVelocity)
    }
}
