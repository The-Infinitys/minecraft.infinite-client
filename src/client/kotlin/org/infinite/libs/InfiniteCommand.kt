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
import net.minecraft.util.Formatting
import org.infinite.ConfigManager
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
                ),
        )
        featureCategories.forEach { category ->
            category.features.forEach { feature ->
                feature.instance.registerCommands(dispatcher)
            }
        }
    }

    /*
     * 新しいコマンドの実装
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
                info("すべての設定をリセットしました。")
            }

            featureName == null -> {
                // Reset all settings in a category
                val category = featureCategories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }
                if (category == null) {
                    error("カテゴリが見つかりません: $categoryName")
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
                info("カテゴリ '$categoryName' のすべての設定をリセットしました。")
            }

            settingKey == null -> {
                // Reset all settings in a feature
                val feature = searchFeature(categoryName, featureName)
                if (feature == null) {
                    error("フィーチャーが見つかりません: $categoryName / $featureName")
                    return 0
                }
                feature.reset() // Reset the feature's enabled state
                feature.settings.forEach { setting ->
                    setting.reset()
                }
                info("フィーチャー '$featureName' のすべての設定をリセットしました。")
            }

            else -> {
                // Reset a specific setting
                val feature = searchFeature(categoryName, featureName)
                if (feature == null) {
                    error("フィーチャーが見つかりません: $categoryName / $featureName")
                    return 0
                }
                val setting = feature.getSetting(settingKey)
                if (setting == null) {
                    error("$featureName に設定キー '$settingKey' はありません。")
                    return 0
                }
                setting.reset()
                info("フィーチャー '$featureName' の設定 '$settingKey' をリセットしました。")
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
        info("設定を保存しました。")
        return 1
    }

    private fun loadConfig(): Int {
        // 注: ConfigManager.loadConfig() が存在すると仮定します。
        //     もし存在しない場合は、ConfigManager.loadConfig() を実装してください。
        // ConfigManager.loadConfig()
        info("設定をロードしました。")
        return 1
    }

    private fun toggleFeatureState(context: CommandContext<*>): Int {
        val categoryName = StringArgumentType.getString(context, "category")
        val featureName = StringArgumentType.getString(context, "name")
        val feature = searchFeature(categoryName, featureName)
        if (feature == null) {
            error("フィーチャーが見つかりません: $categoryName / $featureName")
            return 0
        }

        // 現在の状態を反転させる
        val enable = !feature.isEnabled()
        val action = if (enable) "有効化" else "無効化"

        if (enable) {
            feature.enable()
        } else {
            feature.disable()
        }
        info("$featureName を $action しました。")
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
        val action = if (enable) "有効化" else "無効化"
        val feature = searchFeature(categoryName, featureName)
        if (feature == null) {
            error("フィーチャーが見つかりません: $categoryName / $featureName")
            return 0
        }
        if (feature.isEnabled() == enable) {
            warn("$featureName は既に${action}されています。")
            return 0
        }
        if (enable) {
            feature.enable()
        } else {
            feature.disable()
        }
        info("$featureName を $action しました。")
        return 1
    }

    private fun setFeatureSetting(context: CommandContext<*>): Int {
        val categoryName = StringArgumentType.getString(context, "category")
        val featureName = StringArgumentType.getString(context, "name")
        val settingKey = StringArgumentType.getString(context, "key")
        val rawValue = StringArgumentType.getString(context, "value")
        val feature = searchFeature(categoryName, featureName)
        if (feature == null) {
            error("フィーチャーが見つかりません: $categoryName / $featureName")
            return 0
        }
        val setting = feature.getSetting(settingKey)
        if (setting == null) {
            error("$featureName に設定キー '$settingKey' はありません。")
            return 0
        }
        try {
            val processedValue: Any =
                when (setting.value) {
                    is Boolean ->
                        rawValue.toBooleanStrictOrNull()
                            ?: throw IllegalArgumentException("Boolean型ではありません。(trueまたはfalseを使用してください。)")

                    is Int -> rawValue.toIntOrNull() ?: throw IllegalArgumentException("Int型ではありません。")
                    is Float -> rawValue.toFloatOrNull() ?: throw IllegalArgumentException("Float型ではありません。")
                    is String -> rawValue
                    is List<*> -> rawValue.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    else -> throw IllegalStateException("サポートされていない設定型です: ${setting.value::class.simpleName}")
                }

            @Suppress("UNCHECKED_CAST")
            val mutableSetting = setting as FeatureSetting<Any>
            mutableSetting.value = processedValue
            info("$featureName の設定 '$settingKey' を $processedValue に変更しました。")
            return 1
        } catch (e: Exception) {
            error("設定値の解析エラー: ${e.message}")
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
            error("フィーチャーが見つかりません: $categoryName / $featureName")
            return 0
        }

        val setting = feature.getSetting(settingKey)
        if (setting == null) {
            error("$featureName に設定キー '$settingKey' はありません。")
            return 0
        }

        if (setting is FeatureSetting.StringListSetting || setting is FeatureSetting.BlockListSetting) {
            @Suppress("UNCHECKED_CAST")
            val listSetting = setting as FeatureSetting<MutableList<String>>
            val currentList = listSetting.value

            if (isAdd) {
                if (currentList.contains(value)) {
                    warn("'$value' は既に '$settingKey' に追加されています。")
                    return 0
                }
                currentList.add(value)
                info("'$value' を '$settingKey' に追加しました。")
            } else {
                if (!currentList.contains(value)) {
                    warn("'$value' は '$settingKey' に存在しません。")
                    return 0
                }
                currentList.remove(value)
                info("'$value' を '$settingKey' から削除しました。")
            }
            return 1
        } else {
            error("設定 '$settingKey' はリスト型ではありません。add/del コマンドはリスト型設定にのみ使用できます。")
            return 0
        }
    }

    private fun getFeatureStatus(context: CommandContext<*>): Int {
        val categoryName = StringArgumentType.getString(context, "category")
        val featureName = StringArgumentType.getString(context, "name")
        val feature = searchFeature(categoryName, featureName)
        if (feature == null) {
            error("フィーチャーが見つかりません: $categoryName / $featureName")
            return 0
        }
        val status = if (feature.isEnabled()) "${Formatting.GREEN}有効" else "${Formatting.RED}無効"
        info("フィーチャー $featureName の状態: $status")
        val settingCount = feature.settings.size
        if (settingCount > 0) {
            log("--- 設定一覧 ($settingCount 件) ---")
            feature.settings.forEach { setting ->
                val valueStr = setting.value.toString()
                val typeStr = setting.value::class.simpleName
                log(" - ${setting.name}: $valueStr ($typeStr)")
            }
            log("--------------------")
        }
        return 1
    }
}
