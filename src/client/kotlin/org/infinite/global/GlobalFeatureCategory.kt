package org.infinite.global

import org.infinite.features.Feature

class GlobalFeatureCategory(
    val name: String,
    val features: MutableList<Feature<out ConfigurableGlobalFeature>>,
)
