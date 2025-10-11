package org.theinfinitys.features.fighting.playermanager

import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.settings.InfiniteSetting

class PlayerManager : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.UTILS

    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.PlayerListSetting(
                "Friends",
                "フレンドとして扱うプレイヤーのリスト。",
                mutableListOf(),
            ),
            InfiniteSetting.PlayerListSetting(
                "Enemies",
                "敵として扱うプレイヤーのリスト。",
                mutableListOf(),
            ),
        )
}
