package org.infinite.global.rendering

import org.infinite.global.GlobalFeature
import org.infinite.global.GlobalFeatureCategory
import org.infinite.global.rendering.theme.ThemeSetting

class GlobalRenderingFeatureCategory :
    GlobalFeatureCategory(
        "Rendering",
        mutableListOf(
            GlobalFeature(ThemeSetting()),
        ),
    )
