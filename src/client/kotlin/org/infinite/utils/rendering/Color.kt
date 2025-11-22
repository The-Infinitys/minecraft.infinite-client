package org.infinite.utils.rendering

import net.minecraft.util.math.ColorHelper
import kotlin.math.roundToInt

fun getRainbowColor(value: Float? = null): Int {
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
    val progress = value ?: (elapsedTime.toFloat() / rainbowDuration.toFloat())
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

fun Int.transparent(alpha: Int): Int {
    val alpha = alpha.coerceIn(0, 255)
    return ColorHelper.getArgb(
        alpha,
        ColorHelper.getRed(
            this,
        ),
        ColorHelper.getGreen(
            this,
        ),
        ColorHelper.getBlue(
            this,
        ),
    )
}

fun Int.transparent(alpha: Double): Int = this.transparent(alpha.roundToInt())

fun Int.transparent(alpha: Float): Int = this.transparent(alpha.roundToInt())
