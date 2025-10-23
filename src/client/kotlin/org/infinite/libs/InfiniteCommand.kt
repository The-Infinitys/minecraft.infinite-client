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
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
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
        registry: CommandRegistryAccess,
    ) {
        val infiniteCommand =
            ClientCommandManager
                .literal("infinite")
                // 1. /infinite version
                .then(
                    ClientCommandManager.literal("version").executes { _ -> getVersion() },
                )
                // 2. /infinite config save/load/reset
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
                                        // 💡 修正点: /infinite config reset のサジェストのために動的ルックアップを必要とする
                                        getSettingKeyArgument(dynamicLookup = true).executes { context ->
                                            resetConfig(
                                                context,
                                            )
                                        },
                                    ),
                                ),
                            ),
                        ),
                ).then(
                    ClientCommandManager
                        .literal("theme")
                        .executes { _ -> getTheme() } // /infinite theme
                        .then(
                            ClientCommandManager
                                .literal("list") // /infinite theme list
                                .executes { _ -> getTheme() },
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
                )

        val featureRootLiteral = ClientCommandManager.literal("feature")
        featureCategories.forEach { category ->
            val categoryLiteral = ClientCommandManager.literal(category.name)

            category.features.forEach { feature ->
                val featureBuilder = ClientCommandManager.literal(feature.name)

                // --- フィーチャーの状態トグル ---
                if (feature.instance.preRegisterCommands.contains("enable")) {
                    featureBuilder.then(
                        ClientCommandManager.literal("enable").executes { _ ->
                            toggleFeature(category.name, feature.name, true)
                        },
                    )
                }
                if (feature.instance.preRegisterCommands.contains("disable")) {
                    featureBuilder.then(
                        ClientCommandManager.literal("disable").executes { _ ->
                            toggleFeature(category.name, feature.name, false)
                        },
                    )
                }
                if (feature.instance.preRegisterCommands.contains("toggle")) {
                    featureBuilder.then(
                        ClientCommandManager.literal("toggle").executes { _ ->
                            toggleFeatureState(category.name, feature.name)
                        },
                    )
                }

                // --- セッティング操作: set ---
                if (feature.instance.preRegisterCommands.contains("set")) {
                    featureBuilder.then(
                        ClientCommandManager
                            .literal("set")
                            .then(
                                // 💡 修正点: feature.instance を渡す
                                getSettingKeyArgument(feature.instance)
                                    .then(
                                        getSettingValueArgument()
                                            .executes { context ->
                                                setFeatureSetting(
                                                    context,
                                                    category.name,
                                                    feature.name,
                                                )
                                            },
                                    ),
                            ),
                    )
                }

                // --- セッティング操作: get (status) ---
                if (feature.instance.preRegisterCommands.contains("get")) {
                    featureBuilder.then(
                        ClientCommandManager
                            .literal("get")
                            .then(
                                // 💡 修正点: feature.instance を渡す
                                getSettingKeyArgument(feature.instance)
                                    .executes { _ ->
                                        getFeatureStatus(category.name, feature.name)
                                    },
                            ),
                    )
                }

                // --- セッティング操作: add ---
                if (feature.instance.preRegisterCommands.contains("add")) {
                    featureBuilder.then(
                        ClientCommandManager
                            .literal("add")
                            .then(
                                // 💡 修正点: feature.instance を渡す
                                getSettingKeyArgument(feature.instance)
                                    .then(
                                        getSettingValueArgument()
                                            .executes { context ->
                                                addRemoveFeatureSetting(
                                                    context,
                                                    category.name,
                                                    feature.name,
                                                    true,
                                                )
                                            },
                                    ),
                            ),
                    )
                }

                // --- セッティング操作: del ---
                if (feature.instance.preRegisterCommands.contains("del")) {
                    featureBuilder.then(
                        ClientCommandManager
                            .literal("del")
                            .then(
                                // 💡 修正点: feature.instance を渡す
                                getSettingKeyArgument(feature.instance)
                                    .then(
                                        getSettingValueArgument()
                                            .executes { context ->
                                                addRemoveFeatureSetting(
                                                    context,
                                                    category.name,
                                                    feature.name,
                                                    false,
                                                )
                                            },
                                    ),
                            ),
                    )
                }

                // Allow the feature to register its custom commands as subcommands to its own feature literal
                feature.instance.registerCommands(featureBuilder)

                categoryLiteral.then(featureBuilder)
            }
            featureRootLiteral.then(categoryLiteral)
        }
        infiniteCommand.then(featureRootLiteral)
        dispatcher.register(infiniteCommand)
    }

    private fun getTheme(): Int {
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

    private fun toggleFeatureState(
        categoryName: String,
        featureName: String,
    ): Int {
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
     * 既存の引数定義関数 (変更あり: feature引数とdynamicLookupフラグの追加)
     */

    private fun getCategoryArgument() =
        ClientCommandManager.argument("category", StringArgumentType.word()).suggests(getCategorySuggestions())

    private fun getFeatureNameArgument() =
        ClientCommandManager.argument("name", StringArgumentType.word()).suggests(getFeatureNameSuggestions())

    // 💡 修正: featureインスタンスを直接受け取るか、動的ルックアップを行うフラグを受け取る
    private fun getSettingKeyArgument(
        feature: ConfigurableFeature? = null,
        dynamicLookup: Boolean = false,
    ) = ClientCommandManager
        .argument("key", StringArgumentType.word())
        .suggests(getSettingKeySuggestions(feature, dynamicLookup))

    private fun getSettingValueArgument() = ClientCommandManager.argument("value", StringArgumentType.greedyString())

    /*
     * 既存のサジェスト関数 (変更あり: feature引数とdynamicLookupフラグの追加)
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
            } catch (e: IllegalArgumentException) {
                error("IllegalArgumentException: $e")
            }
            builder.buildFuture()
        }

    // 修正後の getSettingKeySuggestions
    private fun getSettingKeySuggestions(
        feature: ConfigurableFeature? = null,
        dynamicLookup: Boolean = false,
    ): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { context, builder ->
            try {
                val targetFeature =
                    when {
                        feature != null -> feature // Case 1: feature.instanceが直接渡された場合 (feature-specific commands)
                        dynamicLookup -> { // Case 2: dynamicLookupがtrueの場合 (/infinite config reset)
                            // context.parent は使用できないため、context.getArgument() で既に解析された引数を取得する
                            // これらの引数は、/infinite config reset <category> <name> のように、
                            // 現在の <key> に到達する前に定義されているため取得可能
                            val categoryName = StringArgumentType.getString(context, "category")
                            val featureName = StringArgumentType.getString(context, "name")

                            searchFeature(categoryName, featureName)
                        }

                        else -> null
                    }

                if (targetFeature != null) {
                    CommandSource.suggestMatching(
                        targetFeature.settings.map { it.name },
                        builder,
                    )
                }
            } catch (e: Exception) {
                error("Error in getSettingKeySuggestions: ${e.message}")
            }
            builder.buildFuture()
        }

    private fun toggleFeature(
        categoryName: String,
        featureName: String,
        enable: Boolean,
    ): Int {
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

    private fun setFeatureSetting(
        context: CommandContext<FabricClientCommandSource>,
        categoryName: String,
        featureName: String,
    ): Int {
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

                    is Double ->
                        rawValue.toDoubleOrNull()
                            ?: throw IllegalArgumentException(Text.translatable("command.infinite.setting.type.double").string)

                    is String -> rawValue
                    is List<*> -> rawValue.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    else -> throw IllegalStateException(
                        Text
                            .translatable(
                                "command.infinite.setting.type.unsupported",
                                setting.value::class.simpleName,
                            ).string,
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
        context: CommandContext<FabricClientCommandSource>,
        categoryName: String,
        featureName: String,
        isAdd: Boolean,
    ): Int {
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

    private fun getFeatureStatus(
        categoryName: String,
        featureName: String,
    ): Int {
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
