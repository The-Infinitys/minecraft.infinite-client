package org.infinite

import com.google.gson.Gson
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.ColorHelper
import org.infinite.gui.theme.Theme
import org.infinite.gui.theme.official.CyberTheme
import org.infinite.gui.theme.official.HackerTheme
import org.infinite.gui.theme.official.InfiniteTheme
import org.infinite.gui.theme.official.MinecraftTheme
import org.infinite.gui.theme.official.PastelTheme
import org.infinite.gui.theme.official.SmeClanTheme
import org.infinite.libs.ai.AiInterface
import org.infinite.libs.client.control.ControllerInterface
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.infinite.InfiniteAddon
import org.infinite.libs.infinite.InfiniteCommand
import org.infinite.libs.infinite.InfiniteKeyBind
import org.infinite.libs.world.WorldManager
import org.infinite.utils.LogQueue
import org.slf4j.LoggerFactory
import java.io.InputStreamReader
import java.nio.file.Files

object InfiniteClient : ClientModInitializer {
    private val LOGGER = LoggerFactory.getLogger("InfiniteClient")
    lateinit var worldManager: WorldManager
    var themes: List<Theme> = listOf()
    var currentTheme: String = "infinite"
    var loadedAddons: MutableList<InfiniteAddon> = mutableListOf()

    // Added for addon loading
    private data class ModMetadataPlaceholder(
        val id: String,
        val version: String,
    )

