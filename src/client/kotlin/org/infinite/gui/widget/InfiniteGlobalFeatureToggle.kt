package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.infinite.global.ConfigurableGlobalFeature
import org.infinite.global.GlobalFeature
import org.infinite.utils.rendering.drawBorder

class InfiniteGlobalFeatureToggle(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val globalFeature: GlobalFeature<out ConfigurableGlobalFeature>,
    private val isSelected: Boolean, // New parameter
    private val featureDescription: String,
) : ClickableWidget(x, y, width, height, Text.literal(globalFeature.name)) {
    val toggleButton: InfiniteToggleButton
    private val resetButton: InfiniteButton // New reset button
    private val textRenderer = MinecraftClient.getInstance().textRenderer

    init {
        val buttonWidth = 50
        val resetButtonWidth = 20 // Width for the reset button
        val spacing = 5
        val configurableGlobalFeature = globalFeature.instance
        toggleButton =
            InfiniteToggleButton(
                x + width - buttonWidth,
                y,
                buttonWidth,
                height,
                configurableGlobalFeature.isEnabled(),
                configurableGlobalFeature.togglable,
            ) { newState ->
                if (newState) {
                    configurableGlobalFeature.enable()
                } else {
                    configurableGlobalFeature.disable()
                }
            }
        resetButton =
            InfiniteButton(
                x + width - buttonWidth - spacing * 2 - resetButtonWidth,
                y,
                resetButtonWidth,
                height,
                Text.literal("R"), // Placeholder for reset icon/text
            ) {
                // OnPress action for reset button
                configurableGlobalFeature.reset() // Reset feature's enabled state
                configurableGlobalFeature.settings.forEach { setting ->
                    setting.reset() // Reset individual settings
                }
                InfiniteClient.log(
                    Text
                        .translatable(
                            "command.infinite.config.reset.globalFeature",
                            globalFeature.name,
                        ).string,
                )
            }

        // Add listener to update toggle button when feature.enabled changes
        configurableGlobalFeature.addEnabledChangeListener { _, newValue ->
            toggleButton.setState(newValue)
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
            Text.literal(globalFeature.name),
            x + 5, // 左端から少しパディング
            y + (height - textRenderer.fontHeight) / 2, // 垂直方向中央
            InfiniteClient.theme().colors.foregroundColor,
        )

        var descriptionY = y + (height - textRenderer.fontHeight) / 2 + textRenderer.fontHeight + 2 // タイトルの下2ピクセル

        featureDescription.split("\n").forEach { line ->
            context.drawTextWithShadow(
                textRenderer,
                Text.literal(line),
                x + 5, // 左端から少しパディング
                descriptionY,
                InfiniteClient.theme().colors.secondaryColor,
            )
            descriptionY += textRenderer.fontHeight + 1 // 次の行へ
        }
        toggleButton.x = x + width - toggleButton.width
        toggleButton.y = y
        resetButton.x =
            x + width - toggleButton.width - 5 - resetButton.width // Position reset button
        resetButton.y = y
        toggleButton.render(context, mouseX, mouseY, delta)
        resetButton.render(context, mouseX, mouseY, delta) // Render reset button
        if (isSelected) {
            val interpolatedColor =
                InfiniteClient
                    .theme()
                    .colors.primaryColor
            context.drawBorder(x, y, width, height, interpolatedColor)
        }
    }

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean =
        toggleButton.mouseClicked(click, doubled) ||
            resetButton.mouseClicked(click, doubled) // Handle reset button click

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        this.appendDefaultNarrations(builder)
    }
}
