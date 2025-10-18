package org.infinite.features

import org.infinite.feature
import org.infinite.features.server.DetectServer
import org.infinite.features.server.ServerInfo

val server =
    listOf(
        feature(
            "ServerInfo",
            ServerInfo(),
            "feature.server.serverinfo.description",
        ),
        feature("DetectServer", DetectServer(), "feature.server.detectserver.description"),
    )
