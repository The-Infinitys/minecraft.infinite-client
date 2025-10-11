package org.theinfinitys.features.fighting.superattack

import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.settings.InfiniteSetting

class SuperAttack : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT

    enum class AttackMethod {
        PACKET,
        MINI_JUMP,
        FULL_JUMP,
    }

    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.EnumSetting(
                "Method",
                "クリティカル攻撃の方法を選択します。",
                AttackMethod.MINI_JUMP,
                AttackMethod.entries.toList(),
            ),
        )
}
