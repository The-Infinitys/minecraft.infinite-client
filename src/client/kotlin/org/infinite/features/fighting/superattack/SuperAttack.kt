package org.infinite.features.fighting.superattack

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class SuperAttack : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Cheat

    enum class AttackMethod {
        PACKET,
        MINI_JUMP,
        FULL_JUMP,
    }

    val method =
        FeatureSetting.EnumSetting(
            "Method",
            "feature.fighting.superattack.method.description",
            AttackMethod.MINI_JUMP,
            AttackMethod.entries.toList(),
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            method,
        )
}
