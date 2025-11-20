package org.infinite.features.utils

import org.infinite.features.Feature
import org.infinite.features.FeatureCategory
import org.infinite.features.utils.afk.AfkMode
import org.infinite.features.utils.backpack.BackPackManager // 追加
import org.infinite.features.utils.food.FoodManager
import org.infinite.features.utils.map.HyperMap
import org.infinite.features.utils.map.MapFeature
import org.infinite.features.utils.noattack.NoAttack
import org.infinite.features.utils.playermanager.PlayerManager
import org.infinite.features.utils.tool.AutoTool

class UtilsFeatureCategory :
    FeatureCategory(
        "Utils",
        mutableListOf(
            Feature(AfkMode()),
            Feature(AutoTool()),
            Feature(BackPackManager()),
            Feature(NoAttack()),
            Feature(PlayerManager()),
            Feature(HyperMap()),
            Feature(MapFeature()),
            Feature(FoodManager()),
        ),
    )
