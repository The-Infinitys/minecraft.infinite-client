package org.theinfinitys.features.rendering.gui

import drawBorder
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.block.ChestBlock
import net.minecraft.block.enums.ChestType
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.registry.tag.EnchantmentTags
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.ColorHelper
import kotlin.collections.get

object DetailInfoRenderer {
    private const val BORDER_WIDTH = 2
    private const val BAR_HEIGHT = 4
    private const val BAR_PADDING = 5
    private val INNER_COLOR = ColorHelper.getArgb(192, 0, 0, 0)
    private val OUT_OF_REACH_COLOR = ColorHelper.getArgb(255, 150, 150, 150)

    private fun getGradientColor(progress: Float): Int {
        val clampedProgress = progress.coerceIn(0.0f, 1.0f)
        val r: Int
        val g: Int
        val b: Int

        if (clampedProgress <= 0.5f) {
            val p = clampedProgress * 2.0f
            r = 255
            g = (255 * p).toInt()
            b = 0
        } else {
            val p = (clampedProgress - 0.5f) * 2.0f
            r = (255 * (1.0f - p)).toInt()
            g = 255
            b = (255 * p).toInt()
        }

        return ColorHelper.getArgb(255, r, g, b)
    }

    private fun getFeatureColor(isInReach: Boolean): Int =
        if (isInReach) {
            getRainbowColor()
        } else {
            OUT_OF_REACH_COLOR
        }

    private fun drawBackgroundAndBorder(
        context: DrawContext,
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        featureColor: Int,
    ) {
        context.fill(startX, startY, endX, endY, INNER_COLOR)
        context.fill(startX, startY, endX, startY + BORDER_WIDTH, featureColor)
        context.fill(startX, endY - BORDER_WIDTH, endX, endY, featureColor)
        context.fill(startX, startY + BORDER_WIDTH, startX + BORDER_WIDTH, endY - BORDER_WIDTH, featureColor)
        context.fill(endX - BORDER_WIDTH, startY + BORDER_WIDTH, endX, endY - BORDER_WIDTH, featureColor)
    }

    private fun calculateInventoryHeight(
        client: MinecraftClient,
        inventoryData: InventoryData,
        uiWidth: Int,
    ): Int {
        val font = client.textRenderer
        val padding = 5
        val slotSize = 16
        var requiredHeight = 0

        if (inventoryData.items.isEmpty()) return 0

        requiredHeight += font.fontHeight + 2

        val rowsNeeded: Int
        val barSpace = 25 // バー用のスペースを増加

        when (inventoryData.type) {
            InventoryType.FURNACE -> {
                rowsNeeded = 2
                requiredHeight += rowsNeeded * slotSize + barSpace
                requiredHeight += 4 * (font.fontHeight + 2) // テキスト行
            }

            InventoryType.BREWING -> {
                rowsNeeded = 2
                requiredHeight += rowsNeeded * slotSize + barSpace
                requiredHeight += 2 * (font.fontHeight + 2) // テキスト行
            }

            InventoryType.HOPPER -> {
                rowsNeeded = 1
                requiredHeight += rowsNeeded * slotSize
            }

            InventoryType.CHEST -> {
                rowsNeeded = if (inventoryData.items.size > 27) 6 else 3
                requiredHeight += rowsNeeded * slotSize + (rowsNeeded - 1) * 2 // itemPadding = 2
            }

            InventoryType.GENERIC -> {
                val slotSizeWithPadding = slotSize + 2 // itemPadding = 2
                val maxItemsPerRow = ((uiWidth - 2 * BORDER_WIDTH - 2 * padding) / slotSizeWithPadding).coerceAtLeast(1)
                rowsNeeded = (inventoryData.items.size + maxItemsPerRow - 1) / maxItemsPerRow
                requiredHeight += rowsNeeded * slotSize + (rowsNeeded - 1) * 2
            }
        }

        requiredHeight += padding

        return requiredHeight
    }

