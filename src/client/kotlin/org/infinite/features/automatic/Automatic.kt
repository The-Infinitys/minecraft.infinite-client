package org.infinite.features.automatic

import org.infinite.feature
import org.infinite.features.automatic.branch.BranchMiner
import org.infinite.features.automatic.pilot.AutoPilot
import org.infinite.features.automatic.tunnel.ShieldMachine
import org.infinite.features.automatic.wood.WoodMiner

internal val automatic =
    mutableListOf(
        feature("AutoPilot", AutoPilot()),
        feature("WoodMiner", WoodMiner()),
        feature("ShieldMachine", ShieldMachine()),
        feature("BranchMiner", BranchMiner()),
    )
