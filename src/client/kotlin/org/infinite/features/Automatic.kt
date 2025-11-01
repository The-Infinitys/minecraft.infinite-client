package org.infinite.features

import org.infinite.feature
import org.infinite.features.automatic.pilot.AutoPilot

val automatic =
    listOf(
        feature(
            "AutoPilot",
            AutoPilot(),
            "feature.automatic.autopilot.description",
        ),
    )
