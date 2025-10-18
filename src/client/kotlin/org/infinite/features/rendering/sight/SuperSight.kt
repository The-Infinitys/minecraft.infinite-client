package org.infinite.features.rendering.sight

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class SuperSight : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BooleanSetting("FullBright", "ゲーム内の明るさを最大にします。", true),
            FeatureSetting.BooleanSetting("AntiBlind", "盲目や暗闇のエフェクトを無効にします。", true),
        )
}
