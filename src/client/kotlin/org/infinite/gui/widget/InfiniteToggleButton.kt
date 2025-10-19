package org.infinite.gui.widget

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.input.AbstractInput
import net.minecraft.text.Text
import org.infinite.InfiniteClient
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
        val interpolatedColor =
            InfiniteClient
                .theme()
                .colors.primaryColor

        val backgroundColor =
            when {
                !isEnabled ->
                    InfiniteClient
                        .theme()
                        .colors.backgroundColor

                state ->
                    if (isHovered) {
                        InfiniteClient
                            .theme()
                            .colors.greenAccentColor
                    } else {
                        InfiniteClient
                            .theme()
                            .colors.primaryColor // ON state
                    }

                else ->
                    if (isHovered) {
                        InfiniteClient
                            .theme()
                            .colors.secondaryColor
                    } else {
                        InfiniteClient
                            .theme()
                            .colors.backgroundColor // OFF state
                    }
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
            val currentTime = System.currentTimeMillis()
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
        val knobBorderColor =
            if (isEnabled) {
                interpolatedColor
            } else {
                InfiniteClient
                    .theme()
                    .colors.backgroundColor
            }
        context.fill(currentKnobX.toInt(), knobY, currentKnobX.toInt() + knobSize, knobY + knobSize, knobBorderColor)

        // ノブの内側
        val knobInnerColor =
            if (isHovered) {
                InfiniteClient
                    .theme()
                    .colors.primaryColor
            } else {
                InfiniteClient
                    .theme()
                    .colors.foregroundColor
            }
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
