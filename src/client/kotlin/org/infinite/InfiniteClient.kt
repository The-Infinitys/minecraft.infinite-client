package org.infinite

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import net.minecraft.util.math.ColorHelper
import org.infinite.feature.ConfigurableFeature
import org.infinite.features.FeatureCategory
import org.infinite.features.automatic.AutomaticFeatureCategory
import org.infinite.features.fighting.FightingFeatureCategory
import org.infinite.features.movement.MovementFeatureCategory
import org.infinite.features.rendering.RenderingFeatureCategory
import org.infinite.features.rendering.font.HyperTextRenderer
import org.infinite.features.server.ServerFeatureCategory
import org.infinite.features.utils.UtilsFeatureCategory
import org.infinite.global.GlobalFeatureCategory
import org.infinite.global.rendering.GlobalRenderingFeatureCategory
import org.infinite.global.rendering.theme.ThemeSetting
import org.infinite.gui.theme.Theme
import org.infinite.gui.theme.ThemeColors
import org.infinite.gui.theme.official.officialThemes
import org.infinite.libs.ai.AiInterface
import org.infinite.libs.client.async.AsyncInterface
import org.infinite.libs.client.control.ControllerInterface
import org.infinite.libs.client.player.PlayerStatsManager
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.infinite.InfiniteAddon
import org.infinite.libs.infinite.InfiniteCommand
import org.infinite.libs.infinite.InfiniteKeyBind
import org.infinite.libs.world.WorldManager
import org.infinite.utils.LogQueue

object InfiniteClient : ClientModInitializer {
    val featureCategories: MutableList<FeatureCategory> =
        mutableListOf(
            MovementFeatureCategory(),
            RenderingFeatureCategory(),
            FightingFeatureCategory(),
            AutomaticFeatureCategory(),
            ServerFeatureCategory(),
            UtilsFeatureCategory(),
        )
    val globalFeatureCategories: MutableList<GlobalFeatureCategory> =
        mutableListOf(
            GlobalRenderingFeatureCategory(),
        )
    lateinit var worldManager: WorldManager
    var themes: List<Theme> = listOf()
    var currentTheme: String = "infinite"
    var loadedAddons: MutableList<InfiniteAddon> = mutableListOf()
    var loadedThemes: MutableList<Theme> = mutableListOf()
    private val addonFeatureMap: MutableMap<InfiniteAddon, List<FeatureCategory>> = mutableMapOf()
    private val featureInstances: MutableMap<Class<out ConfigurableFeature>, ConfigurableFeature> = mutableMapOf()
    private val globalFeatureInstances: MutableMap<Class<out ConfigurableFeature>, ConfigurableFeature> = mutableMapOf()

    object DefaultThemeColors : ThemeColors() {
        override val primaryColor: Int = 0xFF55FFFF.toInt() // Cyan-like
        override val secondaryColor: Int = 0xFF33AAFF.toInt() // Lighter Blue
        override val greenAccentColor: Int = 0xFF00FF00.toInt() // Green
        override val redAccentColor: Int = 0xFFFF0000.toInt() // Red
        override val foregroundColor: Int = 0xFFFFFFFF.toInt() // White
        override val backgroundColor: Int = 0xFF222222.toInt() // Dark Gray
        override val infoColor: Int = 0xFF00FFFF.toInt() // Cyan
        override val warnColor: Int = 0xFFFFAA00.toInt() // Orange
        override val errorColor: Int = 0xFFFF5555.toInt() // Reddish-Orange
    }

    fun theme(name: String = currentTheme): Theme = themes.find { it.name == name } ?: Theme.default()

    fun getCurrentColors(): ThemeColors {
        val themeSetting = getGlobalFeature(ThemeSetting::class.java)
        return if (themeSetting?.isEnabled() == true) {
            theme().colors
        } else {
            DefaultThemeColors
        }
    }

    private fun checkTranslations(): List<String> {
        val result = mutableListOf<String>()
        for (category in featureCategories) {
            for (feature in category.features) {
                val key = feature.generateKey(category.name)
                if (Text.translatable(key).string == key) {
                    result.add(key)
                }
                for (setting in feature.instance.settings) {
                    val key = setting.generateKey(category.name, feature.name, setting.name)
                    if (Text.translatable(key).string == key) {
                        result.add(key)
                    }
                }
            }
        }
        for (category in globalFeatureCategories) {
            for (feature in category.features) {
                val key = feature.descriptionKey
                if (Text.translatable(key).string == key) {
                    result.add(key)
                }
                for (setting in feature.instance.settings) {
                    val key = setting.generateKey(category.name, feature.name, setting.name)
                    if (Text.translatable(key).string == key) {
                        result.add(key)
                    }
                }
            }
        }
        return result
    }

