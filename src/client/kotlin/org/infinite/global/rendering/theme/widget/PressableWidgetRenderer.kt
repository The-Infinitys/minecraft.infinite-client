package org.infinite.global.rendering.theme.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.CheckboxWidget
import net.minecraft.client.gui.widget.LockButtonWidget
import net.minecraft.client.gui.widget.PageTurnWidget
import net.minecraft.client.gui.widget.PressableTextWidget
import net.minecraft.client.gui.widget.PressableWidget
import net.minecraft.client.gui.widget.TextIconButtonWidget
import net.minecraft.client.gui.widget.TexturedButtonWidget
import org.infinite.InfiniteClient
import org.infinite.gui.theme.ThemeColors
import org.infinite.libs.graphics.Graphics2D

class PressableWidgetRenderer(
    val widget: PressableWidget,
) {
    fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val graphics2D = Graphics2D(context, MinecraftClient.getInstance().renderTickCounter)
        val x = widget.x
        val y = widget.y
        val width = widget.width
        val height = widget.height
        val active = widget.active
        val hovered = widget.isHovered
        val colors: ThemeColors = InfiniteClient.currentColors()

        // --- (背景とボーダーの描画ロジックは変更なし) ---
        var backgroundColor = colors.backgroundColor
        val borderColor = colors.primaryColor
        val textColor = colors.foregroundColor
        if (hovered) {
            backgroundColor = colors.primaryColor
        }

        if (!active) {
            backgroundColor = colors.secondaryColor
        }

        graphics2D.fill(x, y, width, height, backgroundColor)
        val borderWidth = 1
        graphics2D.drawBorder(x, y, width, height, borderColor, borderWidth)
        // -------------------------------------------------

        // ⭐ テキスト描画の制御部分
        val shouldRenderText =
            when (widget) {
                // テキストを表示するウィジェット
                is PressableTextWidget, // 標準的なテキストボタン
                is TextIconButtonWidget.WithText,
                -> true

                // テキスト表示が主目的ではないウィジェット
                is CheckboxWidget,
                is LockButtonWidget,
                is PageTurnWidget,
                is TextIconButtonWidget.IconOnly,
                is TexturedButtonWidget,
                -> false

                // その他の PressableWidget の派生クラスは、デフォルトでテキストを表示する（安全策）
                else -> true
            }

        if (shouldRenderText) {
            widget.drawMessage(context, MinecraftClient.getInstance().textRenderer, textColor)
        }
    }
}
