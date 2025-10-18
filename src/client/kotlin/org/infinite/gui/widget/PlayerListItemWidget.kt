package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.screen.narration.NarrationPart
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text

class PlayerListItemWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int, // This height is for the individual item
    private val playerName: String,
    private val onRemove: (String) -> Unit,
) : ClickableWidget(x, y, width, height, Text.literal(playerName)) {
    private val textRenderer = MinecraftClient.getInstance().textRenderer
    private val padding = 8
    private val removeButtonWidth = 20

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val textX = x + padding
        val textY = y + this.height / 2 - 4
        context.drawTextWithShadow(textRenderer, Text.literal(playerName), textX, textY, 0xFFAAAAAA.toInt())

        val removeButtonX = x + width - padding - removeButtonWidth
        val removeButtonY = y
        val isRemoveButtonHovered =
            mouseX >= removeButtonX &&
                mouseX < removeButtonX + removeButtonWidth &&
                mouseY >= removeButtonY &&
                mouseY < removeButtonY + this.height

        val removeColor = if (isRemoveButtonHovered) 0xFFAA4444.toInt() else 0xFF882222.toInt()

        context.fill(
            removeButtonX,
            removeButtonY,
            removeButtonX + removeButtonWidth,
            removeButtonY + this.height,
            removeColor,
        )
        context.drawText(
            textRenderer,
            "x",
            removeButtonX + removeButtonWidth / 2 - 3,
            removeButtonY + this.height / 2 - 4,
            0xFFFFFFFF.toInt(),
            false,
        )
    }

    /**
     * 新しいClickableWidgetに合わせて mouseClicked のシグネチャを修正しました。
     * 座標は Click オブジェクトから取得し、ボタンは buttonInfo().button() で確認します。
     */
    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        // マウスの座標を取得
        val mouseX = click.x()
        val mouseY = click.y()
        // 左クリック（ボタン0）でのみ処理を実行
        if (click.buttonInfo().button() == 0) {
            val removeButtonX = x + width - padding - removeButtonWidth
            val removeButtonY = y

            if (mouseX >= removeButtonX &&
                mouseX < removeButtonX + removeButtonWidth &&
                mouseY >= removeButtonY &&
                mouseY < removeButtonY + this.height
            ) {
                // ボタンクリック音を鳴らす
                this.playDownSound(MinecraftClient.getInstance().soundManager)
                onRemove(playerName)
                return true
            }
        }

        // それ以外の場合は親クラスの処理を呼び出す（ウィジェット全体のクリック処理など）
        return super.mouseClicked(click, doubled)
    }

    // keyPressed と charTyped は ClickableWidget の Element インターフェースのメソッドですが、
    // ClickableWidget の実装を継承しているため、ここでは削除またはコメントアウトします。
    // 新しい ClickableWidget ではこれらのメソッドをオーバーライドする必要性は低いですが、
    // エラーが出ている場合は残してください。ただし、ClickableWidgetが提供する Element の
    // 実装をそのまま利用することが推奨されます。

    // override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean = super.keyPressed(keyCode, scanCode, modifiers)
    // override fun charTyped(chr: Char, modifiers: Int): Boolean = super.charTyped(chr, modifiers)

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        // デフォルトのナレーションを追加してから、追加の情報を付与
        this.appendDefaultNarrations(builder)
        builder.put(NarrationPart.TITLE, Text.literal("Player List Item: $playerName"))
    }
}
