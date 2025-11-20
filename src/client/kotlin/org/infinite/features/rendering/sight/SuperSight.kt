package org.infinite.features.rendering.sight

import org.infinite.feature.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class SuperSight : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BooleanSetting("NightVision", true),
            FeatureSetting.BooleanSetting("AntiBlind", true),
        )
}
