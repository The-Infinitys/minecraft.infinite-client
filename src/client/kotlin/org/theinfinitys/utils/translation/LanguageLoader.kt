package org.theinfinitys.utils.translation

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

object LanguageLoader {
    private val translations: MutableMap<String, JsonObject> = mutableMapOf()
    private var currentLang: JsonObject? = null
    private var currentLangCode: String = "en_US"
    private val gson = Gson()

    fun load() {
        val languages = listOf("en_US", "ja_JP")

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
                    println("[Translation] Loaded language file: $lang.json")
                    InputStreamReader(stream, StandardCharsets.UTF_8).use { reader ->
                        val json = gson.fromJson(reader, JsonObject::class.java)
                        translations[lang] = json
                    }
                } else {
                    println("[Translation] Could NOT find language file: $lang.json")
                }
            }
        }

        val detected = detectLanguage()

        val selected =
            when {
                translations.containsKey(detected) -> detected
                translations.containsKey("en_US") -> "en_US"
                translations.isNotEmpty() -> translations.keys.first()
                else -> null
            }

        currentLangCode = selected ?: "en_US"
        currentLang = selected?.let { translations[it] }

        if (currentLang == null) {
            println("[Translation] No language files loaded at all! Check resource paths.")
        } else {
            if (currentLangCode != detected) {
                println("[Translation] Detected: $detected, but using fallback: $currentLangCode")
            } else {
                println("[Translation] Using language: $currentLangCode")
            }
        }
    }

    private fun detectLanguage(): String {
        val tag = Locale.getDefault().toLanguageTag() // e.g., "en-US", "ja-JP"
        val normalized =
            when (val underscored = tag.replace("-", "_")) {
                "en" -> "en_US"
                else -> underscored
            }
        println("[Translation] Detected system locale: $normalized")
        return normalized
    }

    fun translate(key: String): String {
        val lang = currentLang ?: return "[Missing translation: $key]"
        val parts = key.split(".")
        var element: JsonElement? = lang

        for (part in parts) {
            element = if (element is JsonObject) element.get(part) else null
            if (element == null) break
        }
        return element?.asString ?: "[Missing translation: $key]"
    }

    fun setLanguage(lang: String): Boolean {
        val json = translations[lang] ?: return false
        currentLang = json
        currentLangCode = lang
        println("[Translation] Switched language to: $lang")
        return true
    }
}