    fun theme(name: String = currentTheme): Theme = themes.find { it.name == name } ?: InfiniteTheme()

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
        return result
    }

    override fun onInitializeClient() {
        LogQueue.registerTickEvent()

        // Addon loading
        val addonsDir =
            FabricLoader
                .getInstance()
                .gameDir
                .resolve("infinite")
                .resolve("addons")
        if (!Files.exists(addonsDir)) {
            Files.createDirectories(addonsDir)
            log("Created addons directory: $addonsDir")
        }

        Files
            .list(addonsDir)
            .filter { it.toString().endsWith(".jar") }
            .forEach { jarPath ->
                log("Found addon JAR: $jarPath")
                try {
                    val classLoader =
                        java.net.URLClassLoader(arrayOf(jarPath.toUri().toURL()), this.javaClass.classLoader)
                    val serviceLoader = java.util.ServiceLoader.load(InfiniteAddon::class.java, classLoader)

                    // Get mod metadata from fabric.mod.json inside the JAR
                    val zipFile = java.util.zip.ZipFile(jarPath.toFile())
                    val entry = zipFile.getEntry("fabric.mod.json")
                    if (entry == null) {
                        warn("Skipping addon $jarPath: fabric.mod.json not found.")
                        return@forEach
                    }
                    val metadataReader = InputStreamReader(zipFile.getInputStream(entry))
                    val gson = Gson()
                    val modMetadata = gson.fromJson(metadataReader, ModMetadataPlaceholder::class.java)
                    metadataReader.close()

                    for (addon in serviceLoader) {
                        log("Loading addon: ${addon.name} v${modMetadata.version}")
                        loadedAddons.add(addon)
                    }
                } catch (e: Exception) {
                    error("Failed to load addon from $jarPath: ${e.message}")
                }
            }

        InfiniteKeyBind.registerKeybindings()
        // --- Event: when player joins a world ---
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            themes =
                listOf(
                    InfiniteTheme(),
                    SmeClanTheme(),
                    HackerTheme(),
                    PastelTheme(),
                    MinecraftTheme(),
                    CyberTheme(),
                )
            ConfigManager.loadConfig()
            for (addon in loadedAddons) { // Addon initialize
                addon.onInitialize()
            }
            for (category in featureCategories) {
                for (features in category.features) {
                    features.instance.start()
                }
            }
            val modContainer = FabricLoader.getInstance().getModContainer("infinite")
            val modVersion = modContainer.map { it.metadata.version.friendlyString }.orElse("unknown")

            log("version $modVersion")
            val lackedTranslations = checkTranslations()
            if (lackedTranslations.isEmpty()) {
                log("Mod initialized successfully.")
            } else {
                val translationList = lackedTranslations.joinToString(",") { "\"$it\":\"$it\"" }
                warn("Missing Translations: [$translationList]")
            }
        }

        // --- Event: when player leaves a world ---
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            ConfigManager.saveConfig()
            for (addon in loadedAddons) { // Addon shutdown
                addon.onShutdown()
            }
            for (category in featureCategories) {
                for (features in category.features) {
                    features.instance.stop()
                }
            }
            AiInterface.clear()
        }
        ServerPlayerEvents.AFTER_RESPAWN.register { _, _, _ ->
            for (category in featureCategories) {
                for (feature in category.features) {
                    if (feature.instance.isEnabled()) {
                        feature.instance.respawn()
                    }
                }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { _ -> handleWorldSystem() }
        ClientTickEvents.START_CLIENT_TICK.register { _ -> ControllerInterface.tick() }
        ClientTickEvents.START_CLIENT_TICK.register { _ -> AiInterface.tick() }
        ClientCommandRegistrationCallback.EVENT.register(InfiniteCommand::registerCommands)
        worldManager = WorldManager()
        ClientTickEvents.START_CLIENT_TICK.register { _ ->
            for (category in featureCategories) {
                for (feature in category.features) {
                    if (feature.instance.isEnabled() && feature.instance.tickTiming ==
                        ConfigurableFeature.TickTiming.Start
                    ) {
                        feature.instance.tick()
                    }
                }
            }
        }
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            for (category in featureCategories) {
                for (feature in category.features) {
                    if (feature.instance.isEnabled() && feature.instance.tickTiming ==
                        ConfigurableFeature.TickTiming.End
                    ) {
                        feature.instance.tick()
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
        LOGGER.info("[Infinite Client]: $text")
        val message =
            createPrefixedMessage("", theme().colors.foregroundColor)
                .append(Text.literal(text).styled { style -> style.withColor(theme().colors.foregroundColor) })
        LogQueue.enqueueMessage(message) // キューに追加
    }

    fun info(text: String) {
        LOGGER.info("[Infinite Client - Info]: $text")
        val message =
            createPrefixedMessage(" - Info ", theme().colors.infoColor)
                .append(Text.literal(text).styled { style -> style.withColor(theme().colors.infoColor) })
        LogQueue.enqueueMessage(message) // キューに追加
    }

    fun warn(text: String) {
        LOGGER.warn("[Infinite Client - Warn]: $text")
        val message =
            createPrefixedMessage(" - Warn ", theme().colors.warnColor)
                .append(Text.literal(text).styled { style -> style.withColor(theme().colors.warnColor) })
        LogQueue.enqueueMessage(message) // キューに追加
    }

    fun error(text: String) {
        LOGGER.error("[Infinite Client - Error]: $text")
        val message =
            createPrefixedMessage(" - Error", theme().colors.errorColor)
                .append(Text.literal(text).styled { style -> style.withColor(theme().colors.errorColor) })
        LogQueue.enqueueMessage(message) // キューに追加
    }

    // フィーチャー関連の関数は変更なし

    fun <T : ConfigurableFeature> getFeature(featureClass: Class<T>): T? {
        for (category in featureCategories) {
            for (feature in category.features) {
                if (featureClass.isInstance(feature.instance)) {
                    @Suppress("UNCHECKED_CAST")
                    return feature.instance as T
                }
            }
        }
        return null
    }

    fun searchFeature(
        category: String,
        name: String,
    ): ConfigurableFeature? =
        featureCategories
            .find { it.name.equals(category, ignoreCase = true) }
            ?.features
            ?.find { it.name.equals(name, ignoreCase = true) }
            ?.instance

    fun <T : ConfigurableFeature> isFeatureEnabled(featureClass: Class<T>): Boolean {
        val feature = getFeature(featureClass)
        return feature != null && feature.isEnabled()
    }

    fun <T : ConfigurableFeature> isSettingEnabled(
        featureClass: Class<T>,
        settingName: String,
    ): Boolean {
        val feature = getFeature(featureClass)
        if (feature == null || !feature.isEnabled()) return false
        val setting = feature.getSetting(settingName)
        return setting != null && setting.value is Boolean && setting.value as Boolean
    }

    fun <T : ConfigurableFeature> getSettingFloat(
        featureClass: Class<T>,
        settingName: String,
        defaultValue: Float,
    ): Float {
        val feature = getFeature(featureClass)
        if (feature == null || !feature.isEnabled()) return defaultValue
        val setting = feature.getSetting(settingName)
        return if (setting != null && setting.value is Float) setting.value as Float else defaultValue
    }

    fun <T : ConfigurableFeature> getSettingInt(
        featureClass: Class<T>,
        settingName: String,
        defaultValue: Int,
    ): Int {
        val feature = getFeature(featureClass)
        if (feature == null || !feature.isEnabled()) return defaultValue
        val setting = feature.getSetting(settingName)
        return if (setting != null && setting.value is Int) setting.value as Int else defaultValue
    }

    fun handle2dGraphics(
        context: DrawContext,
        tickCounter: RenderTickCounter,
    ) {
        val graphics2D = Graphics2D(context, tickCounter)
        for (category in featureCategories) {
            for (features in category.features) {
                val feature = features.instance
                if (feature.isEnabled()) {
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
                if (feature.isEnabled()) {
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
}
