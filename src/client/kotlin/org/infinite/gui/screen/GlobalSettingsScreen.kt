package org.infinite.gui.screen

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.option.OptionsScreen
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.infinite.global.GlobalFeatureCategory
import org.infinite.gui.widget.InfiniteScrollableContainer
import org.infinite.gui.widget.TabButton
import org.infinite.libs.graphics.Graphics2D
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
        categories.forEachIndexed { index, category ->
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
            val contents = InfiniteScrollableContainer(0, tabHeight, width, height - tabHeight, mutableListOf())
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

    override fun close() {
        this.client?.setScreen(this.parent)
    }
}
