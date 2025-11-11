package org.infinite.features.server

import org.infinite.feature
import org.infinite.features.server.anti.AntiVulcan
import org.infinite.features.server.connection.AutoConnect
import org.infinite.features.server.connection.AutoLeave
import org.infinite.features.server.meta.DetectServer
import org.infinite.features.server.meta.ServerInfo

internal val server =
    mutableListOf(
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
        feature("AntiVulcan", AntiVulcan()),
    )
