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
                "エンティティを攻撃する最大距離を設定します。",
                4.2f,
                3.0f,
                7.0f,
            ),
            FeatureSetting.BooleanSetting(
                "Players",
                "プレイヤーをターゲットにします。",
                true,
            ),
            FeatureSetting.BooleanSetting(
                "Mobs",
                "モブをターゲットにします。",
                false,
            ),
            FeatureSetting.IntSetting(
                "MaxTargets",
                "同時に攻撃するエンティティの最大数。(0で無制限)",
                1,
                0,
                10,
            ),
            FeatureSetting.IntSetting(
                "AttackFrequency",
                "攻撃頻度を設定します。(0で自動調整)",
                0,
                0,
                20,
            ),
            FeatureSetting.BooleanSetting(
                "ChangeAngle",
                "攻撃時にエンティティの方向を向きます。",
                false,
            ),
        )
}