    private fun loadAddons() {
        if (!hasLoadedAddons) {
            hasLoadedAddons = true
            for (addon in loadedAddons) { // Addon initialize
                log("Loading addon: ${addon.id} v${addon.version}")
                val providedCategories = addon.features
                addonFeatureMap[addon] = providedCategories // Store provided categories
                for (addonCategory in providedCategories) {
                    val existingCategory = featureCategories.find { it.name == addonCategory.name }
                    if (existingCategory != null) {
                        // Merge features into existing category
                        existingCategory.features.addAll(addonCategory.features)
                    } else {
                        // Add new category
                        featureCategories.add(addonCategory)
                    }
                }
                loadedThemes += addon.themes
                addon.onInitialize()
            }
        }
        updateFeatureInstances()
    }

    var hasLoadedAddons = false

    override fun onInitializeClient() {
        LogQueue.registerTickEvent()
        AsyncInterface.init()
        updateFeatureInstances()
        InfiniteKeyBind.registerKeybindings()
        ClientLifecycleEvents.CLIENT_STARTED.register { _ ->
            loadAddons() // ここで loadedThemes が更新される
            themes = officialThemes + loadedThemes // officialThemesとloadedThemesを結合
            ConfigManager.loadGlobalConfig()
            for (globalFeatureCategory in globalFeatureCategories) {
                for (globalFeature in globalFeatureCategory.features) {
                    globalFeature.generateKey(globalFeatureCategory.name)
                    val feature = globalFeature.instance
                    for (setting in feature.settings) {
                        setting.generateKey(globalFeatureCategory.name, globalFeature.name, setting.name)
                    }
                    feature.onInit()
                }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            for (globalFeatureCategory in globalFeatureCategories) {
                for (globalFeature in globalFeatureCategory.features) {
                    val feature = globalFeature.instance
                    if (feature.tickTiming == ConfigurableFeature.Timing.End) {
                        feature.onTick()
                    }
                }
            }
        }
        ClientTickEvents.START_CLIENT_TICK.register { _ ->
            for (globalFeatureCategory in globalFeatureCategories) {
                for (globalFeature in globalFeatureCategory.features) {
                    val feature = globalFeature.instance
                    if (feature.tickTiming == ConfigurableFeature.Timing.Start) {
                        feature.onTick()
                    }
                }
            }
        }
        ClientLifecycleEvents.CLIENT_STOPPING.register { _ ->
            ConfigManager.saveGlobalConfig() // Save global config after feature config
            for (globalFeatureCategory in globalFeatureCategories) {
                for (globalFeature in globalFeatureCategory.features) {
                    val feature = globalFeature.instance
                    feature.onShutdown()
                }
            }
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            (MinecraftClient.getInstance().textRenderer as? HyperTextRenderer)?.defineFont(
                HyperTextRenderer.HyperFonts(
                    Identifier.of("minecraft", "infinite_regular"),
                    Identifier.of("minecraft", "infinite_italic"),
                    Identifier.of("minecraft", "infinite_bold"),
                    Identifier.of("minecraft", "infinite_bolditalic"),
                ),
            )
            ConfigManager.loadConfig()
            val modContainer = FabricLoader.getInstance().getModContainer("infinite")
            val modVersion = modContainer.map { it.metadata.version.friendlyString }.orElse("unknown")
            log("version $modVersion")
            val lackedTranslations = checkTranslations() + InfiniteKeyBind.checkTranslations()
            if (lackedTranslations.isEmpty()) {
                log("Mod initialized successfully.")
            } else {
                val translationList = lackedTranslations.joinToString(",") { "\"$it\":\"$it\"" }
                warn("Missing Translations: [$translationList]")
            }
            for (category in featureCategories) {
                for (feature in category.features) {
                    feature.instance.onStart()
                }
            }
        }
        // --- Event: when player leaves a world ---
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ConfigManager.saveConfig()
            (MinecraftClient.getInstance().textRenderer as? HyperTextRenderer)?.disable()
            for (addon in loadedAddons) { // Addon shutdown
                addon.onShutdown()
                addonFeatureMap[addon]?.let { providedCategories ->
                    for (addonCategory in providedCategories) {
                        val existingCategory = featureCategories.find { it.name == addonCategory.name }
                        if (existingCategory != null) {
                            existingCategory.features.removeAll(addonCategory.features.toSet())
                            if (existingCategory.features.isEmpty() && providedCategories.contains(existingCategory)) {
                                featureCategories.remove(existingCategory)
                            }
                        } else {
                            featureCategories.remove(addonCategory)
                        }
                    }
                }
            }
            for (category in featureCategories) {
                for (feature in category.features) {
                    feature.instance.stop()
                }
            }
            AiInterface.clear()
        }
        ServerPlayerEvents.AFTER_RESPAWN.register { _, _, _ ->
            for (category in featureCategories) {
                for (feature in category.features) {
                    if (feature.instance.isEnabled()) {
                        feature.instance.onRespawn()
                    }
                }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { _ -> handleWorldSystem() }
        ClientTickEvents.START_CLIENT_TICK.register { _ -> ControllerInterface.tick() }
        ClientTickEvents.START_CLIENT_TICK.register { _ -> AiInterface.tick() }
        PlayerStatsManager.init()
        ClientCommandRegistrationCallback.EVENT.register(InfiniteCommand::registerCommands)
        worldManager = WorldManager()
        ClientTickEvents.START_CLIENT_TICK.register { _ ->
            for (category in featureCategories) {
                for (feature in category.features) {
                    if (feature.instance.isEnabled() && feature.instance.tickTiming == ConfigurableFeature.Timing.Start) {
                        feature.instance.onTick()
                    }
                }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            for (category in featureCategories) {
                for (feature in category.features) {
                    if (feature.instance.isEnabled() && feature.instance.tickTiming == ConfigurableFeature.Timing.End) {
                        feature.instance.onTick()
                    }
                }
            }
        }
    }

    fun rainbowText(text: String): MutableText {
        val colors =
            intArrayOf(
                0xFFFF0000.toInt(),
                0xFFFFFF00.toInt(),
                0xFF00FF00.toInt(),
                0xFF00FFFF.toInt(),
                0xFF0000FF.toInt(),
                0xFFFF00FF.toInt(),
            )

        val totalLength = text.length
        val rainbowText = Text.empty()

        for (i in text.indices) {
            val progress = i.toFloat() / (totalLength - 1).toFloat()
            val colorIndex = (progress * (colors.size - 1)).toInt()
            val startColor = colors[colorIndex]
            val endColor = if (colorIndex < colors.size - 1) colors[colorIndex + 1] else colors[colorIndex]
            val segmentProgress = (progress * (colors.size - 1)) - colorIndex

            val startR = (startColor shr 16) and 0xFF
            val startG = (startColor shr 8) and 0xFF
            val startB = startColor and 0xFF

            val endR = (endColor shr 16) and 0xFF
            val endG = (endColor shr 8) and 0xFF
            val endB = endColor and 0xFF

            val r = (startR * (1 - segmentProgress) + endR * segmentProgress).toInt()
            val g = (startG * (1 - segmentProgress) + endG * segmentProgress).toInt()
            val b = (startB * (1 - segmentProgress) + endB * segmentProgress).toInt()

            val interpolatedColor = ColorHelper.getArgb(0xFF, r, g, b)

            rainbowText.append(
                Text.literal(text[i].toString()).styled { style ->
                    style.withColor(interpolatedColor)
                },
            )
        }

        return rainbowText
    }

    private fun createPrefixedMessage(
        prefixType: String,
        textColor: Int,
    ): MutableText =
        Text
            .literal("[")
            .formatted(Formatting.BOLD)
            .append(rainbowText("Infinite Client").formatted(Formatting.BOLD))
            .append(Text.literal(prefixType).styled { style -> style.withColor(textColor) })
            .append(Text.literal("]: ").formatted(Formatting.RESET))

    fun log(text: String) {
        val message =
            createPrefixedMessage(
                "",
                theme().colors.foregroundColor,
            ).append(Text.literal(text).styled { style -> style.withColor(theme().colors.foregroundColor) })
        LogQueue.enqueueMessage(message) // キューに追加
    }

    fun log(text: Text) {
        val message = createPrefixedMessage("", theme().colors.foregroundColor).append(text)
        LogQueue.enqueueMessage(message)
    }

    fun info(text: String) {
        val message =
            createPrefixedMessage(
                " - Info ",
                theme().colors.infoColor,
            ).append(Text.literal(text).styled { style -> style.withColor(theme().colors.infoColor) })
        LogQueue.enqueueMessage(message) // キューに追加
    }

    fun warn(text: String) {
        val message =
            createPrefixedMessage(
                " - Warn ",
                theme().colors.warnColor,
            ).append(Text.literal(text).styled { style -> style.withColor(theme().colors.warnColor) })
        LogQueue.enqueueMessage(message) // キューに追加
    }

    fun error(text: String) {
        val message =
            createPrefixedMessage(
                " - Error",
                theme().colors.errorColor,
            ).append(Text.literal(text).styled { style -> style.withColor(theme().colors.errorColor) })
        LogQueue.enqueueMessage(message) // キューに追加
    }

    // フィーチャー関連の関数は変更なし

    fun <T : ConfigurableFeature> getFeature(featureClass: Class<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return featureInstances[featureClass] as? T
    }

    fun <T : ConfigurableFeature> getGlobalFeature(featureClass: Class<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return globalFeatureInstances[featureClass] as? T
    }

    fun searchFeature(
        category: String,
        name: String,
    ): ConfigurableFeature? =
        featureCategories
            .find { it.name.equals(category, ignoreCase = true) }
            ?.features
            ?.find {
                it.name.equals(
                    name,
                    ignoreCase = true,
                )
            }?.instance

    fun searchGlobalFeature(
        category: String,
        name: String,
    ): ConfigurableFeature? =
        globalFeatureCategories
            .find { it.name.equals(category, ignoreCase = true) }
            ?.features
            ?.find {
                it.name.equals(
                    name,
                    ignoreCase = true,
                )
            }?.instance

    fun <T : ConfigurableFeature> isFeatureEnabled(featureClass: Class<T>): Boolean {
        val feature = getFeature(featureClass) ?: return false
        return feature.isEnabled()
    }

    fun <T : ConfigurableFeature> isSettingEnabled(
        featureClass: Class<T>,
        settingName: String,
    ): Boolean {
        val feature = getFeature(featureClass) ?: return false
        if (!feature.isEnabled()) return false
        val setting = feature.getSetting(settingName)
        return setting != null && setting.value is Boolean && setting.value as Boolean
    }

    fun <T : ConfigurableFeature> getSettingFloat(
        featureClass: Class<T>,
        settingName: String,
        defaultValue: Float,
    ): Float {
        val feature = getFeature(featureClass) ?: return defaultValue
        if (!feature.isEnabled()) return defaultValue
        val setting = feature.getSetting(settingName)
        return if (setting != null && setting.value is Float) setting.value as Float else defaultValue
    }

    fun <T : ConfigurableFeature> getSettingInt(
        featureClass: Class<T>,
        settingName: String,
        defaultValue: Int,
    ): Int {
        val feature = getFeature(featureClass) ?: return defaultValue
        if (!feature.isEnabled()) return defaultValue
        val setting = feature.getSetting(settingName)
        return if (setting != null && setting.value is Int) setting.value as Int else defaultValue
    }

    fun handle2dGraphics(
        context: DrawContext,
        tickCounter: RenderTickCounter,
        timing: ConfigurableFeature.Timing,
    ) {
        val graphics2D = Graphics2D(context, tickCounter)
        for (category in featureCategories) {
            for (features in category.features) {
                val feature = features.instance
                if (feature.isEnabled() && feature.render2DTiming == timing) {
                    feature.render2d(graphics2D)
                }
            }
        }
    }

    fun handle3dGraphics(
        allocator: net.minecraft.client.util.ObjectAllocator,
        tickCounter: RenderTickCounter,
        renderBlockOutline: Boolean,
        camera: net.minecraft.client.render.Camera,
        positionMatrix: org.joml.Matrix4f,
        projectionMatrix: org.joml.Matrix4f,
        matrix4f2: org.joml.Matrix4f,
        gpuBufferSlice: com.mojang.blaze3d.buffers.GpuBufferSlice,
        vector4f: org.joml.Vector4f,
        bl: Boolean,
        timing: ConfigurableFeature.Timing,
    ) {
        val graphics3D =
            Graphics3D(
                allocator,
                tickCounter,
                renderBlockOutline,
                camera,
                positionMatrix,
                projectionMatrix,
                matrix4f2,
                gpuBufferSlice,
                vector4f,
                bl,
            )
        for (category in featureCategories) {
            for (features in category.features) {
                val feature = features.instance
                if (feature.isEnabled() && feature.render3DTiming == timing) {
                    feature.render3d(graphics3D)
                }
            }
        }
        graphics3D.render()
    }

    fun handleWorldSystem() {
        val worldChunk = worldManager.queue.removeFirstOrNull() ?: return
        for (category in featureCategories) {
            for (features in category.features) {
                val feature = features.instance
                if (feature.isEnabled()) {
                    feature.handleChunk(worldChunk)
                }
            }
        }
    }

    // InfiniteClient オブジェクト内に追加する
    fun updateFeatureInstances() {
        featureInstances.clear() // 既存のマップをクリア
        for (category in featureCategories) {
            for (feature in category.features) {
                // 新しいインスタンス（または既存のインスタンス）をマップに追加
                featureInstances[feature.instance.javaClass] = feature.instance
            }
        }
        globalFeatureInstances.clear() // 既存のマップをクリア
        for (category in globalFeatureCategories) {
            for (feature in category.features) {
                // 新しいインスタンス（または既存のインスタンス）をマップに追加
                globalFeatureInstances[feature.instance.javaClass] = feature.instance
            }
        }
    }
}
