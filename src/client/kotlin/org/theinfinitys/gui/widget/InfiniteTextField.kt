package org.theinfinitys.gui.widget

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
import org.lwjgl.glfw.GLFW
import kotlin.math.max
import kotlin.math.min

class InfiniteTextField(
    private val textRenderer: TextRenderer,
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    text: Text,
    private val inputType: InputType = InputType.ANY_TEXT,
) : TextFieldWidget(textRenderer, x, y, width, height, text) {
    private var suggestions: List<String> = emptyList()
    private var suggestionIndex: Int = -1

    // --- 【追加】サジェストスクロール関連の変数 ---
    private var suggestionScrollY: Double = 0.0
    private val maxVisibleSuggestions = 5
    private val suggestionItemHeight = textRenderer.fontHeight + 2
    // ------------------------------------------

    enum class InputType {
        ANY_TEXT,
        NUMERIC,
        FLOAT,
        BLOCK_ID,
        ENTITY_ID,
        PLAYER_NAME,
    }

    init {
        updateSuggestions()
    }

    override fun charTyped(input: CharInput): Boolean {
        if (!isFocused) return false
        val chr = input.toString().toCharArray().first()
        // ID入力時は、IDに使用可能な文字のみ許可する
        if (inputType == InputType.BLOCK_ID || inputType == InputType.ENTITY_ID) {
            if (chr.isLetterOrDigit() || chr == ':' || chr == '_' || chr == '/') {
                val result = super.charTyped(input)
                updateSuggestions()
                return result
            }
            return false
        }

        val result = super.charTyped(input)
        // 数値/フロートの検証ロジック（簡略化）
//        if (result && (inputType == InputType.NUMERIC || inputType == InputType.FLOAT)) {
        // 有効性の詳細なチェックは keyPressed に依存
//        }
        return result
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
                InputType.BLOCK_ID -> Registries.BLOCK.ids.map { it.toString() }
                InputType.ENTITY_ID -> Registries.ENTITY_TYPE.ids.map { it.toString() }
                InputType.PLAYER_NAME ->
                    MinecraftClient
                        .getInstance()
                        .networkHandler
                        ?.playerList
                        ?.map { it.profile.name } ?: emptyList()

                else -> emptyList()
            }.filter { it.startsWith(text, ignoreCase = true) }
                .sorted()

        suggestionIndex = -1
        suggestionScrollY = 0.0
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (!isFocused) return super.keyPressed(input)

        // Tabキーでのオートコンプリート処理 (スクロール調整付き)
        val keyCode = input.key
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

                // Tabキーで選択が移動した際、スクロールを調整して選択項目を表示圏内に保つ
                val maxScrollIndex = max(0, suggestions.size - maxVisibleSuggestions).toDouble()
                if (suggestionIndex < suggestionScrollY.toInt()) {
                    suggestionScrollY = suggestionIndex.toDouble()
                } else if (suggestionIndex >= suggestionScrollY.toInt() + maxVisibleSuggestions) {
                    suggestionScrollY = (suggestionIndex - maxVisibleSuggestions + 1).toDouble()
                }
                suggestionScrollY = MathHelper.clamp(suggestionScrollY, 0.0, maxScrollIndex)

                text = suggestions[suggestionIndex]
                setCursorToEnd(false)
            }
            return true
        }

        // Enterキーでサジェストが選択されていたら、それを採用して終了
        if (keyCode == GLFW.GLFW_KEY_ENTER && suggestionIndex != -1 && suggestions.isNotEmpty()) {
            text = suggestions[suggestionIndex]
            suggestions = emptyList()
            setCursorToEnd(false)
            return true
        }

        // 共通の編集キーなどの処理
        val result = super.keyPressed(input)
        if ((inputType == InputType.BLOCK_ID || inputType == InputType.ENTITY_ID) &&
            (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE || input.hasCtrl())
        ) {
            updateSuggestions()
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
        val suggestionAreaHeight = maxVisibleSuggestions * suggestionItemHeight
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
    // ------------------------------------

    // --- 描画ロジック：スクロールとクリッピング ---
    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        deltaTicks: Float,
    ) {
        super.renderWidget(context, mouseX, mouseY, deltaTicks)

        if (isFocused && suggestions.isNotEmpty() && (inputType == InputType.BLOCK_ID || inputType == InputType.ENTITY_ID)) {
            val suggestionX = x
            val suggestionY = y + height + 2
            val suggestionWidth = width
            val contentCount = suggestions.size

            // 表示する最大領域の高さ
            val displayHeight = min(contentCount, maxVisibleSuggestions) * suggestionItemHeight

            // スクロールコンテナの背景
            context.fill(
                suggestionX,
                suggestionY,
                suggestionX + suggestionWidth,
                suggestionY + displayHeight,
                0xCC000000.toInt(),
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
                val backgroundColor = if (isSelected) 0xFF5555FF.toInt() else 0x00000000
                val textColor = if (isSelected) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt()

                // 選択されている候補の背景をハイライト
                if (isSelected) {
                    context.fill(
                        suggestionX,
                        currentY,
                        suggestionX + suggestionWidth,
                        currentY + suggestionItemHeight,
                        backgroundColor,
                    )
                }

                // テキストの描画
                context.drawTextWithShadow(textRenderer, suggestion, suggestionX + 2, currentY + 2, textColor)
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
                    0x44FFFFFF,
                )
                // スクロールつまみ (サム)
                context.fill(
                    scrollbarX,
                    scrollbarY.toInt(),
                    scrollbarX + scrollbarWidth,
                    (scrollbarY + scrollbarHeight).toInt(),
                    0xAAFFFFFF.toInt(),
                )
            }
        }
    }
    // -----------------------------------------------------

    // --- mouseClicked: フォーカス解除ロジックを修正 ---
    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        // 1. サジェストリスト内のクリック処理
        val suggestionAreaY = y + height + 2
        val suggestionAreaHeight = min(suggestions.size, maxVisibleSuggestions) * suggestionItemHeight
        val suggestionAreaBottom = suggestionAreaY + suggestionAreaHeight
        val mouseX = click.x
        val mouseY = click.y
        if (isFocused &&
            suggestions.isNotEmpty() &&
            (inputType == InputType.BLOCK_ID || inputType == InputType.ENTITY_ID) &&
            mouseX >= x &&
            mouseX < x + width &&
            mouseY >= suggestionAreaY &&
            mouseY < suggestionAreaBottom
        ) {
            val relativeMouseY = mouseY - suggestionAreaY
            val clickedVisibleIndex = (relativeMouseY / suggestionItemHeight).toInt()
            val clickedSuggestionIndex = suggestionScrollY.toInt() + clickedVisibleIndex

            if (clickedSuggestionIndex >= 0 && clickedSuggestionIndex < suggestions.size) {
                text = suggestions[clickedSuggestionIndex]
                setCursorToEnd(false)
                suggestions = emptyList() // 選択後はサジェストを非表示
                return true
            }
        }

        // 2. 通常のクリック処理
        // super.mouseClicked() がクリック位置に基づいて isFocused を適切に設定/解除します。
        return super.mouseClicked(click, doubled)
    }
    // -----------------------------------------------------------
}
