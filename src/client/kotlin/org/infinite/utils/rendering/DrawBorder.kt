package org.infinite.utils.rendering

import net.minecraft.client.gui.DrawContext
import net.minecraft.util.math.ColorHelper

fun DrawContext.drawBorder(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    color: Int,
    size: Int = 1,
) {
    this.fill(x, y, x + width, y + size, color)
    this.fill(x + width - size, y + size, x + width, y + height - size, color)
    this.fill(x, y + height - size, x + width, y + height, color)
    this.fill(x, y + size, x + size, y + height - size, color)
}

object ColorUtils {
    private val OUT_OF_REACH_COLOR = ColorHelper.getArgb(255, 150, 150, 150)

    fun getGradientColor(
        progress: Float,
        alpha: Int = 255,
    ): Int {
        val clampedProgress = progress.coerceIn(0.0f, 1.0f)
        val r: Int
        val g: Int
        val b: Int

        if (clampedProgress <= 0.5f) {
            val p = clampedProgress * 2.0f
            r = 255
            g = (255 * p).toInt()
            b = 0
        } else {
            val p = (clampedProgress - 0.5f) * 2.0f
            r = (255 * (1.0f - p)).toInt()
            g = 255
            b = (255 * p).toInt()
        }

        return ColorHelper.getArgb(alpha, r, g, b)
    }

    fun getFeatureColor(isInReach: Boolean): Int =
        if (isInReach) {
            getRainbowColor()
        } else {
            OUT_OF_REACH_COLOR
        }

    private fun getRainbowColor(): Int {
        val rainbowDuration = 6000L
        val colors =
            intArrayOf(
                0xFFFF0000.toInt(),
                0xFFFFFF00.toInt(),
                0xFF00FF00.toInt(),
                0xFF00FFFF.toInt(),
                0xFF0000FF.toInt(),
                0xFFFF00FF.toInt(),
                0xFFFF0000.toInt(),
            )
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime % rainbowDuration
        val progress = elapsedTime.toFloat() / rainbowDuration.toFloat()
        val numSegments = colors.size - 1
        val segmentLength = 1.0f / numSegments
        val currentSegmentIndex = (progress / segmentLength).toInt().coerceAtMost(numSegments - 1)
        val segmentProgress = (progress % segmentLength) / segmentLength
        val startColor = colors[currentSegmentIndex]
        val endColor = colors[currentSegmentIndex + 1]

        return ColorHelper.getArgb(
            255,
            (ColorHelper.getRed(startColor) * (1 - segmentProgress) + ColorHelper.getRed(endColor) * segmentProgress).toInt(),
            (ColorHelper.getGreen(startColor) * (1 - segmentProgress) + ColorHelper.getGreen(endColor) * segmentProgress).toInt(),
            (ColorHelper.getBlue(startColor) * (1 - segmentProgress) + ColorHelper.getBlue(endColor) * segmentProgress).toInt(),
        )
    }
}
