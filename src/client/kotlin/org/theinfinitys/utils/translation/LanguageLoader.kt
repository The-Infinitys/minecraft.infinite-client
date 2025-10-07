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

<<<<<<< HEAD
        for (lang in candidates.distinct()) {
            if (!translations.containsKey(lang)) {
                open(lang)?.use { stream ->
=======
        fun open(lang: String): java.io.InputStream? {
            val stream = javaClass.getResourceAsStream("/assets/infinite/i18n/$lang.json")
            if (stream != null) return stream

            val devFile = java.io.File("src/main/resources/assets/infinite/i18n/$lang.json")
            if (devFile.exists()) {
                println("[Translation] Using dev language file: ${devFile.absolutePath}")
                return devFile.inputStream()
            }

            println("[Translation] Could NOT find language file anywhere: $lang.json")
            return null
        }

        for (lang in languages) {
            open(lang).use { stream ->
                if (stream != null) {
>>>>>>> 126e12847dadb5ae723ac63ab7de2db9aff7d2ee
                    println("[Translation] Loaded language file: $lang.json")
                    InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                        val json = gson.fromJson(reader, JsonObject::class.java)
                        translations[lang] = json
                    }
                } ?: println("[Translation] Could NOT find language file: $lang.json")
            }
        }

<<<<<<< HEAD
        val selected = when {
            translations.containsKey(detected) -> detected
            translations.containsKey("en_us") -> "en_us"
            else -> translations.keys.firstOrNull()
        }
=======
        val detected = detectLanguage()

        val selected =
            when {
                translations.containsKey(detected) -> detected
                translations.containsKey("en_US") -> "en_US"
                translations.isNotEmpty() -> translations.keys.first()
                else -> null
            }
>>>>>>> 126e12847dadb5ae723ac63ab7de2db9aff7d2ee

        currentLangCode = selected ?: "en_us"
        currentLang = selected?.let { translations[it] }

        if (currentLang == null)
            println("[Translation] ⚠ No language files loaded! Check resource paths.")
        else if (currentLangCode != detected)
            println("[Translation] Detected Minecraft lang: $detected → fallback: $currentLangCode")
        else
            println("[Translation] Using Minecraft lang: $currentLangCode")
    }

    /** Detect Minecraft’s current language (Fabric). */
    private fun detectMinecraftLanguage(): String {
        return try {
            val mcLang = MinecraftClient.getInstance().options.language ?: "en_us"
            val normalized = mcLang.replace('-', '_').lowercase(Locale.ROOT)
            println("[Translation] Detected Minecraft language: $normalized")
            normalized
        } catch (e: Exception) {
            println("[Translation] Could not get Minecraft language; defaulting to en_us")
            "en_us"
        }
    }

<<<<<<< HEAD
    /** Open from classpath or dev file. */
    private fun open(lang: String): java.io.InputStream? {
        val classpathPath = "/assets/infinite/i18n/${lang.uppercase(Locale.ROOT)}.json"
        LanguageLoader::class.java.getResourceAsStream(classpathPath)?.let { return it }

        val devFile = java.io.File("src/main/resources/assets/infinite/i18n/${lang.uppercase(Locale.ROOT)}.json")
        if (devFile.exists()) {
            println("[Translation] Using dev file: ${devFile.absolutePath}")
            return devFile.inputStream()
        }

        return null
=======
    private fun detectLanguage(): String {
        val tag = Locale.getDefault().toLanguageTag() // e.g., "en-US", "ja-JP"
        val normalized =
            when (val underscored = tag.replace("-", "_")) {
                "en" -> "en_US"
                else -> underscored
            }
        println("[Translation] Detected system locale: $normalized")
        return normalized
>>>>>>> 126e12847dadb5ae723ac63ab7de2db9aff7d2ee
    }

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

<<<<<<< HEAD
    private fun JsonObject.getCaseInsensitive(name: String): JsonElement? {
        if (this.has(name)) return this[name]
        val lower = name.lowercase(Locale.ROOT)
        if (this.has(lower)) return this[lower]
        val upper = name.uppercase(Locale.ROOT)
        if (this.has(upper)) return this[upper]
        return null
    }

=======
>>>>>>> 126e12847dadb5ae723ac63ab7de2db9aff7d2ee
    fun setLanguage(lang: String): Boolean {
        val json = translations[lang] ?: open(lang)?.use {
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
