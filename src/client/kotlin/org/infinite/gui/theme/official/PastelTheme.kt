package org.infinite.gui.theme.official

import org.infinite.gui.theme.Theme
import org.infinite.gui.theme.ThemeColors

class PastelTheme : Theme("pastel", PastelColor(), null)

class PastelColor : ThemeColors() {
    // 背景色: 非常に明るく、少し温かみのあるオフホワイト (Soft Cotton)
    // Lavender Blush (FFF0F5) より少し白く、暖色寄り
    override val backgroundColor: Int = 0xFFFCF7F3.toInt()

    // 前景色: 濃い青みがかったグレー (Dark Slate) - 視認性を確保しつつ、黒より柔らかい印象に
    override val foregroundColor: Int = 0xFF455A64.toInt() // Material Blue Gray 700

    // Primary Color: 落ち着いたライトブルー (Sky Blue)
    override val primaryColor: Int = 0xFFB3E5FC.toInt() // Light Blue (Kept, it's a good base)

    // Secondary Color: ソフトなローズピンク (Muted Rose) - Primaryとのコントラストを出す
    override val secondaryColor: Int = 0xFFFFCDD2.toInt() // Light Rose/Pink

    // アクセントカラーの定義: 彩度を抑えたパステルカラーを使用

    // Red/Error: ソフトなサーモンピンク
    override val redAccentColor: Int = 0xFFFFAB91.toInt() // Light Salmon

    // Yellow/Warn: 薄いピーチイエロー
    override val yellowAccentColor: Int = 0xFFFFECB3.toInt() // Pale Yellow (Kept, good for warning)

    // Green: ソフトなミントグリーン
    override val greenAccentColor: Int = 0xFFC8E6C9.toInt() // Light Green (Now used here)

    // Aqua/Info: 落ち着いたラベンダー
    override val aquaAccentColor: Int = 0xFFD1C4E9.toInt() // Light Purple/Lavender

    // ステータス/汎用カラー
    override val errorColor: Int = redAccentColor
    override val warnColor: Int = yellowAccentColor
    override val infoColor: Int = aquaAccentColor

    /**
     * パネルの色: 背景に溶け込むような、非常に薄いクリーム色。
     * 深度に応じて透明度と**わずかな色相**が変化し、紙を重ねたような優しい奥行き感を出す。
     */
    override fun panelColor(
        index: Int,
        length: Int,
        normalizedZ: Float, // 0.0 (最前面) から 1.0 (最背面)
    ): Int {
        // パネルの基本色は背景よりわずかに濃いクリーム色
        val baseColor = 0xFFF7F2EE.toInt()

        // 深度(normalizedZ)に基づいてアルファ値を計算 (不透明度は低く保つ)
        // normalizedZ=0.0 (前面): alpha = 0x88 (やや不透明)
        // normalizedZ=1.0 (背面): alpha = 0x40 (非常に透明)
        val minAlpha = 0x40
        val maxAlpha = 0x88
        val alphaRange = (maxAlpha - minAlpha).toFloat()

        // 深度が浅いほど不透明度を上げる
        val alpha = (minAlpha + alphaRange * (1.0f - normalizedZ)).toInt().coerceIn(minAlpha, maxAlpha)

        // 深度が深いほど（normalizedZが大きいほど）、わずかに暗いグレー（0xEE）に近づける
        // 0xFFF7F2EE -> 0xFFEAE5E1
        val r = (baseColor shr 16) and 0xFF
        val g = (baseColor shr 8) and 0xFF
        val b = baseColor and 0xFF

        // 深度に応じてRGB値をわずかに下げる (色の変化は最小限に留める)
        val depthFactor = normalizedZ * 0.05f // 最大5%の変化
        val newR = (r * (1.0f - depthFactor)).toInt()
        val newG = (g * (1.0f - depthFactor)).toInt()
        val newB = (b * (1.0f - depthFactor)).toInt()

        val newBaseColor = (newR shl 16) or (newG shl 8) or newB

        // アルファ値を基本色に結合して返す
        return (alpha shl 24) or newBaseColor
    }
}
