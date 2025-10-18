package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import net.minecraft.util.math.MathHelper
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
        message = Text.literal("${setting.name}: $formattedValue")
    }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        context.drawTextWithShadow(
            textRenderer,
            message,
            x + 5,
            y + (height - 8) / 2,
            0xFFFFFFFF.toInt(),
        )

        // Draw slider background
        context.fill(x + 5, y + height - 5, x + width - 5, y + height - 3, 0xFF404040.toInt())

        // Draw slider knob
        val progress = getProgress()
        // スライダーの有効範囲: 幅 - (左パディング5 + 右パディング5 + ノブ幅4) = width - 14
        val knobPositionRange = width - 10 - knobWidth

        // ノブの中央ではなく、ノブの左端が来る位置を計算
        val knobX = x + 5 + (knobPositionRange) * progress

        // 描画
        context.fill(knobX.toInt(), y + height - 7, knobX.toInt() + knobWidth, y + height - 1, 0xFF00FF00.toInt())
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
