package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.rendering.*
import org.theinfinitys.utils.Translation

val rendering = listOf(
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
