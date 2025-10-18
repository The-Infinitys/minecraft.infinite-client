package org.infinite.features

import org.infinite.feature
import org.infinite.features.server.DetectServer
import org.infinite.features.server.ServerInfo

val server =
    listOf(
        feature(
            "feature.server.serverinfo.name",
            ServerInfo(),
            "feature.server.serverinfo.description",
        ),
        feature("feature.server.detectserver.name", DetectServer(), "feature.server.detectserver.description"),
    )
