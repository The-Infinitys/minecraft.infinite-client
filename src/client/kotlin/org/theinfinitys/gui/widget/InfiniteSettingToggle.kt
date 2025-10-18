package org.theinfinitys.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import org.theinfinitys.settings.InfiniteSetting

class InfiniteSettingToggle(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val setting: InfiniteSetting.BooleanSetting,
) : ClickableWidget(x, y, width, height, Text.literal(setting.name)) {
    private val toggleButton: InfiniteToggleButton
    private val textRenderer = MinecraftClient.getInstance().textRenderer

    init {
        val buttonWidth = 50
        toggleButton =
            InfiniteToggleButton(
                x + width - buttonWidth,
                y,
                buttonWidth,
                height,
                setting.value,
                true, // isEnabled
            ) { newState ->
                setting.value = newState
            }
    }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(setting.name),
            x + 5, // Padding from left edge
            y + (height - 8) / 2, // Center text vertically
            0xFFFFFFFF.toInt(),
        )

        toggleButton.x = x + width - toggleButton.width
        toggleButton.y = y
        toggleButton.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean = toggleButton.mouseClicked(click, doubled)

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        this.appendDefaultNarrations(builder)
    }
}
