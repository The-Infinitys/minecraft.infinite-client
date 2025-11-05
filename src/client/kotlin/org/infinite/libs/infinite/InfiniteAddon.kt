package org.infinite.libs.infinite

import org.infinite.ConfigurableFeature

interface InfiniteAddon {
    val category: String
    val name: String
    val description: String
    val feature: ConfigurableFeature

    fun onInitialize()

    fun onShutdown()
}
