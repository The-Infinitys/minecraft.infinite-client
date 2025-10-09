package org.theinfinitys.features.movement

import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.settings.InfiniteSetting

class AntiSlowDown : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.BooleanSetting(
                "Item",
                "アイテム使用時の減速を無効にします。",
                true,
            ),
            InfiniteSetting.BooleanSetting(
                "Cobweb",
                "クモの巣での減速を無効にします。",
                false,
            ),
        )
}
