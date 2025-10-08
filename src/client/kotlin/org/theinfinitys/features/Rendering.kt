package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.rendering.AntiOverlay
import org.theinfinitys.features.rendering.CameraConfig
import org.theinfinitys.features.rendering.Radar
import org.theinfinitys.features.rendering.SuperSight
import org.theinfinitys.features.rendering.XRay
import org.theinfinitys.utils.Translation

val rendering =
    listOf(
        feature(
            "AntiOverlay",
            AntiOverlay(),
            Translation.t("rendering.antioverlay.description"),
        ),
        feature(
            "SuperSight",
            SuperSight(),
            Translation.t("rendering.supersight.description"),
        ),
        feature(
            "XRay",
            XRay(),
            Translation.t("rendering.xray.description"),
        ),
        feature(
            "CameraConfig",
            CameraConfig(),
            Translation.t("rendering.cameraconfig.description"),
        ),
        feature(
            "Radar",
            Radar(),
            Translation.t("rendering.radar.description"),
        ),
    )
