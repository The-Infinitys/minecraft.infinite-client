package org.theinfinitys.features.fighting.noattack

import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting

class NoAttack : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.EntityListSetting(
                "ProtectedEntities",
                "攻撃しないエンティティのリスト。",
                mutableListOf("minecraft:villager", "minecraft:wolf", "minecraft:cat"),
            ),
        )
}
