package org.theinfinitys.features.rendering.detailinfo

import net.minecraft.block.ChestBlock
import net.minecraft.block.enums.ChestType
import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.util.math.ColorHelper
import org.theinfinitys.infinite.graphics.Graphics2D

object BlockContentRenderer {
    private const val PADDING = 5
    private const val ICON_SIZE = 18

    /**
     * 現在のブロックのインベントリデータ（ダブルチェストの場合は結合したもの）を取得します。
     * チェスト以外の場合はそのままのデータを返します。
     */
    private fun getEffectiveInventoryData(
        client: MinecraftClient,
        detail: DetailInfo.TargetDetail.BlockDetail,
        feature: DetailInfo,
    ): InventoryData? {
        val detailPos = detail.pos ?: return null
        val effectiveData = feature.getChestContents(detailPos)

        val blockState = client.world?.getBlockState(detailPos)
        // ダブルチェストの結合ロジック
        if (blockState?.block is ChestBlock && effectiveData?.type == InventoryType.CHEST && effectiveData.items.size == 27) {
            val chestType = blockState.get(ChestBlock.CHEST_TYPE)
            if (chestType != ChestType.SINGLE) {
                val facing = blockState.get(ChestBlock.FACING)
                val otherOffset = if (chestType == ChestType.RIGHT) facing.rotateYClockwise() else facing.rotateYCounterclockwise()
                val otherPos = detailPos.offset(otherOffset)

                val otherData = feature.getChestContents(otherPos)
                if (otherData != null && otherData.items.size == 27) {
                    val leftPos = if (chestType == ChestType.RIGHT) detailPos else otherPos
                    val combinedItems =
                        if (detailPos == leftPos) {
                            effectiveData.items + otherData.items
                        } else {
                            otherData.items + effectiveData.items
                        }
                    // 結合されたInventoryDataを返す
                    return InventoryData(effectiveData.type, combinedItems)
                }
            }
        }
        // チェストでない場合、またはシングルチェスト/結合できなかった場合はそのままのデータを返す
        return effectiveData
    }

    fun calculateHeight(
        client: MinecraftClient,
        detail: DetailInfo.TargetDetail.BlockDetail,
        feature: DetailInfo,
        uiWidth: Int,
        isTargetInReach: Boolean,
    ): Int {
        val font = client.textRenderer
        var requiredHeight = DetailInfoRenderer.BORDER_WIDTH + PADDING + ICON_SIZE + PADDING

        val effectiveData = getEffectiveInventoryData(client, detail, feature)

        if (effectiveData != null && effectiveData.items.isNotEmpty()) {
            requiredHeight += InventoryRenderer.calculateHeight(client, effectiveData, uiWidth)
        }

        requiredHeight += font.fontHeight + 2 + DetailInfoRenderer.BORDER_WIDTH + PADDING
        return requiredHeight
    }

    fun draw(
        graphics2d: Graphics2D,
        client: MinecraftClient,
        detail: DetailInfo.TargetDetail.BlockDetail,
        feature: DetailInfo,
        startX: Int,
        startY: Int,
        uiWidth: Int,
        isTargetInReach: Boolean,
    ) {
        if (detail.pos == null) return
        val font = client.textRenderer
        val iconX = startX + DetailInfoRenderer.BORDER_WIDTH + PADDING
        val iconY = startY + DetailInfoRenderer.BORDER_WIDTH + PADDING
        val textX = iconX + ICON_SIZE + PADDING

        // --- 1. ブロックアイコンと基本情報の描画 ---

        val blockIconStack = ItemStack(detail.block)
        InventoryRenderer.drawItemWithDurability(
            graphics2d,
            blockIconStack,
            iconX,
            iconY,
            ICON_SIZE,
            ColorUtils.getFeatureColor(isTargetInReach),
        )

        val correctTool = ToolChecker.getCorrectTool(detail.block)
        val blockId = Registries.BLOCK.getId(detail.block).toString()
        val infoName = detail.block.name.string

        graphics2d.drawText(infoName, textX, iconY + 1, 0xFFFFFFFF.toInt(), true)
        val nameWidth = font.getWidth(infoName)
        graphics2d.drawText(
            "($blockId)",
            textX + nameWidth + 5,
            iconY + 1,
            ColorHelper.getArgb(192, 255, 255, 255),
            true,
        )
        val correctToolId = correctTool.getId()
        if (correctToolId != null) {
            val toolIconX = startX + uiWidth - ICON_SIZE - PADDING
            graphics2d.fill(
                toolIconX,
                iconY,
                ICON_SIZE,
                ICON_SIZE,
                when (correctTool.checkPlayerToolStatus()) {
                    0 -> 0x8800FF00
                    1 -> 0x88FFFF00
                    2 -> 0x88FF0000
                    else -> 0x88FFFFFF
                }.toInt(),
            )
            InventoryRenderer.drawItemWithDurability(
                graphics2d,
                ToolChecker.getItemStackFromId(correctToolId),
                toolIconX,
                iconY,
                ICON_SIZE,
                ColorUtils.getFeatureColor(isTargetInReach),
            )
        }

        // --- 2. インベントリ中身の描画 ---
        var contentY = startY + DetailInfoRenderer.BORDER_WIDTH + PADDING + ICON_SIZE + PADDING

        // 修正: 汎用的なデータ取得メソッドを使用
        val effectiveData = getEffectiveInventoryData(client, detail, feature)

        if (effectiveData != null && effectiveData.items.isNotEmpty()) {
            // InventoryRenderer.drawに結合済みデータ（またはそのままのデータ）を渡す
            contentY = InventoryRenderer.draw(graphics2d, client, effectiveData, startX, contentY, uiWidth, isTargetInReach, feature)
        }

        // --- 3. 座標情報の描画 ---
        val infoPos = detail.pos
        val posText = "Pos: x=${infoPos.x}, y=${infoPos.y}, z=${infoPos.z}"
        graphics2d.drawText(posText, iconX, contentY, 0xFFFFFFFF.toInt(), true)
    }
}
