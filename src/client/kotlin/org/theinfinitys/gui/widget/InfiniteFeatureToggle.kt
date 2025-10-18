package org.theinfinitys.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import net.minecraft.util.math.ColorHelper
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.Feature
import org.theinfinitys.InfiniteClient
import org.theinfinitys.utils.rendering.drawBorder

class InfiniteFeatureToggle(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    val feature: Feature,
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

        val configurableFeature = feature.instance as ConfigurableFeature

        toggleButton =
            InfiniteToggleButton(
                x + width - buttonWidth,
                y,
                buttonWidth,
                height,
                configurableFeature.isEnabled(),
                configurableFeature.available,
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
                InfiniteClient.log("${feature.name} の設定をリセットしました。")
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
            0xFFFFFFFF.toInt(),
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
            val animationDuration = 6000L // 6 seconds for full cycle
            val colors =
                intArrayOf(
                    0xFFFF0000.toInt(), // #f00
                    0xFFFFFF00.toInt(), // #ff0
                    0xFF00FF00.toInt(), // #0f0
                    0xFF00FFFF.toInt(), // #0ff
                    0xFF0000FF.toInt(), // #00f
                    0xFFFF00FF.toInt(), // #f0f
                    0xFFFF0000.toInt(), // #f00 (for smooth loop)
                )

            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime % animationDuration
            val progress = elapsedTime.toFloat() / animationDuration.toFloat()

            val numSegments = colors.size - 1
            val segmentLength = 1.0f / numSegments
            val currentSegmentIndex = (progress / segmentLength).toInt().coerceAtMost(numSegments - 1)
            val segmentProgress = (progress % segmentLength) / segmentLength

            val startColor = colors[currentSegmentIndex]
            val endColor = colors[currentSegmentIndex + 1]

            val interpolatedColor =
                ColorHelper.getArgb(
                    255, // Alpha
                    (ColorHelper.getRed(startColor) * (1 - segmentProgress) + ColorHelper.getRed(endColor) * segmentProgress).toInt(),
                    (ColorHelper.getGreen(startColor) * (1 - segmentProgress) + ColorHelper.getGreen(endColor) * segmentProgress).toInt(),
                    (ColorHelper.getBlue(startColor) * (1 - segmentProgress) + ColorHelper.getBlue(endColor) * segmentProgress).toInt(),
                )
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
