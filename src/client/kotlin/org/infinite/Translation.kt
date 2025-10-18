package org.infinite

import net.minecraft.text.Text

object Translation {
    fun t(key: String): String {
        return Text.translatable(key).string
    }
}