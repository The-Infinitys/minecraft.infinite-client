package org.infinite.global

import org.infinite.features.Feature
import org.infinite.utils.toSnakeCase

class GlobalFeature<T : ConfigurableGlobalFeature>(
    val instance: T,
) {
    val name: String = instance.javaClass.simpleName

    init {
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
        descriptionKey = "infinite.general_feature.$snakeCategory.$snakeFeatureName.description"
        return descriptionKey
    }
}
