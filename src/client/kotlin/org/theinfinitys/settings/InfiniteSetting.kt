package org.theinfinitys.settings

sealed class InfiniteSetting<T>(
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
    ) : InfiniteSetting<Boolean>(name, description, defaultValue, defaultValue)

    class IntSetting(
        name: String,
        description: String,
        defaultValue: Int,
        val min: Int,
        val max: Int,
    ) : InfiniteSetting<Int>(name, description, defaultValue, defaultValue)

    class FloatSetting(
        name: String,
        description: String,
        defaultValue: Float,
        val min: Float,
        val max: Float,
    ) : InfiniteSetting<Float>(name, description, defaultValue, defaultValue)

    class StringSetting(
        name: String,
        description: String,
        defaultValue: String,
    ) : InfiniteSetting<String>(name, description, defaultValue, defaultValue)

    class StringListSetting(
        name: String,
        description: String,
        defaultValue: List<String>,
    ) : InfiniteSetting<List<String>>(name, description, defaultValue, defaultValue)

    class EnumSetting<E : Enum<E>>(
        name: String,
        description: String,
        defaultValue: E,
        val options: List<E>,
    ) : InfiniteSetting<E>(name, description, defaultValue, defaultValue)

    class BlockIDSetting(
        name: String,
        description: String,
        defaultValue: String,
    ) : InfiniteSetting<String>(name, description, defaultValue, defaultValue)

    class EntityIDSetting(
        name: String,
        description: String,
        defaultValue: String,
    ) : InfiniteSetting<String>(name, description, defaultValue, defaultValue)

    class BlockListSetting(
        name: String,
        description: String,
        defaultValue: MutableList<String>,
    ) : InfiniteSetting<MutableList<String>>(name, description, defaultValue, defaultValue)

    class EntityListSetting(
        name: String,
        description: String,
        defaultValue: MutableList<String>,
    ) : InfiniteSetting<MutableList<String>>(name, description, defaultValue, defaultValue)

    class PlayerListSetting(
        name: String,
        description: String,
        defaultValue: MutableList<String>,
    ) : InfiniteSetting<MutableList<String>>(name, description, defaultValue, defaultValue)
}
