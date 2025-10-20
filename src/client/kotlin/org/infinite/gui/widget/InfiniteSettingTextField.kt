package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.infinite.settings.FeatureSetting

class InfiniteSettingTextField(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val setting: FeatureSetting<*>, // Make generic
) : ClickableWidget(x, y, width, height, Text.literal(setting.name)) {
    private val textField: InfiniteTextField
    private val textRenderer = MinecraftClient.getInstance().textRenderer

    init {
        val inputType =
            when (setting) {
                is FeatureSetting.IntSetting -> InfiniteTextField.InputType.NUMERIC
                is FeatureSetting.FloatSetting -> InfiniteTextField.InputType.FLOAT
                is FeatureSetting.StringSetting -> InfiniteTextField.InputType.ANY_TEXT
                is FeatureSetting.BlockIDSetting -> InfiniteTextField.InputType.BLOCK_ID
                is FeatureSetting.EntityIDSetting -> InfiniteTextField.InputType.ENTITY_ID
                else -> InfiniteTextField.InputType.ANY_TEXT // Handle BlockColorListSetting and other unhandled types
            }

        textField =
            InfiniteTextField(
                textRenderer,
                x + 5 + textRenderer.getWidth(setting.name) + 5, // Position after label
                y,
                150, // Fixed width for debugging
                height,
                Text.literal(setting.value.toString()), // Convert value to String for display
                inputType,
            )
        textField.text = setting.value.toString()
        textField.setChangedListener { newText ->
            when (setting) {
                is FeatureSetting.IntSetting -> setting.value = newText.toIntOrNull() ?: setting.value
                is FeatureSetting.FloatSetting -> setting.value = newText.toFloatOrNull() ?: setting.value
                is FeatureSetting.StringSetting -> setting.value = newText
                is FeatureSetting.BlockIDSetting -> setting.value = newText
                is FeatureSetting.EntityIDSetting -> setting.value = newText
                is FeatureSetting.BooleanSetting -> setting.value = newText.toBooleanStrictOrNull() ?: setting.value
                is FeatureSetting.EnumSetting<*> -> {}
                is FeatureSetting.StringListSetting -> {}
                is FeatureSetting.BlockListSetting -> {}
                is FeatureSetting.EntityListSetting -> {}
                is FeatureSetting.PlayerListSetting -> {}
                else -> {} // Handle BlockColorListSetting and other unhandled types
            }
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
                org.infinite.InfiniteClient
                    .theme()
                    .colors.foregroundColor,
            )
            context.drawTextWithShadow(
                textRenderer,
                Text.translatable(setting.descriptionKey),
                textX,
                descriptionY,
                org.infinite.InfiniteClient
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
                org.infinite.InfiniteClient
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
    ): Boolean = textField.mouseClicked(click, doubled)

    override fun keyPressed(input: KeyInput): Boolean = textField.keyPressed(input)

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        this.appendDefaultNarrations(builder)
    }
}
