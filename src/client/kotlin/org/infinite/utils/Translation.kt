package org.infinite.utils

import org.infinite.utils.translation.LanguageLoader

object Translation {
    fun load() {
        LanguageLoader.load()
    }

    fun t(key: String): String = LanguageLoader.translate(key)
}
