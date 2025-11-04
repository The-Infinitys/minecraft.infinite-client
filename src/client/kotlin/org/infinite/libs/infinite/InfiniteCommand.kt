package org.infinite.libs.infinite

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
import org.infinite.Feature
import org.infinite.InfiniteClient
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
                .then(ClientCommandManager.literal("version").executes { getVersion() })
                .then(
                    ClientCommandManager
                        .literal("config")
                        .then(ClientCommandManager.literal("save").executes { saveConfig() })
                        .then(ClientCommandManager.literal("load").executes { loadConfig() })
                        .then(
                            ClientCommandManager
                                .literal("reset")
                                .executes { resetConfig(it) }
                                .then(
                                    getCategoryArgument()
                                        .executes { resetConfig(it) }
                                        .then(
                                            getFeatureNameArgument()
                                                .executes { resetConfig(it) }
                                                .then(
                                                    getSettingKeyArgument()
                                                        .executes { resetConfig(it) }
                                                        .then(getSettingValueArgument().executes { resetConfig(it) }),
                                                ),
                                        ),
                                ),
                        ),
                ).then(
                    ClientCommandManager
                        .literal("theme")
                        .executes { getTheme() }
                        .then(ClientCommandManager.literal("list").executes { getTheme() })
                        .then(
                            ClientCommandManager
                                .literal("set")
                                .then(
                                    ClientCommandManager
                                        .argument("name", StringArgumentType.word())
                                        .suggests(getThemeSuggestions())
                                        .executes { setTheme(it) },
                                ),
                        ),
                )

        val featureRoot = ClientCommandManager.literal("feature")
        featureCategories.forEach { category ->
            val catLiteral = ClientCommandManager.literal(category.name)
            category.features.forEach { feature ->
                val featBuilder = ClientCommandManager.literal(feature.name)

                if (feature.instance.preRegisterCommands.contains("enable")) {
                    featBuilder.then(ClientCommandManager.literal("enable").executes { toggleFeature(category.name, feature.name, true) })
                }
                if (feature.instance.preRegisterCommands.contains("disable")) {
                    featBuilder.then(ClientCommandManager.literal("disable").executes { toggleFeature(category.name, feature.name, false) })
                }
                if (feature.instance.preRegisterCommands.contains("toggle")) {
                    featBuilder.then(ClientCommandManager.literal("toggle").executes { toggleFeatureState(category.name, feature.name) })
                }

                if (feature.instance.preRegisterCommands.contains("set")) {
                    featBuilder.then(
                        ClientCommandManager
                            .literal("set")
                            .then(
                                getSettingKeyArgument(feature.instance)
                                    .then(
                                        getSettingValueArgument(feature.instance)
                                            .executes { setFeatureSetting(it, category.name, feature.name) },
                                    ),
                            ),
                    )
                }

                if (feature.instance.preRegisterCommands.contains("get")) {
                    featBuilder.then(
                        ClientCommandManager
                            .literal("get")
                            .then(
                                getSettingKeyArgument(feature.instance)
                                    .executes { getFeatureStatus(category.name, feature.name) },
                            ),
                    )
                }

                if (feature.instance.preRegisterCommands.contains("add")) {
                    featBuilder.then(
                        ClientCommandManager
                            .literal("add")
                            .then(
                                getSettingKeyArgument(feature.instance)
                                    .then(
                                        getSettingValueArgument(feature.instance)
                                            .executes { addRemoveFeatureSetting(it, category.name, feature.name, true) },
                                    ),
                            ),
                    )
                }

                if (feature.instance.preRegisterCommands.contains("del")) {
                    featBuilder.then(
                        ClientCommandManager
                            .literal("del")
                            .then(
                                getSettingKeyArgument(feature.instance)
                                    .then(
                                        getSettingValueArgument(feature.instance)
                                            .executes { addRemoveFeatureSetting(it, category.name, feature.name, false) },
                                    ),
                            ),
                    )
                }

                feature.instance.registerCommands(featBuilder)
                catLiteral.then(featBuilder)
            }
            featureRoot.then(catLiteral)
        }
        infiniteCommand.then(featureRoot)
        dispatcher.register(infiniteCommand)

        // === /i ショートカット ===
        registerShortcutCommand(dispatcher)
    }

    // ========================================
    // /i ショートカット（サジェスト完璧）
    // ========================================
    private fun registerShortcutCommand(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        val iCmd = ClientCommandManager.literal("i")
        val featureArg =
            ClientCommandManager
                .argument("feature", StringArgumentType.word())
                .suggests(getAllFeatureSuggestions())

        featureArg.then(ClientCommandManager.literal("enable").executes { runAction(it, "enable") })
        featureArg.then(ClientCommandManager.literal("disable").executes { runAction(it, "disable") })
        featureArg.then(ClientCommandManager.literal("toggle").executes { runAction(it, "toggle") })

        featureArg.then(
            ClientCommandManager
                .literal("get")
                .executes { runAction(it, "get") }
                .then(getIKeyArg().executes { runAction(it, "get") }),
        )

        featureArg.then(
            ClientCommandManager
                .literal("set")
                .then(getIKeyArg().then(getIValueArg().executes { runAction(it, "set") })),
        )

        featureArg.then(
            ClientCommandManager
                .literal("add")
                .then(getIKeyArg().then(getIValueArg().executes { runAction(it, "add") })),
        )

        featureArg.then(
            ClientCommandManager
                .literal("del")
                .then(getIKeyArg().then(getIValueArg().executes { runAction(it, "del") })),
        )

        iCmd.then(featureArg)
        dispatcher.register(iCmd)
    }

    private fun getIKeyArg() = ClientCommandManager.argument("key", StringArgumentType.word()).suggests(getIKeySuggestions())

    private fun getIValueArg() = ClientCommandManager.argument("value", StringArgumentType.greedyString()).suggests(getIValueSuggestions())

    private fun getAllFeatureSuggestions(): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { _, b -> CommandSource.suggestMatching(featureCategories.flatMap { it.features.map { f -> f.name } }, b) }

    private fun getIKeySuggestions(): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { ctx, b ->
            try {
                val name = StringArgumentType.getString(ctx, "feature")
                val feature = findFeature(name)?.instance ?: return@SuggestionProvider b.buildFuture()
                val isList = ctx.nodes.any { it.node.name in listOf("add", "del") }
                val isSet = ctx.nodes.any { it.node.name == "set" }

                val filtered =
                    feature.settings.filter { s ->
                        val isListSetting =
                            s is FeatureSetting.StringListSetting ||
                                s is FeatureSetting.BlockListSetting ||
                                s is FeatureSetting.EntityListSetting ||
                                s is FeatureSetting.PlayerListSetting
                        when {
                            isList -> isListSetting
                            isSet -> !isListSetting
                            else -> true
                        }
                    }
                CommandSource.suggestMatching(filtered.map { it.name }, b)
            } catch (_: Exception) {
            }
            b.buildFuture()
        }

    private fun getIValueSuggestions(): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { ctx, b ->
            try {
                val name = StringArgumentType.getString(ctx, "feature")
                val feature = findFeature(name)?.instance ?: return@SuggestionProvider b.buildFuture()
                val key = StringArgumentType.getString(ctx, "key")
                val setting = feature.getSetting(key) ?: return@SuggestionProvider b.buildFuture()
                val isAdd = ctx.nodes.any { it.node.name == "add" }
                val isDel = ctx.nodes.any { it.node.name == "del" }

                val suggestions =
                    when (setting) {
                        is FeatureSetting.BlockListSetting ->
                            if (isDel) {
                                setting.value
                            } else if (isAdd) {
                                Registries.BLOCK.ids.map { it.toString() }
                            } else {
                                emptyList()
                            }
                        is FeatureSetting.EntityListSetting ->
                            if (isDel) {
                                setting.value
                            } else if (isAdd) {
                                Registries.ENTITY_TYPE.ids.map { it.toString() }
                            } else {
                                emptyList()
                            }
                        is FeatureSetting.PlayerListSetting ->
                            if (isDel) {
                                setting.value
                            } else if (isAdd) {
                                ctx.source.client.networkHandler
                                    ?.playerList
                                    ?.map { it.profile.name }
                                    ?: emptyList()
                            } else {
                                emptyList()
                            }
                        is FeatureSetting.StringListSetting -> if (isDel) setting.value else emptyList()
                        is FeatureSetting.BooleanSetting -> listOf("true", "false")
                        is FeatureSetting.EnumSetting<*> -> setting.options.map { it.toString() }
                        else -> emptyList()
                    }
                CommandSource.suggestMatching(suggestions, b)
            } catch (_: Exception) {
            }
            b.buildFuture()
        }

    private fun findFeature(name: String): Feature? =
        featureCategories.flatMap { it.features }.find { it.name.equals(name, ignoreCase = true) }

    private fun runAction(
        ctx: CommandContext<FabricClientCommandSource>,
        action: String,
    ): Int {
        val name = StringArgumentType.getString(ctx, "feature")
        val entry = findFeature(name) ?: return sendError("command.infinite.feature.notfound", name)
        if (!entry.instance.preRegisterCommands.contains(action)) {
            return sendError("command.infinite.action.notsupported", action, name)
        }

        val category = featureCategories.find { it.features.contains(entry) }!!
        return when (action) {
            "enable" -> toggleFeature(category.name, entry.name, true)
            "disable" -> toggleFeature(category.name, entry.name, false)
            "toggle" -> toggleFeatureState(category.name, entry.name)
            "get" -> getFeatureStatus(category.name, entry.name)
            "set" -> setFeatureSetting(ctx, category.name, entry.name)
            "add" -> addRemoveFeatureSetting(ctx, category.name, entry.name, true)
            "del" -> addRemoveFeatureSetting(ctx, category.name, entry.name, false)
            else -> 0
        }
    }

    private fun sendError(
        key: String,
        vararg args: Any,
    ): Int {
        InfiniteClient.error(Text.translatable(key, *args).string)
        return 0
    }

    // ========================================
    // 既存関数（簡略化・安全）
    // ========================================
    private fun getTheme(): Int {
        InfiniteClient.info(Text.translatable("command.infinite.theme.current", InfiniteClient.currentTheme).string)
        InfiniteClient.info(Text.translatable("command.infinite.theme.available", InfiniteClient.themes.joinToString { it.name }).string)
        return 1
    }

    private fun getThemeSuggestions(): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { _, b -> CommandSource.suggestMatching(InfiniteClient.themes.map { it.name }, b) }

    private fun setTheme(ctx: CommandContext<FabricClientCommandSource>): Int {
        val name = StringArgumentType.getString(ctx, "name")
        if (InfiniteClient.themes.none { it.name.equals(name, true) }) {
            InfiniteClient.error(Text.translatable("command.infinite.theme.notfound", name).string)
            return 0
        }
        InfiniteClient.currentTheme = name
        ConfigManager.saveConfig()
        InfiniteClient.info(Text.translatable("command.infinite.theme.changed", name).string)
        return 1
    }

    private fun resetConfig(ctx: CommandContext<*>): Int {
        val cat =
            try {
                StringArgumentType.getString(ctx, "category")
            } catch (_: Exception) {
                null
            }
        val feat =
            try {
                StringArgumentType.getString(ctx, "name")
            } catch (_: Exception) {
                null
            }
        val key =
            try {
                StringArgumentType.getString(ctx, "key")
            } catch (_: Exception) {
                null
            }

        when {
            cat == null ->
                featureCategories.forEach { c ->
                    c.features.forEach { f ->
                        f.instance.reset()
                        f.instance.settings.forEach { it.reset() }
                    }
                }
            feat == null ->
                featureCategories.find { it.name == cat }?.features?.forEach { f ->
                    f.instance.reset()
                    f.instance.settings.forEach { it.reset() }
                }
                    ?: return 0
            key == null ->
                InfiniteClient.searchFeature(cat, feat)?.let {
                    it.reset()
                    it.settings.forEach { setting ->

                        setting.reset()
                    }
                } ?: return 0
            else -> InfiniteClient.searchFeature(cat, feat)?.getSetting(key)?.reset() ?: return 0
        }
        InfiniteClient.info(Text.translatable("command.infinite.config.reset.all").string)
        return 1
    }

    private fun getVersion(): Int =
        1.also {
            InfiniteClient.log("version ${FabricLoader.getInstance().getModContainer("infinite").get().metadata.version.friendlyString}")
        }

    private fun saveConfig(): Int =
        1.also {
            ConfigManager.saveConfig()
            InfiniteClient.log(Text.translatable("command.infinite.config.save").string)
        }

    private fun loadConfig(): Int = 1.also { InfiniteClient.log(Text.translatable("command.infinite.config.load").string) }

    private fun toggleFeatureState(
        cat: String,
        feat: String,
    ): Int {
        val f = InfiniteClient.searchFeature(cat, feat) ?: return 0
        f.toggle()
        InfiniteClient.info(
            Text.translatable("command.infinite.feature.toggled", feat, if (f.isEnabled()) "enabled" else "disabled").string,
        )
        return 1
    }

    private fun toggleFeature(
        cat: String,
        feat: String,
        enable: Boolean,
    ): Int {
        val f = InfiniteClient.searchFeature(cat, feat) ?: return 0
        if (f.isEnabled() == enable) return 0
        if (enable) f.enable() else f.disable()
        InfiniteClient.info(Text.translatable("command.infinite.feature.toggled", feat, if (enable) "enabled" else "disabled").string)
        return 1
    }

    private fun setFeatureSetting(
        ctx: CommandContext<FabricClientCommandSource>,
        cat: String,
        feat: String,
    ): Int {
        val key = StringArgumentType.getString(ctx, "key")
        val raw = StringArgumentType.getString(ctx, "value")
        val f = InfiniteClient.searchFeature(cat, feat) ?: return 0
        val s = f.getSetting(key) ?: return 0

        try {
            val value: Any =
                when (s.value) {
                    is Boolean -> raw.toBooleanStrict()
                    is Int -> raw.toInt()
                    is Float -> raw.toFloat()
                    is Double -> raw.toDouble()
                    is String -> raw
                    is List<*> -> raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    is Enum<*> -> (s as FeatureSetting.EnumSetting).options.first { it.name.equals(raw, true) }
                    else -> return 0
                }
            @Suppress("UNCHECKED_CAST")
            (s as FeatureSetting<Any>).value = value
            InfiniteClient.info(Text.translatable("command.infinite.setting.changed", feat, key, value).string)
            return 1
        } catch (e: Exception) {
            InfiniteClient.error(Text.translatable("command.infinite.setting.parseerror", e.message).string)
            return 0
        }
    }

    private fun addRemoveFeatureSetting(
        ctx: CommandContext<FabricClientCommandSource>,
        cat: String,
        feat: String,
        add: Boolean,
    ): Int {
        val key = StringArgumentType.getString(ctx, "key")
        val value = StringArgumentType.getString(ctx, "value")
        val f = InfiniteClient.searchFeature(cat, feat) ?: return 0
        val s = f.getSetting(key) ?: return 0

        if (s !is FeatureSetting.StringListSetting && s !is FeatureSetting.BlockListSetting &&
            s !is FeatureSetting.EntityListSetting && s !is FeatureSetting.PlayerListSetting
        ) {
            return 0
        }

        @Suppress("UNCHECKED_CAST")
        val list = s.value as MutableList<String>
        if (add) {
            if (!list.contains(value)) list.add(value)
            InfiniteClient.info(Text.translatable("command.infinite.setting.list.added", value, key).string)
        } else {
            if (list.contains(value)) list.remove(value)
            InfiniteClient.info(Text.translatable("command.infinite.setting.list.removed", value, key).string)
        }
        return 1
    }

    private fun getFeatureStatus(
        cat: String,
        feat: String,
    ): Int {
        val f = InfiniteClient.searchFeature(cat, feat) ?: return 0
        val status = if (f.isEnabled()) "${Formatting.GREEN}enabled" else "${Formatting.RED}disabled"
        InfiniteClient.info(Text.translatable("command.infinite.feature.status", feat, status).string)
        if (f.settings.isNotEmpty()) {
            f.settings.forEach { s -> InfiniteClient.log(" - ${s.name}: ${s.value}") }
        }
        return 1
    }

    // 引数
    private fun getCategoryArgument() =
        ClientCommandManager.argument("category", StringArgumentType.word()).suggests(getCategorySuggestions())

    private fun getFeatureNameArgument() =
        ClientCommandManager.argument("name", StringArgumentType.word()).suggests(getFeatureNameSuggestions())

    private fun getSettingKeyArgument(f: ConfigurableFeature? = null) =
        ClientCommandManager.argument("key", StringArgumentType.word()).suggests(getSettingKeySuggestions(f))

    private fun getSettingValueArgument(f: ConfigurableFeature? = null) =
        ClientCommandManager.argument("value", StringArgumentType.greedyString()).suggests(getSettingValueSuggestions(f))

    private fun getCategorySuggestions(): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { _, b -> CommandSource.suggestMatching(featureCategories.map { it.name }, b) }

    private fun getFeatureNameSuggestions(): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { ctx, b ->
            try {
                val cat = StringArgumentType.getString(ctx, "category")
                val c = featureCategories.find { it.name.equals(cat, true) }
                CommandSource.suggestMatching(c?.features?.map { it.name } ?: emptyList(), b)
            } catch (_: Exception) {
            }
            b.buildFuture()
        }

    private fun getSettingKeySuggestions(f: ConfigurableFeature?): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { ctx, b ->
            val target =
                f ?: run {
                    try {
                        val cat = StringArgumentType.getString(ctx, "category")
                        val name = StringArgumentType.getString(ctx, "name")
                        InfiniteClient.searchFeature(cat, name)
                    } catch (_: Exception) {
                        null
                    }
                } ?: return@SuggestionProvider b.buildFuture()

            val isList = ctx.nodes.any { it.node.name in listOf("add", "del") }
            val filtered =
                target.settings.filter {
                    val list =
                        it is FeatureSetting.StringListSetting || it is FeatureSetting.BlockListSetting ||
                            it is FeatureSetting.EntityListSetting || it is FeatureSetting.PlayerListSetting
                    isList == list
                }
            CommandSource.suggestMatching(filtered.map { it.name }, b)
            b.buildFuture()
        }

    private fun getSettingValueSuggestions(f: ConfigurableFeature?): SuggestionProvider<FabricClientCommandSource> =
        SuggestionProvider { ctx, b ->
            val target =
                f ?: run {
                    try {
                        val cat = StringArgumentType.getString(ctx, "category")
                        val name = StringArgumentType.getString(ctx, "name")
                        InfiniteClient.searchFeature(cat, name)
                    } catch (_: Exception) {
                        null
                    }
                } ?: return@SuggestionProvider b.buildFuture()

            val key = StringArgumentType.getString(ctx, "key")
            val s = target.getSetting(key) ?: return@SuggestionProvider b.buildFuture()
            val isAdd = ctx.nodes.any { it.node.name == "add" }
            val isDel = ctx.nodes.any { it.node.name == "del" }

            val suggestions =
                when (s) {
                    is FeatureSetting.BlockListSetting ->
                        if (isDel) {
                            s.value
                        } else if (isAdd) {
                            Registries.BLOCK.ids.map { it.toString() }
                        } else {
                            emptyList()
                        }
                    is FeatureSetting.EntityListSetting ->
                        if (isDel) {
                            s.value
                        } else if (isAdd) {
                            Registries.ENTITY_TYPE.ids.map { it.toString() }
                        } else {
                            emptyList()
                        }
                    is FeatureSetting.PlayerListSetting ->
                        if (isDel) {
                            s.value
                        } else if (isAdd) {
                            ctx.source.client.networkHandler
                                ?.playerList
                                ?.map { it.profile.name }
                                ?: emptyList()
                        } else {
                            emptyList()
                        }
                    is FeatureSetting.StringListSetting -> if (isDel) s.value else emptyList()
                    is FeatureSetting.BooleanSetting -> listOf("true", "false")
                    is FeatureSetting.EnumSetting<*> -> s.options.map { it.toString() }
                    else -> emptyList()
                }
            CommandSource.suggestMatching(suggestions, b)
            b.buildFuture()
        }
}
