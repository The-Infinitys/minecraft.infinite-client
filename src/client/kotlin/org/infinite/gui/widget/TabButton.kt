package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics2D

class TabButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Text,
    onPress: PressAction,
) : ButtonWidget(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER) {
    var isHighlighted: Boolean = false

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val graphics2D = Graphics2D(context, MinecraftClient.getInstance().renderTickCounter)
        val colors = InfiniteClient.currentColors()
        val textColor = if (isSelected || isHighlighted) colors.primaryColor else colors.foregroundColor
        val backgroundColor = if (isSelected || isHighlighted) colors.backgroundColor else colors.secondaryColor
        graphics2D.fill(x, y, width, height, backgroundColor)
        graphics2D.drawBorder(x, y, width, height, colors.primaryColor)
        graphics2D.centeredText(
            message,
            x + width / 2,
            y + height / 2,
            textColor,
        )
    }
}
