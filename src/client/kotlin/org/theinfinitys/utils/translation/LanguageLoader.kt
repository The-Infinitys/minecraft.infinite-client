package org.theinfinitys.utils.translation

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.client.MinecraftClient
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

object LanguageLoader {
    private val translations: MutableMap<String, JsonObject> = mutableMapOf()
    private var currentLang: JsonObject? = null
    private var currentLangCode: String = "en_us"
    private val gson = Gson()

    fun load() {
        val detected = detectMinecraftLanguage()
        val candidates = listOf(detected, "en_us")

        for (lang in candidates.distinct()) {
            if (!translations.containsKey(lang)) {
                open(lang)?.use { stream ->
                    println("[Translation] Loaded language file: $lang.json")
                    InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                        val json = gson.fromJson(reader, JsonObject::class.java)
                        translations[lang] = json
                    }
                } ?: println("[Translation] Could NOT find language file: $lang.json")
            }
        }

        val selected =
            when {
                translations.containsKey(detected) -> detected
                translations.containsKey("en_us") -> "en_us"
                else -> translations.keys.firstOrNull()
            }

        currentLangCode = selected ?: "en_us"
        currentLang = selected?.let { translations[it] }

        when {
            currentLang == null ->
                println("[Translation] ⚠ No language files loaded! Check resource paths.")
            currentLangCode != detected ->
                println("[Translation] Detected Minecraft lang: $detected → fallback: $currentLangCode")
            else ->
                println("[Translation] Using Minecraft lang: $currentLangCode")
        }
    }

    /**
     * Detect Minecraft's current language more reliably.
     * Uses both Options and LanguageManager, with fallback to en_us.
     */
    private fun detectMinecraftLanguage(): String =
        try {
            val client = MinecraftClient.getInstance()

            // Try options first
            var lang: String? = client.options.language

            // Try LanguageManager (if options isn't ready)
            if (lang.isNullOrEmpty()) {
                val managerLang = client.languageManager?.language
                if (managerLang != null) {
                    // Some MC versions use getCode(), others use code — handle both
                    lang =
                        try {
                            managerLang.javaClass.getMethod("getCode").invoke(managerLang) as? String
                        } catch (_: Exception) {
                            try {
                                managerLang.javaClass.getField("code").get(managerLang) as? String
                            } catch (_: Exception) {
                                null
                            }
                        }
                }
            }

            // Fallback
            if (lang.isNullOrEmpty()) lang = "en_us"

            val normalized = lang.replace('-', '_').lowercase(Locale.ROOT)
            println("[Translation] Detected Minecraft language: $normalized")
            normalized
        } catch (e: Exception) {
            println("[Translation] Could not detect Minecraft language, defaulting to en_us (${e.javaClass.simpleName})")
            "en_us"
        }

    /** Opens the translation file either from classpath or dev resources. */
    private fun open(lang: String): java.io.InputStream? {
        val variants =
            listOf(
                lang.lowercase(Locale.ROOT),
                lang.uppercase(Locale.ROOT),
                lang.replace('-', '_'),
                lang.replace('_', '-'),
            )

        // Try from compiled mod resources
        for (variant in variants) {
            val classpathPath = "/assets/infinite/i18n/$variant.json"
            LanguageLoader::class.java.getResourceAsStream(classpathPath)?.let {
                return it
            }
        }

        // Try from dev resources folder
        for (variant in variants) {
            val devFile = java.io.File("src/main/resources/assets/infinite/i18n/$variant.json")
            if (devFile.exists()) {
                println("[Translation] Using dev file: ${devFile.absolutePath}")
                return devFile.inputStream()
            }
        }

        return null
    }

    /** Retrieves a translation by key, e.g., "fighting.killaura.description". */
    fun translate(key: String): String {
        val lang = currentLang ?: return "[Missing translation: $key]"
        val parts = key.split(".")
        var element: JsonElement? = lang

        for (part in parts) {
            element = (element as? JsonObject)?.getCaseInsensitive(part)
            if (element == null) break
        }

        return element?.asString ?: "[Missing translation: $key]"
    }

    /** Case-insensitive lookup helper. */
    private fun JsonObject.getCaseInsensitive(name: String): JsonElement? {
        if (this.has(name)) return this[name]
        val lower = name.lowercase(Locale.ROOT)
        for ((key, value) in this.entrySet()) {
            if (key.equals(lower, ignoreCase = true)) return value
        }
        return null
    }

    /** Allows switching the language manually in runtime. */
    fun setLanguage(lang: String): Boolean {
        val json =
            translations[lang] ?: open(lang)?.use {
                InputStreamReader(it, StandardCharsets.UTF_8).use { r ->
                    gson.fromJson(r, JsonObject::class.java)
                }
            } ?: return false

        translations[lang] = json
        currentLang = json
        currentLangCode = lang
        println("[Translation] Switched language to: $lang")
        return true
    }
}
