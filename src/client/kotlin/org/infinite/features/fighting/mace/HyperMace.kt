package org.infinite.features.fighting.mace

import org.infinite.feature.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class HyperMace : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Cheat

    val damageBoost: FeatureSetting.IntSetting =
        FeatureSetting.IntSetting(
            "DamageBoost",
            4,
            1,
            10,
        )
    val fallMultiply = FeatureSetting.DoubleSetting("FallMultiply", 1.5, 1.0, 5.0)
    val fallDistance =
        FeatureSetting.DoubleSetting(
            "FallDistance",
            500.0, // 既存のsqrt(500.0)に合わせて初期値を設定
            1.0,
            1000.0,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            damageBoost,
            fallDistance,
        )
}
