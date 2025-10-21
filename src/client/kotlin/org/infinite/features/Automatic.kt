package org.infinite.features

import org.infinite.feature
import org.infinite.features.automatic.aimode.AIMode
import org.infinite.features.automatic.pilot.AutoPilot
import org.infinite.features.automatic.woodcutter.WoodCutter

val automatic =
    listOf(
        feature(
            "AutoPilot",
            AutoPilot(),
            "feature.automatic.autopilot.description",
        ),
        feature(
            "AutoAim",
            AIMode(),
            "feature.automatic.aimode.description",
        ),
        feature(
            "AutoWoodCutter",
            WoodCutter(),
            "feature.automatic.woodcutter.description",
        ),
    )
