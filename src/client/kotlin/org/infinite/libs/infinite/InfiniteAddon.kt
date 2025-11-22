package org.infinite.libs.infinite

import org.infinite.features.FeatureCategory
import org.infinite.global.GlobalFeatureCategory
import org.infinite.gui.theme.Theme

interface InfiniteAddon {
    val id: String
    val version: String

    val features: List<FeatureCategory>
    val globalFeatures: List<GlobalFeatureCategory>
    val themes: List<Theme>

    fun onInit()

    fun onShutdown()
}
