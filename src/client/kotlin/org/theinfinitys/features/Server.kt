package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.server.DetectServer
import org.theinfinitys.features.server.ServerInfo
import org.theinfinitys.utils.Translation

val server =
    listOf(
        feature(
            "ServerInfo",
            ServerInfo(),
            Translation.t("server.serverinfo.description"),
        ),
        feature("DetectServer", DetectServer(), Translation.t("server.detectserver.description")),
    )
