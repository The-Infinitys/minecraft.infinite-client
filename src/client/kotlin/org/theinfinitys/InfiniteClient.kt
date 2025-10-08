package org.theinfinitys

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.ColorHelper
import org.slf4j.LoggerFactory
import org.theinfinitys.infinite.InfiniteCommand
import org.theinfinitys.infinite.InfiniteKeyBind
import org.theinfinitys.utils.Translation

object InfiniteClient : ClientModInitializer {
    private val LOGGER = LoggerFactory.getLogger("InfiniteClient")

    override fun onInitializeClient() {
        Translation.load()
        println("[InfiniteClient] Translation system loaded.")

        InfiniteKeyBind.registerKeybindings()

        for (category in featureCategories) {
            for (features in category.features) {
                (features.instance as? ConfigurableFeature)?.start()
            }
        }

        // --- Event: when player joins a world ---
        ClientPlayConnectionEvents.JOIN.register { _, _, _ ->
            ConfigManager.loadConfig()

            val modContainer = FabricLoader.getInstance().getModContainer("infinite")
            val modVersion = modContainer.map { it.metadata.version.friendlyString }.orElse("unknown")

            log("version $modVersion")
            log("Mod initialized successfully.")
        }

        // --- Event: when player leaves a world ---
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            for (category in featureCategories) {
                for (features in category.features) {
                    (features.instance as? ConfigurableFeature)?.stop()
                }
            }
            ConfigManager.saveConfig()
        }

        // --- Commands ---
        ClientCommandRegistrationCallback.EVENT.register(InfiniteCommand::registerCommands)
    }

    // ============================
    // ===== Utility Functions ====
    // ============================

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
        textColor: Formatting,
    ): MutableText =
        Text
            .literal("[")
            .formatted(Formatting.BOLD)
            .append(rainbowText("Infinite Client").formatted(Formatting.BOLD))
            .append(Text.literal(prefixType).formatted(Formatting.RESET).formatted(textColor))
            .append(Text.literal("]: ").formatted(Formatting.RESET))

    fun log(text: String) {
        LOGGER.info("[Infinite Client]: $text")
        val message =
            createPrefixedMessage("", Formatting.RESET)
                .append(Text.literal(text).formatted(Formatting.RESET))
        MinecraftClient.getInstance().player?.sendMessage(message, false)
    }

    fun info(text: String) {
        LOGGER.info("[Infinite Client - Info]: $text")
        val message =
            createPrefixedMessage(" - Info ", Formatting.AQUA)
                .append(Text.literal(text).formatted(Formatting.AQUA))
        MinecraftClient.getInstance().player?.sendMessage(message, false)
    }

    fun warn(text: String) {
        LOGGER.warn("[Infinite Client - Warn]: $text")
        val message =
            createPrefixedMessage(" - Warn ", Formatting.YELLOW)
                .append(Text.literal(text).formatted(Formatting.YELLOW))
        MinecraftClient.getInstance().player?.sendMessage(message, false)
    }

    fun error(text: String) {
        LOGGER.error("[Infinite Client - Error]: $text")
        val message =
            createPrefixedMessage(" - Error", Formatting.RED)
                .append(Text.literal(text).formatted(Formatting.RED))
        MinecraftClient.getInstance().player?.sendMessage(message, false)
    }

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
            ?.instance as? ConfigurableFeature

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
}
