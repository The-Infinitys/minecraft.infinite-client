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

val rendering =
    listOf(
        feature(
            "feature.rendering.antioverlay.name",
            AntiOverlay(),
            "feature.rendering.antioverlay.description",
        ),
        feature(
            "feature.rendering.supersight.name",
            SuperSight(),
            "feature.rendering.supersight.description",
        ),
        feature(
            "feature.rendering.xray.name",
            XRay(),
            "feature.rendering.xray.description",
        ),
        feature(
            "feature.rendering.cameraconfig.name",
            CameraConfig(),
            "feature.rendering.cameraconfig.description",
        ),
        feature(
            "feature.rendering.freecamera.name", // 追加
            FreeCamera(), // 追加
            "feature.rendering.freecamera.description", // 追加
        ),
        feature(
            "feature.rendering.radar.name",
            Radar(),
            "feature.rendering.radar.description",
        ),
        feature(
            "feature.rendering.extrasensory.name",
            ExtraSensory(),
            "feature.rendering.extrasensory.description",
        ),
        feature(
            "feature.rendering.detailinfo.name",
            DetailInfo(),
            "feature.rendering.detailinfo.description",
        ),
        feature(
            "feature.rendering.hypertag.name",
            HyperTag(),
            "feature.rendering.hypertag.description",
        ),
        feature("feature.rendering.portalgui.name", PortalGui(), "feature.rendering.portalgui.description"),
    )
