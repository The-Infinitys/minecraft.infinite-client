package org.theinfinitys.features

import org.theinfinitys.feature
import org.theinfinitys.features.rendering.camera.CameraConfig
import org.theinfinitys.features.rendering.camera.FreeCamera
import org.theinfinitys.features.rendering.detailinfo.DetailInfo
import org.theinfinitys.features.rendering.overlay.AntiOverlay
import org.theinfinitys.features.rendering.portalgui.PortalGui
import org.theinfinitys.features.rendering.radar.Radar
import org.theinfinitys.features.rendering.sensory.ExtraSensory
import org.theinfinitys.features.rendering.sight.SuperSight
import org.theinfinitys.features.rendering.tag.HyperTag
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
            "FreeCamera", // 追加
            FreeCamera(), // 追加
            Translation.t("rendering.freecamera.description"), // 追加
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
        feature(
            "DetailInfo",
            DetailInfo(),
            Translation.t("rendering.detailinfo.description"),
        ),
        feature(
            "HyperTag",
            HyperTag(),
            Translation.t("rendering.hypertag.description"),
        ),
        feature("PortalGui", PortalGui(), Translation.t("rendering.portalgui.description")),
    )
