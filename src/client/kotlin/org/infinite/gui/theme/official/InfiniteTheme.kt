package org.infinite.gui.theme.official

import net.minecraft.util.Identifier
import org.infinite.gui.theme.Theme
import org.infinite.gui.theme.ThemeColors
import org.infinite.gui.theme.ThemeIcon
import org.infinite.utils.rendering.getRainbowColor
import java.awt.Color as AwtColor

class InfiniteTheme : Theme("infinite", InfiniteColor(), ThemeIcon(Identifier.of("infinite", "icon.png"), 256, 256))

class InfiniteColor : ThemeColors() {
    // 🎨 新しいカラーパレット

    // 背景色: 濃い青
    override val backgroundColor: Int = 0xFF000000.toInt()

    // 前景色: 白
    override val foregroundColor: Int = 0xFFFFFFFF.toInt()

    override val primaryColor: Int
        get() = getRainbowColor()

    // Secondary Color: 純粋なマゼンタ
    override val secondaryColor: Int = 0xFFFF00FF.toInt()

    // アクセントカラーの定義
    // Red: 明るい赤 (エラー色にも使用)
    override val redAccentColor: Int = 0xFFFF0000.toInt()

    // Yellow: 純粋な黄色
    override val yellowAccentColor: Int = 0xFFFFFF00.toInt()

    // Green: 純粋なシアン
    override val greenAccentColor: Int = 0xFF00FFFF.toInt()

    // Aqua/Info: 濃い青紫 (情報色にも使用)
    override val aquaAccentColor: Int = 0xFF0000FF.toInt()

    // ステータス/汎用カラー
    override val errorColor: Int = redAccentColor // エラー
    override val warnColor: Int = 0xFFFF8800.toInt() // 警告: 明るいオレンジ
    override val infoColor: Int = aquaAccentColor // 情報

    // panelColorのロジックは変更せずそのまま残します
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
        val borderAlpha = (255 * distanceFactor).toInt().coerceIn(0, 255)
        return (borderAlpha shl 24) or (newRGB and 0x00FFFFFF)
    }
}
