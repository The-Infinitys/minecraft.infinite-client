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

class InfiniteSummaryWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val title: Text,
    private val summaryText: String,
) : ClickableWidget(x, y, width, height, title) {
    private val textRenderer = MinecraftClient.getInstance().textRenderer
    private val padding = 5
    private val titleHeight = textRenderer.fontHeight + padding

    private lateinit var scrollableContainer: InfiniteScrollableContainer
    private var isInitialized = false

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        if (!isInitialized) {
            initializeScrollableContainer()
            isInitialized = true
        }

        val graphics2D = Graphics2D(context, MinecraftClient.getInstance().renderTickCounter)
        val colors = InfiniteClient.theme().colors

        // タイトルの描画
        graphics2D.drawText(
            title,
            x + padding,
            y + padding,
            colors.foregroundColor,
        )

        // スクロール可能なコンテンツの描画
        scrollableContainer.render(context, mouseX, mouseY, delta)
    }

    private fun initializeScrollableContainer() {
        // サマリーテキストを各行に分割
        val lines = summaryText.split("\n").map { Text.literal(it) }
        val lineWidgets = lines.map { InfiniteTextWidget(0, 0, width - padding * 2, textRenderer.fontHeight, it) }

        scrollableContainer =
            InfiniteScrollableContainer(
                x + padding,
                y + titleHeight + padding, // タイトルの下
                width - padding * 2,
                height - titleHeight - padding * 2, // 残りの高さをスクロールコンテナに割り当てる
                lineWidgets.toMutableList(),
            )
    }

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
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
        if (scrollableContainer.mouseDragged(click, offsetX, offsetY)) {
            return true
        }
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder?) {
        this.appendClickableNarrations(builder)
    }

    override fun mouseReleased(click: Click): Boolean {
        if (scrollableContainer.mouseReleased(click)) {
            return true
        }
        return super.mouseReleased(click)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (scrollableContainer.keyPressed(input)) {
            return true
        }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharInput): Boolean {
        if (scrollableContainer.charTyped(input)) {
            return true
        }
        return super.charTyped(input)
    }
}
