package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.infinite.feature.ConfigurableFeature
import org.infinite.features.Feature
import org.infinite.utils.rendering.drawBorder

class InfiniteFeatureToggle(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val feature: Feature<out ConfigurableFeature>,
    private val isSelected: Boolean, // New parameter
    val onSettings: () -> Unit, // Made public
) : ClickableWidget(x, y, width, height, Text.literal(feature.name)) {
    val toggleButton: InfiniteToggleButton
    private val settingsButton: InfiniteButton
    private val resetButton: InfiniteButton // New reset button
    private val textRenderer = MinecraftClient.getInstance().textRenderer

    init {
        val buttonWidth = 50
        val settingsButtonWidth = 20
        val resetButtonWidth = 20 // Width for the reset button
        val spacing = 5

        val configurableFeature = feature.instance
        toggleButton =
            InfiniteToggleButton(
                x + width - buttonWidth,
                y,
                buttonWidth,
                height,
                configurableFeature.isEnabled(),
                configurableFeature.togglable,
            ) { newState ->
                if (newState) {
                    configurableFeature.enable()
                } else {
                    configurableFeature.disable()
                }
            }

        settingsButton =
            InfiniteButton(
                x + width - buttonWidth - spacing - settingsButtonWidth,
                y,
                settingsButtonWidth,
                height,
                Text.literal("S"),
            ) { onSettings() }

        resetButton =
            InfiniteButton(
                x + width - buttonWidth - spacing * 2 - settingsButtonWidth - resetButtonWidth,
                y,
                resetButtonWidth,
                height,
                Text.literal("R"), // Placeholder for reset icon/text
            ) {
                // OnPress action for reset button
                configurableFeature.reset() // Reset feature's enabled state
                configurableFeature.settings.forEach { setting ->
                    setting.reset() // Reset individual settings
                }
                InfiniteClient.log(Text.translatable("command.infinite.config.reset.feature", feature.name).string)
            }

        // Add listener to update toggle button when feature.enabled changes
        configurableFeature.addEnabledChangeListener { _, newValue ->
            toggleButton.setState(newValue)
        }
    }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        // Draw button text
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(feature.name),
            x + 60,
            y + (height - 8) / 2,
            InfiniteClient
                .theme()
                .colors.foregroundColor,
        )

        toggleButton.x = x + width - toggleButton.width
        toggleButton.y = y
        settingsButton.x = x + width - toggleButton.width - 5 - settingsButton.width
        settingsButton.y = y
        resetButton.x =
            x + width - toggleButton.width - 5 * 2 - settingsButton.width - resetButton.width // Position reset button
        resetButton.y = y

        toggleButton.render(context, mouseX, mouseY, delta)
        settingsButton.render(context, mouseX, mouseY, delta)
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
            settingsButton.mouseClicked(click, doubled) ||
            resetButton.mouseClicked(click, doubled) // Handle reset button click

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        this.appendDefaultNarrations(builder)
    }
}
