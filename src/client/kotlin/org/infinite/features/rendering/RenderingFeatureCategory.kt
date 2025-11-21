package org.infinite.features.rendering

import org.infinite.features.Feature
import org.infinite.features.FeatureCategory
import org.infinite.features.rendering.camera.CameraConfig
import org.infinite.features.rendering.camera.FreeCamera
import org.infinite.features.rendering.detailinfo.DetailInfo
import org.infinite.features.rendering.overlay.AntiOverlay
import org.infinite.features.rendering.portalgui.PortalGui
import org.infinite.features.rendering.search.BlockSearch
import org.infinite.features.rendering.sensory.ExtraSensory
import org.infinite.features.rendering.shader.SimpleShader
import org.infinite.features.rendering.sight.SuperSight
import org.infinite.features.rendering.tag.HyperTag
import org.infinite.features.rendering.ui.HyperUi
import org.infinite.features.rendering.xray.XRay

class RenderingFeatureCategory :
    FeatureCategory(
        "Rendering",
        mutableListOf(
            Feature(HyperUi()),
            Feature(AntiOverlay()),
            Feature(SuperSight()),
            Feature(XRay()),
            Feature(CameraConfig()),
            Feature(FreeCamera()), // 追加),
            Feature(SimpleShader()),
            Feature(ExtraSensory()),
            Feature(DetailInfo()),
            Feature(HyperTag()),
            Feature(PortalGui()),
            Feature(BlockSearch()),
        ),
    )
