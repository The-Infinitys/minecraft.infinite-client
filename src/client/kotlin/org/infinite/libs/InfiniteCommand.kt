package org.infinite.libs

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.command.CommandSource
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.infinite.ConfigManager
import org.infinite.InfiniteClient // Add this import
import org.infinite.InfiniteClient.error
import org.infinite.InfiniteClient.info
import org.infinite.InfiniteClient.log
import org.infinite.InfiniteClient.searchFeature
import org.infinite.InfiniteClient.warn
import org.infinite.featureCategories
import org.infinite.settings.FeatureSetting

object InfiniteCommand {
    fun registerCommands(
        dispatcher: CommandDispatcher<FabricClientCommandSource>,
        registryAccess: CommandRegistryAccess,
    ) {
        dispatcher.register(
            ClientCommandManager
                .literal("infinite")
                // 1. /infinite version
                .then(
                    ClientCommandManager.literal("version").executes { _ -> getVersion() },
                )
                // 2. /infinite config save/load
                .then(
                    ClientCommandManager
                        .literal("config")
                        .then(
                            ClientCommandManager.literal("save").executes { _ -> saveConfig() },
                        ).then(
                            ClientCommandManager.literal("load").executes { _ -> loadConfig() },
                        ).then(
                            ClientCommandManager.literal("reset").executes { context -> resetConfig(context) }.then(
                                getCategoryArgument().executes { context -> resetConfig(context) }.then(
                                    getFeatureNameArgument().executes { context -> resetConfig(context) }.then(
                                        getSettingKeyArgument().executes { context -> resetConfig(context) },
                                    ),
                                ),
                            ),
                        ),
                )
                // 3. /infinite feature ...
                .then(
                    ClientCommandManager.literal("feature").then(
                        getCategoryArgument().then(
                            getFeatureNameArgument()
                                // 3-1. /infinite feature <category> <name> <enable/disable/toggle/get>
                                .then(
                                    ClientCommandManager
                                        .literal("enable")
                                        .executes { context -> toggleFeature(context, true) },
                                ).then(
                                    ClientCommandManager
                                        .literal("disable")
                                        .executes { context -> toggleFeature(context, false) },
                                ).then(
                                    ClientCommandManager
                                        .literal("toggle")
                                        .executes { context -> toggleFeatureState(context) },
                                ).then(
                                    ClientCommandManager
                                        .literal("get")
                                        .executes { context -> getFeatureStatus(context) },
                                )
                                // 3-2. /infinite feature <category> <name> set <key> <value>
                                .then(
                                    ClientCommandManager.literal("set").then(
                                        getSettingKeyArgument().then(
                                            getSettingValueArgument().executes { context ->
                                                setFeatureSetting(context)
                                            },
                                        ),
                                    ),
                                ).then(
                                    ClientCommandManager.literal("add").then(
                                        getSettingKeyArgument().then(
                                            getSettingValueArgument().executes { context ->
                                                addRemoveFeatureSetting(context, true)
                                            },
                                        ),
                                    ),
                                ).then(
                                    ClientCommandManager.literal("del").then(
                                        getSettingKeyArgument().then(
                                            getSettingValueArgument().executes { context ->
                                                addRemoveFeatureSetting(context, false)
                                            },
                                        ),
                                    ),
                                ),
                        ),
                    ),
                )
                // 4. /infinite theme [name]
                .then(
                    ClientCommandManager
                        .literal("theme")
                        .executes { context -> getTheme(context) } // /infinite theme
                        .then(
                            ClientCommandManager
                                .literal("list") // /infinite theme list
                                .executes { context -> getTheme(context) },
                        ).then(
                            ClientCommandManager
                                .literal("set") // /infinite theme set {name}
                                .then(
                                    ClientCommandManager
                                        .argument("name", StringArgumentType.word())
                                        .suggests(getThemeSuggestions())
                                        .executes { context -> setTheme(context) },
                                ),
                        ),
                ),
        )
        featureCategories.forEach { category ->
            category.features.forEach { feature ->
                feature.instance.registerCommands(dispatcher)
            }
        }
    }

