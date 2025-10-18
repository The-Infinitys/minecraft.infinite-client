package org.infinite.gui.screen

import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.infinite.Feature
import org.infinite.gui.widget.InfiniteBlockListField
import org.infinite.gui.widget.InfiniteButton
import org.infinite.gui.widget.InfiniteEntityListField
import org.infinite.gui.widget.InfinitePlayerListField
import org.infinite.gui.widget.InfiniteScrollableContainer
import org.infinite.gui.widget.InfiniteSelectionList
import org.infinite.gui.widget.InfiniteSettingTextField
import org.infinite.gui.widget.InfiniteSettingToggle
import org.infinite.gui.widget.InfiniteSlider
import org.infinite.gui.widget.InfiniteStringListField
import org.infinite.settings.FeatureSetting

class FeatureSettingsScreen(
    private val parent: Screen,
    private val feature: Feature,
) : Screen(Text.literal(feature.name)) {
    private var savedPageIndex: Int = 0

    // 遅延初期化を維持
    private lateinit var scrollableContainer: InfiniteScrollableContainer

    override fun init() {
        super.init()

        if (parent is InfiniteScreen) {
            savedPageIndex = parent.pageIndex
        }

        val settingWidgets = mutableListOf<ClickableWidget>()
        var currentY = 50
        val widgetWidth = width - 40
        val defaultWidgetHeight = 20
        val blockListFieldHeight = height / 2
        val padding = 5

        // (ウィジェットの生成ロジックは変更なし)
        feature.instance.settings.forEach { setting ->
            // ... (ウィジェット生成ロジックは省略) ...
            when (setting) {
                is FeatureSetting.BooleanSetting -> {
                    settingWidgets.add(InfiniteSettingToggle(20, currentY, widgetWidth, defaultWidgetHeight, setting))
                    currentY += defaultWidgetHeight + padding
                }

                is FeatureSetting.IntSetting -> {
                    settingWidgets.add(InfiniteSlider(20, currentY, widgetWidth, defaultWidgetHeight, setting))
                    currentY += defaultWidgetHeight + padding
                }

                is FeatureSetting.FloatSetting -> {
                    settingWidgets.add(InfiniteSlider(20, currentY, widgetWidth, defaultWidgetHeight, setting))
                    currentY += defaultWidgetHeight + padding
                }

                is FeatureSetting.StringSetting -> {
                    settingWidgets.add(
                        InfiniteSettingTextField(
                            20,
                            currentY,
                            widgetWidth,
                            defaultWidgetHeight,
                            setting,
                        ),
                    )
                    currentY += defaultWidgetHeight + padding
                }

                is FeatureSetting.StringListSetting -> {
                    settingWidgets.add(InfiniteStringListField(20, currentY, widgetWidth, defaultWidgetHeight, setting))
                    currentY += defaultWidgetHeight + padding
                }

                is FeatureSetting.EnumSetting<*> -> {
                    settingWidgets.add(InfiniteSelectionList(20, currentY, widgetWidth, defaultWidgetHeight, setting))
                    currentY += defaultWidgetHeight + padding
                }

                is FeatureSetting.BlockIDSetting -> {
                    settingWidgets.add(
                        InfiniteSettingTextField(
                            20,
                            currentY,
                            widgetWidth,
                            defaultWidgetHeight,
                            setting,
                        ),
                    )
                    currentY += defaultWidgetHeight + padding
                }

                is FeatureSetting.EntityIDSetting -> {
                    settingWidgets.add(
                        InfiniteSettingTextField(
                            20,
                            currentY,
                            widgetWidth,
                            defaultWidgetHeight,
                            setting,
                        ),
                    )
                    currentY += defaultWidgetHeight + padding
                }

                is FeatureSetting.BlockListSetting -> {
                    settingWidgets.add(InfiniteBlockListField(20, currentY, widgetWidth, blockListFieldHeight, setting))
                    currentY += blockListFieldHeight + padding
                }

                is FeatureSetting.EntityListSetting -> {
                    settingWidgets.add(
                        InfiniteEntityListField(
                            20,
                            currentY,
                            widgetWidth,
                            blockListFieldHeight,
                            setting,
                        ),
                    )
                    currentY += blockListFieldHeight + padding
                }

                is FeatureSetting.PlayerListSetting -> {
                    settingWidgets.add(
                        InfinitePlayerListField(
                            20,
                            currentY,
                            widgetWidth,
                            blockListFieldHeight,
                            setting,
                        ),
                    )
                    currentY += blockListFieldHeight + padding
                }
            }
        }

        val scrollableContainer =
            InfiniteScrollableContainer(
                20,
                50,
                width - 40,
                height - 100,
                settingWidgets,
            )
        addDrawableChild(scrollableContainer)
        this.scrollableContainer = scrollableContainer

        addDrawableChild(
            InfiniteButton(
                width / 2 - 50,
                height - 30,
                100,
                20,
                Text.literal("Close"),
            ) {
                if (parent is InfiniteScreen) {
                    InfiniteScreen.selectedPageIndex = savedPageIndex
                }
                this.client?.setScreen(parent)
            },
        )
    }

    // --- マウスイベント (Screen.java のシグネチャに合わせる) ---
    // ScreenクラスがAbstractParentElementの古いメソッドをオーバーライドして保持しているため、この形式を維持。

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        if (scrollableContainer.mouseClicked(click, doubled)) return true
        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(
        click: Click,
        offsetX: Double,
        offsetY: Double,
    ): Boolean {
        if (scrollableContainer.mouseDragged(click, offsetX, offsetY)) return true
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: Click): Boolean {
        if (scrollableContainer.mouseReleased(click)) return true
        return super.mouseReleased(click)
    }

    // --- スクロールイベント (ParentElement.java の新しいシグネチャに合わせる) ---

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        // InfiniteScrollableContainer は古い amount 引数 (垂直スクロール) を期待すると仮定し、verticalAmount を転送
        if (scrollableContainer.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) return true
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    // --- キーボードイベント (ParentElement.java の新しいシグネチャに合わせる) ---

    // ParentElement.java で確認された KeyInput シグネチャを使用
    override fun keyPressed(input: KeyInput): Boolean {
        // scrollableContainer の古い keyPressed(keyCode, scanCode, modifiers) に転送
        if (scrollableContainer.keyPressed(input)) return true
        return super.keyPressed(input)
    }

    // ParentElement.java で確認された CharInput シグネチャを使用
    override fun charTyped(input: CharInput): Boolean {
        // scrollableContainer の古い charTyped(chr, modifiers) に転送
        if (scrollableContainer.charTyped(input)) return true
        return super.charTyped(input)
    }

    // --- レンダリングなど (変更なし) ---

    override fun render(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        // 背景の描画 (半透明の黒)
        context.fill(0, 0, width, height, 0x80000000.toInt())

        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.literal(feature.name),
            width / 2,
            20,
            0xFFFFFFFF.toInt(),
        )
        context.drawCenteredTextWithShadow(
            textRenderer,
            Text.translatable(feature.descriptionKey),
            width / 2,
            35,
            0xFFAAAAAA.toInt(),
        )

        // ウィジェットの描画 (scrollableContainerを含む)
        super.render(context, mouseX, mouseY, delta)
    }

    override fun shouldPause(): Boolean = false
}
