package org.infinite.gui.theme

import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.resource.ResourceManager
import net.minecraft.util.Identifier

open class Theme(
    val name: String,
    val colors: ThemeColors,
    val icon: ThemeIcon?,
)

class ThemeIcon(
    val identifier: Identifier,
    customWidth: Int? = null,
    customHeight: Int? = null,
) {
    val width: Int
    val height: Int

    init {
        if (customWidth != null && customHeight != null) {
            width = customWidth
            height = customHeight
        } else {
            val mc: MinecraftClient = MinecraftClient.getInstance()
            val resourceManager: ResourceManager = mc.resourceManager
            val resource = resourceManager.getResource(identifier)
            if (resource.isPresent) {
                val iconResource = resource.get()
                val image = NativeImage.read(iconResource.inputStream)
                width = image.width
                height = image.height
            } else {
                width = 256
                height = 256
            }
        }
    }
}

open class ThemeColors {
    // デフォルトで黒（0xFF000000）
    open val backgroundColor: Int = 0xFF000000.toInt()

    open fun panelColor(
        index: Int,
        length: Int,
        normalizedZ: Float,
    ): Int = 0xFFFFFFFF.toInt()

    // デフォルトで白（0xFFFFFFFF） - 背景の黒と対になる色
    open val foregroundColor: Int = 0xFFFFFFFF.toInt()

    // Primary Color: アプリケーションの主要な色
    open val primaryColor: Int = 0xFF6200EE.toInt()

    // Secondary Color: アプリケーションの副次的な色、Floating Action Buttonなどに使われる
    open val secondaryColor: Int = 0xFF03DAC6.toInt()

    // アクセントカラーの定義
    open val redAccentColor: Int = 0xFFB00020.toInt()
    open val yellowAccentColor: Int = 0xFFFFC107.toInt()
    open val greenAccentColor: Int = 0xFF4CAF50.toInt()
    open val aquaAccentColor: Int = 0xFF00BCD4.toInt()

    // その他の一般的なカラープロパティを追加
    open val errorColor: Int = redAccentColor // エラーメッセージなどに使う色
    open val warnColor: Int = yellowAccentColor // カードやシートなどの表面の色
    open val infoColor: Int = aquaAccentColor
}
