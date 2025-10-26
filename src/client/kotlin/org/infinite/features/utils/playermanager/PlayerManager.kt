package org.infinite.features.utils.playermanager

import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.settings.FeatureSetting

class PlayerManager : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.UTILS

    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.PlayerListSetting(
                "Friends",
                "feature.utils.playermanager.friends.description",
                mutableListOf(),
            ),
            FeatureSetting.PlayerListSetting(
                "Enemies",
                "feature.utils.playermanager.enemies.description",
                mutableListOf(),
            ),
        )
}
