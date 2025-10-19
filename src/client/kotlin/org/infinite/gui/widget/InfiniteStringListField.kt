package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.infinite.settings.FeatureSetting

class InfiniteStringListField(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val setting: FeatureSetting.StringListSetting,
) : ClickableWidget(x, y, width, height, Text.literal(setting.name)) {
    private val textField: InfiniteTextField
    private val textRenderer = MinecraftClient.getInstance().textRenderer

    init {
        textField =
            InfiniteTextField(
                textRenderer,
                x + 5 + textRenderer.getWidth(setting.name) + 5, // Position after label
                y,
                150, // Fixed width for debugging
                height,
                Text.literal(setting.value.joinToString(", ")),
                InfiniteTextField.InputType.BLOCK_ID,
            )
        textField.text = setting.value.joinToString(", ")
        textField.setChangedListener { newText ->
            setting.value = newText.split(",").map { it.trim() }.filter { it.isNotBlank() }
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

        if (setting.descriptionKey.isNotBlank()) {
            totalTextHeight = textRenderer.fontHeight * 2 + 2 // Name + padding + Description
            nameY = y + (height - totalTextHeight) / 2
            descriptionY = nameY + textRenderer.fontHeight + 2

            context.drawTextWithShadow(
                textRenderer,
                Text.translatable(setting.name),
                textX,
                nameY,
                InfiniteClient
                    .theme()
                    .colors.foregroundColor,
            )
            context.drawTextWithShadow(
                textRenderer,
                Text.translatable(setting.descriptionKey),
                textX,
                descriptionY,
                InfiniteClient
                    .theme()
                    .colors.foregroundColor,
            )
        } else {
            totalTextHeight = textRenderer.fontHeight // Only name
            nameY = y + (height - totalTextHeight) / 2

            context.drawTextWithShadow(
                textRenderer,
                Text.translatable(setting.name),
                textX,
                nameY,
                InfiniteClient
                    .theme()
                    .colors.foregroundColor,
            )
        }

        textField.x = x + 5 + textRenderer.getWidth(setting.name) + 5
        textField.y = y
        textField.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        val handled = textField.mouseClicked(click, doubled)
        if (handled) {
            textField.isFocused = true
        }
        return handled
    }

    override fun keyPressed(input: KeyInput): Boolean = textField.keyPressed(input)

    override fun charTyped(input: CharInput): Boolean = textField.charTyped(input)

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        this.appendDefaultNarrations(builder)
    }
}
