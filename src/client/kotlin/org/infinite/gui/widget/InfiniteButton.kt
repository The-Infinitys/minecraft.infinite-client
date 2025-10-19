package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import org.infinite.InfiniteClient

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
        val borderWidth = 1 // 1px border

        // Animation colors (same as InfiniteScreen)
        val interpolatedColor =
            InfiniteClient
                .theme()
                .colors.primaryColor

        // Button rendering logic (similar to InfiniteScreen)
        // 1. Outer background
        context.fill(
            x,
            y,
            x + width,
            y + height,
            InfiniteClient
                .theme()
                .colors.backgroundColor,
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
                .theme()
                .colors.backgroundColor,
        )

        // Draw button text
        val textColor =
            if (isHovered) {
                InfiniteClient.theme().colors.primaryColor
            } else {
                InfiniteClient.theme().colors.foregroundColor // Darker foreground when hovered, foreground otherwise
            }
        context.drawCenteredTextWithShadow(
            MinecraftClient.getInstance().textRenderer, // Use MinecraftClient's textRenderer
            message,
            x + width / 2,
            y + (height - 8) / 2, // Center text vertically, 8 is approx text height
            textColor,
        )
    }
}