    private fun getTheme(context: CommandContext<FabricClientCommandSource>): Int {
        val currentThemeName = InfiniteClient.currentTheme
        info(Text.translatable("command.infinite.theme.current", currentThemeName).string)
        val availableThemes = InfiniteClient.themes.joinToString(", ") { it.name }
        info(Text.translatable("command.infinite.theme.available", availableThemes).string)
        return 1
    }

    private fun getThemeSuggestions(): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { _, builder ->
            CommandSource.suggestMatching(
                InfiniteClient.themes.map { it.name },
                builder,
            )
        }

    private fun setTheme(context: CommandContext<FabricClientCommandSource>): Int {
        val themeName = StringArgumentType.getString(context, "name")
        val theme = InfiniteClient.themes.find { it.name.equals(themeName, ignoreCase = true) }
        if (theme == null) {
            error(Text.translatable("command.infinite.theme.notfound", themeName).string)
            return 0
        }
        InfiniteClient.currentTheme = theme.name
        ConfigManager.saveConfig() // Save the updated theme
        info(Text.translatable("command.infinite.theme.changed", theme.name).string)
        return 1
    }

    /*
     * 既存の引数定義関数 (変更なし)
     */

    private fun resetConfig(context: CommandContext<*>): Int {
        val categoryName =
            try {
                StringArgumentType.getString(context, "category")
            } catch (_: IllegalArgumentException) {
                null
            }
        val featureName =
            try {
                StringArgumentType.getString(context, "name")
            } catch (_: IllegalArgumentException) {
                null
            }
        val settingKey =
            try {
                StringArgumentType.getString(context, "key")
            } catch (_: IllegalArgumentException) {
                null
            }

        when {
            categoryName == null -> {
                // Reset all settings
                featureCategories.forEach { category ->
                    category.features.forEach { feature ->
                        feature.instance.let { configurableFeature ->
                            configurableFeature.reset() // Reset the feature's enabled state
                            configurableFeature.settings.forEach { setting ->
                                setting.reset()
                            }
                        }
                    }
                }
                info(Text.translatable("command.infinite.config.reset.all").string)
            }

            featureName == null -> {
                // Reset all settings in a category
                val category = featureCategories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
                if (category == null) {
                    error(Text.translatable("command.infinite.category.notfound", categoryName).string)
                    return 0
                }
                category.features.forEach { feature ->
                    feature.instance.let { configurableFeature ->
                        configurableFeature.reset() // Reset the feature's enabled state
                        configurableFeature.settings.forEach { setting ->
                            setting.reset()
                        }
                    }
                }
                info(Text.translatable("command.infinite.config.reset.category", categoryName).string)
            }

            settingKey == null -> {
                // Reset all settings in a feature
                val feature = searchFeature(categoryName, featureName)
                if (feature == null) {
                    error(Text.translatable("command.infinite.feature.notfound", categoryName, featureName).string)
                    return 0
                }
                feature.reset() // Reset the feature's enabled state
                feature.settings.forEach { setting ->
                    setting.reset()
                }
                info(Text.translatable("command.infinite.config.reset.feature", featureName).string)
            }

            else -> {
                // Reset a specific setting
                val feature = searchFeature(categoryName, featureName)
                if (feature == null) {
                    error(Text.translatable("command.infinite.feature.notfound", categoryName, featureName).string)
                    return 0
                }
                val setting = feature.getSetting(settingKey)
                if (setting == null) {
                    error(Text.translatable("command.infinite.setting.notfound", featureName, settingKey).string)
                    return 0
                }
                setting.reset()
                info(Text.translatable("command.infinite.config.reset.setting", featureName, settingKey).string)
            }
        }
        return 1
    }

    private fun getVersion(): Int {
        val modContainer = FabricLoader.getInstance().getModContainer("infinite")
        val modVersion = modContainer.map { it.metadata.version.friendlyString }.orElse("unknown")
        log("version $modVersion")
        return 1
    }

    private fun saveConfig(): Int {
        ConfigManager.saveConfig()
        log(Text.translatable("command.infinite.config.save").string)
        return 1
    }

    private fun loadConfig(): Int {
        log(Text.translatable("command.infinite.config.load").string)
        return 1
    }

