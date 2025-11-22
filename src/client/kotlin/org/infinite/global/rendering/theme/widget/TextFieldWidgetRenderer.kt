package org.infinite.global.rendering.theme.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.cursor.StandardCursors
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.OrderedText
import net.minecraft.util.Util
import net.minecraft.util.math.MathHelper
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics2D
import java.util.Objects
import kotlin.math.min

class TextFieldWidgetRenderer(
    val widget: TextFieldWidget,
) {
    private val client: MinecraftClient = MinecraftClient.getInstance()
    private val textRenderer: TextRenderer = client.textRenderer

    fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        if (widget.isVisible) {
            val graphics2D = Graphics2D(context)
            // テーマ色を取得
            val colors = InfiniteClient.currentColors()
            val backgroundColor = colors.backgroundColor
            val primaryColor = colors.primaryColor // 枠線に適用
            val foregroundColor = colors.foregroundColor
            val secondaryColor = colors.secondaryColor
            val infoColor = colors.infoColor // 提案テキスト（Suggestion）に適用（元のコードは-8355712）

            // 1. 背景と枠線の描画
            if (widget.drawsBackground()) {
                graphics2D.fill(
                    widget.x,
                    widget.y,
                    widget.getWidth(),
                    widget.getHeight(),
                    backgroundColor,
                )
                graphics2D.drawBorder(
                    widget.x,
                    widget.y,
                    widget.width,
                    widget.height,
                    primaryColor, // アクセントカラーとしてprimaryColorを使用
                    1,
                )
            }

            // 2. テキストの色設定
            val textColor: Int = if (widget.isEditable) foregroundColor else secondaryColor

            // 3. 描画するテキストの準備
            // カーソル（キャレット）の描画位置（stringの先頭からのインデックス）
            val startCursorIndexInScreenString: Int = widget.selectionStart - widget.firstCharacterIndex

            // 画面幅に収まるように切り詰められたテキスト
            val screenVisibleText: String =
                textRenderer.trimToWidth(
                    widget.text.substring(widget.firstCharacterIndex),
                    widget.innerWidth,
                )

            // 選択開始インデックスが画面上の表示範囲内にあるか
            val isSelectionStartVisible =
                startCursorIndexInScreenString >= 0 && startCursorIndexInScreenString <= screenVisibleText.length

            // カーソル点滅ロジック: フォーカスがあり、時間で点滅、かつ選択開始位置が表示されているか
            val shouldDrawBlinkingCursor =
                widget.isFocused && (Util.getMeasuringTimeMs() - widget.lastSwitchFocusTime) / 300L % 2L == 0L && isSelectionStartVisible

            // 選択終了インデックス（画面上の表示範囲にクランプ）
            val endCursorIndexInScreenString =
                MathHelper.clamp(widget.selectionEnd - widget.firstCharacterIndex, 0, screenVisibleText.length)

            // 現在のテキスト描画X座標
            var textRenderX: Int = widget.textX

            // 4. 選択範囲の前のテキストの描画
            if (!screenVisibleText.isEmpty()) {
                val textBeforeCursor =
                    if (isSelectionStartVisible) screenVisibleText.take(startCursorIndexInScreenString) else screenVisibleText
                val orderedTextBeforeCursor: OrderedText? = widget.format(textBeforeCursor, widget.firstCharacterIndex)

                context.drawText(
                    textRenderer,
                    orderedTextBeforeCursor,
                    textRenderX,
                    widget.textY,
                    textColor,
                    widget.textShadow,
                )
                textRenderX += textRenderer.getWidth(orderedTextBeforeCursor) + 1
            }

            // 5. カーソル（キャレット）の位置計算
            // テキストの末尾（最大長）に達していない、または最大長に達しているか
            val isCursorAtEndOrMaximized =
                widget.selectionStart < widget.text.length || widget.text.length >= widget.maxLength

            // カーソル描画X座標の初期値
            var cursorRenderX = textRenderX

            if (!isSelectionStartVisible) {
                // カーソルが画面外（左側）の場合
                cursorRenderX = if (startCursorIndexInScreenString > 0) widget.textX + widget.width else widget.textX
            } else if (isCursorAtEndOrMaximized) {
                // カーソルが選択範囲の前のテキストの直後（文字と文字の間）
                cursorRenderX = textRenderX - 1
                --textRenderX // 次のテキスト描画X座標を1戻す
            }

            // 6. 選択範囲の後ろのテキストの描画
            if (!screenVisibleText.isEmpty() && isSelectionStartVisible && startCursorIndexInScreenString < screenVisibleText.length) {
                context.drawText(
                    textRenderer,
                    widget.format(screenVisibleText.substring(startCursorIndexInScreenString), widget.selectionStart),
                    textRenderX,
                    widget.textY,
                    textColor,
                    widget.textShadow,
                )
            }

            // 7. プレースホルダーテキストの描画
            if (widget.placeholder != null && screenVisibleText.isEmpty() && !widget.isFocused) {
                // プレースホルダーの描画位置は、テキストの描画が始まる位置 (k) から
                context.drawTextWithShadow(
                    textRenderer,
                    widget.placeholder,
                    textRenderX,
                    widget.textY,
                    secondaryColor,
                ) // secondaryColorで目立たなく
            }

            // 8. 提案テキスト（Suggestion）の描画
            if (!isCursorAtEndOrMaximized && widget.suggestion != null) {
                context.drawText(
                    textRenderer,
                    widget.suggestion,
                    cursorRenderX - 1, // カーソル位置から
                    widget.textY,
                    infoColor,
                    widget.textShadow,
                )
            }

            // 9. 選択範囲（ハイライト）の描画
            if (endCursorIndexInScreenString != startCursorIndexInScreenString) {
                val endSelectionX: Int =
                    widget.textX + textRenderer.getWidth(screenVisibleText.take(endCursorIndexInScreenString))

                // 描画範囲のクランプ処理を分かりやすい変数名に
                val selectionStartXClamped = min(cursorRenderX, widget.x + widget.width)
                val selectionEndXClamped = min(endSelectionX - 1, widget.x + widget.width)

                // 選択範囲のY座標
                val selectionYStart: Int = widget.textY - 1
                Objects.requireNonNull<TextRenderer?>(textRenderer)
                val selectionYEnd: Int = selectionYStart + 1 + 9 // テキストの高さに依存

                context.drawSelection(selectionStartXClamped, selectionYStart, selectionEndXClamped, selectionYEnd)
            }

            // 10. ブリンキングカーソル（キャレット）の描画
            if (shouldDrawBlinkingCursor) {
                if (isCursorAtEndOrMaximized) {
                    // カーソルが文字の間にある場合（縦棒）
                    val caretYStart: Int = widget.textY - 1
                    val caretXEnd = cursorRenderX + 1
                    Objects.requireNonNull<TextRenderer?>(textRenderer)
                    val caretYEnd: Int = caretYStart + 1 + 9 // テキストの高さに依存

                    context.fill(
                        cursorRenderX,
                        caretYStart,
                        caretXEnd,
                        caretYEnd,
                        foregroundColor,
                    ) // foregroundColorで描画
                } else {
                    // カーソルがテキストの末尾の後の場合（アンダースコア）
                    context.drawText(textRenderer, "_", cursorRenderX, widget.textY, foregroundColor, widget.textShadow)
                }
            }

            // 11. マウスカーソルの設定
            if (widget.isHovered) {
                context.setCursor(if (widget.isEditable) StandardCursors.IBEAM else StandardCursors.NOT_ALLOWED)
            }
        }
    }
}
