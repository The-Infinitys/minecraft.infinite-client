package org.infinite.gui.theme.official

import org.infinite.gui.theme.Theme
import org.infinite.gui.theme.ThemeColors

class PastelTheme : Theme("pastel", PastelColor(), null)

class PastelColor : ThemeColors() {
    // 背景色: 薄いピンクベージュ
    override val backgroundColor: Int = 0xFFFFF0F5.toInt() // Lavender Blush

    // 前景色: 濃いグレー（文字の視認性確保のため）
    override val foregroundColor: Int = 0xFF555555.toInt()

    // Primary Color: 薄い青
    override val primaryColor: Int = 0xFFB3E5FC.toInt() // Light Blue

    // Secondary Color: 薄い緑
    override val secondaryColor: Int = 0xFFC8E6C9.toInt() // Light Green

    // アクセントカラーの定義
    // Red/Error: 薄い赤
    override val redAccentColor: Int = 0xFFFFCDD2.toInt() // Pale Red

    // Yellow/Warn: 薄い黄色
    override val yellowAccentColor: Int = 0xFFFFECB3.toInt() // Pale Yellow

    // Green: 薄いミントグリーン
    override val greenAccentColor: Int = 0xFFB2FF59.toInt() // Light Lime

    // Aqua/Info: 薄い紫
    override val aquaAccentColor: Int = 0xFFE1BEE7.toInt() // Light Purple

    // ステータス/汎用カラーはアクセントカラーと連動
    override val errorColor: Int = redAccentColor
    override val warnColor: Int = yellowAccentColor
    override val infoColor: Int = aquaAccentColor

    // パネルの色は透明度の低い薄いグレーにします
    override fun panelColor(
        index: Int,
        length: Int,
        normalizedZ: Float,
    ): Int {
        // アルファ値を少し設定した白っぽい色
        val baseColor = 0xAAEEEEEE.toInt()
        // 深度に応じて透明度を変更
        val alpha = (0xAA * (1 - normalizedZ)).toInt().coerceIn(0x40, 0xAA)
        return (alpha shl 24) or (baseColor and 0x00FFFFFF)
    }
}
