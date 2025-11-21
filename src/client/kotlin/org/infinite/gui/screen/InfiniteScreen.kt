package org.infinite.gui.screen

import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.input.CharInput
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.infinite.InfiniteClient
import org.lwjgl.glfw.GLFW
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class InfiniteScreen(
    title: Text,
) : Screen(title) {
    companion object {
        var selectedPageIndex: Int = 0
    }

    private val uiWidth: Int
        get() = (width * 0.5).toInt().coerceAtLeast(400)
    private val uiHeight: Int
        get() = (height * 0.8).toInt()
    private val startY: Int
        get() = (height - uiHeight) / 2

    var pageIndex: Int = 0
        private set
    private var currentAngle: Float = 0.0f
    private var targetAngle: Float = 0.0f
    private var animationStartTime: Long = 0L
    private var initialAngle: Float = 0.0f
    private val animationDurationMs: Long = 300L

    private lateinit var sections: List<UISection>
    private val radius: Float = 400.0f

    data class RenderableSectionInfo(
        val section: UISection,
        val screenX: Int,
        val screenY: Int,
        val scaledWidth: Int,
        val scaledHeight: Int,
        val z3d: Float,
        val isSelected: Boolean,
        val index: Int,
    )

    override fun init() {
        super.init()

        val dynamicSections = mutableListOf<UISection>()
        dynamicSections.add(UISection("main", this))

        InfiniteClient.featureCategories.forEach { category ->
            dynamicSections.add(UISection(category.name.lowercase() + "-settings", this, category.features))
        }

        sections = dynamicSections

        pageIndex = selectedPageIndex
        val sectionSpacingAngle = 360.0f / sections.size
        currentAngle = -pageIndex * sectionSpacingAngle
        targetAngle = currentAngle
        initialAngle = currentAngle
        animationStartTime = System.currentTimeMillis()
    }

    override fun render(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val sectionWidth = uiWidth
        val sectionHeight = uiHeight
        val sectionSpacingAngle = 360.0f / sections.size

        val zOffset = radius * 2.0f
        val zoom = zOffset - radius

        val elapsedAnimationTime = System.currentTimeMillis() - animationStartTime
        val animationProgress = (elapsedAnimationTime.toFloat() / animationDurationMs.toFloat()).coerceIn(0.0f, 1.0f)

        if (animationProgress < 1.0f) {
            val easedProgress = 1 - (1 - animationProgress).let { it * it * it }
            currentAngle = initialAngle + (targetAngle - initialAngle) * easedProgress
        } else {
            currentAngle = targetAngle
        }

        val selectedSectionId = sections[pageIndex].id
        val renderedSections = mutableListOf<RenderableSectionInfo>()

        for ((index, section) in sections.withIndex()) {
            val angle = (index * sectionSpacingAngle + currentAngle) * (PI / 180.0f).toFloat()
            val cosAngle = cos(angle)

            val x3d = radius * sin(angle)
            val z3d = zOffset - radius * cosAngle

            val newX = (x3d / z3d * zoom)
            val newY = (0.0f / z3d * zoom)

            val scale = zoom / z3d
            val scaledWidth = (sectionWidth * scale).toInt()
            val scaledHeight = (sectionHeight * scale).toInt()

            val screenX = (width / 2 + newX - scaledWidth / 2).toInt()
            val screenY = (height / 2 + newY - scaledHeight / 2).toInt()

            val isCurrentSelected = section.id == selectedSectionId

            renderedSections.add(
                RenderableSectionInfo(
                    section,
                    screenX,
                    screenY,
                    scaledWidth,
                    scaledHeight,
                    z3d,
                    isCurrentSelected,
                    index,
                ),
            )
        }

        renderedSections.sortByDescending { it.z3d }

        if (animationProgress >= 1.0f) {
            context.drawCenteredTextWithShadow(
                textRenderer,
                title,
                width / 2,
                startY + 15,
                InfiniteClient.getCurrentColors().foregroundColor,
            )
        }

        val minZ = zOffset - radius
        val maxZ = zOffset + radius

        // 描画ロジックの修正
        for (info in renderedSections) {
            val normalizedZ = ((info.z3d - minZ) / (maxZ - minZ)).coerceIn(0f, 1f)
            val alpha = (80 - 60 * normalizedZ).toInt()
            val newBorderColor = InfiniteClient.theme().colors.panelColor(info.index, sections.size, normalizedZ)

            // 中央に来ていないパネルはタイトルのみ描画
            val renderContent = info.z3d < minZ + 1f // Z値が最も手前にあるパネルのみコンテンツを描画
            info.section.render(
                context,
                info.screenX,
                info.screenY,
                info.scaledWidth,
                info.scaledHeight,
                mouseX,
                mouseY,
                delta,
                info.isSelected,
                textRenderer,
                newBorderColor,
                alpha,
                renderContent,
            )
        }
    }

    override fun keyPressed(input: KeyInput): Boolean {
        if (System.currentTimeMillis() - animationStartTime < animationDurationMs) {
            return true
        }

        val sectionSpacingAngle = 360.0f / sections.size
        val oldPageIndex = pageIndex

        pageIndex =
            when (input.key) {
                GLFW.GLFW_KEY_LEFT -> {
                    (pageIndex - 1 + sections.size) % sections.size
                }

                GLFW.GLFW_KEY_RIGHT -> {
                    (pageIndex + 1) % sections.size
                }

                else -> {
                    sections[pageIndex].keyPressed(input, true)
                    return super.keyPressed(input)
                }
            }

        // Calculate the shortest path
        val diff = pageIndex - oldPageIndex
        val direction = if (diff > 0) 1 else -1 // Determine the direction of change
        val absDiff = abs(diff)

        // Check if wrapping around is shorter
        val shortestDiff =
            if (absDiff > sections.size / 2) {
                // Wrap around
                (sections.size - absDiff) * -direction
            } else {
                diff
            }

        initialAngle = targetAngle
        targetAngle += -shortestDiff * sectionSpacingAngle
        animationStartTime = System.currentTimeMillis()

        return true
    }

    override fun mouseClicked(
        click: Click,
        doubled: Boolean,
    ): Boolean {
        if (System.currentTimeMillis() - animationStartTime < animationDurationMs) {
            return true
        }
        val isSelected = true
        sections[pageIndex].mouseClicked(click, doubled, isSelected)
        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        if (System.currentTimeMillis() - animationStartTime < animationDurationMs) {
            return true
        }
        sections[pageIndex].mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount, true)
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseDragged(
        click: Click,
        offsetX: Double,
        offsetY: Double,
    ): Boolean {
        if (System.currentTimeMillis() - animationStartTime < animationDurationMs) {
            return true
        }
        sections[pageIndex].mouseDragged(click, offsetX, offsetY, true)
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseReleased(click: Click): Boolean {
        if (System.currentTimeMillis() - animationStartTime < animationDurationMs) {
            return true
        }
        sections[pageIndex].mouseReleased(click, true)
        return super.mouseReleased(click)
    }

    override fun charTyped(input: CharInput): Boolean {
        if (System.currentTimeMillis() - animationStartTime < animationDurationMs) {
            return true
        }
        sections[pageIndex].charTyped(input, true)
        return super.charTyped(input)
    }

    override fun shouldPause(): Boolean = false
}
