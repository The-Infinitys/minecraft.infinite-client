package org.infinite.features.fighting.playermanager

import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.settings.FeatureSetting

class PlayerManager : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.UTILS

    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.PlayerListSetting(
                "Friends",
                "フレンドとして扱うプレイヤーのリスト。",
                mutableListOf(),
            ),
            FeatureSetting.PlayerListSetting(
                "Enemies",
                "敵として扱うプレイヤーのリスト。",
                mutableListOf(),
            ),
        )
}
