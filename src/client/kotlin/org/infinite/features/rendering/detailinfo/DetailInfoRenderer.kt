package org.infinite.features.rendering.detailinfo

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import net.minecraft.text.Text
import net.minecraft.util.math.ColorHelper
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.rendering.ColorUtils

object DetailInfoRenderer {
    internal const val BORDER_WIDTH = 2
    const val BAR_HEIGHT = 4
    const val BAR_PADDING = 5
    private val INNER_COLOR =
        ColorHelper.getArgb(
            192,
            ColorHelper.getRed(
                org.infinite.InfiniteClient
                    .theme()
                    .colors.backgroundColor,
            ),
            ColorHelper.getGreen(
                org.infinite.InfiniteClient
                    .theme()
                    .colors.backgroundColor,
            ),
            ColorHelper.getBlue(
                org.infinite.InfiniteClient
                    .theme()
                    .colors.backgroundColor,
            ),
        )

    fun render(
        graphics2d: Graphics2D,
        client: MinecraftClient,
        detailInfoFeature: DetailInfo,
    ) {
        val detail = detailInfoFeature.targetDetail ?: return
        val interactionManager = client.interactionManager ?: return
        val isTargetInReach = detailInfoFeature.isTargetInReach

        val screenWidth = client.window.scaledWidth
        val widthSetting = detailInfoFeature.getSetting("Width")?.value as? Int ?: return
        val startY = detailInfoFeature.getSetting("PaddingTop")?.value as? Int ?: return

        val uiWidth = (screenWidth * widthSetting / 100)
        val startX = (screenWidth / 2) - (uiWidth / 2)
        val endX = startX + uiWidth

        val requiredHeight =
            when (detail) {
                is DetailInfo.TargetDetail.BlockDetail -> {
                    BlockContentRenderer.calculateHeight(client, detail, detailInfoFeature, uiWidth, isTargetInReach)
                }
                is DetailInfo.TargetDetail.EntityDetail -> {
                    EntityContentRenderer.calculateHeight(client, detail, uiWidth)
                }
            }

        val endY = startY + requiredHeight
        val featureColor = ColorUtils.getFeatureColor(isTargetInReach)
        drawBackgroundAndBorder(graphics2d, startX, startY, endX, endY, featureColor)

        when (detail) {
            is DetailInfo.TargetDetail.BlockDetail -> {
                BlockContentRenderer.draw(graphics2d, client, detail, detailInfoFeature, startX, startY, uiWidth, isTargetInReach)
                if (interactionManager.isBreakingBlock) {
                    val progress = interactionManager.currentBreakingProgress.coerceIn(0.0f, 1.0f)
                    val infoText = TimeFormatter.getBreakingTimeText(progress, client)
                    drawBar(graphics2d, startX, endX, endY, progress, infoText)
                }
            }
            is DetailInfo.TargetDetail.EntityDetail -> {
                EntityContentRenderer.draw(graphics2d, client, detail, startX, startY, uiWidth)
                val entity = detail.entity
                if (entity is LivingEntity) {
                    val progress = entity.health / entity.maxHealth
                    val infoText = Text.literal("HP: ${"%.1f".format(entity.health)} / ${entity.maxHealth}")
                    drawBar(graphics2d, startX, endX, endY, progress, infoText)
                }
            }
        }
    }

    private fun drawBackgroundAndBorder(
        graphics2d: Graphics2D,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        featureColor: Int,
    ) {
        graphics2d.fill(startX, startY, endX - startX, endY - startY, INNER_COLOR)
        graphics2d.fill(startX, startY, endX - startX, BORDER_WIDTH, featureColor)
        graphics2d.fill(startX, endY - BORDER_WIDTH, endX - startX, BORDER_WIDTH, featureColor)
        graphics2d.fill(startX, startY + BORDER_WIDTH, BORDER_WIDTH, endY - startY - 2 * BORDER_WIDTH, featureColor)
        graphics2d.fill(endX - BORDER_WIDTH, startY + BORDER_WIDTH, BORDER_WIDTH, endY - startY - 2 * BORDER_WIDTH, featureColor)
    }

    private fun drawBar(
        graphics2d: Graphics2D,
        startX: Int,
        endX: Int,
        endY: Int,
        progress: Float,
        infoText: Text,
    ) {
        val barY = endY - BORDER_WIDTH - BAR_HEIGHT - BAR_PADDING
        val barStartX = startX + BORDER_WIDTH + BAR_PADDING
        val barEndX = endX - BORDER_WIDTH - BAR_PADDING
        val barWidth = barEndX - barStartX
        val font = MinecraftClient.getInstance().textRenderer

        val fillWidth = (barWidth * progress).toInt()
        val barBackgroundColor =
            ColorHelper.getArgb(
                128,
                ColorHelper.getRed(
                    org.infinite.InfiniteClient
                        .theme()
                        .colors.backgroundColor,
                ),
                ColorHelper.getGreen(
                    org.infinite.InfiniteClient
                        .theme()
                        .colors.backgroundColor,
                ),
                ColorHelper.getBlue(
                    org.infinite.InfiniteClient
                        .theme()
                        .colors.backgroundColor,
                ),
            )
        graphics2d.fill(barStartX, barY, barWidth, BAR_HEIGHT, barBackgroundColor)

        if (fillWidth > 0) {
            for (x in 0 until fillWidth) {
                val colorProgress = x.toFloat() / barWidth.toFloat()
                val color = ColorUtils.getGradientColor(colorProgress)
                graphics2d.fill(barStartX + x, barY, 1, BAR_HEIGHT, color)
            }
        }

        graphics2d.drawText(
            infoText.string,
            barStartX,
            barY - font.fontHeight - 2,
            org.infinite.InfiniteClient
                .theme()
                .colors.foregroundColor,
            true,
        )
    }
}
