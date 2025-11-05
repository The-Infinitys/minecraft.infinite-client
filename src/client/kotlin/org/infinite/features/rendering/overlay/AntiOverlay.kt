package org.infinite.features.rendering.overlay

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class AntiOverlay : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Utils
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BooleanSetting("NoPumpkinOverlay", true),
            FeatureSetting.BooleanSetting("NoDarknessOverlay", true),
            FeatureSetting.BooleanSetting("NoLiquidOverlay", true),
            FeatureSetting.BooleanSetting("NoFogOverlay", true),
        )
}
