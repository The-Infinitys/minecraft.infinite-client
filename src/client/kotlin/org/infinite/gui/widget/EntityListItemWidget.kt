package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.screen.narration.NarrationPart
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics2D

class EntityListItemWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int, // This height is for the individual item
    private val entityId: String,
    private val onRemove: (String) -> Unit,
) : ClickableWidget(x, y, width, height, Text.literal(entityId)) {
    private val padding = 8
    private val iconSize = 16
    private val iconPadding = 2
    private val iconTotalWidth = iconSize + iconPadding
    private val removeButtonWidth = 20

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val graphics2D = Graphics2D(context, MinecraftClient.getInstance().renderTickCounter)

        val textX = iconTotalWidth
        val textY = y + this.height / 2 - 4
        graphics2D.drawText(
            Text.literal(entityId),
            textX,
            textY,
            InfiniteClient
                .getCurrentColors()
                .foregroundColor,
            true, // shadow = true
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
                .getCurrentColors()
                .errorColor
        val hoverColor =
            InfiniteClient
                .getCurrentColors()
                .errorColor
        val removeColor = if (isRemoveButtonHovered) hoverColor else baseColor

        context.fill(
            removeButtonX,
            removeButtonY,
            removeButtonX + removeButtonWidth,
            removeButtonY + this.height,
            removeColor,
        )
        graphics2D.drawText(
            "x",
            removeButtonX + removeButtonWidth / 2 - 3,
            removeButtonY + this.height / 2 - 4,
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
