package org.infinite.features.rendering.overlay

import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.settings.FeatureSetting

class AntiOverlay : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.UTILS
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BooleanSetting("NoPumpkinOverlay", "カボチャのオーバーレイを削除します。", true),
            FeatureSetting.BooleanSetting("NoDarknessOverlay", "暗闇のオーバーレイ（盲目効果など）を削除します。", true),
            FeatureSetting.BooleanSetting("NoLiquidOverlay", "水/溶岩中のオーバーレイを削除します。", true),
            FeatureSetting.BooleanSetting("NoFogOverlay", "フォグ(霧のようなエフェクト)を無効化します。", true),
        )
}
