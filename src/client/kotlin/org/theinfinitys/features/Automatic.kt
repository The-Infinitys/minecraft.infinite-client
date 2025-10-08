package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.automatic.AIMode
import org.theinfinitys.features.automatic.WoodCutter
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
