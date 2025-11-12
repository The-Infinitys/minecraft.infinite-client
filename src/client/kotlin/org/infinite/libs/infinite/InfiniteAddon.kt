package org.infinite.libs.infinite

import org.infinite.FeatureCategory
import org.infinite.gui.theme.Theme

interface InfiniteAddon {
    val id: String
    val version: String

    val features: List<FeatureCategory>
    val themes: List<Theme>

    fun onInitialize()

    fun onShutdown()
}
