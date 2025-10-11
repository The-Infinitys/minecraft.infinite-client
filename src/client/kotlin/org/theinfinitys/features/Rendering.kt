package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.rendering.camera.AntiOverlay
import org.theinfinitys.features.rendering.camera.CameraConfig
import org.theinfinitys.features.rendering.camera.ExtraSensory
import org.theinfinitys.features.rendering.camera.SuperSight
import org.theinfinitys.features.rendering.gui.DetailInfo
import org.theinfinitys.features.rendering.gui.Radar
import org.theinfinitys.features.rendering.xray.XRay
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
        feature(
            "ExtraSensory",
            ExtraSensory(),
            Translation.t("rendering.extrasensory.description"),
        ),
        feature("DetailInfo", DetailInfo(), Translation.t("rendering.detailinfo.description")),
    )
