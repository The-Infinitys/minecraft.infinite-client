package org.infinite.features

import org.infinite.feature.ConfigurableFeature

open class FeatureCategory(
    val name: String,
    open val features: MutableList<Feature<out ConfigurableFeature>>,
)
