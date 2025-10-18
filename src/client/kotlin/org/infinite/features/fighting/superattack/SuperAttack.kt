package org.infinite.features.fighting.superattack

import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.settings.FeatureSetting

class SuperAttack : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT

    enum class AttackMethod {
        PACKET,
        MINI_JUMP,
        FULL_JUMP,
    }

    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.EnumSetting(
                "Method",
                "feature.fighting.superattack.method.description",
                AttackMethod.MINI_JUMP,
                AttackMethod.entries.toList(),
            ),
        )
}
