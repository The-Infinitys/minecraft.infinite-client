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
import org.lwjgl.glfw.GLFW

class InfinitePlayerListField(
    x: Int,
    y: Int,
    width: Int,
    totalHeight: Int, // This is the total height of the InfinitePlayerListField widget
    private val setting: FeatureSetting.PlayerListSetting,
) : ClickableWidget(x, y, width, totalHeight, Text.literal(setting.name)) {
    private val textRenderer = MinecraftClient.getInstance().textRenderer
    private val inputFieldHeight = 20
    private val padding = 5
    private val buttonSize = inputFieldHeight // Add button size is same as input field height

    private val baseLabelHeight = textRenderer.fontHeight
    private val descriptionHeight =
        if (setting.descriptionKey != null &&
            setting.descriptionKey!!.isNotBlank()
        ) {
            textRenderer.fontHeight + 2
        } else {
            0
        }
    private val totalLabelHeight = baseLabelHeight + descriptionHeight
    private val minHeaderHeight = totalLabelHeight + padding + inputFieldHeight + padding

    private val headerHeight: Int
    private val scrollableListHeight: Int

    private val textField: InfiniteTextField
    private lateinit var scrollableContainer: InfiniteScrollableContainer
    private var isScrollableContainerInitialized = false
    private val playerItemWidgets = mutableListOf<PlayerListItemWidget>()

    init {
        // Calculate header and scrollable list heights based on totalHeight
        val calculatedHeaderHeight = (totalHeight * 0.2).toInt()
        headerHeight = if (calculatedHeaderHeight < minHeaderHeight) minHeaderHeight else calculatedHeaderHeight
        scrollableListHeight = totalHeight - headerHeight

        // Initialize textField
        val labelWidth = textRenderer.getWidth(setting.name)
        val inputFieldWidth =
            width - (padding * 3) - labelWidth - buttonSize // Adjust width for padding, label, and button

        textField =
            InfiniteTextField(
                textRenderer,
                0, // x will be set in renderWidget
                0, // y will be set in renderWidget
                inputFieldWidth,
                inputFieldHeight,
                Text.literal(""),
                InfiniteTextField.InputType.PLAYER_NAME,
            )

        textField.setChangedListener { newText ->
            currentInput = newText.trim()
        }

        // Initialize scrollableContainer
        updateScrollableContainer()
    }

    private var currentInput: String = ""

    private fun updateScrollableContainer() {
        playerItemWidgets.clear()

        // 既存のscrollableContainerがあればそのscrollYを取得、なければ0.0を使用
        val scrolledY =
            if (::scrollableContainer.isInitialized) {
                scrollableContainer.scrollY
            } else {
                // Use 0.0 if not initialized (as requested)
                0.0
            }

        // 1. スクロールコンテナの利用可能幅を先に計算
        val containerWidth = width - padding * 2

        // 3. 子ウィジェットの幅を計算
        // ScrollContainerの内部ロジックを信頼し、コンテナの幅（containerWidth）から
        // 便宜上のパディングを引いた値を子の幅とします。
        val itemWidth = containerWidth - padding * 2

        setting.value.forEach { name ->
            playerItemWidgets.add(
                PlayerListItemWidget(
                    0, // x will be set by scrollable container
                    0, // y will be set by scrollable container
                    // 親の ScrollContainer の幅に依存する形で itemWidth を設定
                    itemWidth,
                    textRenderer.fontHeight + padding * 2, // Height of each item
                    name,
                ) { playerNameToRemove ->
                    removeNameFromList(playerNameToRemove)
                },
            )
        }

        // 4. 正しいリストで ScrollableContainer を初期化（または再初期化）
        // 既存の scrollableContainer が null 許容型でない場合（元のコードの文脈からそうではない可能性が高いですが）、
        // ここで上書きして問題ありません。
        scrollableContainer =
            InfiniteScrollableContainer(
                x + padding,
                y + headerHeight,
                containerWidth,
                scrollableListHeight,
                playerItemWidgets.toMutableList(),
            )

        // 5. スクロール位置を復元
        // scrollableContainer が null 許容型で定義されていることを前提とします。
        // もし null でなければ、安全に scrollY を設定します。
        scrollableContainer.scrollY = scrolledY
    }

    private fun addNameToList() {
        if (currentInput.isNotBlank()) {
            val nameToAdd = currentInput.trim()
            if (nameToAdd.isNotBlank() && !setting.value.contains(nameToAdd)) {
                setting.value.add(nameToAdd)
                updateScrollableContainer()
                textField.text = ""
                currentInput = ""
            }
        }
    }

    override fun setPosition(
        x: Int,
        y: Int,
    ) {
        // 1. 親の ClickableWidget の x/y を更新
        super.setPosition(x, y)

        // 2. 内側のウィジェットの絶対座標を更新

        // TextField の絶対座標を更新
        textField.x = this.x + padding
        textField.y = this.y + totalLabelHeight + padding

        // ScrollableContainer の絶対座標を更新
        val newContainerX = this.x + padding
        val newContainerY = this.y + headerHeight

        // setPosition を呼び出すことで、InfiniteScrollableContainer 内部の
        // updateWidgetPositions() がトリガーされ、子の PlayerListItemWidget も位置が更新される。
        scrollableContainer.setPosition(newContainerX, newContainerY)
    }

    private fun removeNameFromList(nameToRemove: String) {
        if (setting.value.remove(nameToRemove)) {
            updateScrollableContainer()
        }
    }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        if (!isScrollableContainerInitialized) {
            updateScrollableContainer()
            isScrollableContainerInitialized = true
        }
        // ScrollableContainerを描画
        val containerX = x + padding
        val containerY = y + totalLabelHeight + padding * 2 + textField.height
        scrollableContainer.setPosition(containerX, containerY)
        scrollableContainer.render(context, mouseX, mouseY, delta)

        val labelX = x + padding
        context.drawTextWithShadow(
            textRenderer,
            Text.translatable(setting.name),
            labelX,
            y + padding,
            0xFFFFFFFF.toInt(),
        )
        if (setting.descriptionKey != null && setting.descriptionKey!!.isNotBlank()) {
            context.drawTextWithShadow(
                textRenderer,
                Text.translatable(setting.descriptionKey!!),
                labelX,
                y + padding + baseLabelHeight + 2,
                0xFFA0A0A0.toInt(),
            )
        }

        // TextFieldの位置を調整
        val textFieldX = x + padding
        val textFieldY = y + totalLabelHeight + padding
        textField.x = textFieldX
        textField.y = textFieldY
        textField.render(context, mouseX, mouseY, delta)
        // 追加ボタン (+) を描画
        val addButtonX = textField.x + textField.width + padding
        val isAddButtonHovered =
            mouseX >= addButtonX && mouseX < addButtonX + buttonSize && mouseY >= textFieldY && mouseY < textFieldY + buttonSize

        context.fill(
            addButtonX,
            textFieldY,
            addButtonX + buttonSize,
            textFieldY + buttonSize,
            if (isAddButtonHovered) 0xFF44AA44.toInt() else 0xFF228822.toInt(),
        )
        context.drawText(
            textRenderer,
            "+",
            addButtonX + buttonSize / 2 - 3,
            textFieldY + buttonSize / 2 - 4,
            0xFFFFFFFF.toInt(),
            false,
        )
    }

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        if (textField.mouseClicked(click, doubled)) {
            textField.isFocused = true
            return true
        }

        val addButtonX = textField.x + textField.width + padding
        val addButtonY = textField.y
        val mouseX = click.x
        val mouseY = click.y
        if (mouseX >= addButtonX && mouseX < addButtonX + buttonSize && mouseY >= addButtonY && mouseY < addButtonY + buttonSize) {
            addNameToList()
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
        if (scrollableContainer.mouseReleased(click)) {
            return true
        }
        return super.mouseReleased(click)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        val keyCode = input.key
        if (keyCode == GLFW.GLFW_KEY_ENTER && textField.isFocused) {
            addNameToList()
            return true
        }
        if (textField.keyPressed(input)) {
            return true
        }
        if (scrollableContainer.keyPressed(input)) {
            return true
        }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharInput): Boolean {
        if (textField.charTyped(input)) {
            return true
        }
        if (scrollableContainer.charTyped(input)) {
            return true
        }
        return super.charTyped(input)
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        textField.appendNarrations(builder)
        // Removed: scrollableContainer.appendClickableNarrations(builder) as it's a protected method.
    }
}
