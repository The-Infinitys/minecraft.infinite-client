package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.math.MathHelper
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.rendering.drawBorder
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

class InfiniteTextField(
    textRenderer: TextRenderer,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    text: Text,
    private val inputType: InputType = InputType.ANY_TEXT,
) : TextFieldWidget(textRenderer, x, y, width, height, text) {
    private var suggestions: List<String> = emptyList()
    private var suggestionIndex: Int = -1

    // --- サジェストスクロール関連の変数 ---
    private var suggestionScrollY: Double = 0.0
    private val maxVisibleSuggestions = 5
    private val suggestionItemHeight = textRenderer.fontHeight + 2

    enum class InputType {
        ANY_TEXT,
        NUMERIC,
        FLOAT,
        BLOCK_ID,
        ENTITY_ID,
        PLAYER_NAME,
        HEX_COLOR,
    }

    init {
        updateSuggestions()
    }

    override fun charTyped(input: CharInput): Boolean {
        if (!isFocused) return false
        val inputString = input.asString()
        if (inputString.isEmpty()) return false
        val chr = inputString.toCharArray().first()
        val canType =
            when (inputType) {
                InputType.BLOCK_ID, InputType.ENTITY_ID -> {
                    chr.isLetterOrDigit() || chr == ':' || chr == '_' || chr == '/'
                }

                InputType.HEX_COLOR -> {
                    chr.isDigit() || (chr.lowercaseChar() in 'a'..'f')
                }

                InputType.NUMERIC -> {
                    chr.isDigit() || chr == '-'
                }

                InputType.FLOAT -> {
                    chr.isDigit() || chr == '-' || chr == '.'
                }

                else -> {
                    true
                }
            }
        if (canType) {
            super.write(input.asString())
            updateSuggestions()
            return true
        } else {
            return false
        }
    }

    private fun updateSuggestions() {
        if (text.isEmpty()) {
            suggestions = emptyList()
            suggestionIndex = -1
            suggestionScrollY = 0.0
            return
        }

        suggestions =
            when (inputType) {
                InputType.BLOCK_ID -> {
                    Registries.BLOCK.ids.map { it.toString() }
                }

                InputType.ENTITY_ID -> {
                    Registries.ENTITY_TYPE.ids.map { it.toString() }
                }

                InputType.PLAYER_NAME -> {
                    MinecraftClient
                        .getInstance()
                        .networkHandler
                        ?.playerList
                        ?.map { it.profile.name } ?: emptyList()
                }

                else -> {
                    emptyList()
                }
            }.filter { it.startsWith(text, ignoreCase = true) }
                .sorted()

        suggestionIndex = -1
        suggestionScrollY = 0.0
    }

    override fun keyPressed(input: KeyInput): Boolean {
        val keyCode = input.key
        if (!isFocused) {
            return super.keyPressed(input)
        }

        // --- Tabキーによるオートコンプリート処理 ---
        if (keyCode == GLFW.GLFW_KEY_TAB && (inputType == InputType.BLOCK_ID || inputType == InputType.ENTITY_ID)) {
            if (suggestions.isNotEmpty()) {
                suggestionIndex =
                    if (input.hasShift()) {
                        // Shift + Tab で前の候補
                        (suggestionIndex - 1 + suggestions.size) % suggestions.size
                    } else {
                        // Tab で次の候補
                        (suggestionIndex + 1) % suggestions.size
                    }

                // スクロールを調整して選択項目を表示圏内に保つ
                val maxScrollIndex = max(0, suggestions.size - maxVisibleSuggestions).toDouble()
                if (suggestionIndex < suggestionScrollY.toInt()) {
                    suggestionScrollY = suggestionIndex.toDouble()
                } else if (suggestionIndex >= suggestionScrollY.toInt() + maxVisibleSuggestions) {
                    suggestionScrollY = (suggestionIndex - maxVisibleSuggestions + 1).toDouble()
                }
                suggestionScrollY = MathHelper.clamp(suggestionScrollY, 0.0, maxScrollIndex)

                text = suggestions[suggestionIndex]
                setCursorToEnd(false)
                return true // 処理完了。親クラスの処理をスキップ
            }
        }

        // --- Enterキーによるサジェスト採用処理 ---
        if (keyCode == GLFW.GLFW_KEY_ENTER && suggestionIndex != -1 && suggestions.isNotEmpty()) {
            text = suggestions[suggestionIndex]
            suggestions = emptyList()
            setCursorToEnd(false)
            return true // 処理完了。親クラスの処理をスキップ
        }

        // --- それ以外のキー処理は親クラスに委譲し、結果を取得 ---
        val result = super.keyPressed(input)

        // キー入力の結果、テキストが変更された可能性がある場合、サジェストを更新
        if (result && (inputType == InputType.BLOCK_ID || inputType == InputType.ENTITY_ID)) {
            // BACKSPACE, DELETE, Ctrl + V/X/Aなどの操作後にサジェストを更新
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE || input.hasCtrl()) {
                updateSuggestions()
            }
            // その他のキー処理では charTyped で更新されるためここでは不要だが、
            // 念のため、一般的なキー入力ではない場合に処理を限定する。
        }
        return result
    }

    // --- マウススクロール処理 ---
    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        // サジェストリストが表示されている領域を判定
        val suggestionAreaY = y + height + 2
        val suggestionAreaHeight = min(suggestions.size, maxVisibleSuggestions) * suggestionItemHeight
        val suggestionAreaBottom = suggestionAreaY + suggestionAreaHeight

        if (isFocused &&
            suggestions.size > maxVisibleSuggestions &&
            (inputType == InputType.BLOCK_ID || inputType == InputType.ENTITY_ID) &&
            mouseX >= x &&
            mouseX < x + width &&
            mouseY >= suggestionAreaY &&
            mouseY < suggestionAreaBottom
        ) {
            val contentSize = suggestions.size.toDouble()
            val maxScrollIndex = max(0.0, contentSize - maxVisibleSuggestions)

            // スクロール量を調整
            suggestionScrollY = MathHelper.clamp(suggestionScrollY - verticalAmount, 0.0, maxScrollIndex)
            return true
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun drawsBackground(): Boolean = false

    // --- 描画ロジック：スクロールとクリッピング ---
    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        deltaTicks: Float,
    ) {
        val graphics2D = Graphics2D(context, MinecraftClient.getInstance().renderTickCounter)

        // 1. テキストフィールドの背景と枠線の描画
        context.fill(x, y, x + width, y + height, InfiniteClient.currentColors().backgroundColor)
        context.drawBorder(x, y, width, height, InfiniteClient.currentColors().primaryColor)

        // 2. 親クラスの描画（テキスト、カーソル、選択範囲）
        super.renderWidget(context, mouseX, mouseY, deltaTicks)

        // 3. サジェストリストの描画
        if (isFocused && suggestions.isNotEmpty() && (inputType == InputType.BLOCK_ID || inputType == InputType.ENTITY_ID)) {
            val suggestionX = x
            val suggestionY = y + height + 2
            val suggestionWidth = width
            val contentCount = suggestions.size

            // 表示する最大領域の高さ
            val displayHeight = min(suggestions.size, maxVisibleSuggestions) * suggestionItemHeight

            // スクロールコンテナの背景
            context.fill(
                suggestionX,
                suggestionY,
                suggestionX + suggestionWidth,
                suggestionY + displayHeight,
                InfiniteClient.currentColors().backgroundColor,
            )

            // クリップ (Scissor) を設定して、枠外の描画を防ぐ
            context.enableScissor(suggestionX, suggestionY, suggestionX + suggestionWidth, suggestionY + displayHeight)

            val startScrollIndex = suggestionScrollY.toInt()

            // 描画する候補の範囲を決定
            val endRenderIndex = min(suggestions.size, startScrollIndex + maxVisibleSuggestions)

            for (i in startScrollIndex until endRenderIndex) {
                val suggestion = suggestions[i]

                // スクロール位置を考慮した相対的なY座標
                val relativeY = (i - startScrollIndex) * suggestionItemHeight
                val currentY = suggestionY + relativeY

                val isSelected = i == suggestionIndex

                // 選択色と非選択色
                val selectedBgColor = InfiniteClient.currentColors().secondaryColor
                val defaultTextColor = InfiniteClient.currentColors().foregroundColor

                // 選択されている候補の背景をハイライト
                if (isSelected) {
                    context.fill(
                        suggestionX,
                        currentY,
                        suggestionX + suggestionWidth,
                        currentY + suggestionItemHeight,
                        selectedBgColor,
                    )
                }

                // テキストの描画
                val textColor = if (isSelected) selectedBgColor else defaultTextColor
                graphics2D.drawText(suggestion, suggestionX + 2, currentY + 2, textColor, true)
            }

            context.disableScissor()

            // スクロールバーの描画 (候補が多い場合のみ)
            if (contentCount > maxVisibleSuggestions) {
                val scrollbarWidth = 4
                val scrollbarX = suggestionX + suggestionWidth - scrollbarWidth

                val visibleRatio = maxVisibleSuggestions.toDouble() / contentCount
                val scrollbarHeight = (displayHeight * visibleRatio).toInt()

                val maxScroll = contentCount - maxVisibleSuggestions
                val scrollRatio = suggestionScrollY / maxScroll.toDouble()
                val scrollbarY = suggestionY + (displayHeight - scrollbarHeight) * scrollRatio.toFloat()

                // スクロールトラック (背景)
                context.fill(
                    scrollbarX,
                    suggestionY,
                    scrollbarX + scrollbarWidth,
                    suggestionY + displayHeight,
                    InfiniteClient
                        .currentColors()
                        .backgroundColor,
                )
                // スクロールつまみ (サム)
                context.fill(
                    scrollbarX,
                    scrollbarY.toInt(),
                    scrollbarX + scrollbarWidth,
                    (scrollbarY + scrollbarHeight).toInt(),
                    InfiniteClient
                        .currentColors()
                        .foregroundColor,
                )
            }
        }
    }

    // --- mouseClicked: サジェスト選択とフォーカス管理 ---
    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        val mouseX = click.x
        val mouseY = click.y

        // 1. サジェストリスト内のクリック処理
        if (isFocused && suggestions.isNotEmpty() && (inputType == InputType.BLOCK_ID || inputType == InputType.ENTITY_ID)) {
            val suggestionAreaY = y + height + 2
            val suggestionAreaHeight = min(suggestions.size, maxVisibleSuggestions) * suggestionItemHeight
            val suggestionAreaBottom = suggestionAreaY + suggestionAreaHeight

            if (mouseX >= x && mouseX < x + width && mouseY >= suggestionAreaY && mouseY < suggestionAreaBottom) {
                val relativeMouseY = mouseY - suggestionAreaY
                val clickedVisibleIndex = (relativeMouseY / suggestionItemHeight).toInt()
                val clickedSuggestionIndex = suggestionScrollY.toInt() + clickedVisibleIndex

                if (clickedSuggestionIndex >= 0 && clickedSuggestionIndex < suggestions.size) {
                    text = suggestions[clickedSuggestionIndex]
                    setCursorToEnd(false)
                    suggestions = emptyList() // 選択後はサジェストを非表示
                    return true // クリックイベントを消費
                }
            }
        }
        // 2. 通常のクリック処理
        // super.mouseClicked() が isFocused の設定/解除とカーソルの位置設定を行います。
        return super.mouseClicked(click, doubled)
    }
}
