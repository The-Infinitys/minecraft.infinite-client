package org.infinite.gui.screen

import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import net.minecraft.util.math.ColorHelper
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.Feature
import org.infinite.gui.widget.FeatureSearchWidget
import org.infinite.gui.widget.InfiniteButton
import org.infinite.gui.widget.InfiniteFeatureToggle
import org.infinite.gui.widget.InfiniteScrollableContainer
import org.infinite.utils.rendering.drawBorder
import org.infinite.utils.rendering.transparent

class UISection(
    val id: String,
    private val screen: Screen,
    featureList: List<Feature<out ConfigurableFeature>>? = null,
) {
    private var closeButton: InfiniteButton? = null
    val widgets = mutableListOf<ClickableWidget>()
    private var featureSearchWidget: FeatureSearchWidget? = null
    private var isMainSectionInitialized = false

    init {
        when (id) {
            "main" -> {
                // Initialization moved to renderMain
            }

            else -> {
                featureList?.let {
                    setupFeatureWidgets(it)
                }
            }
        }
    }

    private fun setupFeatureWidgets(features: List<Feature<out ConfigurableFeature>>) {
        val featureWidgets =
            features.map { feature ->
                feature.name
                InfiniteFeatureToggle(0, 0, 280, 20, feature, false) {
                    MinecraftClient.getInstance().setScreen(FeatureSettingsScreen(screen, feature))
                }
            }

        if (featureWidgets.isNotEmpty()) {
            val container = InfiniteScrollableContainer(0, 0, 300, 180, featureWidgets.toMutableList())
            widgets.add(container)
        }
    }

    fun render(
        context: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        isSelected: Boolean,
        textRenderer: TextRenderer,
        borderColor: Int,
        alpha: Int,
        renderContent: Boolean,
    ) {
        // Draw the icon in the center of the panel
        val icon = InfiniteClient.theme().icon
        if (icon != null) {
            val iconWidth = if (icon.width > icon.height) 256 else 256 * icon.width / icon.height
            val iconHeight = if (icon.width < icon.height) 256 else 256 * icon.height / icon.width
            val iconX = x + (width - iconWidth) / 2
            val iconY = y + (height - iconHeight) / 2
            val iconColor =
                borderColor.transparent(128)
            context.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                icon.identifier,
                iconX,
                iconY,
                0f,
                0f,
                iconWidth,
                iconHeight,
                icon.width,
                icon.height,
                icon.width,
                icon.height,
                iconColor,
            )
        }
        val backgroundColor =
            InfiniteClient
                .theme()
                .colors.backgroundColor
                .transparent(alpha)
        context.drawBorder(x, y, width, height, borderColor)
        context.fill(x, y, x + width, y + height, backgroundColor)

        val titleText =
            when (id) {
                "main" -> {
                    "Main"
                }

                else -> {
                    id
                        .replace("-settings", "")
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } +
                        " Settings"
                }
            }

        if (id == "main") {
            renderMain(context, x, y, width, height, textRenderer, isSelected, mouseX, mouseY, delta, renderContent)
        } else {
            renderSettings(
                context,
                x,
                y,
                width,
                height,
                textRenderer,
                titleText,
                isSelected,
                mouseX,
                mouseY,
                delta,
                renderContent,
            )
        }

        if (isSelected && renderContent) {
            if (closeButton == null || closeButton?.x != x + width - 30 || closeButton?.y != y + 10) {
                closeButton =
                    InfiniteButton(
                        x = x + width - 30,
                        y = y + 10,
                        width = 20,
                        height = 20,
                        message = Text.literal("X"),
                    ) {
                        screen.close()
                    }
            }
            closeButton?.render(context, mouseX, mouseY, delta)
        } else {
            closeButton = null
        }
    }

    private fun renderMain(
        context: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        textRenderer: TextRenderer,
        isSelected: Boolean,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        renderContent: Boolean,
    ) {
        renderTitle(context, x, y, width, textRenderer, "Main", isSelected)
        if (!renderContent) return

        if (!isMainSectionInitialized) {
            featureSearchWidget = FeatureSearchWidget(x + 20, y + 50, width - 40, height - 70, screen)
            isMainSectionInitialized = true
        }

        featureSearchWidget?.let {
            it.x = x + 20
            it.y = y + 50
            it.width = width - 40
            it.height = height - 70
            it.render(context, mouseX, mouseY, delta)
        }
    }

    private fun renderSettings(
        context: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        textRenderer: TextRenderer,
        titleText: String,
        isSelected: Boolean,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
        renderContent: Boolean,
    ) {
        renderTitle(context, x, y, width, textRenderer, titleText, isSelected)
        if (!renderContent) return

        var currentY = y + 50
        widgets.forEach { widget ->
            if (widget is InfiniteScrollableContainer) {
                // スクロールコンテナの位置と高さの再計算
                widget.setPosition(x + (width - widget.width) / 2 - 20, y + 50)
                widget.height = height - 60
            } else {
                widget.x = x + 20
                widget.y = currentY
                currentY += widget.height + 5
            }
            widget.render(context, mouseX, mouseY, delta)
        }
    }

    private fun renderTitle(
        context: DrawContext,
        x: Int,
        y: Int,
        width: Int,
        textRenderer: TextRenderer,
        titleText: String,
        isSelected: Boolean,
    ) {
        val title = Text.of(titleText)
        val textWidth = textRenderer.getWidth(title)
        val textX = x + (width - textWidth) / 2
        val textY = y + 20

        val color =
            if (isSelected) {
                InfiniteClient
                    .theme()
                    .colors.foregroundColor
            } else {
                ColorHelper.getArgb(
                    255,
                    ColorHelper.getRed(
                        InfiniteClient
                            .theme()
                            .colors.foregroundColor,
                    ) / 2,
                    ColorHelper.getGreen(
                        InfiniteClient
                            .theme()
                            .colors.foregroundColor,
                    ) / 2,
                    ColorHelper.getBlue(
                        InfiniteClient
                            .theme()
                            .colors.foregroundColor,
                    ) / 2,
                )
            }
        context.drawTextWithShadow(textRenderer, title, textX, textY, color)
    }

    // ★ 修正点: mouseClicked を Boolean 戻り値に変更し、
    // イベントを処理したウィジェットでループを停止する
    fun mouseClicked(
        click: Click,
        doubled: Boolean,
        isSelected: Boolean,
    ): Boolean { // ★ 戻り値を Boolean に変更
        if (!isSelected) return false

        if (id == "main") {
            featureSearchWidget?.mouseClicked(click, doubled)?.let { if (it) return true }
        }

        // 1. closeButtonのクリック
        if (closeButton?.mouseClicked(click, doubled) == true) {
            return true
        }

        // 2. 他のウィジェットのクリック
        for (widget in widgets) {
            if (widget.mouseClicked(click, doubled)) {
                return true // ★ 最初に応答したウィジェットで停止し、フォーカスを与える
            }
        }

        return false
    }

    fun keyPressed(
        input: KeyInput,
        isSelected: Boolean,
    ) {
        if (!isSelected) return

        if (id == "main") {
            featureSearchWidget?.keyPressed(input)
        }

        // keyPressed は一般的に全ての子に転送されます
        widgets.forEach { it.keyPressed(input) }
    }

    fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
        isSelected: Boolean,
    ): Boolean {
        if (!isSelected) return false

        if (id == "main") {
            featureSearchWidget
                ?.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
                ?.let { if (it) return true }
        }

        for (widget in widgets) {
            if (widget.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
                return true
            }
        }
        return false
    }

    // ★ 修正点: mouseDragged を全てのウィジェットに転送
    fun mouseDragged(
        click: Click,
        offsetX: Double,
        offsetY: Double,
        isSelected: Boolean,
    ): Boolean { // ★ 戻り値は Boolean
        if (!isSelected) return false

        if (id == "main") {
            featureSearchWidget?.mouseDragged(click, offsetX, offsetY)?.let { if (it) return true }
        }

        // closeButtonへのドラッグを処理
        if (closeButton?.mouseDragged(click, offsetX, offsetY) == true) {
            return true
        }

        // ★ スクロールコンテナとその他のウィジェット（スライダーなど）の両方に転送
        for (widget in widgets) {
            if (widget.mouseDragged(click, offsetX, offsetY)) {
                return true
            }
        }
        return false
    }

    // ★ 修正点: mouseReleased を全てのウィジェットに転送
    fun mouseReleased(
        click: Click,
        isSelected: Boolean,
    ): Boolean { // ★ 戻り値は Boolean
        if (!isSelected) return false

        if (id == "main") {
            featureSearchWidget?.mouseReleased(click)?.let { if (it) return true }
        }

        // closeButtonの mouseReleased を処理
        if (closeButton?.mouseReleased(click) == true) {
            return true
        }

        // ★ スクロールコンテナとその他のウィジェットの両方に転送
        for (widget in widgets) {
            if (widget.mouseReleased(click)) {
                return true
            }
        }
        return false
    }

    fun charTyped(
        input: CharInput,
        isSelected: Boolean,
    ): Boolean {
        if (!isSelected) return false

        if (id == "main") {
            featureSearchWidget?.charTyped(input)?.let { if (it) return true }
        }

        for (widget in widgets) {
            if (widget.charTyped(input)) {
                return true
            }
        }
        return false
    }
}
