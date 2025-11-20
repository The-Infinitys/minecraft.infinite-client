package org.infinite.global

open class GlobalFeatureCategory(
    val name: String,
    val features: MutableList<GlobalFeature<out ConfigurableGlobalFeature>>,
)
