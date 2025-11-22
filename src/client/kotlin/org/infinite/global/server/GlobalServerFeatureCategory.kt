package org.infinite.global.server

import org.infinite.global.GlobalFeature
import org.infinite.global.GlobalFeatureCategory
import org.infinite.global.server.protocol.ProtocolSpoofingSetting

class GlobalServerFeatureCategory :
    GlobalFeatureCategory(
        "Server",
        mutableListOf(
            GlobalFeature(ProtocolSpoofingSetting()), // Add ProtocolSpoofingSetting here
        ),
    )
