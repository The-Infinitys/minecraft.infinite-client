package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.infinite.settings.FeatureSetting

class InfiniteSelectionListField(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val setting: FeatureSetting<*>,
) : ClickableWidget(x, y, width, height, Text.literal(setting.name)) {
    private val textRenderer = MinecraftClient.getInstance().textRenderer
    private var cycleButton: InfiniteButton
    private val buttonWidth: Int

    init {
        val options: List<String> =
            when (setting) {
                is FeatureSetting.EnumSetting<*> -> setting.options.map { it.name }
                is FeatureSetting.StringListSetting -> setting.options
                else -> emptyList()
            }
        buttonWidth =
            (
                options.maxOfOrNull { textRenderer.getWidth(it) + 8 }
                    ?: 0
            ).coerceAtLeast(50)

        // Initialize cycleButton here
        cycleButton =
            InfiniteButton(
                x + width - buttonWidth, // Right-aligned
                y, // Vertically centered within the widget's height
                buttonWidth,
                height,
                getCurrentSettingValueAsText(),
            ) {
                cycleOption()
            }
    }

    private fun getCurrentSettingValueAsText(): Text =
        when (setting) {
            is FeatureSetting.EnumSetting<*> -> Text.literal(setting.value.name)
            is FeatureSetting.StringListSetting -> Text.literal(setting.value)
            else -> Text.literal("N/A")
        }

    private fun cycleOption() {
        when (setting) {
            // FeatureSetting.EnumSetting<*> の代わりに、ローカル変数として val enumSetting を定義し、
            // 以下のブロックで安全にキャストできることをコンパイラに伝えます。
            is FeatureSetting.EnumSetting<*> -> {
                // 安全性確保のためのアンチェックキャスト（非推奨だが、このパターンでは必要になることが多い）
                // setting を一旦 EnumSetting<Enum<*>> として扱うことで、T の情報を回復させる
                // 実際には T は特定の Enum 型です
                @Suppress("UNCHECKED_CAST")
                val enumSetting = setting as FeatureSetting.EnumSetting<Enum<*>>

                // 1. 現在のインデックスを取得
                val currentIndex = enumSetting.options.indexOf(enumSetting.value)

                // 2. 次のインデックスを計算（循環）
                val nextIndex = (currentIndex + 1) % enumSetting.options.size

                // 3. 修正: options リストから次の「値」を取得して代入
                // これで、enumSetting.value が期待する型 (Enum<*>) の値が代入されます。
                enumSetting.value = enumSetting.options[nextIndex] // 修正箇所

                // 4. ボタンのメッセージを更新
                cycleButton.message = Text.literal(enumSetting.value.name)
            }

            is FeatureSetting.StringListSetting -> {
                val currentIndex = setting.options.indexOf(setting.value)
                val nextIndex = (currentIndex + 1) % setting.options.size
                setting.set(setting.options[nextIndex])
                cycleButton.message = Text.literal(setting.value)
            }

            else -> {}
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
