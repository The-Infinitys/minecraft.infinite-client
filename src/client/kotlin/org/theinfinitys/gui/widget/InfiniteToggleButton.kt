package org.theinfinitys.gui.widget

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.input.AbstractInput
import net.minecraft.text.Text
import net.minecraft.util.math.ColorHelper
import kotlin.math.sin

class InfiniteToggleButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private var state: Boolean,
    private var isEnabled: Boolean,
    private val onToggle: (Boolean) -> Unit,
) : ButtonWidget(
        x,
        y,
        width,
        height,
        Text.literal(""),
        { _ -> },
        DEFAULT_NARRATION_SUPPLIER,
    ) {
    private var animationStartTime: Long = -1L
    private val animationDuration = 200L // アニメーションにかける時間（ミリ秒）

    override fun onPress(input: AbstractInput?) {
        if (isEnabled) {
            state = !state
            onToggle(state)
            animationStartTime = System.currentTimeMillis() // アニメーション開始時間を記録
        }
    }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        // レインボーアニメーションのロジック
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

        val interpolatedColor =
            ColorHelper.getArgb(
                255,
                (ColorHelper.getRed(startColor) * (1 - segmentProgress) + ColorHelper.getRed(endColor) * segmentProgress).toInt(),
                (ColorHelper.getGreen(startColor) * (1 - segmentProgress) + ColorHelper.getGreen(endColor) * segmentProgress).toInt(),
                (ColorHelper.getBlue(startColor) * (1 - segmentProgress) + ColorHelper.getBlue(endColor) * segmentProgress).toInt(),
            )

        val backgroundColor =
            when {
                !isEnabled -> ColorHelper.getArgb(255, 64, 64, 64) // 無効時の背景色
                state -> if (isHovered) 0xFF40A040.toInt() else 0xFF00FF00.toInt() // ON state
                else -> if (isHovered) 0xFF606060.toInt() else 0xFF808080.toInt() // OFF state
            }

        // ノブのサイズを基準にバーの幅を決定
        val knobSize = height - 4
        val barWidth = (knobSize * 2).toFloat()
        val barHeight = height.toFloat() / 2.5f
        val barY = y + (height - barHeight.toInt()) / 2
        val barX = x + (width - barWidth.toInt()) / 2

        // 背景バーの描画
        context.fill(barX, barY, (barX + barWidth).toInt(), (barY + barHeight).toInt(), backgroundColor)

        // **ノブのアニメーションロジック**
        val knobBorder = 2
        val startKnobX = if (!state) barX + barWidth.toInt() - knobSize - 2 else barX + 2
        val endKnobX = if (state) barX + barWidth.toInt() - knobSize - 2 else barX + 2

        var currentKnobX = endKnobX.toFloat()
        if (animationStartTime != -1L) {
            val animProgress = (currentTime - animationStartTime).toFloat() / animationDuration.toFloat()
            if (animProgress < 1.0f) {
                // スムーズなアニメーションのためにsin関数を使用
                val easedProgress = sin(animProgress * Math.PI / 2).toFloat()
                currentKnobX = startKnobX + (endKnobX - startKnobX) * easedProgress
            } else {
                animationStartTime = -1L // アニメーション終了
                currentKnobX = endKnobX.toFloat()
            }
        }
        val knobY = y + 2

        // ノブの縁 (有効時のみレインボーアニメーション)
        val knobBorderColor = if (isEnabled) interpolatedColor else 0xFF404040.toInt()
        context.fill(currentKnobX.toInt(), knobY, currentKnobX.toInt() + knobSize, knobY + knobSize, knobBorderColor)

        // ノブの内側
        val knobInnerColor = if (isHovered) 0xFFA0A0A0.toInt() else 0xFFFFFFFF.toInt()
        context.fill(
            currentKnobX.toInt() + knobBorder,
            knobY + knobBorder,
            currentKnobX.toInt() + knobSize - knobBorder,
            knobY + knobSize - knobBorder,
            knobInnerColor,
        )
    }

    fun setState(newState: Boolean) {
        state = newState
    }
}
