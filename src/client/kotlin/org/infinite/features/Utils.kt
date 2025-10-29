package org.infinite.features

import org.infinite.feature
import org.infinite.features.utils.afk.AfkMode
import org.infinite.features.utils.backpack.BackPackManager // 追加
import org.infinite.features.utils.map.HyperMap
import org.infinite.features.utils.map.MapFeature
import org.infinite.features.utils.noattack.NoAttack
import org.infinite.features.utils.playermanager.PlayerManager
import org.infinite.features.utils.tool.AutoTool

val utils =
    listOf(
        feature("AfkMode", AfkMode(), "feature.utils.afkmode.description"),
        feature(
            "AutoTool",
            AutoTool(),
            "feature.utils.autotool.description",
        ),
        feature(
            // 追加
            "BackPackManager",
            BackPackManager(),
            "feature.utils.backpackmanager.description",
        ),
        feature(
            "NoAttack",
            NoAttack(),
            "feature.utils.noattack.description",
        ),
        feature(
            "PlayerManager",
            PlayerManager(),
            "feature.utils.playermanager.description",
        ),
        feature("HyperMap", HyperMap(), "feature.utils.hypermap.description"),
        feature("MapFeature", MapFeature(), "feature.map.description"),
    )
