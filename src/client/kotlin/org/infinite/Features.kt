package org.infinite

import org.infinite.features.automatic.automatic
import org.infinite.features.fighting.fighting
import org.infinite.features.movement.movement
import org.infinite.features.rendering.rendering
import org.infinite.features.server.server
import org.infinite.features.utils.utils
import org.infinite.utils.toSnakeCase

class Feature(
    val name: String,
    val instance: ConfigurableFeature,
) {
    init {
        // --- Name Validation ---
        // 1. nameが空でないこと
        if (name.isEmpty()) {
            throw IllegalArgumentException("FeatureSetting name must not be empty.")
        }
        // 2. nameがアッパーキャメルケース (Upper Camel Case, PascalCase) に沿っていること
        // 最初の文字が大文字で、空白や特殊文字を含まず、単語の区切りに大文字を使用しているか
        if (!name.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*$"))) {
            throw IllegalArgumentException(
                "Feature name '$name' must follow a valid naming convention (alphanumeric or underscores, starting with a letter)." +
                    " " +
                    "Example: 'targetHunger', 'allow_rotten_flesh', or 'TargetHunger'.",
            )
        }
    }

    lateinit var descriptionKey: String

    fun generateKey(category: String): String {
        val snakeCategory = toSnakeCase(category)
        val snakeFeatureName = toSnakeCase(name)
        descriptionKey = "infinite.feature.$snakeCategory.$snakeFeatureName.description"
        return descriptionKey
    }
}

data class FeatureCategory(
    val name: String,
    val features: MutableList<Feature>,
)

fun <T : ConfigurableFeature> feature(
    name: String,
    instance: T,
): Feature = Feature(name, instance)

val featureCategories =
    mutableListOf(
        FeatureCategory("Movement", movement),
        FeatureCategory("Rendering", rendering),
        FeatureCategory("Fighting", fighting),
        FeatureCategory("Automatic", automatic),
        FeatureCategory("Server", server),
        FeatureCategory("Utils", utils),
    )
