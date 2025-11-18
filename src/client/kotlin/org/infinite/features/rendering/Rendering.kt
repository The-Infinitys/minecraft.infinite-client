package org.infinite.features.rendering

import org.infinite.feature
import org.infinite.features.rendering.camera.CameraConfig
import org.infinite.features.rendering.camera.FreeCamera
import org.infinite.features.rendering.detailinfo.DetailInfo
import org.infinite.features.rendering.font.HyperFont
import org.infinite.features.rendering.overlay.AntiOverlay
import org.infinite.features.rendering.portalgui.PortalGui
import org.infinite.features.rendering.search.BlockSearch
import org.infinite.features.rendering.sensory.ExtraSensory
import org.infinite.features.rendering.sight.SuperSight
import org.infinite.features.rendering.tag.HyperTag
import org.infinite.features.rendering.ui.HyperUi
import org.infinite.features.rendering.xray.XRay

internal val rendering =
    mutableListOf(
        feature("HyperUi", HyperUi()),
        feature("HyperFont", HyperFont()),
        feature(
            "AntiOverlay",
            AntiOverlay(),
        ),
        feature(
            "SuperSight",
            SuperSight(),
        ),
        feature(
            "XRay",
            XRay(),
        ),
        feature(
            "CameraConfig",
            CameraConfig(),
        ),
        feature(
            "FreeCamera", // 追加
            FreeCamera(), // 追加
            // 追加
        ),
        feature(
            "ExtraSensory",
            ExtraSensory(),
        ),
        feature(
            "DetailInfo",
            DetailInfo(),
        ),
        feature(
            "HyperTag",
            HyperTag(),
        ),
        feature("PortalGui", PortalGui()),
        feature(
            "BlockSearch",
            BlockSearch(),
        ),
    )
