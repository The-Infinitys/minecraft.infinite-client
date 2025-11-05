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
        ),
        feature("AutoConnect", AutoConnect()),
        feature("DetectServer", DetectServer()),
        feature(
            "AutoLeave",
            AutoLeave(),
        ),
    )
