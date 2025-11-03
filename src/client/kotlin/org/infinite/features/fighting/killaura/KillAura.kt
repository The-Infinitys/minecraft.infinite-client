package org.infinite.features.fighting.killaura

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class KillAura : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Cheat
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.FloatSetting(
                "Range",
                4.2f,
                3.0f,
                7.0f,
            ),
            FeatureSetting.BooleanSetting(
                "Players",
                true,
            ),
            FeatureSetting.BooleanSetting(
                "Mobs",
                false,
            ),
            FeatureSetting.IntSetting(
                "MaxTargets",
                1,
                0,
                10,
            ),
            FeatureSetting.IntSetting(
                "AttackFrequency",
                0,
                0,
                20,
            ),
            FeatureSetting.BooleanSetting(
                "ChangeAngle",
                false,
            ),
        )
}
