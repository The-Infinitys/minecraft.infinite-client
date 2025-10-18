package org.infinite.features.rendering.detailinfo

import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.math.ColorHelper
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.rendering.ColorUtils

object InventoryRenderer {
    private const val PADDING = 5
    private const val SLOT_SIZE = 16
    private const val ITEM_PADDING = 2
    private const val BAR_HEIGHT = 4

    fun calculateHeight(
        client: MinecraftClient,
        inventoryData: InventoryData,
        uiWidth: Int,
    ): Int {
        val font = client.textRenderer
        var requiredHeight = font.fontHeight + 2

        if (inventoryData.items.isEmpty()) return requiredHeight + PADDING

        val rowsNeeded: Int
        val barSpace = 25

        when (inventoryData.type) {
            InventoryType.FURNACE -> {
                rowsNeeded = 2
                requiredHeight += (rowsNeeded * SLOT_SIZE + 10) + barSpace
                requiredHeight += 4 * (font.fontHeight + 2)
            }
            InventoryType.BREWING -> {
                rowsNeeded = 2
                requiredHeight += rowsNeeded * SLOT_SIZE + barSpace
                requiredHeight += 2 * (font.fontHeight + 2)
                requiredHeight += 2 * (BAR_HEIGHT + 2)
            }
            InventoryType.HOPPER -> {
                rowsNeeded = 1
                requiredHeight += rowsNeeded * SLOT_SIZE
            }
            InventoryType.CHEST -> {
                rowsNeeded = if (inventoryData.items.size > 27) 6 else 3
                requiredHeight += rowsNeeded * SLOT_SIZE + (rowsNeeded - 1) * 2
            }
            InventoryType.GENERIC -> {
                val slotSizeWithPadding = SLOT_SIZE + 2
                val maxItemsPerRow = ((uiWidth - 2 * DetailInfoRenderer.BORDER_WIDTH - 2 * PADDING) / slotSizeWithPadding).coerceAtLeast(1)
                rowsNeeded = (inventoryData.items.size + maxItemsPerRow - 1) / maxItemsPerRow
                requiredHeight += rowsNeeded * SLOT_SIZE + (rowsNeeded - 1) * 2
            }
        }

        return requiredHeight + PADDING
    }