    private fun toggleFeatureState(context: CommandContext<*>): Int {
        val categoryName = StringArgumentType.getString(context, "category")
        val featureName = StringArgumentType.getString(context, "name")
        val feature = searchFeature(categoryName, featureName)
        if (feature == null) {
            error("${Text.translatable("command.infinite.nofeature")}: $categoryName / $featureName")
            return 0
        }
        val enable = !feature.isEnabled()
        // 現在の状態を反転させる
        val action =
            if (enable) {
                Text
                    .translatable(
                        "command.infinite.action.enabled",
                    ).string
            } else {
                Text.translatable("command.infinite.action.disabled").string
            }

        if (enable) {
            feature.enable()
        } else {
            feature.disable()
        }
        info(Text.translatable("command.infinite.feature.toggled", featureName, action).string)
        return 1
    }

    /*
     * 既存の引数定義関数 (変更なし)
     */

    private fun getCategoryArgument() =
        ClientCommandManager.argument("category", StringArgumentType.word()).suggests(getCategorySuggestions())

    private fun getFeatureNameArgument() =
        ClientCommandManager.argument("name", StringArgumentType.word()).suggests(getFeatureNameSuggestions())

    private fun getSettingKeyArgument() =
        ClientCommandManager.argument("key", StringArgumentType.word()).suggests(getSettingKeySuggestions())

    private fun getSettingValueArgument() = ClientCommandManager.argument("value", StringArgumentType.greedyString())

    /*
     * 既存のサジェスト関数 (変更なし)
     */

    private fun getCategorySuggestions(): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { _, builder ->
            CommandSource.suggestMatching(
                featureCategories.map { it.name },
                builder,
            )
        }

    private fun getFeatureNameSuggestions(): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { context, builder ->
            try {
                val categoryName = StringArgumentType.getString(context, "category")
                val category = featureCategories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
                if (category != null) {
                    CommandSource.suggestMatching(
                        category.features.map { it.name },
                        builder,
                    )
                }
            } catch (_: IllegalArgumentException) {
            }
            builder.buildFuture()
        }

    private fun getSettingKeySuggestions(): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { context, builder ->
            try {
                val categoryName = StringArgumentType.getString(context, "category")
                val featureName = StringArgumentType.getString(context, "name")
                val feature = searchFeature(categoryName, featureName)
                if (feature != null) {
                    CommandSource.suggestMatching(
                        feature.settings.map { it.name },
                        builder,
                    )
                }
            } catch (_: IllegalArgumentException) {
            }
            builder.buildFuture()
        }

    /*
     * 既存の機能操作関数 (一部変更/再利用)
     */

    private fun toggleFeature(
        context: CommandContext<*>,
        enable: Boolean,
    ): Int {
        val categoryName = StringArgumentType.getString(context, "category")
        val featureName = StringArgumentType.getString(context, "name")
        val action =
            if (enable) {
                Text
                    .translatable(
                        "command.infinite.action.enabled",
                    ).string
            } else {
                Text.translatable("command.infinite.action.disabled").string
            }
        val feature = searchFeature(categoryName, featureName)
        if (feature == null) {
            error(Text.translatable("command.infinite.feature.notfound", categoryName, featureName).string)
            return 0
        }
        if (feature.isEnabled() == enable) {
            warn(Text.translatable("command.infinite.feature.already", featureName, action).string)
            return 0
        }
        if (enable) {
            feature.enable()
        } else {
            feature.disable()
        }
        info(Text.translatable("command.infinite.feature.toggled", featureName, action).string)
        return 1
    }

