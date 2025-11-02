package org.infinite.settings

sealed class FeatureSetting<T>(
    val name: String,
    val descriptionKey: String,
    var value: T,
    val defaultValue: T,
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
                "FeatureSetting name '$name' must follow a valid naming convention (alphanumeric or underscores, starting with a letter)." +
                    " " +
                    "Example: 'targetHunger', 'allow_rotten_flesh', or 'TargetHunger'.",
            )
        }

        // --- Description Key Validation ---
        // descriptionKeyが feature.(category).(name).(settingname).description の形式に沿っているか
        // より具体的なチェック: feature. の後に3つ以上のピリオド区切りのセクションがあり、最後に .description で終わっていること
        val keyPattern = Regex("^feature\\.[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+\\.[a-zA-Z0-9_]+\\.description$")
        if (!descriptionKey.matches(keyPattern)) {
            throw IllegalArgumentException(
                "FeatureSetting descriptionKey '$descriptionKey' must follow the format" +
                    " 'feature.(category).(name).(settingname).description'. " +
                    "Example: 'feature.utils.foodmanager.target_hunger.description'.",
            )
        }
    }

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
