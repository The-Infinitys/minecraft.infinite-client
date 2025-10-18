package org.infinite.features.rendering.camera

import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.settings.FeatureSetting

class CameraConfig : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.UTILS
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.FloatSetting(
                "CameraDistance",
                "feature.rendering.cameraconfig.cameradistance.description",
                8.0f,
                -1.0f,
                15.0f,
            ),
            FeatureSetting.BooleanSetting("ClipBlock", "feature.rendering.cameraconfig.clipblock.description", true),
            FeatureSetting.BooleanSetting("AntiHurtTilt", "feature.rendering.cameraconfig.antihurttilt.description", true),
            FeatureSetting.BooleanSetting("ExtraCamera", "feature.rendering.cameraconfig.extracamera.description", false),
        )
}
