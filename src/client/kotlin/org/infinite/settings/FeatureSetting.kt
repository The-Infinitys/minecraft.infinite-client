package org.infinite.settings

import org.infinite.utils.toSnakeCase

sealed class FeatureSetting<T>(
    val name: String,
    var value: T,
    val defaultValue: T,
) {
    lateinit var descriptionKey: String

    init {
        // --- Name Validation ---
        // 1. nameが空でないこと
        if (name.isEmpty()) {
            throw IllegalArgumentException("FeatureSetting name must not be empty.")
        }
        // 最初の文字が大文字で、空白や特殊文字を含まず、単語の区切りに大文字を使用しているか
        if (!name.matches(Regex("^[a-zA-Z][a-zA-Z0-9_]*$"))) {
            throw IllegalArgumentException(
                "FeatureSetting name '$name' must follow a valid naming convention (alphanumeric or underscores, starting with a letter)." +
                    " " +
                    "Example: 'targetHunger', 'allow_rotten_flesh', or 'TargetHunger'.",
            )
        }
    }

    fun generateKey(
        category: String,
        featureName: String,
        name: String,
    ): String {
        val snakeCategory = toSnakeCase(category)
        val snakeFeatureName = toSnakeCase(featureName)
        val snakeName = toSnakeCase(name)
        // スネークケースに変換した文字列を使用してdescriptionKeyを構築
        descriptionKey = "infinite.feature.$snakeCategory.$snakeFeatureName.$snakeName.description"
        return descriptionKey
    }

    fun reset() {
        value = defaultValue
    }

    class BooleanSetting(
        name: String,
        defaultValue: Boolean,
    ) : FeatureSetting<Boolean>(name, defaultValue, defaultValue)

    class IntSetting(
        name: String,
        defaultValue: Int,
        val min: Int,
        val max: Int,
    ) : FeatureSetting<Int>(name, defaultValue, defaultValue)

    class FloatSetting(
        name: String,
        defaultValue: Float,
        val min: Float,
        val max: Float,
    ) : FeatureSetting<Float>(name, defaultValue, defaultValue)

    class DoubleSetting(
        name: String,
        defaultValue: Double,
        val min: Double,
        val max: Double,
    ) : FeatureSetting<Double>(name, defaultValue, defaultValue)

    class StringSetting(
        name: String,
        defaultValue: String,
    ) : FeatureSetting<String>(name, defaultValue, defaultValue)

    class StringListSetting(
        name: String,
        defaultValue: String,
        val options: List<String>,
    ) : FeatureSetting<String>(name, defaultValue, defaultValue) {
        fun set(value: String) {
            this.value = options.find { it == value } ?: return
        }
    }

    class EnumSetting<E : Enum<E>>(
        name: String,
        defaultValue: E,
        val options: List<E>,
    ) : FeatureSetting<E>(name, defaultValue, defaultValue) {
        fun set(enumName: String) {
            this.value = options.find { it.name == enumName } ?: return
        }
    }

    class BlockIDSetting(
        name: String,
        defaultValue: String,
    ) : FeatureSetting<String>(name, defaultValue, defaultValue)

    class EntityIDSetting(
        name: String,
        defaultValue: String,
    ) : FeatureSetting<String>(name, defaultValue, defaultValue)

    class BlockListSetting(
        name: String,
        defaultValue: MutableList<String>,
    ) : FeatureSetting<MutableList<String>>(name, defaultValue, defaultValue)

    class EntityListSetting(
        name: String,
        defaultValue: MutableList<String>,
    ) : FeatureSetting<MutableList<String>>(name, defaultValue, defaultValue)

    class PlayerListSetting(
        name: String,
        defaultValue: MutableList<String>,
    ) : FeatureSetting<MutableList<String>>(name, defaultValue, defaultValue)

    class BlockColorListSetting(
        name: String,
        defaultValue: MutableMap<String, Int>,
    ) : FeatureSetting<MutableMap<String, Int>>(name, defaultValue, defaultValue)
}
