package org.infinite.features.rendering.ui

import net.minecraft.util.math.MathHelper
import org.infinite.gui.theme.ThemeColors
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.rendering.transparent
import kotlin.math.abs

class CompassRenderer(
    private val config: UiRenderConfig,
) {
    fun render(
        graphics2D: Graphics2D,
        playerYaw: Float,
        colors: ThemeColors,
    ) {
        val width = graphics2D.width
        val padding = config.padding

        val compassWidth = 256 // 方位計の全体の幅
        val compassHeight = 8 // 方位計の高さ
        val topY = padding.toDouble() // 画面上部からのパディング

        val centerX = width / 2

        // 現在のプレイヤーのヨー角を正規化
        val currentYaw = MathHelper.wrapDegrees(playerYaw)

        // 主要な方角
        val cardinalPoints = mapOf(0f to "S", 90f to "W", 180f to "N", 270f to "E")
        // 中間の方角
        val interCardinalPoints = mapOf(45f to "SW", 135f to "NW", 225f to "NE", 315f to "SE")

        // 方位計の表示範囲 (例: 中心から左右90度ずつ、合計180度)
        val displayRangeDegrees = 90f
        val pixelsPerDegree = compassWidth / (displayRangeDegrees * 2) // 1度あたりのピクセル数
        val majorMarkColor = colors.foregroundColor
        val minorMarkColor = colors.secondaryColor

        // 中央の現在位置を示すポインター
        graphics2D.drawLine(
            centerX.toDouble(),
            topY,
            centerX.toDouble(),
            topY + compassHeight,
            colors.primaryColor.transparent(255),
            2,
        )

        for (degree in 0 until 360 step 5) { // 5度刻みで目盛りをチェック
            val relativeDegree = MathHelper.wrapDegrees(degree - currentYaw)

            // 表示範囲内にある場合のみ描画
            if (relativeDegree >= -displayRangeDegrees && relativeDegree <= displayRangeDegrees) {
                val markX = centerX + (relativeDegree * pixelsPerDegree).toInt()
                var markLength = 5.0 // 小さい目盛りの長さ (元のコードから微調整)
                var displayChar: String? = null
                var markDrawColor: Int

                // 透明度（アルファ値）の計算 (元のロジックを保持)
                val distanceRatio = abs(relativeDegree) / displayRangeDegrees
                val fadeFactor = 1.0 - distanceRatio
                val alpha = (255 * fadeFactor).coerceIn(0.0, 255.0).toInt()

                if (degree % 90 == 0) { // 主要な方角 (N, S, E, W)
                    markLength = 10.0
                    displayChar = cardinalPoints[degree.toFloat()]
                    markDrawColor = majorMarkColor
                    if (displayChar == "N") markDrawColor = colors.purpleAccentColor // 北だけ特別色
                } else if (degree % 45 == 0) { // 中間の方角 (NE, NW, SE, SW)
                    markLength = 8.0
                    displayChar = interCardinalPoints[degree.toFloat()]
                    markDrawColor = majorMarkColor
                } else if (degree % 10 == 0) { // 10度ごとの目盛り
                    markLength = 7.0
                    markDrawColor = minorMarkColor
                } else {
                    markDrawColor = minorMarkColor
                }

                // 目盛り線
                graphics2D.drawLine(
                    markX.toDouble(),
                    topY + compassHeight,
                    markX.toDouble(),
                    topY + compassHeight + markLength,
                    markDrawColor.transparent(alpha),
                    2,
                )

                // 方角テキスト
                if (displayChar != null) {
                    val textWidth = graphics2D.textWidth(displayChar)
                    graphics2D.drawText(
                        displayChar,
                        markX - textWidth / 2.0,
                        topY,
                        markDrawColor.transparent(alpha),
                        true, // shadow
                    )
                }
            }
        }
    }
}
