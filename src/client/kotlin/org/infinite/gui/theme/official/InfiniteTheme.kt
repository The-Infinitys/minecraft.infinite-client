package org.infinite.gui.theme.official

import net.minecraft.util.Identifier
import org.infinite.gui.theme.Theme
import org.infinite.gui.theme.ThemeColors
import org.infinite.utils.rendering.getRainbowColor
import java.awt.Color as AwtColor

class InfiniteTheme : Theme("infinite", InfiniteColor(), Identifier.of("infinite", "icon.png"))

class InfiniteColor : ThemeColors() {
    override val backgroundColor: Int
        get() = 0xFF000000.toInt()
    override val primaryColor: Int
        get() = getRainbowColor()

    override fun panelColor(
        index: Int,
        length: Int,
        normalizedZ: Float,
    ): Int {
        val baseHue = (index.toFloat() / length) % 1.0f
        val baseColor = AwtColor.HSBtoRGB(baseHue, 0.8f, 0.9f)
        val r = (baseColor shr 16) and 0xFF
        val g = (baseColor shr 8) and 0xFF
        val b = baseColor and 0xFF
        val hsb = AwtColor.RGBtoHSB(r, g, b, null)
        val distanceFactor = 1 - normalizedZ
        hsb[1] *= distanceFactor
        hsb[2] *= distanceFactor
        val newRGB = AwtColor.HSBtoRGB(hsb[0], hsb[1], hsb[2])
        val borderAlpha = (255 * distanceFactor).toInt()
        return (borderAlpha shl 24) or (newRGB and 0x00FFFFFF)
    }
}
