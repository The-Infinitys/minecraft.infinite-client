package org.infinite.gui.widget

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.screen.narration.NarrationPart
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics2D

class InfiniteTextWidget(
    x: Int,
    width: Int,
    y: Int,
    height: Int,
    private val text: Text,
) : ClickableWidget(x, y, width, height, text) {
    private val padding = 2

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val graphics2D = Graphics2D(context, MinecraftClient.getInstance().renderTickCounter)
        val colors = InfiniteClient.theme().colors

        graphics2D.drawText(
            text,
            this.x + padding,
            this.y + padding,
            colors.foregroundColor,
        )
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        builder.put(NarrationPart.TITLE, text)
    }
}
