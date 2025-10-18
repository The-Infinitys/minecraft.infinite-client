package org.infinite.features.rendering.sight

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class SuperSight : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BooleanSetting("FullBright", "feature.rendering.supersight.fullbright.description", true),
            FeatureSetting.BooleanSetting("AntiBlind", "feature.rendering.supersight.antiblind.description", true),
        )
}
