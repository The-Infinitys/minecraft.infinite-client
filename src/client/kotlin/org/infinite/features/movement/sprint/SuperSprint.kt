package org.infinite.features.movement.sprint

import net.minecraft.client.MinecraftClient
import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class SuperSprint : ConfigurableFeature(initialEnabled = false) {
    private val onlyWhenForward =
        FeatureSetting.BooleanSetting(
            "OnlyWhenForward",
            "feature.movement.supersprint.onlywhenforward.description",
            true,
        )
    private val evenIfHungry =
        FeatureSetting.BooleanSetting(
            "EvenIfHungry",
            "feature.movement.supersprint.evenifhungry.description",
            false,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            onlyWhenForward,
            evenIfHungry,
        )

    override fun disabled() {
        MinecraftClient
            .getInstance()
            .options
            ?.sprintKey
            ?.isPressed = false
    }

    override fun tick() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val options = client.options ?: return
        if (!evenIfHungry.value) { // If EvenIfHungry is false, check hunger
            if (player.hungerManager.foodLevel <= 6) {
                player.isSprinting = false // Stop sprinting if hunger is too low
                return // Prevent further sprint logic
            }
        }
        if (!options.sprintKey.isPressed) {
            options.sprintKey.isPressed = !player.isGliding && options.forwardKey.isPressed
        }
        if (!onlyWhenForward.value) {
            val movementKeyPressed = (
                options.forwardKey.isPressed ||
                    options.backKey.isPressed ||
                    options.leftKey.isPressed ||
                    options.rightKey.isPressed
            )
            player.isSprinting = movementKeyPressed && !player.isGliding
        }
    }
}
