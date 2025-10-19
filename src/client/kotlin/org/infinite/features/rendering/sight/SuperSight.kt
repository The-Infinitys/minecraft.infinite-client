package org.infinite.features.rendering.sight

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class SuperSight : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BooleanSetting("NightVision", "feature.rendering.supersight.nightvision.description", true),
            FeatureSetting.BooleanSetting("AntiBlind", "feature.rendering.supersight.antiblind.description", true),
        )
}
