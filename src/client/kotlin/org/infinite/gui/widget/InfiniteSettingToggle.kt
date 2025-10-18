package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import org.infinite.settings.FeatureSetting

class InfiniteSettingToggle(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val setting: FeatureSetting.BooleanSetting,
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
        val textX = x + 5 // Padding from left edge
        val totalTextHeight: Int
        val nameY: Int
        val descriptionY: Int?

        if (setting.description != null && setting.description!!.isNotBlank()) {
            totalTextHeight = textRenderer.fontHeight * 2 + 2 // Name + padding + Description
            nameY = y + (height - totalTextHeight) / 2
            descriptionY = nameY + textRenderer.fontHeight + 2

            context.drawTextWithShadow(
                textRenderer,
                Text.literal(setting.name),
                textX,
                nameY,
                0xFFFFFFFF.toInt(),
            )
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(setting.description!!),
                textX,
                descriptionY,
                0xFFA0A0A0.toInt(), // Gray color for description
            )
        } else {
            totalTextHeight = textRenderer.fontHeight // Only name
            nameY = y + (height - totalTextHeight) / 2
            descriptionY = null

            context.drawTextWithShadow(
                textRenderer,
                Text.literal(setting.name),
                textX,
                nameY,
                0xFFFFFFFF.toInt(),
            )
        }

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
