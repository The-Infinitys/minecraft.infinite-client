package org.infinite.features.fighting.noattack

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class NoAttack : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.EntityListSetting(
                "ProtectedEntities",
                "攻撃しないエンティティのリスト。",
                mutableListOf("minecraft:villager", "minecraft:wolf", "minecraft:cat"),
            ),
        )
}
