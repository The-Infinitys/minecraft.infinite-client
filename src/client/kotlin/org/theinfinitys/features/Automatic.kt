package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.automatic.aimode.AIMode
import org.theinfinitys.features.automatic.woodcutter.WoodCutter
import org.theinfinitys.utils.Translation

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
