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
import org.infinite.libs.graphics.Graphics2D
import org.infinite.settings.FeatureSetting
import org.lwjgl.glfw.GLFW

class InfiniteBlockColorListField(
    x: Int,
    y: Int,
    width: Int,
    totalHeight: Int, // This is the total height of the InfiniteBlockColorListField widget
    private val setting: FeatureSetting.BlockColorListSetting,
) : ClickableWidget(x, y, width, totalHeight, Text.literal(setting.name)) {
    private val textRenderer = MinecraftClient.getInstance().textRenderer
    private val inputFieldHeight = 20
    private val padding = 5
    private val buttonSize = inputFieldHeight // Add button size is same as input field height

    private val baseLabelHeight = textRenderer.fontHeight
    private val descriptionHeight = textRenderer.fontHeight + 2
    private val totalLabelHeight = baseLabelHeight + descriptionHeight
    private val minHeaderHeight = totalLabelHeight + padding + inputFieldHeight + padding

    private val headerHeight: Int
    private val scrollableListHeight: Int

    private val blockIdTextField: InfiniteTextField
    private val colorTextField: InfiniteTextField
    private lateinit var scrollableContainer: InfiniteScrollableContainer
    private var isScrollableContainerInitialized = false
    private val blockColorItemWidgets = mutableListOf<BlockColorListItemWidget>()

    init {
        // Calculate header and scrollable list heights based on totalHeight
        val calculatedHeaderHeight = (totalHeight * 0.2).toInt()
        headerHeight = if (calculatedHeaderHeight < minHeaderHeight) minHeaderHeight else calculatedHeaderHeight
        scrollableListHeight = totalHeight - headerHeight

        // Initialize blockIdTextField
        val labelWidth = textRenderer.getWidth(setting.name)
        val inputFieldWidth = (width - (padding * 4) - labelWidth - buttonSize) / 2 // Split width for block ID and color

        blockIdTextField =
            InfiniteTextField(
                textRenderer,
                0, // x will be set in renderWidget
                0, // y will be set in renderWidget
                inputFieldWidth,
                inputFieldHeight,
                Text.literal("Block ID"),
                InfiniteTextField.InputType.BLOCK_ID,
            )

        blockIdTextField.setChangedListener { newText ->
            currentBlockIdInput = newText.trim()
        }

        // Initialize colorTextField
        colorTextField =
            InfiniteTextField(
                textRenderer,
                0, // x will be set in renderWidget
                0, // y will be set in renderWidget
                inputFieldWidth,
                inputFieldHeight,
                Text.literal("ARGB Color (e.g., FFAABBCC)"),
                InfiniteTextField.InputType.HEX_COLOR, // New InputType for hex color
            )

        colorTextField.setChangedListener { newText ->
            currentColorInput = newText.trim()
        }

        // Initialize scrollableContainer
        updateScrollableContainer()
    }

    private var currentBlockIdInput: String = ""
    private var currentColorInput: String = ""

    private fun updateScrollableContainer() {
        blockColorItemWidgets.clear()

        val scrolledY =
            if (::scrollableContainer.isInitialized) {
                scrollableContainer.scrollY
            } else {
                0.0
            }

        val containerWidth = width - padding * 2
        val itemWidth = containerWidth - padding * 2

        setting.value.forEach { (blockId, color) ->
            blockColorItemWidgets.add(
                BlockColorListItemWidget(
                    0,
                    0,
                    itemWidth,
                    textRenderer.fontHeight + padding * 2,
                    blockId,
                    color,
                ) { blockIdToRemove ->
                    removeIdFromList(blockIdToRemove)
                },
            )
        }

        scrollableContainer =
            InfiniteScrollableContainer(
                x + padding,
                y + headerHeight,
                containerWidth,
                scrollableListHeight,
                blockColorItemWidgets.toMutableList(),
            )

        scrollableContainer.scrollY = scrolledY
    }

    private fun addIdToList() {
        if (currentBlockIdInput.isNotBlank() && currentColorInput.isNotBlank()) {
            val idToAdd = currentBlockIdInput.trim()
            val colorToAdd = currentColorInput.trim().toLongOrNull(16)?.toInt() // Convert hex string to Int

            if (idToAdd.isNotBlank() && colorToAdd != null && !setting.value.containsKey(idToAdd)) {
                setting.value[idToAdd] = colorToAdd
                updateScrollableContainer()
                blockIdTextField.text = ""
                colorTextField.text = ""
                currentBlockIdInput = ""
                currentColorInput = ""
            }
        }
    }

    override fun setPosition(
        x: Int,
        y: Int,
    ) {
        super.setPosition(x, y)

        blockIdTextField.x = this.x + padding
        blockIdTextField.y = this.y + totalLabelHeight + padding

        colorTextField.x = this.x + padding + blockIdTextField.width + padding
        colorTextField.y = this.y + totalLabelHeight + padding

        val newContainerX = this.x + padding
        val newContainerY = this.y + headerHeight

        scrollableContainer.setPosition(newContainerX, newContainerY)
    }

    private fun removeIdFromList(idToRemove: String) {
        if (setting.value.remove(idToRemove) != null) {
            updateScrollableContainer()
        }
    }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val graphics2D = Graphics2D(context, MinecraftClient.getInstance().renderTickCounter)

        if (!isScrollableContainerInitialized) {
            updateScrollableContainer()
            isScrollableContainerInitialized = true
        }

        val containerX = x + padding
        val containerY = y + totalLabelHeight + padding * 2 + blockIdTextField.height
        scrollableContainer.setPosition(containerX, containerY)
        scrollableContainer.render(context, mouseX, mouseY, delta)

        val labelX = x + padding
        graphics2D.drawText(
            Text.translatable(setting.name),
            labelX,
            y + padding,
            InfiniteClient
                .getCurrentColors()
                .foregroundColor,
            true, // shadow = true
        )
        if (setting.descriptionKey.isNotBlank()) {
            graphics2D.drawText(
                Text.translatable(setting.descriptionKey),
                labelX,
                y + padding + baseLabelHeight + 2,
                InfiniteClient.getCurrentColors().foregroundColor,
                true, // shadow = true
            )
        }

        // Block ID TextField
        val blockIdTextFieldX = x + padding
        val blockIdTextFieldY = y + totalLabelHeight + padding
        blockIdTextField.x = blockIdTextFieldX
        blockIdTextField.y = blockIdTextFieldY + (inputFieldHeight - blockIdTextField.height) / 2
        blockIdTextField.render(context, mouseX, mouseY, delta)

        // Color TextField
        val colorTextFieldX = blockIdTextFieldX + blockIdTextField.width + padding
        val colorTextFieldY = y + totalLabelHeight + padding
        colorTextField.x = colorTextFieldX
        colorTextField.y = colorTextFieldY + (inputFieldHeight - colorTextField.height) / 2
        colorTextField.render(context, mouseX, mouseY, delta)

        // Add Button
        val addButtonX = colorTextField.x + colorTextField.width + padding
        val addButtonY = y + totalLabelHeight + padding
        val isAddButtonHovered =
            mouseX >= addButtonX && mouseX < addButtonX + buttonSize && mouseY >= addButtonY && mouseY < addButtonY + buttonSize

        context.fill(
            addButtonX,
            addButtonY,
            addButtonX + buttonSize,
            addButtonY + buttonSize,
            if (isAddButtonHovered) {
                InfiniteClient
                    .getCurrentColors()
                    .primaryColor
            } else {
                InfiniteClient
                    .getCurrentColors()
                    .greenAccentColor
            },
        )
        graphics2D.drawText(
            "+",
            addButtonX + buttonSize / 2 - 3,
            addButtonY + buttonSize / 2 - 4,
            InfiniteClient
                .getCurrentColors()
                .foregroundColor,
            false, // shadow = false
        )
    }

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        if (blockIdTextField.mouseClicked(click, doubled)) {
            blockIdTextField.isFocused = true
            colorTextField.isFocused = false
            return true
        }
        if (colorTextField.mouseClicked(click, doubled)) {
            colorTextField.isFocused = true
            blockIdTextField.isFocused = false
            return true
        }

        val addButtonX = colorTextField.x + colorTextField.width + padding
        val addButtonY = y + totalLabelHeight + padding
        val mouseX = click.x
        val mouseY = click.y
        if (mouseX >= addButtonX && mouseX < addButtonX + buttonSize && mouseY >= addButtonY && mouseY < addButtonY + buttonSize) {
            addIdToList()
            return true
        }

        if (scrollableContainer.mouseClicked(click, doubled)) {
            return true
        }

        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
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
        if (scrollableContainer.mouseDragged(click, offsetX, offsetY)) return true
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: Click): Boolean {
        if (scrollableContainer.mouseReleased(click)) return true
        return super.mouseReleased(click)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        val keyCode = input.key

        if (keyCode == GLFW.GLFW_KEY_ENTER) {
            if (blockIdTextField.isFocused || colorTextField.isFocused) {
                addIdToList()
                return true
            }
        }
        if (blockIdTextField.keyPressed(input)) {
            return true
        }
        if (colorTextField.keyPressed(input)) {
            return true
        }
        if (scrollableContainer.keyPressed(input)) {
            return true
        }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharInput): Boolean {
        if (blockIdTextField.charTyped(input)) {
            return true
        }
        if (colorTextField.charTyped(input)) {
            return true
        }
        if (scrollableContainer.charTyped(input)) {
            return true
        }
        return super.charTyped(input)
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        blockIdTextField.appendNarrations(builder)
        colorTextField.appendNarrations(builder)
    }
}
