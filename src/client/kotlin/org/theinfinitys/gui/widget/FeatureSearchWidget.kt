package org.theinfinitys.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import org.theinfinitys.Feature
import org.theinfinitys.featureCategories
import org.theinfinitys.gui.screen.FeatureSettingsScreen

class FeatureSearchWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val parentScreen: Screen,
) : ClickableWidget(x, y, width, height, Text.empty()) {
    private val textRenderer: TextRenderer = MinecraftClient.getInstance().textRenderer
    private lateinit var searchField: InfiniteTextField
    private lateinit var scrollableContainer: InfiniteScrollableContainer
    private var allFeatures: List<Feature> = featureCategories.flatMap { it.features }
    private var filteredFeatures: List<Feature>
    private var isInitialized = false
    private var selectedIndex: Int = -1 // -1 means no item is selected

    init {
        filteredFeatures = allFeatures
    }

    private fun filterFeatures(searchText: String) {
        filteredFeatures =
            if (searchText.isBlank()) {
                allFeatures
            } else {
                allFeatures.filter { feature ->
                    val categoryName = featureCategories.find { it.features.contains(feature) }?.name ?: ""
                    feature.name.contains(searchText, ignoreCase = true) ||
                        categoryName.contains(
                            searchText,
                            ignoreCase = true,
                        )
                }
            }.sortedBy { it.name } // Sort alphabetically for consistent ordering

        selectedIndex = -1 // Reset selection on filter change

        if (isInitialized) {
            scrollableContainer.widgets.clear()
            scrollableContainer.widgets.addAll(createFeatureToggleWidgets(filteredFeatures))
            scrollableContainer.scrollY = 0.0 // Reset scroll position on filter
            scrollableContainer.updateWidgetPositions() // Update positions after changing widgets
        }
    }

    private fun createFeatureToggleWidgets(features: List<Feature>): List<ClickableWidget> =
        features.mapIndexed { index, feature ->
            InfiniteFeatureToggle(
                0,
                0,
                scrollableContainer.width - scrollableContainer.internalPadding * 2,
                20,
                feature,
                index == selectedIndex, // isSelected
            ) {
                // onSettings lambda
                MinecraftClient.getInstance().setScreen(FeatureSettingsScreen(parentScreen, feature))
            }
        }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        if (!isInitialized) {
            searchField =
                InfiniteTextField(
                    textRenderer,
                    x,
                    y,
                    width,
                    20, // Height of the search field
                    Text.literal("Search features..."),
                    InfiniteTextField.InputType.ANY_TEXT,
                )
            searchField.setChangedListener { newText ->
                filterFeatures(newText)
            }

            scrollableContainer =
                InfiniteScrollableContainer(
                    x,
                    y + searchField.height + 5, // Position below search field
                    width,
                    height - searchField.height - 5, // Remaining height for scrollable container
                    mutableListOf(), // Initialize with an empty list
                )
            filterFeatures("") // Initial filter to populate the scrollable container
            isInitialized = true
        }

        searchField.x = x
        searchField.y = y
        searchField.render(context, mouseX, mouseY, delta)
        searchField.isFocused = true // Always keep focus on the search field

        scrollableContainer.x = x
        scrollableContainer.y = y + searchField.height + 5
        scrollableContainer.render(context, mouseX, mouseY, delta)
    }

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        if (searchField.mouseClicked(click, doubled)) {
            return true
        }
        if (scrollableContainer.mouseClicked(click, doubled)) {
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (searchField.keyPressed(input)) {
            return true
        }

        if (filteredFeatures.isNotEmpty()) {
            val keyCode = input.key
            when (keyCode) {
                GLFW.GLFW_KEY_UP -> {
                    selectedIndex = (selectedIndex - 1 + filteredFeatures.size) % filteredFeatures.size
                    scrollableContainer.widgets.clear()
                    scrollableContainer.widgets.addAll(createFeatureToggleWidgets(filteredFeatures))
                    scrollableContainer.updateWidgetPositions()
                    ensureSelectedVisible(MoveWay.Top)
                    return true
                }

                GLFW.GLFW_KEY_DOWN -> {
                    selectedIndex = (selectedIndex + 1) % filteredFeatures.size
                    scrollableContainer.widgets.clear()
                    scrollableContainer.widgets.addAll(createFeatureToggleWidgets(filteredFeatures))
                    scrollableContainer.updateWidgetPositions()
                    ensureSelectedVisible(MoveWay.Bottom)
                    return true
                }

                GLFW.GLFW_KEY_ENTER -> {
                    if (selectedIndex != -1) {
                        val selectedToggle = scrollableContainer.widgets[selectedIndex] as? InfiniteFeatureToggle
                        selectedToggle?.toggleButton?.onPress(input) // Simulate button press
                        return true
                    }
                }

                GLFW.GLFW_KEY_RIGHT -> {
                    if (selectedIndex != -1) {
                        val selectedToggle = scrollableContainer.widgets[selectedIndex] as? InfiniteFeatureToggle
                        selectedToggle?.onSettings?.invoke() // Open settings
                        return true
                    }
                }
            }
        }

        if (scrollableContainer.keyPressed(input)) {
            return true
        }
        return super.keyPressed(input)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        if (searchField.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true
        }
        if (scrollableContainer.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseDragged(
        click: Click,
        offsetX: Double,
        offsetY: Double,
    ): Boolean {
        if (searchField.mouseDragged(click, offsetX, offsetY)) {
            return true
        }
        if (scrollableContainer.mouseDragged(click, offsetX, offsetY)) {
            return true
        }
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: Click): Boolean {
        if (searchField.mouseReleased(click)) {
            return true
        }
        if (scrollableContainer.mouseReleased(click)) {
            return true
        }
        return super.mouseReleased(click)
    }

    override fun charTyped(input: CharInput): Boolean {
        if (searchField.charTyped(input)) {
            return true
        }
        if (scrollableContainer.charTyped(input)) {
            return true
        }
        return super.charTyped(input)
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        searchField.appendNarrations(builder)
        // scrollableContainer.appendClickableNarrations(builder) // Protected method
    }

    private enum class MoveWay {
        Top,
        Bottom,
    }

    private fun ensureSelectedVisible(moveWay: MoveWay) {
        if (selectedIndex == -1 || !::scrollableContainer.isInitialized) return

        val selectedWidget = scrollableContainer.widgets[selectedIndex]
        val widgetContentTop = selectedWidget.y - scrollableContainer.y + scrollableContainer.scrollY.toInt()
        val widgetHeight = selectedWidget.height

        // If the selected widget is above the visible area
        when (moveWay) {
            MoveWay.Top -> {
                if (widgetContentTop - widgetHeight < scrollableContainer.scrollY) {
                    scrollableContainer.scrollY = widgetContentTop.toDouble() - widgetHeight
                }
            }

            MoveWay.Bottom -> {
                if (widgetContentTop + 2 * widgetHeight > scrollableContainer.scrollY + scrollableContainer.height) {
                    scrollableContainer.scrollY =
                        (widgetContentTop + 2 * widgetHeight - scrollableContainer.height).toDouble()
                }
            }
        }
    }
}
