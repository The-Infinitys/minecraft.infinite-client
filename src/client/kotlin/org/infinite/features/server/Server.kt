package org.infinite.features.server

import org.infinite.feature

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
    )
