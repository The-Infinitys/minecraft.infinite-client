package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import org.infinite.libs.graphics.Graphics2D
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
        val graphics2D = Graphics2D(context, MinecraftClient.getInstance().renderTickCounter)

        val textX = x + 5 // Padding from left edge
        val totalTextHeight: Int
        val nameY: Int
        val descriptionY: Int?

        if (setting.descriptionKey.isNotBlank()) {
            totalTextHeight = textRenderer.fontHeight * 2 + 2 // Name + padding + Description
            nameY = y + (height - totalTextHeight) / 2
            descriptionY = nameY + textRenderer.fontHeight + 2

            graphics2D.drawText(
                Text.translatable(setting.name),
                textX,
                nameY,
                org.infinite.InfiniteClient
                    .currentColors()
                    .foregroundColor,
                true, // shadow = true
            )
            graphics2D.drawText(
                Text.translatable(setting.descriptionKey),
                textX,
                descriptionY,
                org.infinite.InfiniteClient
                    .currentColors()
                    .foregroundColor,
                true, // shadow = true
            )
        } else {
            totalTextHeight = textRenderer.fontHeight // Only name
            nameY = y + (height - totalTextHeight) / 2

            graphics2D.drawText(
                Text.translatable(setting.name),
                textX,
                nameY,
                org.infinite.InfiniteClient
                    .currentColors()
                    .foregroundColor,
                true, // shadow = true
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
