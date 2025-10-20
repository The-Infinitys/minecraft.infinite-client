package org.infinite.gui.theme.official

import org.infinite.gui.theme.Theme
import org.infinite.gui.theme.ThemeColors

// テーマ名に「Cyber」の要素を追加して、よりモダンなハッカー感を出す
class CyberTheme : Theme("cyber", CyberColor(), null)

class CyberColor : ThemeColors() {
    // 背景色: 非常に濃い、少し青みがかった黒（OLED/ターミナル風）
    override val backgroundColor: Int = 0xFF0A0A10.toInt() // Darker, slightly blueish black

    // 前景色: クラシックなネオングリーン
    override val foregroundColor: Int = 0xFF39FF14.toInt() // Classic Neon Green

    // Primary Color: ハイライト/重要な要素に使うネオングリーン
    override val primaryColor: Int = 0xFF00FF41.toInt() // Bright, Glowing Green

    // Secondary Color: 補完色としてネオンシアン/ブルー
    override val secondaryColor: Int = 0xFF00BFFF.toInt() // Deep Sky Blue (A common cyberpunk accent)

    // アクセントカラーの定義:
    // Red/Error: ネオンマゼンタに近い鮮やかな赤
    override val redAccentColor: Int = 0xFFFF0077.toInt() // Neon Magenta-Red

    // Yellow/Warn: 鮮やかな電気イエロー
    override val yellowAccentColor: Int = 0xFFF0FF00.toInt() // Electric Yellow

    // Green: Primaryと同じネオングリーン
    override val greenAccentColor: Int = primaryColor

    // Aqua/Info: Secondaryと同じネオンシアン
    override val aquaAccentColor: Int = secondaryColor

    // ステータス/汎用カラー
    override val errorColor: Int = redAccentColor
    override val warnColor: Int = yellowAccentColor
    override val infoColor: Int = aquaAccentColor

    /**
     * パネルの色: 半透明で、深度に応じて透明度と色調が変化するホログラフィックな効果を狙う。
     * - 背景色は暗いプライマリ色（緑）
     * - normalizedZ（深度）が深いほど（=0に近いほど）、不透明度が高く（明るく）なる
     */
    override fun panelColor(
        index: Int,
        length: Int,
        normalizedZ: Float, // 0.0 (最前面) から 1.0 (最背面)
    ): Int {
        // 基本となるパネルの色（濃いシアン/緑の混合色）
        val baseColor = 0xFF0A201A.toInt() // 暗い青緑

        // 深度(normalizedZ)に基づいてアルファ値（不透明度）を計算
        // normalizedZ=0.0 (前面): alpha = 0x38 (やや不透明)
        // normalizedZ=1.0 (背面): alpha = 0x18 (非常に透明)
        val minAlpha = 0x18
        val maxAlpha = 0x38
        val alphaRange = (maxAlpha - minAlpha).toFloat()

        // 深度が浅いほど不透明度を上げる
        val alpha = (minAlpha + alphaRange * (1.0f - normalizedZ)).toInt().coerceIn(minAlpha, maxAlpha)

        // アルファ値を基本色に結合して返す
        return (alpha shl 24) or (baseColor and 0x00FFFFFF)
    }
}
