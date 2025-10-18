package org.infinite.gui.widget

import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.screen.narration.NarrationPart
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.MathHelper
import org.infinite.utils.rendering.drawBorder

class BlockListItemWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int, // This height is for the individual item
    private val blockId: String,
    private val onRemove: (String) -> Unit,
) : ClickableWidget(x, y, width, height, Text.literal(blockId)) {
    private val textRenderer = MinecraftClient.getInstance().textRenderer
    private val padding = 8
    private val iconSize = 16
    private val iconPadding = 2
    private val iconTotalWidth = iconSize + iconPadding
    private val removeButtonWidth = 20
    private val removeButtonHeight = height

    // 削除ボタンのクリック可能領域を計算
    private val removeButtonX: Int
        get() = this.x + this.width - padding - removeButtonWidth
    private val removeButtonY: Int
        get() = this.y

    /**
     * ブロックID文字列から対応するItemStackを取得します。
     * IDが無効な場合は代替となるItemStack（例：バリアブロック）を返します。
     */
    private fun getItemStackFromId(id: String): ItemStack =
        try {
            val identifier = Identifier.of(id)
            val block = Registries.BLOCK.get(identifier)
            if (block != Blocks.AIR) {
                block.asItem().defaultStack
            } else {
                Items.BARRIER.defaultStack
            }
        } catch (_: Exception) {
            Items.BARRIER.defaultStack
        }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        // ... (描画ロジックは変更なし: 以前の修正が有効なため) ...
        val alpha = MathHelper.clamp(this.alpha, 0.0f, 1.0f)
        val fullColor = 0xFFFFFF or (MathHelper.floor(alpha * 255.0f) shl 24)

        // 1. アイテム全体の背景 (ホバー時のみ)
        if (this.isHovered) {
            context.fill(this.x, this.y, this.x + this.width, this.y + this.height, 0x30FFFFFF)
        }

        val itemX = x + padding
        val iconX = itemX + 2
        val iconY = y + (this.height - iconSize) / 2

        // 2. アイコンの描画
        val itemStack = getItemStackFromId(blockId)
        context.drawItem(itemStack, iconX, iconY)

        // 3. テキストの描画
        val textX = iconX + iconTotalWidth
        val textY = y + this.height / 2 - 4
        context.drawTextWithShadow(textRenderer, Text.literal(blockId), textX, textY, fullColor)

        // 4. 削除ボタンの描画
        val isRemoveButtonHovered = isMouseOverRemoveButton(mouseX.toDouble(), mouseY.toDouble())

        val baseColor = 0xFF882222.toInt()
        val hoverColor = 0xFFAA4444.toInt()
        val removeColor = if (isRemoveButtonHovered) hoverColor else baseColor

        context.fill(removeButtonX, removeButtonY, removeButtonX + removeButtonWidth, removeButtonY + removeButtonHeight, removeColor)
        context.drawBorder(removeButtonX, removeButtonY, removeButtonWidth, removeButtonHeight, 0xFF000000.toInt())

        // 削除テキスト 'x' の描画
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
     * マウスが削除ボタンの上にいるかチェックするヘルパーメソッド
     * 注意: isMouseOver は ClickableWidget に定義されているため、このメソッドは個別のボタン判定に専念する。
     */
    private fun isMouseOverRemoveButton(
        mouseX: Double,
        mouseY: Double,
    ): Boolean =
        mouseX >= removeButtonX &&
            mouseX < removeButtonX + removeButtonWidth &&
            mouseY >= removeButtonY &&
            mouseY < removeButtonY + removeButtonHeight

    /**
     * ★ 修正済み: Click オブジェクトを受け取る形式に再修正
     */
    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        // ClickableWidget の mouseClicked は、既に this.isMouseOver(click.x(), click.y()) をチェックしている。
        // ここでは、クリックが削除ボタンの領域内であったかどうかを確認する。
        if (this.isValidClickButton(click.buttonInfo()) && isMouseOverRemoveButton(click.x(), click.y())) {
            // 削除ボタンをクリックした場合、アクションを実行
            onRemove(blockId)
            // 親クラスの onClick(click, doubled) は呼び出さない (クリック音を防ぐため)
            // 代わりに ClickableWidget のチェックをスキップするため false を返す
            return true
        }

        // 削除ボタン以外をクリックした場合は、親クラスの ClickableWidget.mouseClicked に処理を委譲。
        // これにより、クリック音が鳴り、ウィジェットがフォーカスを得るなどの標準動作が行われる。
        return super.mouseClicked(click, doubled)
    }

    override fun mouseReleased(click: Click): Boolean = super.mouseReleased(click)

    override fun mouseDragged(
        click: Click,
        offsetX: Double,
        offsetY: Double,
    ): Boolean = super.mouseDragged(click, offsetX, offsetY)

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        builder.put(NarrationPart.TITLE, Text.literal("Block List Item: $blockId"))
        builder.put(NarrationPart.HINT, Text.literal("Press Enter to remove this block."))
    }
}
