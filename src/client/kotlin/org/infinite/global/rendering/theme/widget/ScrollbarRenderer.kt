package org.infinite.global.rendering.theme.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.cursor.StandardCursors
import net.minecraft.client.gui.widget.ScrollableWidget
import org.infinite.InfiniteClient
import org.infinite.gui.theme.ThemeColors
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.rendering.transparent

class ScrollbarRenderer(
    val widget: ScrollableWidget,
) {
    fun renderScrollbar(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float, // delta is not used in the original drawScrollbar, but kept for consistency
    ) {
        val graphics2D = Graphics2D(context, MinecraftClient.getInstance().renderTickCounter)
        val colors: ThemeColors = InfiniteClient.currentColors()

        if (widget.overflows()) {
            val i = widget.scrollbarX
            val j = widget.scrollbarThumbHeight
            val k = widget.scrollbarThumbY

            // カスタムのスクロールバー背景の描画
            graphics2D.fill(
                i,
                widget.y,
                6, // width of the scrollbar background
                widget.height,
                colors.backgroundColor.transparent(128), // 半透明の背景色
            )

            // カスタムのスクロールバーサム（つまみ）の描画
            graphics2D.fill(
                i,
                k,
                6, // width of the scrollbar thumb
                j,
                colors.primaryColor, // プライマリカラーのつまみ
            )
            graphics2D.drawBorder(
                i,
                k,
                6,
                j,
                colors.foregroundColor, // つまみのボーダー
                1,
            )

            if (widget.isInScrollbar(mouseX.toDouble(), mouseY.toDouble())) {
                context.setCursor(if (widget.scrollbarDragged) StandardCursors.RESIZE_NS else StandardCursors.POINTING_HAND)
            }
        }
    }
}
