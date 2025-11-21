package org.infinite.gui.screen

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import net.minecraft.client.gui.screen.option.OptionsScreen
import net.minecraft.client.gui.widget.ClickableWidget
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.infinite.global.ConfigurableGlobalFeature
import org.infinite.global.GlobalFeature
import org.infinite.global.GlobalFeatureCategory
import org.infinite.gui.widget.InfiniteBlockColorListField
import org.infinite.gui.widget.InfiniteBlockListField
import org.infinite.gui.widget.InfiniteEntityListField
import org.infinite.gui.widget.InfiniteGlobalFeatureToggle
import org.infinite.gui.widget.InfinitePlayerListField
import org.infinite.gui.widget.InfiniteScrollableContainer
import org.infinite.gui.widget.InfiniteSelectionList
import org.infinite.gui.widget.InfiniteSettingTextField
import org.infinite.gui.widget.InfiniteSettingToggle
import org.infinite.gui.widget.InfiniteSlider
import org.infinite.gui.widget.InfiniteStringListField
import org.infinite.gui.widget.TabButton
import org.infinite.libs.graphics.Graphics2D
import org.infinite.settings.FeatureSetting
import org.infinite.utils.rendering.transparent
import org.lwjgl.glfw.GLFW

class GlobalSettingsScreen(
    optionsScreen: OptionsScreen,
) : Screen(Text.literal("Infinite Client Global Settings")) {
    private val parent: Screen = optionsScreen
    private var selectedCategory: GlobalFeatureCategory? = null
    private val sections: MutableMap<GlobalFeatureCategory, Section> = mutableMapOf()
    private val categories = InfiniteClient.globalFeatureCategories

    class Section(
        val tab: TabButton,
        val contents: InfiniteScrollableContainer,
    )

    override fun init() {
        super.init()
        sections.clear()
        val tabSpacing = 2
        val tabWidth =
            categories.maxOf { textRenderer.getWidth(it.name) + tabSpacing * 2 }.coerceAtLeast(width / categories.size)
        val tabHeight = (textRenderer.fontHeight + tabSpacing) * 2
        val totalTabsWidth = (tabWidth + tabSpacing) * categories.size - tabSpacing
        var x = (this.width - totalTabsWidth) / 2
        categories.forEach { category ->
            val tabButton =
                TabButton(
                    x,
                    0,
                    tabWidth,
                    tabHeight,
                    Text.translatable(category.name),
                ) {
                    selectedCategory = category
                    updateTabButtonStates()
                }
            val contents =
                InfiniteScrollableContainer(0, tabHeight, width, height - tabHeight, generateWidgets(category))
            x += tabWidth + tabSpacing
            sections[category] = Section(tabButton, contents)
            addSelectableChild(tabButton)
            addSelectableChild(contents)
        }

        selectedCategory = categories.firstOrNull()
        updateTabButtonStates()
    }

    private fun updateTabButtonStates() {
        sections
            .map { it.value }
            .forEach { it.tab.isHighlighted = it.tab.message.string == getCategoryDisplayName(selectedCategory) }
    }

    private fun getCategoryDisplayName(category: GlobalFeatureCategory?): String =
        category?.let {
            Text.translatable("infinite.global_category.${it.name.lowercase()}").string
        } ?: ""

    override fun render(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        super.render(context, mouseX, mouseY, delta)
        val graphics2D = Graphics2D(context, client!!.renderTickCounter)
        graphics2D.fill(
            0,
            0,
            graphics2D.width,
            graphics2D.height,
            InfiniteClient
                .theme()
                .colors.backgroundColor
                .transparent(100),
        )
        sections.map { it.value }.forEach { it.tab.render(context, mouseX, mouseY, delta) }
        selectedCategory?.let { category ->
            renderCategoryContent(context, category, mouseX, mouseY, delta)
        }
    }

    private fun renderCategoryContent(
        context: DrawContext,
        category: GlobalFeatureCategory,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val section = sections[category] ?: return
        section.contents.render(context, mouseX, mouseY, delta)
    }

    override fun keyPressed(input: KeyInput): Boolean {
        val keyCode = input.key
        when (keyCode) {
            GLFW.GLFW_KEY_ESCAPE -> this.close()
            GLFW.GLFW_KEY_LEFT -> selectPreviousCategory()
            GLFW.GLFW_KEY_RIGHT -> selectNextCategory()
            else -> return super.keyPressed(input)
        }
        return true
    }

    private fun selectPreviousCategory() {
        if (categories.isEmpty()) return
        val currentIndex = categories.indexOf(selectedCategory)
        val newIndex = if (currentIndex <= 0) categories.size - 1 else currentIndex - 1
        selectedCategory = categories[newIndex]
        updateTabButtonStates()
    }

    private fun selectNextCategory() {
        if (categories.isEmpty()) return
        val currentIndex = categories.indexOf(selectedCategory)
        val newIndex = if (currentIndex >= categories.size - 1) 0 else currentIndex + 1
        selectedCategory = categories[newIndex]
        updateTabButtonStates()
    }

    private fun generateWidgets(category: GlobalFeatureCategory): MutableList<ClickableWidget> {
        val allCategoryWidgets = mutableListOf<ClickableWidget>()
        val contentWidth = width - 40
        val padding = 5
        val defaultWidgetHeight = 20 // ここに追加

        category.features.forEach { feature ->
            Text.translatable(feature.name)
            val featureDescription = Text.translatable(feature.descriptionKey).string

            // isEnabledトグルボタンを追加
            allCategoryWidgets.add(
                InfiniteGlobalFeatureToggle(
                    0, // x は ScrollableContainer が設定
                    0, // y は ScrollableContainer が設定
                    contentWidth,
                    defaultWidgetHeight,
                    feature,
                    false,
                    featureDescription,
                ),
            )

            // 概要ウィジェットと設定ウィジェットの間のスペーサー
            allCategoryWidgets.add(
                object : ClickableWidget(0, 0, contentWidth, 10, Text.empty()) {
                    override fun renderWidget(
                        context: DrawContext,
                        mouseX: Int,
                        mouseY: Int,
                        delta: Float,
                    ) {
                        // スペーサー、自身の視覚的レンダリングは不要
                    }

                    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {}
                },
            )
            allCategoryWidgets.addAll(generateWidgets(feature))

            // 各フィーチャー間の視覚的な区切りとして大きなスペーサーを追加
            allCategoryWidgets.add(
                object : ClickableWidget(0, 0, contentWidth, padding, Text.empty()) {
                    override fun renderWidget(
                        context: DrawContext,
                        mouseX: Int,
                        mouseY: Int,
                        delta: Float,
                    ) {
                    }

                    override fun appendClickableNarrations(builder: NarrationMessageBuilder) {}
                },
            )
        }
        return allCategoryWidgets
    }

    private fun generateWidgets(feature: GlobalFeature<out ConfigurableGlobalFeature>): MutableList<ClickableWidget> {
        val settingWidgets = mutableListOf<ClickableWidget>()
        // x, y, width, heightはScrollableContainerが調整するため、暫定的な値
        val widgetWidth = width - 40 // ScrollableContainerの幅に合わせるため、仮の値
        val defaultWidgetHeight = 20
        val sliderWidgetHeight = 35
        val blockListFieldHeight = height / 2

        feature.instance.settings.forEach { setting ->
            when (setting) {
                is FeatureSetting.BooleanSetting -> {
                    settingWidgets.add(InfiniteSettingToggle(0, 0, widgetWidth, defaultWidgetHeight, setting))
                }

                is FeatureSetting.IntSetting, is FeatureSetting.FloatSetting, is FeatureSetting.DoubleSetting -> {
                    settingWidgets.add(InfiniteSlider(0, 0, widgetWidth, sliderWidgetHeight, setting))
                }

                is FeatureSetting.StringSetting -> {
                    settingWidgets.add(InfiniteSettingTextField(0, 0, widgetWidth, defaultWidgetHeight, setting))
                }

                is FeatureSetting.StringListSetting -> {
                    settingWidgets.add(InfiniteStringListField(0, 0, widgetWidth, defaultWidgetHeight, setting))
                }

                is FeatureSetting.EnumSetting<*> -> {
                    settingWidgets.add(InfiniteSelectionList(0, 0, widgetWidth, defaultWidgetHeight, setting))
                }

                is FeatureSetting.BlockIDSetting -> {
                    settingWidgets.add(InfiniteSettingTextField(0, 0, widgetWidth, defaultWidgetHeight, setting))
                }

                is FeatureSetting.EntityIDSetting -> {
                    settingWidgets.add(InfiniteSettingTextField(0, 0, widgetWidth, defaultWidgetHeight, setting))
                }

                is FeatureSetting.BlockListSetting -> {
                    settingWidgets.add(InfiniteBlockListField(0, 0, widgetWidth, blockListFieldHeight, setting))
                }

                is FeatureSetting.EntityListSetting -> {
                    settingWidgets.add(InfiniteEntityListField(0, 0, widgetWidth, blockListFieldHeight, setting))
                }

                is FeatureSetting.PlayerListSetting -> {
                    settingWidgets.add(InfinitePlayerListField(0, 0, widgetWidth, blockListFieldHeight, setting))
                }

                is FeatureSetting.BlockColorListSetting -> {
                    settingWidgets.add(InfiniteBlockColorListField(0, 0, widgetWidth, blockListFieldHeight, setting))
                }
            }
        }
        return settingWidgets
    }

    override fun close() {
        this.client?.setScreen(this.parent)
    }
}
