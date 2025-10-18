package org.infinite.features.movement.walk

import net.minecraft.client.MinecraftClient
import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.settings.FeatureSetting

class AutoWalk : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.UTILS

    private val client: MinecraftClient
        get() = MinecraftClient.getInstance()

    override val settings: List<FeatureSetting<*>> = emptyList()

    override fun tick() {
        val player = client.player ?: return
        val options = client.options

        // 前進キーを強制的に押す
        options.forwardKey.isPressed = true
    }

    override fun disabled() {
        super.disabled()
        // 無効になったときに前進キーを離す
        client.options.forwardKey.isPressed = false
    }
}
