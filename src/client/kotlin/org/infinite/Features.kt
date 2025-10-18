package org.infinite

import org.infinite.features.automatic
import org.infinite.features.fighting
import org.infinite.features.movement
import org.infinite.features.rendering
import org.infinite.features.server

data class Feature(
    val nameKey: String,
    val instance: Any,
    val descriptionKey: String = "",
)

data class FeatureCategory(
    val name: String,
    val features: List<Feature>,
)

fun feature(
    nameKey: String,
    instance: Any,
    descriptionKey: String,
): Feature = Feature(nameKey, instance, descriptionKey)

val featureCategories =
    listOf(
        FeatureCategory("Movement", movement),
        FeatureCategory("Rendering", rendering),
        FeatureCategory("Fighting", fighting),
        FeatureCategory("Automatic", automatic),
        FeatureCategory("Server", server),
    )
