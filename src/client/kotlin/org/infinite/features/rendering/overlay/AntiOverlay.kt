package org.infinite.features.rendering.overlay

import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.settings.FeatureSetting

class AntiOverlay : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.UTILS
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BooleanSetting("NoPumpkinOverlay", "feature.rendering.antioverlay.nopumpkinoverlay.description", true),
            FeatureSetting.BooleanSetting("NoDarknessOverlay", "feature.rendering.antioverlay.nodarknessoverlay.description", true),
            FeatureSetting.BooleanSetting("NoLiquidOverlay", "feature.rendering.antioverlay.noliquidoverlay.description", true),
            FeatureSetting.BooleanSetting("NoFogOverlay", "feature.rendering.antioverlay.nofogoverlay.description", true),
        )
}
