package org.theinfinitys.features.fighting.reach

import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.settings.InfiniteSetting

class Reach : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.FloatSetting(
                "ReachDistance",
                "ブロックおよびエンティティとの対話距離を拡張します。",
                4.5f,
                3.0f,
                7.0f,
            ),
        )
}
