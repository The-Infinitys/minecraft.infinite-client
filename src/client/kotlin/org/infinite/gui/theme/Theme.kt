package org.infinite.gui.theme

import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier

open class Theme(
    val name: String,
    val colors: ThemeColors,
    val icon: ThemeIcon?,
) {
    companion object {
        fun default(): Theme = Theme("default", ThemeColors(), null)
    }
}
