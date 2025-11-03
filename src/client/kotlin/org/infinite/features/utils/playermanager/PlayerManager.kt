package org.infinite.features.utils.playermanager

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class PlayerManager : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Utils
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.PlayerListSetting(
                "Friends",
                mutableListOf(),
            ),
            FeatureSetting.PlayerListSetting(
                "Enemies",
                mutableListOf(),
            ),
        )
}
