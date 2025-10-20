package org.infinite.features

import org.infinite.feature
import org.infinite.features.rendering.camera.CameraConfig
import org.infinite.features.rendering.camera.FreeCamera
import org.infinite.features.rendering.detailinfo.DetailInfo
import org.infinite.features.rendering.overlay.AntiOverlay
import org.infinite.features.rendering.portalgui.PortalGui
import org.infinite.features.rendering.radar.Radar
import org.infinite.features.rendering.search.BlockSearch
import org.infinite.features.rendering.sensory.ExtraSensory
import org.infinite.features.rendering.sight.SuperSight
import org.infinite.features.rendering.tag.HyperTag
import org.infinite.features.rendering.xray.XRay

val rendering =
    listOf(
        feature(
            "AntiOverlay",
            AntiOverlay(),
            "feature.rendering.antioverlay.description",
        ),
        feature(
            "SuperSight",
            SuperSight(),
            "feature.rendering.supersight.description",
        ),
        feature(
            "XRay",
            XRay(),
            "feature.rendering.xray.description",
        ),
        feature(
            "CameraConfig",
            CameraConfig(),
            "feature.rendering.cameraconfig.description",
        ),
        feature(
            "FreeCamera", // 追加
            FreeCamera(), // 追加
            "feature.rendering.freecamera.description", // 追加
        ),
        feature(
            "Radar",
            Radar(),
            "feature.rendering.radar.description",
        ),
        feature(
            "ExtraSensory",
            ExtraSensory(),
            "feature.rendering.extrasensory.description",
        ),
        feature(
            "DetailInfo",
            DetailInfo(),
            "feature.rendering.detailinfo.description",
        ),
        feature(
            "HyperTag",
            HyperTag(),
            "feature.rendering.hypertag.description",
        ),
        feature("PortalGui", PortalGui(), "feature.rendering.portalgui.description"),
        feature(
            "BlockSearch",
            BlockSearch(),
            "feature.rendering.search.blocksearch.description",
        ),
    )
