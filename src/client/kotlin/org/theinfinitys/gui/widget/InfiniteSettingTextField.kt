package org.theinfinitys.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.theinfinitys.settings.InfiniteSetting

class InfiniteSettingTextField(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val setting: InfiniteSetting<*>, // Make generic
) : ClickableWidget(x, y, width, height, Text.literal(setting.name)) {
    private val textField: InfiniteTextField
    private val textRenderer = MinecraftClient.getInstance().textRenderer

    init {
        val inputType =
            when (setting) {
                is InfiniteSetting.IntSetting -> InfiniteTextField.InputType.NUMERIC
                is InfiniteSetting.FloatSetting -> InfiniteTextField.InputType.FLOAT
                is InfiniteSetting.StringSetting -> InfiniteTextField.InputType.ANY_TEXT
                is InfiniteSetting.BlockIDSetting -> InfiniteTextField.InputType.BLOCK_ID
                is InfiniteSetting.EntityIDSetting -> InfiniteTextField.InputType.ENTITY_ID
                else -> InfiniteTextField.InputType.ANY_TEXT // Default for other types
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
                is InfiniteSetting.IntSetting -> setting.value = newText.toIntOrNull() ?: setting.value
                is InfiniteSetting.FloatSetting -> setting.value = newText.toFloatOrNull() ?: setting.value
                is InfiniteSetting.StringSetting -> setting.value = newText
                is InfiniteSetting.BlockIDSetting -> setting.value = newText
                is InfiniteSetting.EntityIDSetting -> setting.value = newText
                is InfiniteSetting.BooleanSetting -> setting.value = newText.toBooleanStrictOrNull() ?: setting.value
                is InfiniteSetting.EnumSetting<*> -> {}
                is InfiniteSetting.StringListSetting -> {}
                is InfiniteSetting.BlockListSetting -> {}
                is InfiniteSetting.EntityListSetting -> {}
                is InfiniteSetting.PlayerListSetting -> {}
            }
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
            x + 5,
            y + (height - 8) / 2,
            0xFFFFFFFF.toInt(),
        )

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
