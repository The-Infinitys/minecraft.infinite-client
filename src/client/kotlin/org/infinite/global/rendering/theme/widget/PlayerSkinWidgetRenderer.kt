package org.infinite.global.rendering.theme.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.PlayerSkinWidget
import org.infinite.InfiniteClient
import org.infinite.gui.theme.ThemeColors
import org.infinite.libs.graphics.Graphics2D // Import Graphics2D

class PlayerSkinWidgetRenderer(
    val widget: PlayerSkinWidget,
) {
    fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val graphics2D = Graphics2D(context, MinecraftClient.getInstance().renderTickCounter) // Instantiate Graphics2D

        val x = widget.x
        val y = widget.y
        val width = widget.width
        val height = widget.height
        val hovered = widget.isHovered
        val colors: ThemeColors = InfiniteClient.currentColors()

        // テーマとホバー状態に基づいて色を決定
        var backgroundColor = colors.backgroundColor
        var borderColor = colors.primaryColor

        if (hovered) {
            backgroundColor = colors.primaryColor
        }

        // カスタム背景の描画
        graphics2D.fill(x, y, width, height, backgroundColor)

        // カスタムボーダーの描画
        val borderWidth = 1 // 枠線の太さ
        graphics2D.drawBorder(x, y, width, height, borderColor, borderWidth)
    }
}
