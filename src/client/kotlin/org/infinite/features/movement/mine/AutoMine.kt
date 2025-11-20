package org.infinite.features.movement.mine

import org.infinite.feature.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class AutoMine : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Utils
    override val settings: List<FeatureSetting<*>> = emptyList()

    override fun onTick() {
        // 左クリック（攻撃/採掘）を強制的に押す
        client.options.attackKey.isPressed = true
    }

    override fun onDisabled() {
        super.onDisabled()
        // 無効になったときに左クリックを離す
        client.options.attackKey.isPressed = false
    }
}
