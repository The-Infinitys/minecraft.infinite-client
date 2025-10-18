package org.theinfinitys.utils

import org.theinfinitys.utils.translation.LanguageLoader

object Translation {
    fun load() {
        LanguageLoader.load()
    }

    fun t(key: String): String = LanguageLoader.translate(key)
}
