package org.infinite.gui.theme.official

import org.infinite.gui.theme.Theme
import org.infinite.gui.theme.ThemeColors

class HackerTheme : Theme("hacker", HackerColor(), null)

class HackerColor : ThemeColors() {
    // 背景色: 濃い黒（ターミナル風）
    override val backgroundColor: Int = 0xFF111111.toInt()

    // 前景色: 明るいグリーン（ネオン風）
    override val foregroundColor: Int = 0xFF00FF41.toInt()

    // Primary Color: ネオングリーン
    override val primaryColor: Int = 0xFF00C853.toInt() // Green Accent 4

    // Secondary Color: 明るいシアン
    override val secondaryColor: Int = 0xFF00E5FF.toInt()

    // アクセントカラーの定義 (Primary/Foregroundと区別するために少しトーンを落とすか、別の色相を選ぶ)
    // Red/Error: ネオンレッド
    override val redAccentColor: Int = 0xFFFF1744.toInt()

    // Yellow/Warn: ネオンイエロー
    override val yellowAccentColor: Int = 0xFFFFEA00.toInt()

    // Green: Primaryと同じネオングリーン
    override val greenAccentColor: Int = primaryColor

    // Aqua/Info: 濃い青
    override val aquaAccentColor: Int = 0xFF2962FF.toInt()

    // ステータス/汎用カラー
    override val errorColor: Int = redAccentColor
    override val warnColor: Int = yellowAccentColor
    override val infoColor: Int = aquaAccentColor

    // パネルの色: 透明度の低いネオングリーン
    override fun panelColor(
        index: Int,
        length: Int,
        normalizedZ: Float,
    ): Int {
        val baseColor = 0xFF003300.toInt() // 濃い緑
        // 深度に応じて明るい緑の境界線を追加するイメージ
        val alpha = (0x20 * (1 - normalizedZ)).toInt().coerceIn(0x10, 0x20)
        return (alpha shl 24) or (baseColor and 0x00FFFFFF)
    }
}
