package org.infinite.features

import org.infinite.feature
import org.infinite.features.automatic.aimode.AIMode
import org.infinite.features.automatic.woodcutter.WoodCutter
import org.infinite.utils.Translation

val automatic =
    listOf(
        feature(
            "AIMode",
            AIMode(),
            Translation.t("automatic.aimode.description"),
        ),
        feature(
            "WoodCutter",
            WoodCutter(),
            Translation.t("automatic.woodcutter.description"),
        ),
    )
