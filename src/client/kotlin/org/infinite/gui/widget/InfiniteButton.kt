package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient // Needed for textRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.math.ColorHelper

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
        val animationDuration = 6000L // 6 seconds for full cycle

        // Animation colors (same as InfiniteScreen)
        val colors =
            intArrayOf(
                0xFFFF0000.toInt(), // #f00
                0xFFFFFF00.toInt(), // #ff0
                0xFF00FF00.toInt(), // #0f0
                0xFF00FFFF.toInt(), // #0ff
                0xFF0000FF.toInt(), // #00f
                0xFFFF00FF.toInt(), // #f0f
                0xFFFF0000.toInt(), // #f00 (for smooth loop)
            )

        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime % animationDuration
        val progress = elapsedTime.toFloat() / animationDuration.toFloat()

        val numSegments = colors.size - 1
        val segmentLength = 1.0f / numSegments
        val currentSegmentIndex = (progress / segmentLength).toInt().coerceAtMost(numSegments - 1)
        val segmentProgress = (progress % segmentLength) / segmentLength

        val startColor = colors[currentSegmentIndex]
        val endColor = colors[currentSegmentIndex + 1]

        val interpolatedColor =
            ColorHelper.getArgb(
                255, // Alpha
                (ColorHelper.getRed(startColor) * (1 - segmentProgress) + ColorHelper.getRed(endColor) * segmentProgress).toInt(),
                (ColorHelper.getGreen(startColor) * (1 - segmentProgress) + ColorHelper.getGreen(endColor) * segmentProgress).toInt(),
                (ColorHelper.getBlue(startColor) * (1 - segmentProgress) + ColorHelper.getBlue(endColor) * segmentProgress).toInt(),
            )

        // Button rendering logic (similar to InfiniteScreen)
        // 1. Outer black background
        context.fill(x, y, x + width, y + height, ColorHelper.getArgb(255, 0, 0, 0))

        // 2. Animated border
        context.fill(
            x + borderWidth,
            y + borderWidth,
            x + width - borderWidth,
            y + height - borderWidth,
            interpolatedColor,
        )

        // 3. Inner black background
        context.fill(
            x + borderWidth * 2,
            y + borderWidth * 2,
            x + width - borderWidth * 2,
            y + height - borderWidth * 2,
            ColorHelper.getArgb(255, 0, 0, 0),
        )

        // Draw button text
        val textColor = if (isHovered) 0xFFA0A0A0.toInt() else 0xFFFFFFFF.toInt() // Gray when hovered, white otherwise
        context.drawCenteredTextWithShadow(
            MinecraftClient.getInstance().textRenderer, // Use MinecraftClient's textRenderer
            message,
            x + width / 2,
            y + (height - 8) / 2, // Center text vertically, 8 is approx text height
            textColor,
        )
    }
}