    fun draw(
        graphics2d: Graphics2D,
        client: MinecraftClient,
        inventoryData: InventoryData,
        startX: Int,
        currentY: Int,
        uiWidth: Int,
        isTargetInReach: Boolean,
        detailInfoFeature: DetailInfo,
    ): Int {
        val font = client.textRenderer
        var drawingY = currentY
        val featureColor = ColorUtils.getFeatureColor(isTargetInReach)
        val headerText = Text.literal("Inventory: (${inventoryData.type})")
        val headerX = startX + DetailInfoRenderer.BORDER_WIDTH + PADDING
        graphics2d.drawText(headerText.string, headerX, drawingY, 0xFFFFFFFF.toInt(), true)
        drawingY += font.fontHeight + 2

        if (inventoryData.items.isEmpty()) return drawingY + PADDING

        val innerContentWidth = uiWidth - 2 * DetailInfoRenderer.BORDER_WIDTH - 2 * PADDING
        val centerX = startX + DetailInfoRenderer.BORDER_WIDTH + PADDING + innerContentWidth / 2

        when (inventoryData.type) {
            InventoryType.FURNACE -> {
                val spaceBetween = 10
                val barLength = 60
                val totalWidth = SLOT_SIZE + barLength + spaceBetween + SLOT_SIZE
                val leftX = centerX - totalWidth / 2

                val inputY = drawingY
                val inputX = leftX
                graphics2d.drawBorder(inputX, inputY, SLOT_SIZE, SLOT_SIZE, featureColor)
                val inputItem = inventoryData.items.getOrElse(0) { ItemStack.EMPTY }
                drawItemWithDurability(graphics2d, inputItem, inputX, inputY, SLOT_SIZE, featureColor)
                if (inputItem.count > 1) {
                    graphics2d.drawText(
                        inputItem.count.toString(),
                        inputX + SLOT_SIZE - font.getWidth(inputItem.count.toString()) - 2,
                        inputY + SLOT_SIZE - font.fontHeight,
                        0xFFFFFFFF.toInt(),
                        true,
                    )
                }

                val fuelY = inputY + SLOT_SIZE + spaceBetween
                val fuelX = leftX
                graphics2d.drawBorder(fuelX, fuelY, SLOT_SIZE, SLOT_SIZE, featureColor)
                val fuelItem = inventoryData.items.getOrElse(1) { ItemStack.EMPTY }
                drawItemWithDurability(graphics2d, fuelItem, fuelX, fuelY, SLOT_SIZE, featureColor)
                if (fuelItem.count > 1) {
                    graphics2d.drawText(
                        fuelItem.count.toString(),
                        fuelX + SLOT_SIZE - font.getWidth(fuelItem.count.toString()) - 2,
                        fuelY + SLOT_SIZE - font.fontHeight,
                        0xFFFFFFFF.toInt(),
                        true,
                    )
                }

                val outputX = leftX + SLOT_SIZE + barLength + spaceBetween
                val outputY = inputY + (SLOT_SIZE + spaceBetween) / 2
                graphics2d.drawBorder(outputX, outputY, SLOT_SIZE, SLOT_SIZE, featureColor)
                val outputItem = inventoryData.items.getOrElse(2) { ItemStack.EMPTY }
                drawItemWithDurability(graphics2d, outputItem, outputX, outputY, SLOT_SIZE, featureColor)
                if (outputItem.count > 1) {
                    graphics2d.drawText(
                        outputItem.count.toString(),
                        outputX + SLOT_SIZE - font.getWidth(outputItem.count.toString()) - 2,
                        outputY + SLOT_SIZE - font.fontHeight,
                        0xFFFFFFFF.toInt(),
                        true,
                    )
                }

                val blockPos = detailInfoFeature.targetDetail?.pos ?: return drawingY + PADDING
                val furnaceData = detailInfoFeature.getFurnaceData(blockPos)
                val litTimeRemaining = furnaceData.litTimeRemaining
                val litTotalTime = furnaceData.litTotalTime
                val cookingTimeSpent = furnaceData.cookingTimeSpent
                val cookingTotalTime = furnaceData.cookingTotalTime

                val fuelPercent = if (litTotalTime > 0) litTimeRemaining.toFloat() / litTotalTime.toFloat() else 0f
                val progress = if (cookingTotalTime > 0) cookingTimeSpent.toFloat() / cookingTotalTime.toFloat() else 0f

                val arrowX = inputX + SLOT_SIZE + 5
                val arrowY = inputY + SLOT_SIZE / 2 - 2
                val arrowWidth = barLength
                graphics2d.fill(arrowX, arrowY, arrowWidth, 4, ColorHelper.getArgb(128, 50, 50, 50))
                val fillWidth = (arrowWidth * progress).toInt()
                graphics2d.fill(arrowX, arrowY, fillWidth, 4, ColorUtils.getGradientColor(progress))

                val flameX = fuelX + SLOT_SIZE + 5
                val flameY = fuelY + SLOT_SIZE / 2 - 2
                val flameWidth = barLength
                graphics2d.fill(flameX, flameY, flameWidth, 4, ColorHelper.getArgb(128, 50, 50, 50))
                val fillFlame = (flameWidth * fuelPercent).toInt()
                graphics2d.fill(flameX, flameY, fillFlame, 4, ColorUtils.getGradientColor(fuelPercent))

                drawingY = fuelY + SLOT_SIZE + 5
                graphics2d.drawText(
                    "Cooking progress: ${(progress * 100).toInt()}%",
                    headerX,
                    drawingY,
                    0xFFFFFFFF.toInt(),
                    true,
                )
                drawingY += font.fontHeight + 2

                val currentTimeLeft = (cookingTotalTime - cookingTimeSpent) / 20.0
                graphics2d.drawText(
                    "Time to complete current: ${TimeFormatter.formatTime(currentTimeLeft)}",
                    headerX,
                    drawingY,
                    0xFFFFFFFF.toInt(),
                    true,
                )
                drawingY += font.fontHeight + 2

                val inputCount = inputItem.count
                val additional = if (cookingTimeSpent > 0) inputCount - 1 else inputCount
                val totalTime = currentTimeLeft + additional * (cookingTotalTime / 20.0)
                graphics2d.drawText(
                    "Time to complete all: ${TimeFormatter.formatTime(totalTime)}",
                    headerX,
                    drawingY,
                    0xFFFFFFFF.toInt(),
                    true,
                )
                drawingY += font.fontHeight + 2
                val fuelRegistry = client.world!!.fuelRegistry
                val fuelLeftTick = litTimeRemaining + fuelItem.count * fuelRegistry.getFuelTicks(fuelItem)
                val fuelLeft = fuelLeftTick / 20.0
                graphics2d.drawText(
                    "Fuel remaining: ${TimeFormatter.formatTime(fuelLeft)}",
                    headerX,
                    drawingY,
                    0xFFFFFFFF.toInt(),
                    true,
                )
                drawingY += font.fontHeight + 2

                return drawingY + PADDING
            }
            InventoryType.BREWING -> {
                val itemY = drawingY
                val ingredientX = centerX - SLOT_SIZE / 2
                graphics2d.drawBorder(ingredientX, itemY, SLOT_SIZE, SLOT_SIZE, featureColor)
                val ingredientItem = inventoryData.items.getOrElse(3) { ItemStack.EMPTY }
                drawItemWithDurability(graphics2d, ingredientItem, ingredientX, itemY, SLOT_SIZE, featureColor)
                if (ingredientItem.count > 1) {
                    graphics2d.drawText(
                        ingredientItem.count.toString(),
                        ingredientX + SLOT_SIZE - font.getWidth(ingredientItem.count.toString()) - 2,
                        itemY + SLOT_SIZE - font.fontHeight,
                        0xFFFFFFFF.toInt(),
                        true,
                    )
                }

                val barSpace = 20
                val row2Y = itemY + SLOT_SIZE + barSpace
                val blazeX = startX + DetailInfoRenderer.BORDER_WIDTH + PADDING + 30
                graphics2d.drawBorder(blazeX, row2Y, SLOT_SIZE, SLOT_SIZE, featureColor)
                val blazeItem = inventoryData.items.getOrElse(4) { ItemStack.EMPTY }
                drawItemWithDurability(graphics2d, blazeItem, blazeX, row2Y, SLOT_SIZE, featureColor)
                if (blazeItem.count > 1) {
                    graphics2d.drawText(
                        blazeItem.count.toString(),
                        blazeX + SLOT_SIZE - font.getWidth(blazeItem.count.toString()) - 2,
                        row2Y + SLOT_SIZE - font.fontHeight,
                        0xFFFFFFFF.toInt(),
                        true,
                    )
                }

                val potionWidth = 3 * SLOT_SIZE + 2 * ITEM_PADDING
                val potionStartX = centerX - potionWidth / 2
                for (i in 0..2) {
                    val potionX = potionStartX + i * (SLOT_SIZE + ITEM_PADDING)
                    graphics2d.drawBorder(potionX, row2Y, SLOT_SIZE, SLOT_SIZE, featureColor)
                    val potionItem = inventoryData.items.getOrElse(i) { ItemStack.EMPTY }
                    drawItemWithDurability(graphics2d, potionItem, potionX, row2Y, SLOT_SIZE, featureColor)
                    if (potionItem.count > 1) {
                        graphics2d.drawText(
                            potionItem.count.toString(),
                            potionX + SLOT_SIZE - font.getWidth(potionItem.count.toString()) - 2,
                            row2Y + SLOT_SIZE - font.fontHeight,
                            0xFFFFFFFF.toInt(),
                            true,
                        )
                    }
                }

                val blockPos = detailInfoFeature.targetDetail?.pos ?: return drawingY + PADDING
                val brewingData = detailInfoFeature.getBrewingData(blockPos)
                val brewTime = brewingData.brewTime
                val fuel = brewingData.fuel

                val arrowX = ingredientX + SLOT_SIZE / 2 - 5
                val arrowY = itemY + SLOT_SIZE + 8
                val arrowHeight = row2Y - (itemY + SLOT_SIZE) - 10
                val progress = if (brewTime > 0) (400 - brewTime).toFloat() / 400 else 0f
                graphics2d.fill(arrowX, arrowY, 10, arrowHeight, ColorHelper.getArgb(128, 50, 50, 50))
                val fillHeight = (arrowHeight * progress).toInt()
                graphics2d.fill(arrowX, arrowY, 10, fillHeight, ColorUtils.getGradientColor(progress))

                val fuelProgress = fuel.toFloat() / 20f
                val fuelBarX = blazeX + SLOT_SIZE + 5
                val fuelBarY = row2Y + SLOT_SIZE / 2 - 2
                val fuelBarWidth = 40
                graphics2d.fill(fuelBarX, fuelBarY, fuelBarWidth, 4, ColorHelper.getArgb(128, 50, 50, 50))
                val fuelFillWidth = (fuelBarWidth * fuelProgress).toInt()
                graphics2d.fill(fuelBarX, fuelBarY, fuelFillWidth, 4, ColorUtils.getGradientColor(fuelProgress))

                drawingY = row2Y + SLOT_SIZE + 5
                val brewTimeLeft = brewTime / 20.0
                graphics2d.drawText(
                    "Brewing time left: ${TimeFormatter.formatTime(brewTimeLeft)}",
                    headerX,
                    drawingY,
                    0xFFFFFFFF.toInt(),
                    true,
                )
                drawingY += font.fontHeight + 2

                graphics2d.drawText(
                    "Fuel left: $fuel / 20 brews",
                    headerX,
                    drawingY,
                    0xFFFFFFFF.toInt(),
                    true,
                )
                drawingY += font.fontHeight + 2

                return drawingY + PADDING
            }
            else -> {
                val maxItemsPerRow: Int
                val fixedRows: Int?

                when (inventoryData.type) {
                    InventoryType.HOPPER -> {
                        maxItemsPerRow = 5
                        fixedRows = 1
                    }
                    InventoryType.CHEST -> {
                        maxItemsPerRow = 9
                        fixedRows = if (inventoryData.items.size > 27) 6 else 3
                    }
                    InventoryType.GENERIC -> {
                        maxItemsPerRow = (innerContentWidth / (SLOT_SIZE + ITEM_PADDING)).coerceAtLeast(1)
                        fixedRows = null
                    }
                    else -> return drawingY + PADDING
                }

                val totalItems = inventoryData.items.size
                val rowsNeeded =
                    if (totalItems == 0) {
                        0
                    } else {
                        val calculatedRows = (totalItems + maxItemsPerRow - 1) / maxItemsPerRow
                        if (fixedRows != null) calculatedRows.coerceAtMost(fixedRows) else calculatedRows
                    }

                val itemsStartY = drawingY
                var rowCount = 0

                for (row in 0 until rowsNeeded) {
                    val startItemIndex = row * maxItemsPerRow
                    val itemsInCurrentRow = (totalItems - startItemIndex).coerceAtMost(maxItemsPerRow)
                    val totalRowWidth = itemsInCurrentRow * SLOT_SIZE + (itemsInCurrentRow - 1) * ITEM_PADDING
                    val rowStartX = startX + DetailInfoRenderer.BORDER_WIDTH + PADDING + (innerContentWidth - totalRowWidth) / 2
                    val itemDrawingY = itemsStartY + row * (SLOT_SIZE + ITEM_PADDING)

                    for (col in 0 until itemsInCurrentRow) {
                        val index = startItemIndex + col
                        if (index >= inventoryData.items.size) continue

                        val itemStack = inventoryData.items[index]
                        val itemX = rowStartX + col * (SLOT_SIZE + ITEM_PADDING)
                        graphics2d.drawBorder(itemX, itemDrawingY, SLOT_SIZE, SLOT_SIZE, featureColor)
                        drawItemWithDurability(graphics2d, itemStack, itemX, itemDrawingY, SLOT_SIZE, featureColor)
                        val itemCount = itemStack.count
                        if (itemCount > 1) {
                            graphics2d.drawText(
                                itemCount.toString(),
                                itemX + SLOT_SIZE - font.getWidth(itemCount.toString()) - 2,
                                itemDrawingY + SLOT_SIZE - font.fontHeight,
                                0xFFFFFFFF.toInt(),
                                true,
                            )
                        }
                    }
                    rowCount = row + 1
                }

                return itemsStartY + rowCount * (SLOT_SIZE + ITEM_PADDING) + PADDING
            }
        }
    }

    fun drawItemWithDurability(
        graphics2d: Graphics2D,
        itemStack: ItemStack,
        x: Int,
        y: Int,
        slotSize: Int,
        featureColor: Int,
    ) {
        graphics2d.drawItem(itemStack, x, y)
        if (itemStack.isDamageable && itemStack.damage > 0) {
            val durability = itemStack.maxDamage - itemStack.damage
            val progress = durability.toFloat() / itemStack.maxDamage.toFloat()
            val barColor = ColorUtils.getGradientColor(progress)
            val barHeight = 2
            val barY = y + slotSize - barHeight - 1
            val barWidth = (slotSize * progress).toInt()
            graphics2d.fill(x, barY, barWidth, barHeight, barColor)
            graphics2d.fill(x + barWidth, barY, slotSize - barWidth, barHeight, ColorHelper.getArgb(128, 0, 0, 0))
        }
    }
}
