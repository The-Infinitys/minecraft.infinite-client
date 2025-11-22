package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics2D

class InfiniteButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    message: Text,
    onPress: PressAction,
) : ButtonWidget(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER) {
    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val graphics2D = Graphics2D(context, MinecraftClient.getInstance().renderTickCounter)

        val borderWidth = 1 // 1px border

        // Animation colors (same as InfiniteScreen)
        val interpolatedColor =
            InfiniteClient
                .currentColors()
                .primaryColor

        // Button rendering logic (similar to InfiniteScreen)
        // 1. Outer background
        context.fill(
            x,
            y,
            x + width,
            y + height,
            InfiniteClient
                .currentColors()
                .backgroundColor,
        )

        // 2. Animated border
        context.fill(
            x + borderWidth,
            y + borderWidth,
            x + width - borderWidth,
            y + height - borderWidth,
            interpolatedColor,
        )

        // 3. Inner background
        context.fill(
            x + borderWidth * 2,
            y + borderWidth * 2,
            x + width - borderWidth * 2,
            y + height - borderWidth * 2,
            InfiniteClient
                .currentColors()
                .backgroundColor,
        )

        // Draw button text
        val textColor =
            if (isHovered) {
                InfiniteClient.currentColors().primaryColor
            } else {
                InfiniteClient.currentColors().foregroundColor // Darker foreground when hovered, foreground otherwise
            }
        graphics2D.centeredText(
            message,
            x + width / 2,
            y + height / 2,
            textColor,
            true, // shadow = true
        )
    }
}
