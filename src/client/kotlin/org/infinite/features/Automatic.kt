package org.infinite.features

import org.infinite.feature
import org.infinite.features.automatic.aimode.AIMode
import org.infinite.features.automatic.woodcutter.WoodCutter

val automatic =
    listOf(
        feature(
            "feature.automatic.aimode.name",
            AIMode(),
            "feature.automatic.aimode.description",
        ),
        feature(
            "feature.automatic.woodcutter.name",
            WoodCutter(),
            "feature.automatic.woodcutter.description",
        ),
    )
