package org.infinite.features.automatic

import org.infinite.features.Feature
import org.infinite.features.FeatureCategory
import org.infinite.features.automatic.branch.BranchMiner
import org.infinite.features.automatic.pilot.AutoPilot
import org.infinite.features.automatic.tunnel.ShieldMachine
import org.infinite.features.automatic.wood.WoodMiner

class AutomaticFeatureCategory :
    FeatureCategory(
        "Automatic",
        mutableListOf(
            Feature(AutoPilot()),
            Feature(WoodMiner()),
            Feature(ShieldMachine()),
            Feature(BranchMiner()),
        ),
    )
