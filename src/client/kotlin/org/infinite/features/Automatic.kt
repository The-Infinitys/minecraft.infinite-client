package org.infinite.features

import org.infinite.feature
import org.infinite.features.automatic.pilot.AutoPilot
import org.infinite.features.automatic.wood.WoodMiner

val automatic =
    listOf(
        feature(
            "AutoPilot",
            AutoPilot(),
            "feature.automatic.autopilot.description",
        ),
        feature("WoodMiner", WoodMiner(), "feature.automatic.woodminer.description"),
    )