    private fun drawItemWithDurability(
        context: DrawContext,
        itemStack: ItemStack,
        x: Int,
        y: Int,
        slotSize: Int,
        featureColor: Int,
    ) {
        context.drawItem(itemStack, x, y)
        if (itemStack.isDamageable && itemStack.damage > 0) {
            val durability = itemStack.maxDamage - itemStack.damage
            val progress = durability.toFloat() / itemStack.maxDamage.toFloat()
            val barColor = getGradientColor(progress)
            val barHeight = 2
            val barY = y + slotSize - barHeight - 1
            val barWidth = (slotSize * progress).toInt()
            context.fill(x, barY, x + barWidth, barY + barHeight, barColor)
            context.fill(x + barWidth, barY, x + slotSize, barY + barHeight, ColorHelper.getArgb(128, 0, 0, 0)) // 背景グレー
        }
    }

    private fun drawInventoryContents(
        context: DrawContext,
        client: MinecraftClient,
        inventoryData: InventoryData,
        startX: Int,
        currentY: Int,
        uiWidth: Int,
        isTargetInReach: Boolean,
        detailInfoFeature: DetailInfo,
    ): Int {
        val font = client.textRenderer
        val padding = 5
        val itemPadding = 2
        val slotSize = 16
        var drawingY = currentY

        val headerText = Text.literal("Inventory: (${inventoryData.type})")
        val headerX = startX + BORDER_WIDTH + padding
        context.drawText(
            font,
            headerText,
            headerX,
            drawingY,
            0xFFFFFFFF.toInt(),
            true,
        )
        drawingY += font.fontHeight + 2

        if (inventoryData.items.isEmpty()) {
            return drawingY + padding
        }

        val featureColor = getFeatureColor(isTargetInReach)
        val innerContentWidth = uiWidth - 2 * BORDER_WIDTH - 2 * padding
        val centerX = startX + BORDER_WIDTH + padding + innerContentWidth / 2

        when (inventoryData.type) {
            InventoryType.FURNACE -> {
                // かまどのレイアウト
                val inputY = drawingY
                val inputX = centerX - slotSize / 2
                context.drawBorder(inputX, inputY, slotSize, slotSize, featureColor)
                val inputItem = inventoryData.items.getOrElse(0) { ItemStack.EMPTY }
                drawItemWithDurability(context, inputItem, inputX, inputY, slotSize, featureColor)
                if (inputItem.count > 1) {
                    context.drawText(
                        font,
                        inputItem.count.toString(),
                        inputX + slotSize - font.getWidth(inputItem.count.toString()) - 2,
                        inputY + slotSize - font.fontHeight,
                        0xFFFFFFFF.toInt(),
                        true,
                    )
                }

                val barSpace = 25
                val row2Y = inputY + slotSize + barSpace

                val sectionWidth = innerContentWidth / 3
                val fuelX = startX + BORDER_WIDTH + padding + sectionWidth / 2 - slotSize / 2
                context.drawBorder(fuelX, row2Y, slotSize, slotSize, featureColor)
                val fuelItem = inventoryData.items.getOrElse(1) { ItemStack.EMPTY }
                drawItemWithDurability(context, fuelItem, fuelX, row2Y, slotSize, featureColor)
                if (fuelItem.count > 1) {
                    context.drawText(
                        font,
                        fuelItem.count.toString(),
                        fuelX + slotSize - font.getWidth(fuelItem.count.toString()) - 2,
                        row2Y + slotSize - font.fontHeight,
                        0xFFFFFFFF.toInt(),
                        true,
                    )
                }

                val outputX = startX + BORDER_WIDTH + padding + 2 * sectionWidth + sectionWidth / 2 - slotSize / 2
                context.drawBorder(outputX, row2Y, slotSize, slotSize, featureColor)
                val outputItem = inventoryData.items.getOrElse(2) { ItemStack.EMPTY }
                drawItemWithDurability(context, outputItem, outputX, row2Y, slotSize, featureColor)
                if (outputItem.count > 1) {
                    context.drawText(
                        font,
                        outputItem.count.toString(),
                        outputX + slotSize - font.getWidth(outputItem.count.toString()) - 2,
                        row2Y + slotSize - font.fontHeight,
                        0xFFFFFFFF.toInt(),
                        true,
                    )
                }

                // データ取得（FurnaceDataを使用）
                val blockPos = detailInfoFeature.targetDetail?.pos ?: return drawingY + padding
                val furnaceData = detailInfoFeature.getFurnaceData(blockPos)
                val litTimeRemaining = furnaceData.litTimeRemaining
                val litTotalTime = furnaceData.litTotalTime
                val cookingTimeSpent = furnaceData.cookingTimeSpent
                val cookingTotalTime = furnaceData.cookingTotalTime

                val fuelPercent = if (litTotalTime > 0) litTimeRemaining.toFloat() / litTotalTime.toFloat() else 0f
                val progress = if (cookingTotalTime > 0) cookingTimeSpent.toFloat() / cookingTotalTime.toFloat() else 0f

                // 炎バー (燃料の下、垂直)
                val flameY = row2Y + slotSize + 2
                val flameHeight = 12
                val fillHeight = (flameHeight * fuelPercent).toInt()
                context.fill(
                    fuelX + 2,
                    flameY + flameHeight - fillHeight,
                    fuelX + slotSize - 2,
                    flameY + flameHeight,
                    getGradientColor(1f - fuelPercent),
                )

                // 矢印バー (入力から出力まで水平)
                val arrowX = inputX + slotSize + 5
                val arrowY = inputY + slotSize / 2 - 2
                val arrowWidth = outputX - arrowX - 5
                if (arrowWidth > 0) {
                    context.fill(arrowX, arrowY, arrowX + arrowWidth, arrowY + 4, ColorHelper.getArgb(128, 50, 50, 50))
                    val fillWidth = (arrowWidth * progress).toInt()
                    context.fill(arrowX, arrowY, arrowX + fillWidth, arrowY + 4, getGradientColor(progress))
                }

                // テキスト
                drawingY = row2Y + slotSize + flameHeight + 5
                context.drawText(
                    font,
                    Text.literal("Cooking progress: ${(progress * 100).toInt()}%"),
                    headerX,
                    drawingY,
                    0xFFFFFFFF.toInt(),
                    true,
                )
                drawingY += font.fontHeight + 2

                val currentTimeLeft = (cookingTotalTime - cookingTimeSpent) / 20.0
                context.drawText(
                    font,
                    Text.literal("Time to complete current: %.1f s".format(currentTimeLeft)),
                    headerX,
                    drawingY,
                    0xFFFFFFFF.toInt(),
                    true,
                )
                drawingY += font.fontHeight + 2

                val inputCount = inputItem.count
                val additional = if (cookingTimeSpent > 0) inputCount - 1 else inputCount
                val totalTime = currentTimeLeft + additional * (cookingTotalTime / 20.0)
                context.drawText(
                    font,
                    Text.literal("Time to complete all: %.1f s".format(totalTime)),
                    headerX,
                    drawingY,
                    0xFFFFFFFF.toInt(),
                    true,
                )
                drawingY += font.fontHeight + 2

                val fuelLeft = litTimeRemaining / 20.0
                context.drawText(font, Text.literal("Fuel remaining: %.1f s".format(fuelLeft)), headerX, drawingY, 0xFFFFFFFF.toInt(), true)
                drawingY += font.fontHeight + 2

                return drawingY + padding
            }

            InventoryType.BREWING -> {
                // 醸造台のレイアウト
                val itemY = drawingY
                val ingredientX = centerX - slotSize / 2
                context.drawBorder(ingredientX, itemY, slotSize, slotSize, featureColor)
                val ingredientItem = inventoryData.items.getOrElse(3) { ItemStack.EMPTY }
                drawItemWithDurability(context, ingredientItem, ingredientX, itemY, slotSize, featureColor)
                if (ingredientItem.count > 1) {
                    context.drawText(
                        font,
                        ingredientItem.count.toString(),
                        ingredientX + slotSize - font.getWidth(ingredientItem.count.toString()) - 2,
                        itemY + slotSize - font.fontHeight,
                        0xFFFFFFFF.toInt(),
                        true,
                    )
                }

                val barSpace = 20
                val row2Y = itemY + slotSize + barSpace

                val blazeX = startX + BORDER_WIDTH + padding + 30
                context.drawBorder(blazeX, row2Y, slotSize, slotSize, featureColor)
                val blazeItem = inventoryData.items.getOrElse(4) { ItemStack.EMPTY }
                drawItemWithDurability(context, blazeItem, blazeX, row2Y, slotSize, featureColor)
                if (blazeItem.count > 1) {
                    context.drawText(
                        font,
                        blazeItem.count.toString(),
                        blazeX + slotSize - font.getWidth(blazeItem.count.toString()) - 2,
                        row2Y + slotSize - font.fontHeight,
                        0xFFFFFFFF.toInt(),
                        true,
                    )
                }

                val potionWidth = 3 * slotSize + 2 * itemPadding
                val potionStartX = centerX - potionWidth / 2
                for (i in 0..2) {
                    val potionX = potionStartX + i * (slotSize + itemPadding)
                    context.drawBorder(potionX, row2Y, slotSize, slotSize, featureColor)
                    val potionItem = inventoryData.items.getOrElse(i) { ItemStack.EMPTY }
                    drawItemWithDurability(context, potionItem, potionX, row2Y, slotSize, featureColor)
                    if (potionItem.count > 1) {
                        context.drawText(
                            font,
                            potionItem.count.toString(),
                            potionX + slotSize - font.getWidth(potionItem.count.toString()) - 2,
                            row2Y + slotSize - font.fontHeight,
                            0xFFFFFFFF.toInt(),
                            true,
                        )
                    }
                }

                // データ取得（BrewingDataを使用）
                val blockPos = detailInfoFeature.targetDetail?.pos ?: return drawingY + padding
                val brewingData = detailInfoFeature.getBrewingData(blockPos)
                val brewTime = brewingData.brewTime
                val fuel = brewingData.fuel

                // 矢印バー (垂直)
                val arrowX = ingredientX + slotSize / 2 - 5
                val arrowY = itemY + slotSize + 8
                val arrowHeight = row2Y - (itemY + slotSize) - 10
                val progress = if (brewTime > 0) (400 - brewTime).toFloat() / 400 else 0f
                val fillHeight = (arrowHeight * progress).toInt()
                context.fill(arrowX, arrowY + arrowHeight - fillHeight, arrowX + 10, arrowY + arrowHeight, getGradientColor(progress))

                // テキスト
                drawingY = row2Y + slotSize + 5
                val brewTimeLeft = brewTime / 20.0
                context.drawText(
                    font,
                    Text.literal("Brewing time left: %.1f s".format(brewTimeLeft)),
                    headerX,
                    drawingY,
                    0xFFFFFFFF.toInt(),
                    true,
                )
                drawingY += font.fontHeight + 2

                context.drawText(font, Text.literal("Fuel left: $fuel brews"), headerX, drawingY, 0xFFFFFFFF.toInt(), true)
                drawingY += font.fontHeight + 2

                return drawingY + padding
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
                        maxItemsPerRow = (innerContentWidth / (slotSize + itemPadding)).coerceAtLeast(1)
                        fixedRows = null
                    }

                    else -> return drawingY + padding
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
                    val totalRowWidth = itemsInCurrentRow * slotSize + (itemsInCurrentRow - 1) * itemPadding
                    val rowStartX = startX + BORDER_WIDTH + padding + (innerContentWidth - totalRowWidth) / 2
                    val itemDrawingY = itemsStartY + row * (slotSize + itemPadding)

                    for (col in 0 until itemsInCurrentRow) {
                        val index = startItemIndex + col
                        if (index >= inventoryData.items.size) continue

                        val itemStack = inventoryData.items[index]
                        val itemX = rowStartX + col * (slotSize + itemPadding)
                        context.drawBorder(itemX, itemDrawingY, slotSize, slotSize, featureColor)
                        drawItemWithDurability(context, itemStack, itemX, itemDrawingY, slotSize, featureColor)
                        val itemCount = itemStack.count
                        if (itemCount > 1) {
                            context.drawText(
                                font,
                                itemCount.toString(),
                                itemX + slotSize - font.getWidth(itemCount.toString()) - 2,
                                itemDrawingY + slotSize - font.fontHeight,
                                0xFFFFFFFF.toInt(),
                                true,
                            )
                        }
                    }
                    rowCount = row + 1
                }

                return itemsStartY + rowCount * (slotSize + itemPadding) + padding
            }
        }
    }

    private enum class ToolKind {
        Sword,
        Axe,
        PickAxe,
        Shovel,
        Hoe,
    }

    private class CorrectTool(
        val toolKind: ToolKind?,
        val toolLevel: Int,
        val isSilkTouchRequired: Boolean = false,
    ) {
        fun checkPlayerToolStatus(): Int {
            val client = MinecraftClient.getInstance()
            val player = client.player ?: return 2
            val heldItem: ItemStack = player.mainHandStack

            if (toolKind == null) {
                return 0
            }

            val toolId = Registries.ITEM.getId(heldItem.item).toString()
            val isCorrectToolKind =
                when (toolKind) {
                    ToolKind.PickAxe -> toolId.endsWith("_pickaxe")
                    ToolKind.Axe -> toolId.endsWith("_axe")
                    ToolKind.Shovel -> toolId.endsWith("_shovel")
                    ToolKind.Sword -> toolId.endsWith("_sword")
                    ToolKind.Hoe -> toolId.endsWith("_hoe")
                }

            if (!isCorrectToolKind) {
                return 2
            }

            if (isSilkTouchRequired) {
                val hasSilkTouch =
                    EnchantmentHelper
                        .getEnchantments(heldItem)
                        .enchantments
                        .any { it.isIn(EnchantmentTags.PREVENTS_BEE_SPAWNS_WHEN_MINING) }

                if (!hasSilkTouch) {
                    return 1
                }
            }

            return 0
        }

        fun getId(): String? {
            if (toolKind == null) {
                return null
            }

            val material: String =
                when (toolLevel) {
                    3 -> "diamond"
                    2 -> "iron"
                    1 -> "stone"
                    0 -> "wooden"
                    else -> "wooden"
                }

            val toolSuffix: String =
                when (toolKind) {
                    ToolKind.PickAxe -> "pickaxe"
                    ToolKind.Shovel -> "shovel"
                    ToolKind.Axe -> "axe"
                    ToolKind.Hoe -> "hoe"
                    ToolKind.Sword -> "sword"
                }

            return "minecraft:${material}_$toolSuffix"
        }
    }

    fun isSilkTouchRequiredClient(block: Block): Boolean {
        val state = block.defaultState
        val id = Registries.BLOCK.getId(block).path

        if (id.endsWith("_ore") || id == "ancient_debris") {
            return true
        }

        if (block == Blocks.STONE || block == Blocks.DEEPSLATE) {
            return true
        }

        if (block == Blocks.GILDED_BLACKSTONE) {
            return true
        }

        if (id.contains("glass") ||
            id.contains("ice") ||
            block == Blocks.BLUE_ICE ||
            block == Blocks.PACKED_ICE ||
            block == Blocks.FROSTED_ICE
        ) {
            return block != Blocks.FROSTED_ICE
        }

        if (block == Blocks.GLOWSTONE) {
            return true
        }

        if (block == Blocks.COBWEB) {
            return true
        }

        if (block == Blocks.SEA_LANTERN) {
            return true
        }

        if (block == Blocks.GRASS_BLOCK || block == Blocks.MYCELIUM || block == Blocks.PODZOL || block == Blocks.DIRT_PATH) {
            return true
        }

        if (block == Blocks.ENDER_CHEST) {
            return true
        }

        if (block == Blocks.BEEHIVE || block == Blocks.BEE_NEST) {
            return true
        }

        if (state.isIn(BlockTags.LEAVES)) {
            return true
        }

        val amethistId = Registries.BLOCK.getId(block).path
        if (amethistId.startsWith("small_amethyst_bud") ||
            amethistId.startsWith("medium_amethyst_bud") ||
            amethistId.startsWith("large_amethyst_bud") ||
            amethistId == "amethyst_cluster"
        ) {
            return true
        }

        if (state.isIn(BlockTags.CORAL_BLOCKS) || state.isIn(BlockTags.CORAL_PLANTS)) {
            return true
        }

        return false
    }

    private fun getCorrectTool(block: Block): CorrectTool {
        val state = block.defaultState
        val toolLevel =
            if (state.isIn(BlockTags.NEEDS_STONE_TOOL)) {
                1
            } else if (state.isIn(BlockTags.NEEDS_IRON_TOOL)) {
                2
            } else if (state.isIn(BlockTags.NEEDS_DIAMOND_TOOL)) {
                3
            } else {
                0
            }
        val toolKind =
            if (state.isIn(BlockTags.AXE_MINEABLE)) {
                ToolKind.Axe
            } else if (state.isIn(BlockTags.PICKAXE_MINEABLE)) {
                ToolKind.PickAxe
            } else if (state.isIn(BlockTags.SHOVEL_MINEABLE)) {
                ToolKind.Shovel
            } else if (state.isIn(BlockTags.HOE_MINEABLE)) {
                ToolKind.Hoe
            } else if (state.isIn(BlockTags.LEAVES) || Registries.BLOCK.getId(block).toString() == "minecraft:cobweb") {
                ToolKind.Sword
            } else {
                null
            }

        val isSilkTouchRequired = isSilkTouchRequiredClient(block)

        return CorrectTool(toolKind, toolLevel, isSilkTouchRequired)
    }

    private fun getItemStackFromId(id: String): ItemStack =
        try {
            val identifier = Identifier.of(id)
            val item = Registries.ITEM.get(identifier)
            if (item != Items.AIR) {
                ItemStack(item)
            } else {
                ItemStack(Items.BARRIER)
            }
        } catch (_: Exception) {
            ItemStack(Items.BARRIER)
        }

    private fun drawBlockContent(
        context: DrawContext,
        client: MinecraftClient,
        detail: DetailInfo.TargetDetail.BlockDetail,
        feature: DetailInfo,
        startX: Int,
        startY: Int,
        uiWidth: Int,
        drawOnly: Boolean,
        isTargetInReach: Boolean,
    ): Int {
        val font = client.textRenderer
        val padding = 5
        val iconSize = 18
        val iconX = startX + BORDER_WIDTH + padding
        val iconY = startY + BORDER_WIDTH + padding
        val textX = iconX + iconSize + padding

        var requiredHeight = BORDER_WIDTH + padding + iconSize + padding

        if (drawOnly) {
            val blockIconStack = ItemStack(detail.block)
            drawItemWithDurability(context, blockIconStack, iconX, iconY, iconSize, getFeatureColor(isTargetInReach))

            val correctTool = getCorrectTool(detail.block)
            val blockId = Registries.BLOCK.getId(detail.block).toString()
            val infoName = detail.block.name.string

            context.drawText(font, Text.literal(infoName), textX, iconY + 1, 0xFFFFFFFF.toInt(), true)
            val nameWidth = font.getWidth(infoName)
            context.drawText(
                font,
                Text.literal("($blockId)"),
                textX + nameWidth + 5,
                iconY + 1,
                ColorHelper.getArgb(192, 255, 255, 255),
                true,
            )
            val correctToolId = correctTool.getId()
            if (correctToolId != null) {
                val toolIconX = startX + uiWidth - iconSize - padding
                context.fill(
                    toolIconX,
                    iconY,
                    toolIconX + iconSize,
                    iconY + iconSize,
                    when (correctTool.checkPlayerToolStatus()) {
                        0 -> 0x8800FF00
                        1 -> 0x88FFFF00
                        2 -> 0x88FF0000
                        else -> 0x88FFFFFF
                    }.toInt(),
                )
                drawItemWithDurability(
                    context,
                    getItemStackFromId(correctToolId),
                    toolIconX,
                    iconY,
                    iconSize,
                    getFeatureColor(isTargetInReach),
                )
            }
        }

        val inventoryData = feature.scannedInventoryData[detail.pos]

        // ラージチェストの場合、データを結合
        var effectiveData: InventoryData? = inventoryData
        val blockState = client.world?.getBlockState(detail.pos)
        if (blockState?.block is ChestBlock && inventoryData?.type == InventoryType.CHEST && inventoryData.items.size == 27) {
            val chestType = blockState.get(ChestBlock.CHEST_TYPE)
            if (chestType != ChestType.SINGLE) {
                val facing = blockState.get(ChestBlock.FACING)
                val otherOffset = if (chestType == ChestType.RIGHT) facing.rotateYClockwise() else facing.rotateYCounterclockwise()
                val otherPos = detail.pos?.offset(otherOffset)
                if (otherPos != null) {
                    val otherData = feature.scannedInventoryData[otherPos]
                    if (otherData != null && otherData.items.size == 27) {
                        val leftPos = if (chestType == ChestType.RIGHT) detail.pos else otherPos
                        val combinedItems =
                            if (detail.pos == leftPos) {
                                inventoryData.items + otherData.items
                            } else {
                                otherData.items + inventoryData.items
                            }
                        effectiveData = InventoryData(inventoryData.type, combinedItems)
                    }
                }
            }
        }

        var contentY = startY + requiredHeight

        if (effectiveData != null && effectiveData.items.isNotEmpty()) {
            val inventoryHeight = calculateInventoryHeight(client, effectiveData, uiWidth)
            requiredHeight += inventoryHeight

            if (drawOnly) {
                contentY =
                    drawInventoryContents(
                        context,
                        client,
                        effectiveData,
                        startX,
                        contentY,
                        uiWidth,
                        isTargetInReach,
                        feature,
                    )
            }
        }

        requiredHeight += font.fontHeight + 2

        if (drawOnly) {
            val infoPos = detail.pos
            val posText = if (infoPos != null) "Pos: x=${infoPos.x}, y=${infoPos.y}, z=${infoPos.z}" else "Pos: Unknown"
            context.drawText(font, Text.literal(posText), iconX, contentY, 0xFFFFFFFF.toInt(), true)
            font.fontHeight + 2
        } else {
            font.fontHeight + 2
        }

        requiredHeight += font.fontHeight + 2 + BORDER_WIDTH + padding

        return requiredHeight
    }

    private fun drawEntityContent(
        context: DrawContext,
        client: MinecraftClient,
        detail: DetailInfo.TargetDetail.EntityDetail,
        startX: Int,
        startY: Int,
        uiWidth: Int,
        drawOnly: Boolean,
    ): Int {
        val font = client.textRenderer
        val padding = 5
        val iconX = startX + BORDER_WIDTH + padding
        val iconY = startY + BORDER_WIDTH + padding

        var requiredHeight =
            BORDER_WIDTH + padding + font.fontHeight + padding

        if (drawOnly) {
            val entityName = detail.entity.type.name.string
            val entityId = Registries.ENTITY_TYPE.getId(detail.entity.type).toString()

            context.drawText(font, Text.literal(entityName), iconX, iconY + 1, 0xFFFFFFFF.toInt(), true)

            val nameWidth = font.getWidth(entityName)
            context.drawText(
                font,
                Text.literal("($entityId)"),
                iconX + nameWidth + 5,
                iconY + 1,
                ColorHelper.getArgb(192, 255, 255, 255),
                true,
            )
        }

        val entity = detail.entity
        val hasBar = entity is LivingEntity
        if (hasBar) {
            requiredHeight += font.fontHeight + 2 + BAR_HEIGHT + BAR_PADDING
        }

        requiredHeight += font.fontHeight + 2

        val contentY = startY + requiredHeight - (font.fontHeight + 2)

        if (drawOnly) {
            val infoPos = detail.entity.blockPos
            val posText = "Pos: x=${infoPos.x}, y=${infoPos.y}, z=${infoPos.z}"
            context.drawText(font, Text.literal(posText), iconX, contentY, 0xFFFFFFFF.toInt(), true)
        }

        requiredHeight += font.fontHeight + 2 + BORDER_WIDTH + padding

        return requiredHeight
    }

    private fun drawBar(
        context: DrawContext,
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
        val barBackgroundColor = ColorHelper.getArgb(128, 50, 50, 50)
        context.fill(barStartX, barY, barEndX, barY + BAR_HEIGHT, barBackgroundColor)

        if (fillWidth > 0) {
            for (x in 0 until fillWidth) {
                val colorProgress = x.toFloat() / barWidth.toFloat()
                val color = getGradientColor(colorProgress)
                context.fill(barStartX + x, barY, barStartX + x + 1, barY + BAR_HEIGHT, color)
            }
        }

        context.drawText(
            font,
            infoText,
            barStartX,
            barY - font.fontHeight - 2,
            0xFFFFFFFF.toInt(),
            true,
        )
    }

    private fun getBreakingTimeText(
        progress: Float,
        client: MinecraftClient,
    ): Text {
        val player = client.player ?: return Text.empty()
        val world = client.world ?: return Text.empty()
        val interactionManager = client.interactionManager ?: return Text.empty()

        val blockPos = interactionManager.currentBreakingPos
        val blockState = client.world?.getBlockState(blockPos)
        val destroySpeed = blockState?.calcBlockBreakingDelta(player, world, blockPos) ?: 0.0f

        if (destroySpeed <= 0.0001f) return Text.literal("Indestructible")

        val totalTicks = 1.0f / destroySpeed
        val remainingTicks = (1.0f - progress) * totalTicks
        val totalSeconds = totalTicks / 20.0f
        val remainingSeconds = remainingTicks / 20.0f

        val totalSecStr = "%.1f".format(totalSeconds)
        val remainingSecStr = "%.1f".format(remainingSeconds)

        return Text.literal("Time: $remainingSecStr / $totalSecStr s")
    }

    fun render(
        context: DrawContext,
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
                    drawBlockContent(
                        context,
                        client,
                        detail,
                        detailInfoFeature,
                        startX,
                        startY,
                        uiWidth,
                        drawOnly = false,
                        isTargetInReach = isTargetInReach,
                    )
                }

                is DetailInfo.TargetDetail.EntityDetail -> {
                    drawEntityContent(context, client, detail, startX, startY, uiWidth, drawOnly = false)
                }
            }

        val endY = startY + requiredHeight
        val featureColor = getFeatureColor(isTargetInReach)
        drawBackgroundAndBorder(context, startX, startY, endX, endY, featureColor)

        when (detail) {
            is DetailInfo.TargetDetail.BlockDetail -> {
                drawBlockContent(
                    context,
                    client,
                    detail,
                    detailInfoFeature,
                    startX,
                    startY,
                    uiWidth,
                    drawOnly = true,
                    isTargetInReach = isTargetInReach,
                )

                if (interactionManager.isBreakingBlock) {
                    val progress = interactionManager.currentBreakingProgress.coerceIn(0.0f, 1.0f)
                    val infoText = getBreakingTimeText(progress, client)
                    drawBar(context, startX, endX, endY, progress, infoText)
                }
            }

            is DetailInfo.TargetDetail.EntityDetail -> {
                drawEntityContent(context, client, detail, startX, startY, uiWidth, drawOnly = true)

                val entity = detail.entity
                if (entity is LivingEntity) {
                    val progress = entity.health / entity.maxHealth
                    val infoText = Text.literal("HP: ${"%.1f".format(entity.health)} / ${entity.maxHealth}")
                    drawBar(context, startX, endX, endY, progress, infoText)
                }
            }
        }
    }

    private fun getRainbowColor(): Int {
        val rainbowDuration = 6000L
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
        val elapsedTime = currentTime % rainbowDuration
        val progress = elapsedTime.toFloat() / rainbowDuration.toFloat()
        val numSegments = colors.size - 1
        val segmentLength = 1.0f / numSegments
        val currentSegmentIndex = (progress / segmentLength).toInt().coerceAtMost(numSegments - 1)
        val segmentProgress = (progress % segmentLength) / segmentLength
        val startColor = colors[currentSegmentIndex]
        val endColor = colors[currentSegmentIndex + 1]

        return ColorHelper.getArgb(
            255,
            (ColorHelper.getRed(startColor) * (1 - segmentProgress) + ColorHelper.getRed(endColor) * segmentProgress).toInt(),
            (ColorHelper.getGreen(startColor) * (1 - segmentProgress) + ColorHelper.getGreen(endColor) * segmentProgress).toInt(),
            (ColorHelper.getBlue(startColor) * (1 - segmentProgress) + ColorHelper.getBlue(endColor) * segmentProgress).toInt(),
        )
    }
}
