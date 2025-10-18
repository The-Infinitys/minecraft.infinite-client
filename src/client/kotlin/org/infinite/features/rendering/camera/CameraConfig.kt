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
                "カメラとプレイヤーとの距離を設定します。",
                8.0f,
                -1.0f,
                15.0f,
            ),
            FeatureSetting.BooleanSetting("ClipBlock", "カメラが地形を貫通するかを設定します。", true),
            FeatureSetting.BooleanSetting("AntiHurtTilt", "ダメージを受けた際にカメラが揺れなくなります。", true),
            FeatureSetting.BooleanSetting("ExtraCamera", "カメラがプレイヤーの向きに依存しなくなります", false),
        )
}
