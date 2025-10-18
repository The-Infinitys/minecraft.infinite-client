package org.infinite.features.movement.sprint

import net.minecraft.client.MinecraftClient
import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class SuperSprint : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BooleanSetting("OnlyWhenForward", "feature.movement.supersprint.onlywhenforward.description", true),
            FeatureSetting.BooleanSetting(
                "EvenIfHungry",
                "feature.movement.supersprint.evenifhungry.description",
                false,
            ),
        )

    override fun tick() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return

        val onlyWhenForward = (settings[0] as FeatureSetting.BooleanSetting).value
        val evenIfHungry = (settings[1] as FeatureSetting.BooleanSetting).value

        if (onlyWhenForward) {
            val lookVec = player.rotationVector // Get player's look vector
            val velocityVec = player.velocity // Get player's velocity vector

            // Calculate dot product to determine if moving forward or backward
            val dotProduct = lookVec.dotProduct(velocityVec)

            // If moving backward (dot product is negative), prevent sprinting and return
            // A small epsilon can be used to account for floating point inaccuracies
            if (dotProduct < -0.01) { // Adjust epsilon as needed
                player.isSprinting = false
                player.setVelocity(0.0, player.velocity.y, 0.0) // Stop horizontal movement
                return
            }
        }

        // Handle EvenIfHungry
        if (!evenIfHungry) { // If EvenIfHungry is false, check hunger
            // Player needs at least 7 hunger points (3.5 hunger bars) to sprint
            if (player.hungerManager.foodLevel <= 6) {
                player.isSprinting = false // Stop sprinting if hunger is too low
                return // Prevent further sprint logic
            }
        }

        if ((player.velocity.x != 0.0 || player.velocity.z != 0.0) && !player.isSprinting) {
            player.isSprinting = true
        }
    }
}
