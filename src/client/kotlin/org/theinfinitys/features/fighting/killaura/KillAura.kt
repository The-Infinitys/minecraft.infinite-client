package org.theinfinitys.features.fighting.killaura

import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.settings.InfiniteSetting

class KillAura : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.FloatSetting(
                "Range",
                "エンティティを攻撃する最大距離を設定します。",
                4.2f,
                3.0f,
                7.0f,
            ),
            InfiniteSetting.BooleanSetting(
                "Players",
                "プレイヤーをターゲットにします。",
                true,
            ),
            InfiniteSetting.BooleanSetting(
                "Mobs",
                "モブをターゲットにします。",
                false,
            ),
            InfiniteSetting.IntSetting(
                "MaxTargets",
                "同時に攻撃するエンティティの最大数。(0で無制限)",
                1,
                0,
                10,
            ),
            InfiniteSetting.IntSetting(
                "AttackFrequency",
                "攻撃頻度を設定します。(0で自動調整)",
                0,
                0,
                20,
            ),
            InfiniteSetting.BooleanSetting(
                "ChangeAngle",
                "攻撃時にエンティティの方向を向きます。",
                false,
            ),
        )
}
