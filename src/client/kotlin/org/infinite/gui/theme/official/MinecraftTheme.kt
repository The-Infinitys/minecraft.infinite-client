package org.infinite.gui.theme.official

import net.minecraft.util.Identifier
import org.infinite.gui.theme.Theme
import org.infinite.gui.theme.ThemeColors
import org.infinite.gui.theme.ThemeIcon

class MinecraftTheme :
    Theme(
        "minecraft",
        MinecraftColor(),
        ThemeIcon(Identifier.of("minecraft", "textures/gui/title/minecraft.png")),
    )

class MinecraftColor : ThemeColors() {
    // 背景色: 濃い土/石の色
    override val backgroundColor: Int = 0xFF363636.toInt()

    // 前景色: 白または薄い黄色（文字の色）
    override val foregroundColor: Int = 0xFFEEEEEE.toInt()

    // Primary Color: クリーパーの緑や草の色
    override val primaryColor: Int = 0xFF55AA55.toInt() // MC Green

    // Secondary Color: 木の板や砂利の色
    override val secondaryColor: Int = 0xFFAA5500.toInt() // MC Brown

    // アクセントカラーの定義
    // Red/Error: 赤石やマグマの色
    override val redAccentColor: Int = 0xFFAA0000.toInt()

    // Yellow/Warn: グロウストーンや金の色
    override val yellowAccentColor: Int = 0xFFFFFF55.toInt()

    // Green: Primaryと同じ草の色
    override val greenAccentColor: Int = primaryColor

    // Aqua/Info: ダイヤモンドの色
    override val aquaAccentColor: Int = 0xFF55FFFF.toInt()

    // ステータス/汎用カラー
    override val errorColor: Int = redAccentColor
    override val warnColor: Int = yellowAccentColor
    override val infoColor: Int = aquaAccentColor

    // パネルの色: MCのボタンやインベントリの灰色
    override fun panelColor(
        index: Int,
        length: Int,
        normalizedZ: Float,
    ): Int {
        // デフォルトのGUIパネルの灰色
        val baseColor = 0xFFC6C6C6.toInt()
        // 深度に応じて暗くする
        val intensityFactor = 1.0f - (normalizedZ * 0.3f) // 最大30%暗くする

        val r = ((baseColor shr 16) and 0xFF) * intensityFactor
        val g = ((baseColor shr 8) and 0xFF) * intensityFactor
        val b = (baseColor and 0xFF) * intensityFactor

        val newRGB = (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()

        // 完全に不透明な灰色で、深度に応じて暗さが変化
        return newRGB
    }
}
