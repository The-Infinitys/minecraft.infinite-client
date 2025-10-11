package org.theinfinitys.features.movement.mine

import net.minecraft.client.MinecraftClient
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.settings.InfiniteSetting

class AutoMine : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.UTILS
    private val client: MinecraftClient
        get() = MinecraftClient.getInstance()

    override val settings: List<InfiniteSetting<*>> = emptyList()

    override fun tick() {
        // 左クリック（攻撃/採掘）を強制的に押す
        client.options.attackKey.isPressed = true
    }

    override fun disabled() {
        super.disabled()
        // 無効になったときに左クリックを離す
        client.options.attackKey.isPressed = false
    }
}
