package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.screen.narration.NarrationPart
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.item.SpawnEggItem
import net.minecraft.registry.Registries
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.infinite.InfiniteClient

class EntityListItemWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int, // This height is for the individual item
    private val entityId: String,
    private val onRemove: (String) -> Unit,
) : ClickableWidget(x, y, width, height, Text.literal(entityId)) {
    private val textRenderer = MinecraftClient.getInstance().textRenderer
    private val padding = 8
    private val iconSize = 16
    private val iconPadding = 2
    private val iconTotalWidth = iconSize + iconPadding
    private val removeButtonWidth = 20

    /**
     * エンティティID文字列から対応するItemStackを取得します。
     * IDが無効な場合は代替となるItemStack（例：バリアブロック）を返します。
     */
    private fun getItemStackFromId(id: String): ItemStack =
        try {
            val identifier = Identifier.of(id)
            val entityType = Registries.ENTITY_TYPE.get(identifier)
            SpawnEggItem.forEntity(entityType)?.defaultStack ?: Items.BARRIER.defaultStack
        } catch (_: Exception) {
            Items.BARRIER.defaultStack
        }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val textX = iconTotalWidth
        val textY = y + this.height / 2 - 4
        context.drawTextWithShadow(
            textRenderer,
            Text.literal(entityId),
            textX,
            textY,
            InfiniteClient
                .theme()
                .colors.foregroundColor,
        )

        val removeButtonX = x + width - padding - removeButtonWidth
        val removeButtonY = y
        val isRemoveButtonHovered =
            mouseX >= removeButtonX &&
                mouseX < removeButtonX + removeButtonWidth &&
                mouseY >= removeButtonY &&
                mouseY < removeButtonY + this.height

        val baseColor =
            InfiniteClient
                .theme()
                .colors.errorColor
        val hoverColor =
            InfiniteClient
                .theme()
                .colors.errorColor
        val removeColor = if (isRemoveButtonHovered) hoverColor else baseColor

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
            InfiniteClient
                .theme()
                .colors.foregroundColor,
            false,
        )
    }

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        val removeButtonX = x + width - padding - removeButtonWidth
        val removeButtonY = y
        val mouseX = click.x
        val mouseY = click.y
        if (mouseX >= removeButtonX &&
            mouseX < removeButtonX + removeButtonWidth &&
            mouseY >= removeButtonY &&
            mouseY < removeButtonY + this.height
        ) {
            onRemove(entityId)
            return true
        }
        return super.mouseClicked(click, doubled)
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        builder.put(NarrationPart.TITLE, Text.literal("Entity List Item: $entityId"))
    }
}
