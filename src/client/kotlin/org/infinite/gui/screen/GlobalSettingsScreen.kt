package org.infinite.gui.screen

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.option.OptionsScreen
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.infinite.global.GlobalFeatureCategory
import org.infinite.gui.widget.TabButton
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.rendering.transparent
import org.lwjgl.glfw.GLFW

class GlobalSettingsScreen(
    optionsScreen: OptionsScreen,
) : Screen(Text.literal("Infinite Client Global Settings")) {
    private val parent: Screen = optionsScreen
    private var selectedCategory: GlobalFeatureCategory? = null
    private val tabButtons: MutableList<TabButton> = mutableListOf()
    private val categories = InfiniteClient.globalFeatureCategories

    override fun init() {
        super.init()
        tabButtons.clear()
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
                    20,
                    tabWidth,
                    tabHeight,
                    Text.translatable(category.name),
                ) {
                    selectedCategory = category
                    updateTabButtonStates()
                }
            tabButtons.add(tabButton)
            this.addDrawableChild(tabButton)
            x += tabWidth + tabSpacing
        }

        selectedCategory = categories.firstOrNull()
        updateTabButtonStates()
    }

    private fun updateTabButtonStates() {
        tabButtons.forEach { it.isHighlighted = it.message.string == getCategoryDisplayName(selectedCategory) }
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
        val graphics = Graphics2D(context, client!!.renderTickCounter)
        val contentX = this.width / 2 - 150
        val contentY = 50
        graphics.drawText(
            Text.literal("Settings for ${getCategoryDisplayName(category)}").string,
            contentX,
            contentY,
            0xFFFFFF,
        )
        var currentY = contentY + 20
        category.features.forEach { globalFeature ->
            graphics.drawText("  - ${globalFeature.name}", contentX, currentY, 0xAAAAAA)
            currentY += 12
            globalFeature.instance.settings.forEach { setting ->
                graphics.drawText("    - ${setting.name}: ${setting.value}", contentX, currentY, 0x888888)
                currentY += 12
            }
        }
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
