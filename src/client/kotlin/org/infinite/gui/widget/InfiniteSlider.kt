package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import net.minecraft.util.math.MathHelper
import org.infinite.InfiniteClient
import org.infinite.settings.FeatureSetting

class InfiniteSlider<T : Number>(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val setting: FeatureSetting<T>,
) : ClickableWidget(x, y, width, height, Text.literal(setting.name)) {
    private val textRenderer = MinecraftClient.getInstance().textRenderer
    private var dragging = false
    private val knobWidth = 4

    init {
        updateMessage()
    }

    private fun updateMessage() {
        val formattedValue =
            when (setting) {
                is FeatureSetting.IntSetting -> setting.value.toString()
                is FeatureSetting.FloatSetting -> String.format("%.2f", setting.value)
                else -> throw IllegalStateException("InfiniteSlider can only be used with IntSetting or FloatSetting")
            }
        message = Text.translatable(setting.name).append(": $formattedValue")
    }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val textX = x + 5 // Padding from left edge
        var currentY = y + 2 // Start drawing text from top with small padding

        context.drawTextWithShadow(
            textRenderer,
            Text.translatable(setting.name),
            textX,
            currentY,
            InfiniteClient
                .theme()
                .colors.foregroundColor,
        )
        currentY += textRenderer.fontHeight + 2 // Move Y down for description

        if (setting.descriptionKey.isNotBlank()) {
            context.drawTextWithShadow(
                textRenderer,
                Text.translatable(setting.descriptionKey),
                textX,
                currentY,
                InfiniteClient
                    .theme()
                    .colors.foregroundColor,
            )
            textRenderer.fontHeight + 2 // Move Y down after description
        }

        // Draw slider background at the bottom of the widget
        val sliderBackgroundY = y + height - 5
        context.fill(
            x + 5,
            sliderBackgroundY,
            x + width - 5,
            sliderBackgroundY + 2,
            InfiniteClient
                .theme()
                .colors.backgroundColor,
        )

        // Draw slider knob
        val progress = getProgress()
        val knobPositionRange = width - 10 - knobWidth
        val knobX = x + 5 + (knobPositionRange) * progress
        val knobY = y + height - 7 // Knob is 6px tall, so its top is 7px from bottom

        context.fill(
            knobX.toInt(),
            knobY,
            knobX.toInt() + knobWidth,
            knobY + 6,
            InfiniteClient
                .theme()
                .colors.primaryColor,
        )
    }

    private fun getProgress(): Float =
        when (setting) {
            is FeatureSetting.IntSetting -> {
                val range = (setting.max - setting.min).toFloat()
                (setting.value.toFloat() - setting.min) / range
            }

            is FeatureSetting.FloatSetting -> {
                val range = (setting.max - setting.min)
                (setting.value - setting.min) / range
            }

            else -> throw IllegalStateException("InfiniteSlider can only be used with IntSetting or FloatSetting")
        }

    private fun setValueFromMouse(mouseX: Double) {
        // スライダーの有効範囲 (ノブの左端が動ける範囲)
        val minX = x + 5.0
        val maxX = x + width - 5.0 - knobWidth

        // マウス位置をスライダー範囲にクランプ
        val clampedMouseX = MathHelper.clamp(mouseX, minX, maxX)

        // クランプされたX位置から進行度 (0.0F〜1.0F) を計算
        val progress = ((clampedMouseX - minX) / (maxX - minX)).toFloat()

        when (setting) {
            is FeatureSetting.IntSetting -> {
                // 新しい値を計算し、Intに丸める
                val newValue = (setting.min + progress * (setting.max - setting.min)).toInt()
                (setting as FeatureSetting.IntSetting).value = newValue
            }

            is FeatureSetting.FloatSetting -> {
                // 新しい値を計算
                val newValue = setting.min + progress * (setting.max - setting.min)
                (setting as FeatureSetting.FloatSetting).value = newValue
            }

            else -> throw IllegalStateException("InfiniteSlider can only be used with IntSetting or FloatSetting")
        }
        updateMessage()
    }

    /**
     * マウスがクリックされたとき、ドラッグ状態を開始し、初期値を設定します。
     */
    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        if (isMouseOver(click.x, click.y) && click.button() == 0) {
            dragging = true
            // 値を設定し、mouseDraggedで継続して処理されるように true を返す
            setValueFromMouse(click.x)
            return true
        }
        return false
    }

    /**
     * マウスがドラッグされているとき、スライダーの値を更新します。
     */
    override fun mouseDragged(
        click: Click,
        offsetX: Double,
        offsetY: Double,
    ): Boolean {
        if (dragging) {
            setValueFromMouse(click.x)
            return true
        }
        return false
    }

    /**
     * マウスがリリースされたとき、ドラッグ状態を終了します。
     */
    override fun mouseReleased(click: Click): Boolean {
        if (dragging) {
            dragging = false
            return true // ドラッグを終了したので true を返す
        }
        return super.mouseReleased(click)
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        this.appendDefaultNarrations(builder)
    }
}
