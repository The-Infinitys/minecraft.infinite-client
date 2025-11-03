package org.infinite.features.utils.noattack

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class NoAttack : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.EntityListSetting(
                "ProtectedEntities",
                mutableListOf("minecraft:villager", "minecraft:wolf", "minecraft:cat"),
            ),
        )
}
