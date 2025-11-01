package org.infinite.features.fighting.reach

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class Reach : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.FloatSetting(
                "ReachDistance",
                "feature.fighting.reach.reachdistance.description",
                4.5f,
                3.0f,
                7.0f,
            ),
        )
}
