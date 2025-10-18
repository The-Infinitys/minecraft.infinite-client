package org.infinite.features

import org.infinite.feature
import org.infinite.features.server.DetectServer
import org.infinite.features.server.ServerInfo
import org.infinite.utils.Translation

val server =
    listOf(
        feature(
            "ServerInfo",
            ServerInfo(),
            Translation.t("server.serverinfo.description"),
        ),
        feature("DetectServer", DetectServer(), Translation.t("server.detectserver.description")),
    )
