package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.input.CharInput
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

        val labelWidth = textRenderer.getWidth(setting.name) // initでは概算で良い
        val inputFieldCalculatedWidth = width - (5 + labelWidth + 5 + 5) // Padding left, label, padding, padding right

        textField =
            InfiniteTextField(
                textRenderer,
                0, // x will be set in renderWidget
                0, // y will be set in renderWidget
                inputFieldCalculatedWidth.coerceAtLeast(50), // 最小幅を確保
                height,
                Text.literal(setting.value.toString()), // Convert value to String for display
                inputType,
            )
        textField.text = setting.value.toString()
        textField.setChangedListener { newText ->
            when (setting) {
                is FeatureSetting.IntSetting -> {
                    setting.value = newText.toIntOrNull() ?: setting.value
                }

                is FeatureSetting.FloatSetting -> {
                    setting.value = newText.toFloatOrNull() ?: setting.value
                }

                is FeatureSetting.StringSetting -> {
                    setting.value = newText
                }

                is FeatureSetting.BlockIDSetting -> {
                    setting.value = newText
                }

                is FeatureSetting.EntityIDSetting -> {
                    setting.value = newText
                }

                is FeatureSetting.BooleanSetting -> {
                    setting.value = newText.toBooleanStrictOrNull() ?: setting.value
                }

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

        val currentLabelWidth = textRenderer.getWidth(Text.translatable(setting.name))
        textField.x = x + 5 + currentLabelWidth + 5
        textField.y = y + (height - textField.height) / 2

        // 現在の文字列の描画幅をベースにした理想の幅
        val idealWidth = textRenderer.getWidth(textField.text) + 10 // テキストの幅 + 左右のパディング

        // 親ウィジェットが提供する最大利用可能幅
        val maxAvailableWidth = width - (textField.x - x) - 5

        // 実際の幅は、理想の幅と最大利用可能幅の小さい方、ただし最低幅は確保
        val actualWidth = idealWidth.coerceAtMost(maxAvailableWidth).coerceAtLeast(50) // 50は例としての最低幅

        textField.setWidth(actualWidth) // 親ウィジェットの幅を考慮してテキストフィールドの幅を設定
        textField.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        // まず、内部のtextFieldがクリックされたかどうかを試す
        if (textField.mouseClicked(click, doubled)) {
            isFocused = false
            return true // イベントを消費し、親のClickableWidgetのmouseClickedは呼ばない
        } else {
            // textFieldがクリックを処理しなかった場合、または領域外だった場合
            // textFieldのフォーカスを解除する（他の場所がクリックされたと見なす）
            textField.isFocused = false
        }

        // textFieldがクリックを処理しなかった場合、親のClickableWidgetのmouseClickedを呼び出す
        return super.mouseClicked(click, doubled)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (textField.keyPressed(input)) {
            return true
        }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharInput): Boolean {
        if (textField.charTyped(input)) {
            return true
        }
        return super.charTyped(input)
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        this.appendDefaultNarrations(builder)
    }
}
