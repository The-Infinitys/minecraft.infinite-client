package org.infinite.features.server

import org.infinite.features.Feature
import org.infinite.features.FeatureCategory
import org.infinite.features.server.anti.AntiVulcan
import org.infinite.features.server.connection.AutoConnect
import org.infinite.features.server.connection.AutoLeave
import org.infinite.features.server.meta.DetectServer
import org.infinite.features.server.meta.ServerInfo

class ServerFeatureCategory :
    FeatureCategory(
        "Server",
        mutableListOf(
            Feature(ServerInfo()),
            Feature(AutoConnect()),
            Feature(DetectServer()),
            Feature(AutoLeave()),
            Feature(AntiVulcan()),
        ),
    )
