package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import org.infinite.settings.FeatureSetting

class InfiniteSelectionList<E : Enum<E>>(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val setting: FeatureSetting.EnumSetting<E>,
) : ClickableWidget(x, y, width, height, Text.literal(setting.name)) {
    private val textRenderer = MinecraftClient.getInstance().textRenderer
    private lateinit var cycleButton: InfiniteButton

    private fun cycleOption() {
        val currentIndex = setting.options.indexOf(setting.value)
        val nextIndex = (currentIndex + 1) % setting.options.size
        setting.value = setting.options[nextIndex]
        cycleButton.message = Text.literal(setting.value.name)
    }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        if (!::cycleButton.isInitialized) {
            // ここで初期化を実行する。
            val labelAndPaddingWidth = 5 + textRenderer.getWidth(setting.name) + 5
            cycleButton =
                InfiniteButton(
                    x + labelAndPaddingWidth, // Position after label
                    y,
                    width - labelAndPaddingWidth, // Remaining width
                    height,
                    Text.literal(setting.value.name),
                ) {
                    cycleOption()
                }
        }
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(setting.name),
            x + 5,
            y + (height - 8) / 2,
            0xFFFFFFFF.toInt(),
        )

        cycleButton.x = x + 5 + textRenderer.getWidth(setting.name) + 5
        cycleButton.y = y
        cycleButton.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean = cycleButton.mouseClicked(click, doubled)

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        this.appendDefaultNarrations(builder)
    }
}
