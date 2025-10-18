package org.infinite.settings

sealed class FeatureSetting<T>(
    val name: String,
    val description: String,
    var value: T,
    private val defaultValue: T,
) {
    fun reset() {
        value = defaultValue
    }

    class BooleanSetting(
        name: String,
        description: String,
        defaultValue: Boolean,
    ) : FeatureSetting<Boolean>(name, description, defaultValue, defaultValue)

    class IntSetting(
        name: String,
        description: String,
        defaultValue: Int,
        val min: Int,
        val max: Int,
    ) : FeatureSetting<Int>(name, description, defaultValue, defaultValue)

    class FloatSetting(
        name: String,
        description: String,
        defaultValue: Float,
        val min: Float,
        val max: Float,
    ) : FeatureSetting<Float>(name, description, defaultValue, defaultValue)

    class StringSetting(
        name: String,
        description: String,
        defaultValue: String,
    ) : FeatureSetting<String>(name, description, defaultValue, defaultValue)

    class StringListSetting(
        name: String,
        description: String,
        defaultValue: List<String>,
    ) : FeatureSetting<List<String>>(name, description, defaultValue, defaultValue)

    class EnumSetting<E : Enum<E>>(
        name: String,
        description: String,
        defaultValue: E,
        val options: List<E>,
    ) : FeatureSetting<E>(name, description, defaultValue, defaultValue)

    class BlockIDSetting(
        name: String,
        description: String,
        defaultValue: String,
    ) : FeatureSetting<String>(name, description, defaultValue, defaultValue)

    class EntityIDSetting(
        name: String,
        description: String,
        defaultValue: String,
    ) : FeatureSetting<String>(name, description, defaultValue, defaultValue)

    class BlockListSetting(
        name: String,
        description: String,
        defaultValue: MutableList<String>,
    ) : FeatureSetting<MutableList<String>>(name, description, defaultValue, defaultValue)

    class EntityListSetting(
        name: String,
        description: String,
        defaultValue: MutableList<String>,
    ) : FeatureSetting<MutableList<String>>(name, description, defaultValue, defaultValue)

    class PlayerListSetting(
        name: String,
        description: String,
        defaultValue: MutableList<String>,
    ) : FeatureSetting<MutableList<String>>(name, description, defaultValue, defaultValue)
}
