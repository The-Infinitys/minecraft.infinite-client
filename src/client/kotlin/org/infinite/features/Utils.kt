package org.infinite.features

import org.infinite.feature
import org.infinite.features.utils.afk.AfkMode
import org.infinite.features.utils.backpack.BackPackManager // 追加
import org.infinite.features.utils.food.FoodManager
import org.infinite.features.utils.map.HyperMap
import org.infinite.features.utils.map.MapFeature
import org.infinite.features.utils.noattack.NoAttack
import org.infinite.features.utils.playermanager.PlayerManager
import org.infinite.features.utils.tool.AutoTool

val utils =
    listOf(
        feature("AfkMode", AfkMode()),
        feature(
            "AutoTool",
            AutoTool(),
        ),
        feature(
            // 追加
            "BackPackManager",
            BackPackManager(),
        ),
        feature(
            "NoAttack",
            NoAttack(),
        ),
        feature(
            "PlayerManager",
            PlayerManager(),
        ),
        feature("HyperMap", HyperMap()),
        feature("MapFeature", MapFeature()),
        feature("FoodManager", FoodManager()),
    )
