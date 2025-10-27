package org.infinite.features

import org.infinite.feature
import org.infinite.features.server.AutoConnect
import org.infinite.features.server.AutoLeave
import org.infinite.features.server.DetectServer
import org.infinite.features.server.ServerInfo

val server =
    listOf(
        feature(
            "ServerInfo",
            ServerInfo(),
            "feature.server.serverinfo.description",
        ),
        feature("AutoConnect", AutoConnect(), "feature.server.autoconnect.description"),
        feature("DetectServer", DetectServer(), "feature.server.detectserver.description"),
        feature(
            "AutoLeave",
            AutoLeave(),
            "feature.server.autoleave.description",
        ),
    )