    private fun setFeatureSetting(context: CommandContext<*>): Int {
        val categoryName = StringArgumentType.getString(context, "category")
        val featureName = StringArgumentType.getString(context, "name")
        val settingKey = StringArgumentType.getString(context, "key")
        val rawValue = StringArgumentType.getString(context, "value")
        val feature = searchFeature(categoryName, featureName)
        if (feature == null) {
            error(Text.translatable("command.infinite.feature.notfound", categoryName, featureName).string)
            return 0
        }
        val setting = feature.getSetting(settingKey)
        if (setting == null) {
            error(Text.translatable("command.infinite.setting.notfound", featureName, settingKey).string)
            return 0
        }
        try {
            val processedValue: Any =
                when (setting.value) {
                    is Boolean ->
                        rawValue.toBooleanStrictOrNull()
                            ?: throw IllegalArgumentException(Text.translatable("command.infinite.setting.type.boolean").string)

                    is Int ->
                        rawValue.toIntOrNull()
                            ?: throw IllegalArgumentException(Text.translatable("command.infinite.setting.type.int").string)
                    is Float ->
                        rawValue.toFloatOrNull()
                            ?: throw IllegalArgumentException(Text.translatable("command.infinite.setting.type.float").string)
                    is String -> rawValue
                    is List<*> -> rawValue.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    else -> throw IllegalStateException(
                        Text.translatable("command.infinite.setting.type.unsupported", setting.value::class.simpleName).string,
                    )
                }

            @Suppress("UNCHECKED_CAST")
            val mutableSetting = setting as FeatureSetting<Any>
            mutableSetting.value = processedValue
            info(Text.translatable("command.infinite.setting.changed", featureName, settingKey, processedValue).string)
            return 1
        } catch (e: Exception) {
            error(Text.translatable("command.infinite.setting.parseerror", e.message).string)
            return 0
        }
    }

    private fun addRemoveFeatureSetting(
        context: CommandContext<*>,
        isAdd: Boolean,
    ): Int {
        val categoryName = StringArgumentType.getString(context, "category")
        val featureName = StringArgumentType.getString(context, "name")
        val settingKey = StringArgumentType.getString(context, "key")
        val value = StringArgumentType.getString(context, "value")

        val feature = searchFeature(categoryName, featureName)
        if (feature == null) {
            error(Text.translatable("command.infinite.feature.notfound", categoryName, featureName).string)
            return 0
        }

        val setting = feature.getSetting(settingKey)
        if (setting == null) {
            error(Text.translatable("command.infinite.setting.notfound", featureName, settingKey).string)
            return 0
        }

        if (setting is FeatureSetting.StringListSetting || setting is FeatureSetting.BlockListSetting) {
            @Suppress("UNCHECKED_CAST")
            val listSetting = setting as FeatureSetting<MutableList<String>>
            val currentList = listSetting.value

            if (isAdd) {
                if (currentList.contains(value)) {
                    warn(Text.translatable("command.infinite.setting.list.alreadyadded", value, settingKey).string)
                    return 0
                }
                currentList.add(value)
                info(Text.translatable("command.infinite.setting.list.added", value, settingKey).string)
            } else {
                if (!currentList.contains(value)) {
                    warn(Text.translatable("command.infinite.setting.list.notexist", value, settingKey).string)
                    return 0
                }
                currentList.remove(value)
                info(Text.translatable("command.infinite.setting.list.removed", value, settingKey).string)
            }
            return 1
        } else {
            error(Text.translatable("command.infinite.setting.list.notlist", settingKey).string)
            return 0
        }
    }

    private fun getFeatureStatus(context: CommandContext<*>): Int {
        val categoryName = StringArgumentType.getString(context, "category")
        val featureName = StringArgumentType.getString(context, "name")
        val feature = searchFeature(categoryName, featureName)
        if (feature == null) {
            error(Text.translatable("command.infinite.feature.notfound", categoryName, featureName).string)
            return 0
        }
        val status =
            if (feature.isEnabled()) {
                "${Formatting.GREEN}" + Text.translatable("command.infinite.action.enabled").string
            } else {
                "${Formatting.RED}" +
                    Text.translatable("command.infinite.action.disabled").string
            }
        info(Text.translatable("command.infinite.feature.status", featureName, status).string)
        val settingCount = feature.settings.size
        if (settingCount > 0) {
            log(Text.translatable("command.infinite.setting.list.header", settingCount).string)
            feature.settings.forEach { setting ->
                val valueStr = setting.value.toString()
                val typeStr = setting.value::class.simpleName
                log(" - ${setting.name}: $valueStr ($typeStr)")
            }
            log(Text.translatable("command.infinite.setting.list.footer").string)
        }
        return 1
    }
}
