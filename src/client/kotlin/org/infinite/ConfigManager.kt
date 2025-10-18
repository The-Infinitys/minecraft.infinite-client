package org.infinite

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.util.WorldSavePath
import org.infinite.settings.FeatureSetting
import java.nio.file.Path

object ConfigManager {
    private val json = Json { prettyPrint = true }

    @Serializable
    data class FeatureConfig(
        val nameKey: String,
        val enabled: Boolean,
        val settings: Map<String, JsonElement>,
    )

    @Serializable
    data class AppConfig(
        val features: List<FeatureConfig>,
    )

    // Function to get config directory based on server type
    private fun getConfigDirectory(): Path {
        val gameDir = FabricLoader.getInstance().gameDir
        val configDir = gameDir.resolve("infinite").resolve("config")

        // Determine if single player or multiplayer
        val client = MinecraftClient.getInstance()
        val isSinglePlayer = client.isIntegratedServerRunning // Check if integrated server is running

        val serverName =
            if (isSinglePlayer) {
                client.server
                    ?.getSavePath(WorldSavePath.ROOT)
                    ?.parent
                    ?.fileName
                    ?.toString() ?: "single_player_world" // Use world name for single player
            } else {
                client.currentServerEntry?.address ?: "multi_player_server" // Use server address for multiplayer
            }

        return if (isSinglePlayer) {
            configDir.resolve("single_player").resolve(serverName)
        } else {
            configDir.resolve("multi_player").resolve(serverName)
        }
    }

    fun saveConfig() {
        val configDir = getConfigDirectory().toFile()
        if (!configDir.exists()) {
            configDir.mkdirs()
        }

        val configFile = configDir.resolve("config.json")
        val featureConfigs =
            featureCategories
                .flatMap { category ->
                    category.features.map { feature ->
                        val configurableFeature = feature.instance
                        run {
                            val settingMap: Map<String, JsonElement> =
                                configurableFeature.settings.associate { setting ->
                                    (
                                        setting.name to
                                            when (setting) {
                                                is FeatureSetting.BooleanSetting -> JsonPrimitive(setting.value)
                                                is FeatureSetting.IntSetting -> JsonPrimitive(setting.value)
                                                is FeatureSetting.FloatSetting -> JsonPrimitive(setting.value)
                                                is FeatureSetting.StringSetting -> JsonPrimitive(setting.value)
                                                is FeatureSetting.StringListSetting ->
                                                    JsonArray(setting.value.map { JsonPrimitive(it) })

                                                is FeatureSetting.EnumSetting<*> -> JsonPrimitive(setting.value.name)
                                                is FeatureSetting.BlockIDSetting -> JsonPrimitive(setting.value)
                                                is FeatureSetting.EntityIDSetting -> JsonPrimitive(setting.value)
                                                is FeatureSetting.BlockListSetting ->
                                                    JsonArray(setting.value.map { JsonPrimitive(it) })

                                                is FeatureSetting.EntityListSetting ->
                                                    JsonArray(setting.value.map { JsonPrimitive(it) })

                                                is FeatureSetting.PlayerListSetting ->
                                                    JsonArray(setting.value.map { JsonPrimitive(it) })
                                            }
                                    )
                                }
                            FeatureConfig(feature.name, configurableFeature.isEnabled(), settingMap)
                        }
                    }
                }

        val appConfig = AppConfig(featureConfigs)
        val jsonString = json.encodeToString(AppConfig.serializer(), appConfig)
        configFile.writeText(jsonString)
        InfiniteClient.log("Configuration saved to ${configFile.absolutePath}")
    }

// ... (omitted preceding code)

    fun loadConfig() {
        val configDir = getConfigDirectory().toFile()
        val configFile = configDir.resolve("config.json")

        if (!configFile.exists()) {
            InfiniteClient.warn("Configuration file not found: ${configFile.absolutePath}")
            return
        }

        try {
            val jsonString = configFile.readText()
            val appConfig = json.decodeFromString(AppConfig.serializer(), jsonString)

            appConfig.features.forEach { featureConfig ->
                featureCategories.flatMap { it.features }.find { it.name == featureConfig.nameKey }?.let { feature ->
                    val configurableFeature = feature.instance
                    if (featureConfig.enabled) {
                        configurableFeature.enable()
                    } else {
                        configurableFeature.disable()
                    }
                    featureConfig.settings.forEach { (settingName, jsonElement) ->
                        configurableFeature.settings.find { it.name == settingName }?.let { setting ->
                            when (setting) {
                                is FeatureSetting.BooleanSetting ->
                                    setting.value =
                                        json.decodeFromJsonElement(jsonElement)

                                is FeatureSetting.IntSetting ->
                                    setting.value =
                                        json.decodeFromJsonElement(jsonElement)

                                is FeatureSetting.FloatSetting ->
                                    setting.value =
                                        json.decodeFromJsonElement(jsonElement)

                                is FeatureSetting.StringSetting ->
                                    setting.value =
                                        json.decodeFromJsonElement(jsonElement)

                                is FeatureSetting.StringListSetting ->
                                    setting.value =
                                        json
                                            .decodeFromJsonElement<List<String>>(jsonElement)
                                            .toMutableList() // **Changed to MutableList**

                                // 🚀 **追加されたBlockIDSettingとEntityIDSettingの処理**
                                is FeatureSetting.BlockIDSetting ->
                                    setting.value =
                                        json.decodeFromJsonElement(jsonElement)

                                is FeatureSetting.EntityIDSetting ->
                                    setting.value =
                                        json.decodeFromJsonElement(jsonElement)

                                // 🚀 **追加されたBlockListSettingの処理**
                                is FeatureSetting.BlockListSetting ->
                                    setting.value =
                                        json.decodeFromJsonElement<List<String>>(jsonElement).toMutableList()
                                is FeatureSetting.EntityListSetting ->
                                    setting.value =
                                        json.decodeFromJsonElement<List<String>>(jsonElement).toMutableList()
                                is FeatureSetting.PlayerListSetting ->
                                    setting.value =
                                        json.decodeFromJsonElement<List<String>>(jsonElement).toMutableList()

                                is FeatureSetting.EnumSetting<*> -> {
                                    val enumName = json.decodeFromJsonElement<String>(jsonElement)
                                    val enumClass = setting.options.first().javaClass
                                    val enumEntry =
                                        enumClass.enumConstants.firstOrNull { (it as Enum<*>).name == enumName }
                                    if (enumEntry != null) {
                                        @Suppress("UNCHECKED_CAST")
                                        (setting as FeatureSetting.EnumSetting<Enum<*>>).value = enumEntry
                                    }
                                }
                            }
                        }
                    }
                }
            }
            InfiniteClient.log("Configuration loaded from ${configFile.absolutePath}")
        } catch (e: Exception) {
            InfiniteClient.error("Failed to load configuration: ${e.message}")
            e.printStackTrace()
        }
    }
}
