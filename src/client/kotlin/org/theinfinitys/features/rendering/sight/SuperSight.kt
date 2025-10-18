package org.theinfinitys.features.rendering.sight

import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting

class SuperSight : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.BooleanSetting("FullBright", "ゲーム内の明るさを最大にします。", true),
            InfiniteSetting.BooleanSetting("AntiBlind", "盲目や暗闇のエフェクトを無効にします。", true),
        )
}
