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
    private val buttonWidth = 50 // Fixed width like ToggleButton

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
            cycleButton =
                InfiniteButton(
                    x + width - buttonWidth, // Right-aligned
                    y, // Vertically centered within the widget's height
                    buttonWidth,
                    height,
                    Text.literal(setting.value.name),
                ) {
                    cycleOption()
                }
        }

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

        cycleButton.x = x + width - buttonWidth
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
