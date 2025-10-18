package org.theinfinitys.gui.widget

import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.screen.narration.NarrationPart
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import net.minecraft.util.math.ColorHelper
import net.minecraft.util.math.MathHelper
import org.lwjgl.glfw.GLFW
import kotlin.math.max

class InfiniteScrollableContainer(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    var widgets: MutableList<ClickableWidget>, // Made mutable and public
) : ClickableWidget(x, y, width, height, Text.literal("")) {
    var scrollY: Double = 0.0
        set(value) {
            val contentHeight = widgets.sumOf { it.height + internalPadding }
            field = MathHelper.clamp(value, 0.0, max(0.0, contentHeight - height.toDouble()))
        }
    private val scrollbarWidth = 6
    internal val internalPadding = 5 // Made internal
    private var isDragging = false
    private var dragYOffset = 0.0

    private val shouldShowScrollbar: Boolean
        get() = widgets.sumOf { it.height + internalPadding } > height

    init {
        updateWidgetPositions()
    }

    override fun setPosition(
        x: Int,
        y: Int,
    ) {
        // 親ウィジェットの setPosition が呼ばれたときに、コンテナ自身の x/y は更新される
        this.x = x
        this.y = y
        // 親の移動に合わせて、子の絶対座標もすべて更新する
        updateWidgetPositions()
    }

    internal fun updateWidgetPositions() { // Made internal
        val effectiveScrollbarWidth = if (shouldShowScrollbar) scrollbarWidth else 0
        val contentWidth = this.width - 2 * internalPadding - effectiveScrollbarWidth

        // currentY は、親コンテナの Y 座標（this.y）からの相対 Y 座標（スクロールオフセット適用済み）
        var currentRelativeContentY = internalPadding - scrollY.toInt()

        for (widget in widgets) {
            // X座標: このコンテナの絶対X + パディング
            widget.x = this.x + internalPadding

            // Y座標: このコンテナの絶対Y + 相対Y
            // これにより、子の widget.x と widget.y は常にグローバル座標を保持する
            widget.y = this.y + currentRelativeContentY

            // 幅の更新
            widget.setWidth(contentWidth)

            currentRelativeContentY += widget.height + internalPadding
        }
    }

    override fun renderWidget(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        // 描画範囲をコンテナの境界内に制限
        context.enableScissor(x, y, x + width, y + height)

        for (widget in widgets) {
            // widget.y には既に親の絶対座標とスクロールオフセットが含まれているため、そのまま描画を試みる
            // クリッピングチェックは、現在の widget.y (グローバル座標)で行う
            if (widget.y + widget.height > y && widget.y < y + height) {
                widget.render(context, mouseX, mouseY, delta)
            }
        }

        context.disableScissor()
        renderScrollbar(context)
    }

    // renderScrollbar, mouseScrolled, mouseClicked, mouseDragged, mouseReleased, keyPressed, charTyped, appendClickableNarrations は
    // 前回の最終版のロジック（親へのスクロール伝播、子のイベント処理優先、ドラッグ処理）を維持します。

    private fun renderScrollbar(context: DrawContext) {
        val contentHeight = widgets.sumOf { it.height + internalPadding }
        if (contentHeight > height) {
            val maxScrollY = max(0.0, contentHeight - height.toDouble())
            val scrollbarHeight = MathHelper.clamp((height.toDouble() / contentHeight * height).toInt(), 32, height - 8)

            val scrollbarY = y + (scrollY / maxScrollY * (height - scrollbarHeight)).toInt()
            val scrollbarX = x + width - scrollbarWidth

            context.fill(scrollbarX, y, scrollbarX + scrollbarWidth, y + height, 0x80000000.toInt())

            val animationDuration = 6000L
            val colors =
                intArrayOf(
                    0xFFFF0000.toInt(),
                    0xFFFFFF00.toInt(),
                    0xFF00FF00.toInt(),
                    0xFF00FFFF.toInt(),
                    0xFF0000FF.toInt(),
                    0xFFFF00FF.toInt(),
                    0xFFFF0000.toInt(),
                )
            val currentTime = System.currentTimeMillis()
            val elapsedTime = currentTime % animationDuration
            val progress = elapsedTime.toFloat() / animationDuration.toFloat()
            val numSegments = colors.size - 1
            val segmentLength = 1.0f / numSegments
            val currentSegmentIndex = (progress / segmentLength).toInt().coerceAtMost(numSegments - 1)
            val segmentProgress = (progress % segmentLength) / segmentLength
            val startColor = colors[currentSegmentIndex]
            val endColor = colors[currentSegmentIndex + 1]
            val interpolatedColor =
                ColorHelper.getArgb(
                    255,
                    (ColorHelper.getRed(startColor) * (1 - segmentProgress) + ColorHelper.getRed(endColor) * segmentProgress).toInt(),
                    (ColorHelper.getGreen(startColor) * (1 - segmentProgress) + ColorHelper.getGreen(endColor) * segmentProgress).toInt(),
                    (ColorHelper.getBlue(startColor) * (1 - segmentProgress) + ColorHelper.getBlue(endColor) * segmentProgress).toInt(),
                )

            context.fill(
                scrollbarX,
                scrollbarY,
                scrollbarX + scrollbarWidth,
                scrollbarY + scrollbarHeight,
                interpolatedColor,
            )
        }
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        // 1. 子ウィジェットのスクロール処理をまず試みる (ネストされたスクロールコンテナが優先されるため)
        var scrolledChild = false
        // visibleなウィジェットのみにイベントを伝播させるべき
        for (widget in widgets) {
            // クリッピングチェックと同様の条件で、描画範囲内のウィジェットのみにイベントを渡す
            if (widget.y + widget.height > y && widget.y < y + height) {
                if (widget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                    scrolledChild = true
                    break // 子ウィジェットがイベントを処理した場合、通常はそこで処理を終了
                }
            }
        }

        // 子ウィジェットがイベントを処理したら、このコンテナでのスクロールは行わない
        if (scrolledChild) {
            return true
        }

        // 2. 子ウィジェットが処理しなかった場合、このコンテナ自身のスクロール処理を行う
        val contentHeight = widgets.sumOf { it.height + internalPadding }
        var scrolledSelf = false
        if (isMouseOver(mouseX, mouseY) && contentHeight > height) {
            val oldScrollY = scrollY
            scrollY -= verticalAmount * 20
            scrollY = MathHelper.clamp(scrollY, 0.0, max(0.0, contentHeight - height.toDouble()))

            if (scrollY != oldScrollY) {
                updateWidgetPositions()
                scrolledSelf = true
            }
        }

        // 子ウィジェットが処理しなかった場合に、自身の処理結果を返す
        return scrolledSelf
    }

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        val mouseX = click.x
        val mouseY = click.y
        if (!isMouseOver(mouseX, mouseY)) {
            return false
        }

        val contentHeight = widgets.sumOf { it.height + internalPadding }

        for (widget in widgets) {
            if (widget.y + widget.height > y && widget.y < y + height) {
                if (widget.mouseClicked(click, doubled)) {
                    isDragging = false
                    return true
                }
            }
        }

        if (contentHeight > height) {
            val scrollbarX = x + width - scrollbarWidth
            if (mouseX >= scrollbarX && mouseX < x + width) {
                isDragging = true

                val maxScrollY = max(0.0, contentHeight - height.toDouble())
                val scrollbarHeight =
                    MathHelper.clamp((height.toDouble() / contentHeight * height).toInt(), 32, height - 8)
                val maxScrollbarY = height - scrollbarHeight
                val currentScrollbarY = y + (scrollY / maxScrollY * maxScrollbarY).toInt()

                dragYOffset = mouseY - currentScrollbarY

                return true
            }
        }

        return false
    }

    override fun mouseDragged(
        click: Click,
        offsetX: Double,
        offsetY: Double,
    ): Boolean {
        // 1. スクロールバーのドラッグ処理（最優先）
        if (isDragging) {
            val contentHeight = widgets.sumOf { it.height + internalPadding }
            if (contentHeight > height) {
                val maxScrollY = max(0.0, contentHeight - height.toDouble())
                val scrollbarHeight =
                    MathHelper.clamp((height.toDouble() / contentHeight * height).toInt(), 32, height - 8)
                val maxScrollbarY = height - scrollbarHeight

                val newScrollbarY = click.y - dragYOffset

                val clampedScrollbarY = MathHelper.clamp(newScrollbarY - y, 0.0, maxScrollbarY.toDouble())

                scrollY = clampedScrollbarY / maxScrollbarY * maxScrollY

                scrollY = MathHelper.clamp(scrollY, 0.0, maxScrollY)
                updateWidgetPositions()
                return true // イベントを消費
            }
            // コンテンツが小さくなり、isDraggingが残ってしまった場合
            isDragging = false
            return false
        }

        // 2. 子ウィジェットへのドラッグ処理伝播
        // スクロールバーのドラッグでなければ、子ウィジェットのドラッグ処理を試みる
        for (widget in widgets) {
            // 表示範囲内のウィジェットにのみ伝播
            if (widget.y + widget.height > y && widget.y < y + height) {
                // mouseDragged が true を返した場合、イベントを消費
                if (widget.mouseDragged(click, offsetX, offsetY)) {
                    return true
                }
            }
        }

        // 3. どちらも処理しなかった場合
        return false
    }

    override fun mouseReleased(click: Click): Boolean {
        val wasDragging = isDragging
        isDragging = false
        if (wasDragging) {
            return true
        }

        for (widget in widgets) {
            if (widget.mouseReleased(click)) {
                return true
            }
        }

        return false
    }

    override fun keyPressed(input: KeyInput): Boolean {
        val keyCode = input.key
        val contentHeight = widgets.sumOf { it.height + internalPadding }
        if (contentHeight > height) {
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                scrollY += 10
                scrollY = MathHelper.clamp(scrollY, 0.0, max(0.0, contentHeight - height.toDouble()))
                updateWidgetPositions()
                return true
            } else if (keyCode == GLFW.GLFW_KEY_UP) {
                scrollY -= 10
                scrollY = MathHelper.clamp(scrollY, 0.0, max(0.0, contentHeight - height.toDouble()))
                updateWidgetPositions()
                return true
            }
        }
        for (widget in widgets) {
            if (widget.keyPressed(input)) {
                return true
            }
        }
        return super.keyPressed(input)
    }

    override fun charTyped(input: CharInput): Boolean {
        for (widget in widgets) {
            if (widget.charTyped(input)) {
                return true
            }
        }
        return super.charTyped(input)
    }

    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {
        builder.put(NarrationPart.TITLE, "Scrollable Container")
    }
}
