package org.infinite.libs.infinite

import org.infinite.FeatureCategory

interface InfiniteAddon {
    val id: String
    val version: String

    fun getFeatures(): List<FeatureCategory>

    fun onInitialize()

    fun onShutdown()
}
