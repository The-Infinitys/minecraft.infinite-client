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
import net.minecraft.registry.Registries
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
                                        getSettingKeyArgument(dynamicLookup = true)
                                            .executes { context ->
                                                resetConfig(
                                                    context,
                                                )
                                            }.then(
                                                getSettingValueArgument(dynamicLookup = true).executes { context ->
                                                    resetConfig(
                                                        context,
                                                    )
                                                },
                                            ),
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
                                getSettingKeyArgument(feature.instance)
                                    .then(
                                        getSettingValueArgument(feature.instance)
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
                                getSettingKeyArgument(feature.instance)
                                    .then(
                                        getSettingValueArgument(feature.instance)
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
                                getSettingKeyArgument(feature.instance)
                                    .then(
                                        getSettingValueArgument(feature.instance)
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
        ConfigManager.saveConfig()
        info(Text.translatable("command.infinite.theme.changed", theme.name).string)
        return 1
    }

    /*
     * 引数定義関数
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
                featureCategories.forEach { category ->
                    category.features.forEach { feature ->
                        feature.instance.let { configurableFeature ->
                            configurableFeature.reset()
                            configurableFeature.settings.forEach { setting ->
                                setting.reset()
                            }
                        }
                    }
                }
                info(Text.translatable("command.infinite.config.reset.all").string)
            }

            featureName == null -> {
                val category = featureCategories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
                if (category == null) {
                    error(Text.translatable("command.infinite.category.notfound", categoryName).string)
                    return 0
                }
                category.features.forEach { feature ->
                    feature.instance.let { configurableFeature ->
                        configurableFeature.reset()
                        configurableFeature.settings.forEach { setting ->
                            setting.reset()
                        }
                    }
                }
                info(Text.translatable("command.infinite.config.reset.category", categoryName).string)
            }

            settingKey == null -> {
                val feature = searchFeature(categoryName, featureName)
                if (feature == null) {
                    error(Text.translatable("command.infinite.feature.notfound", categoryName, featureName).string)
                    return 0
                }
                feature.reset()
                feature.settings.forEach { setting ->
                    setting.reset()
                }
                info(Text.translatable("command.infinite.config.reset.feature", featureName).string)
            }

            else -> {
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
     * 引数定義関数
     */

    private fun getCategoryArgument() =
        ClientCommandManager.argument("category", StringArgumentType.word()).suggests(getCategorySuggestions())

    private fun getFeatureNameArgument() =
        ClientCommandManager.argument("name", StringArgumentType.word()).suggests(getFeatureNameSuggestions())

    private fun getSettingKeyArgument(
        feature: ConfigurableFeature? = null,
        dynamicLookup: Boolean = false,
    ) = ClientCommandManager
        .argument("key", StringArgumentType.word())
        .suggests(getSettingKeySuggestions(feature, dynamicLookup))

    private fun getSettingValueArgument(
        feature: ConfigurableFeature? = null,
        dynamicLookup: Boolean = false,
    ) = ClientCommandManager
        .argument("value", StringArgumentType.greedyString())
        .suggests(getSettingValueSuggestions(feature, dynamicLookup))

    /*
     * サジェスト関数
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

    private fun getSettingKeySuggestions(
        feature: ConfigurableFeature? = null,
        dynamicLookup: Boolean = false,
    ): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { context, builder ->
            try {
                val targetFeature =
                    when {
                        feature != null -> feature
                        dynamicLookup -> {
                            val categoryName = StringArgumentType.getString(context, "category")
                            val featureName = StringArgumentType.getString(context, "name")
                            searchFeature(categoryName, featureName)
                        }

                        else -> null
                    }

                if (targetFeature != null) {
                    // コマンドパスから add/del/set を判定
                    val commandNodeNames = context.nodes.map { it.node.name }
                    val isAddCommand = commandNodeNames.contains("add")
                    val isDelCommand = commandNodeNames.contains("del")
                    val isSetCommand = commandNodeNames.contains("set")
                    val isListCommand = isAddCommand || isDelCommand

                    // サジェストする設定キーのリストをフィルタリング
                    val filteredSettings =
                        targetFeature.settings.filter { setting ->
                            val isListSetting =
                                when (setting) {
                                    is FeatureSetting.StringListSetting,
                                    is FeatureSetting.BlockListSetting,
                                    is FeatureSetting.EntityListSetting,
                                    is FeatureSetting.PlayerListSetting,
                                    -> true

                                    else -> false
                                }

                            when {
                                isListCommand -> {
                                    // add/del の場合: リスト設定のみを表示
                                    isListSetting
                                }

                                isSetCommand -> {
                                    // set の場合: 非リスト設定のみを表示
                                    !isListSetting
                                }

                                else -> {
                                    // get/reset の場合: 全ての設定を表示
                                    true
                                }
                            }
                        }

                    CommandSource.suggestMatching(
                        filteredSettings.map { it.name },
                        builder,
                    )
                }
            } catch (_: Exception) {
                // Ignore if arguments are not fully present yet
            }
            builder.buildFuture()
        }

    private fun getSettingValueSuggestions(
        feature: ConfigurableFeature? = null,
        dynamicLookup: Boolean = false,
    ): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { context, builder ->
            try {
                val targetFeature =
                    when {
                        feature != null -> feature
                        dynamicLookup -> {
                            val categoryName = StringArgumentType.getString(context, "category")
                            val featureName = StringArgumentType.getString(context, "name")
                            searchFeature(categoryName, featureName)
                        }

                        else -> null
                    }

                if (targetFeature != null) {
                    val settingKey = StringArgumentType.getString(context, "key")
                    val setting = targetFeature.getSetting(settingKey)
                    val rawInput = builder.input.substring(builder.start) // ユーザーの入力プレフィックス

                    if (setting != null) {
                        // コマンドパスから add/del を判定
                        val commandNodeNames = context.nodes.map { it.node.name }
                        val isAddCommand = commandNodeNames.contains("add")
                        val isDelCommand = commandNodeNames.contains("del")
                        val isListCommand = isAddCommand || isDelCommand

                        val suggestions =
                            when (setting) {
                                // リスト設定: ADD/DELの時のみサジェストを生成
                                is FeatureSetting.BlockListSetting -> {
                                    when {
                                        isDelCommand -> {
                                            @Suppress("UNCHECKED_CAST")
                                            (setting.value as? List<String>) ?: emptyList()
                                        }

                                        isAddCommand -> {
                                            Registries.BLOCK.ids.map { it.toString() }
                                        }

                                        else -> emptyList() // set/get/resetの場合は非表示
                                    }
                                }

                                is FeatureSetting.EntityListSetting -> {
                                    when {
                                        isDelCommand -> {
                                            @Suppress("UNCHECKED_CAST")
                                            (setting.value as? List<String>) ?: emptyList()
                                        }

                                        isAddCommand -> {
                                            Registries.ENTITY_TYPE.ids.map { it.toString() }
                                        }

                                        else -> emptyList() // set/get/resetの場合は非表示
                                    }
                                }

                                is FeatureSetting.PlayerListSetting -> {
                                    when {
                                        isDelCommand -> {
                                            @Suppress("UNCHECKED_CAST")
                                            (setting.value as? List<String>) ?: emptyList()
                                        }

                                        isAddCommand -> {
                                            context.source.client.networkHandler
                                                ?.playerList
                                                ?.map { it.profile.name }
                                                ?: emptyList()
                                        }

                                        else -> emptyList() // set/get/resetの場合は非表示
                                    }
                                }

                                is FeatureSetting.StringListSetting -> {
                                    when {
                                        isDelCommand -> {
                                            @Suppress("UNCHECKED_CAST")
                                            (setting.value)
                                        }

                                        isAddCommand -> {
                                            // 自由な文字列リストのため、追加時のサジェストは提供しない
                                            emptyList()
                                        }

                                        else -> emptyList() // set/get/resetの場合は非表示
                                    }
                                }

                                // 非リスト設定: SET/RESETの時のみサジェストを生成
                                is FeatureSetting.BooleanSetting -> {
                                    if (isListCommand) emptyList() else listOf("true", "false")
                                }

                                is FeatureSetting.EnumSetting<*> -> {
                                    if (isListCommand) emptyList() else setting.options.map { it.toString() }
                                }

                                // 数値設定: 範囲サジェスト
                                is FeatureSetting.IntSetting -> {
                                    if (isListCommand) {
                                        emptyList()
                                    } else {
                                        getNumericSuggestions(
                                            setting.min,
                                            setting.max,
                                            setting.defaultValue,
                                            rawInput,
                                        )
                                    }
                                }

                                is FeatureSetting.FloatSetting -> {
                                    if (isListCommand) {
                                        emptyList()
                                    } else {
                                        getNumericSuggestions(
                                            setting.min,
                                            setting.max,
                                            setting.defaultValue,
                                            rawInput,
                                        )
                                    }
                                }

                                is FeatureSetting.DoubleSetting -> {
                                    if (isListCommand) {
                                        emptyList()
                                    } else {
                                        getNumericSuggestions(
                                            setting.min,
                                            setting.max,
                                            setting.defaultValue,
                                            rawInput,
                                        )
                                    }
                                }
                                // その他 (Stringなど): 常にサジェストなし
                                else -> emptyList()
                            }

                        CommandSource.suggestMatching(suggestions, builder)
                    }
                }
            } catch (_: Exception) {
            }
            builder.buildFuture()
        }

    // ヘルパー関数: 数値設定のサジェストを生成
    private fun getNumericSuggestions(
        min: Number,
        max: Number,
        default: Number,
        rawInput: String,
    ): List<String> {
        val suggestions = mutableSetOf<String>()

        // 1. 境界値とデフォルト値を追加
        suggestions.add(min.toString())
        suggestions.add(max.toString())
        suggestions.add(default.toString())

        // 2. プレフィックスが数値の場合、その周辺の値をサジェスト
        if (rawInput.isNotEmpty()) {
            val isInt = min is Int
            try {
                // プレフィックスを数値として解析
                val parsedPrefix: Double = rawInput.toDoubleOrNull() ?: return suggestions.toList()
                val maxDouble = max.toDouble()
                val minDouble = min.toDouble()

                // プレフィックスが範囲内かチェック
                if (parsedPrefix in minDouble..maxDouble) {
                    if (isInt) {
                        // Intの場合: プレフィックスと±1, ±5の整数値をサジェスト
                        val parsedInt = parsedPrefix.toInt()
                        suggestions.add(parsedInt.toString())
                        suggestions.add((parsedInt + 1).coerceAtMost(max.toInt()).toString())
                        suggestions.add((parsedInt - 1).coerceAtLeast(min).toString())
                        suggestions.add((parsedInt + 5).coerceAtMost(max.toInt()).toString())
                        suggestions.add((parsedInt - 5).coerceAtLeast(min).toString())
                    } else {
                        // Float/Doubleの場合: プレフィックスそのものをサジェスト
                        suggestions.add(String.format("%.2f", parsedPrefix).removeSuffix(".00").removeSuffix(".0"))
                        // さらに、0.5刻みの周辺の値をサジェスト
                        val nearUp = (parsedPrefix + 0.5).coerceAtMost(maxDouble)
                        val nearDown = (parsedPrefix - 0.5).coerceAtLeast(minDouble)
                        suggestions.add(String.format("%.2f", nearUp).removeSuffix(".00").removeSuffix(".0"))
                        suggestions.add(String.format("%.2f", nearDown).removeSuffix(".00").removeSuffix(".0"))
                    }
                }
            } catch (_: Exception) {
                // Ignore (already handled by toDoubleOrNull)
            }
        }

        // 3. ユーザーの入力 (rawInput) に合致する、ユニークな値のみを返す
        return suggestions
            .map {
                // IntSettingの場合に .0 を削除 (既にヘルパー関数内で .0 の削除処理をしていますが、念のため)
                if (min is Int) it.removeSuffix(".0") else it
            }.filter { it.startsWith(rawInput, ignoreCase = true) }
            .distinct()
            .toList()
    }

    /*
     * 既存の機能操作関数
     */

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
                    is Enum<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val enumClass = setting.value!!::class.java as Class<out Enum<*>>
                        enumClass.enumConstants.firstOrNull { it.name.equals(rawValue, ignoreCase = true) }
                            ?: throw IllegalArgumentException(Text.translatable("command.infinite.setting.type.enum.notfound").string)
                    }

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

        if (setting is FeatureSetting.StringListSetting || setting is FeatureSetting.BlockListSetting ||
            setting is FeatureSetting.EntityListSetting ||
            setting is FeatureSetting.PlayerListSetting
        ) {
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
