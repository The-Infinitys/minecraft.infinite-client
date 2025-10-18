package org.infinite.features

import org.infinite.feature
import org.infinite.features.rendering.camera.CameraConfig
import org.infinite.features.rendering.camera.FreeCamera
import org.infinite.features.rendering.detailinfo.DetailInfo
import org.infinite.features.rendering.overlay.AntiOverlay
import org.infinite.features.rendering.portalgui.PortalGui
import org.infinite.features.rendering.radar.Radar
import org.infinite.features.rendering.sensory.ExtraSensory
import org.infinite.features.rendering.sight.SuperSight
import org.infinite.features.rendering.tag.HyperTag
import org.infinite.features.rendering.xray.XRay
import org.infinite.utils.Translation

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
