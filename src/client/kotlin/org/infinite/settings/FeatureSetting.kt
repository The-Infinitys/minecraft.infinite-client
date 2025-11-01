package org.infinite.settings

sealed class FeatureSetting<T>(
    val name: String,
    val descriptionKey: String,
    var value: T,
    val defaultValue: T,
) {
    fun reset() {
        value = defaultValue
    }

    class BooleanSetting(
        name: String,
        descriptionKey: String,
        defaultValue: Boolean,
    ) : FeatureSetting<Boolean>(name, descriptionKey, defaultValue, defaultValue)

    class IntSetting(
        name: String,
        descriptionKey: String,
        defaultValue: Int,
        val min: Int,
        val max: Int,
    ) : FeatureSetting<Int>(name, descriptionKey, defaultValue, defaultValue)

    class FloatSetting(
        name: String,
        descriptionKey: String,
        defaultValue: Float,
        val min: Float,
        val max: Float,
    ) : FeatureSetting<Float>(name, descriptionKey, defaultValue, defaultValue)

    class DoubleSetting(
        name: String,
        descriptionKey: String,
        defaultValue: Double,
        val min: Double,
        val max: Double,
    ) : FeatureSetting<Double>(name, descriptionKey, defaultValue, defaultValue)

    class StringSetting(
        name: String,
        descriptionKey: String,
        defaultValue: String,
    ) : FeatureSetting<String>(name, descriptionKey, defaultValue, defaultValue)

    class StringListSetting(
        name: String,
        descriptionKey: String,
        defaultValue: List<String>,
    ) : FeatureSetting<List<String>>(name, descriptionKey, defaultValue, defaultValue)

    class EnumSetting<E : Enum<E>>(
        name: String,
        descriptionKey: String,
        defaultValue: E,
        val options: List<E>,
    ) : FeatureSetting<E>(name, descriptionKey, defaultValue, defaultValue) {
        fun set(enumName: String) {
            this.value = options.find { it.name == enumName } ?: return
        }
    }

    class BlockIDSetting(
        name: String,
        descriptionKey: String,
        defaultValue: String,
    ) : FeatureSetting<String>(name, descriptionKey, defaultValue, defaultValue)

    class EntityIDSetting(
        name: String,
        descriptionKey: String,
        defaultValue: String,
    ) : FeatureSetting<String>(name, descriptionKey, defaultValue, defaultValue)

    class BlockListSetting(
        name: String,
        descriptionKey: String,
        defaultValue: MutableList<String>,
    ) : FeatureSetting<MutableList<String>>(name, descriptionKey, defaultValue, defaultValue)

    class EntityListSetting(
        name: String,
        descriptionKey: String,
        defaultValue: MutableList<String>,
    ) : FeatureSetting<MutableList<String>>(name, descriptionKey, defaultValue, defaultValue)

    class PlayerListSetting(
        name: String,
        descriptionKey: String,
        defaultValue: MutableList<String>,
    ) : FeatureSetting<MutableList<String>>(name, descriptionKey, defaultValue, defaultValue)

    class BlockColorListSetting(
        name: String,
        descriptionKey: String,
        defaultValue: MutableMap<String, Int>,
    ) : FeatureSetting<MutableMap<String, Int>>(name, descriptionKey, defaultValue, defaultValue)
}
