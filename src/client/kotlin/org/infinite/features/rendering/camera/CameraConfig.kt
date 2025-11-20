package org.infinite.features.rendering.camera

import org.infinite.feature.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class CameraConfig : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Utils
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.FloatSetting(
                "CameraDistance",
                8.0f,
                -1.0f,
                15.0f,
            ),
            FeatureSetting.BooleanSetting("ClipBlock", true),
            FeatureSetting.BooleanSetting("AntiHurtTilt", true),
            FeatureSetting.BooleanSetting("ExtraCamera", false),
        )
}
