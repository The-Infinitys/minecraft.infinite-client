package org.theinfinitys.features.rendering.camera

import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.settings.InfiniteSetting

class CameraConfig : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.UTILS
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.FloatSetting(
                "CameraDistance",
                "カメラとプレイヤーとの距離を設定します。",
                8.0f,
                -1.0f,
                15.0f,
            ),
            InfiniteSetting.BooleanSetting("ClipBlock", "カメラが地形を貫通するかを設定します。", true),
            InfiniteSetting.BooleanSetting("AntiHurtTilt", "ダメージを受けた際にカメラが揺れなくなります。", true),
            InfiniteSetting.BooleanSetting("ExtraCamera", "カメラがプレイヤーの向きに依存しなくなります", false),
        )
}
