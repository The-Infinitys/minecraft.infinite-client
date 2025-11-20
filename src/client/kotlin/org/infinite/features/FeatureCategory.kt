package org.infinite.features

import org.infinite.ConfigurableFeature

open class FeatureCategory(
    val name: String,
    val features: MutableList<Feature<out ConfigurableFeature>>,
)
