package org.infinite.features.fighting.killaura

import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.settings.FeatureSetting

class KillAura : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.FloatSetting(
                "Range",
                "feature.fighting.killaura.range.description",
                4.2f,
                3.0f,
                7.0f,
            ),
            FeatureSetting.BooleanSetting(
                "Players",
                "feature.fighting.killaura.players.description",
                true,
            ),
            FeatureSetting.BooleanSetting(
                "Mobs",
                "feature.fighting.killaura.mobs.description",
                false,
            ),
            FeatureSetting.IntSetting(
                "MaxTargets",
                "feature.fighting.killaura.maxtargets.description",
                1,
                0,
                10,
            ),
            FeatureSetting.IntSetting(
                "AttackFrequency",
                "feature.fighting.killaura.attackfrequency.description",
                0,
                0,
                20,
            ),
            FeatureSetting.BooleanSetting(
                "ChangeAngle",
                "feature.fighting.killaura.changeangle.description",
                false,
            ),
        )
}
