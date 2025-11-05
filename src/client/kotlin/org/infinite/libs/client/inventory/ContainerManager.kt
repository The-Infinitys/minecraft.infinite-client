package org.infinite.libs.client.inventory

import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.math.BlockPos
import org.infinite.libs.client.player.ClientInterface

/**
 * チェスト操作用のInventoryManager拡張
 */
object ChestManager : ClientInterface() {
    /**
     * 指定された位置のチェストを開きます
     * @param pos チェストの位置
     * @return 成功した場合true
     */
    fun openChest(pos: BlockPos): Boolean {
        val currentPlayer = player ?: return false
        val currentWorld = world ?: return false

        val blockEntity = currentWorld.getBlockEntity(pos)
        if (blockEntity !is ChestBlockEntity) return false

        // チェストをインタラクト
        currentWorld.getBlockState(pos)
        interactionManager?.interactBlock(
            currentPlayer,
            currentPlayer.activeHand,
            net.minecraft.util.hit.BlockHitResult(
                pos.toCenterPos(),
                net.minecraft.util.math.Direction.UP,
                pos,
                false,
            ),
        )

        return true
    }

    /**
     * 現在開いているチェストを閉じます
     */
    fun closeChest() {
        player?.closeHandledScreen()
    }

    /**
     * 指定されたアイテムをチェストに格納します
     * @param item 格納するアイテム
     * @return 格納した個数
     */
    fun storeItemInChest(item: Item): Int {
        val player = player ?: return 0
        val screen = client.currentScreen

        // プレイヤーのインベントリ画面でない場合（チェストが開いている場合）
        if (screen !is GenericContainerScreen) return 0

        var totalStored = 0
        val screenHandler = player.currentScreenHandler
        // プレイヤーのインベントリからアイテムを探す
        // ホットバー (ネットワークスロット 36-44)
        for (i in 36..44) {
            val stack = screenHandler.getSlot(i).stack
            if (stack.item == item && !stack.isEmpty) {
                // Shift+クリックでチェストに移動
                interactionManager?.clickSlot(
                    screenHandler.syncId,
                    i,
                    0,
                    SlotActionType.QUICK_MOVE,
                    player,
                )
                totalStored += stack.count
            }
        }

        // バックパック (ネットワークスロット 9-35)
        for (i in 9..35) {
            val stack = screenHandler.getSlot(i).stack
            if (stack.item == item && !stack.isEmpty) {
                interactionManager?.clickSlot(
                    screenHandler.syncId,
                    i,
                    0,
                    SlotActionType.QUICK_MOVE,
                    player,
                )
                totalStored += stack.count
            }
        }

        return totalStored
    }

    /**
     * 鉱石と石系ブロックをチェストに格納します
     * @return 格納した総アイテム数
     */
    fun storeMinedItems(): Int {
        var total = 0

        // 鉱石類
        val oreItems =
            listOf(
                Items.COAL,
                Items.RAW_IRON,
                Items.RAW_COPPER,
                Items.RAW_GOLD,
                Items.DIAMOND,
                Items.EMERALD,
                Items.LAPIS_LAZULI,
                Items.REDSTONE,
                Items.QUARTZ,
                Items.COAL_ORE,
                Items.DEEPSLATE_COAL_ORE,
                Items.IRON_ORE,
                Items.DEEPSLATE_IRON_ORE,
                Items.COPPER_ORE,
                Items.DEEPSLATE_COPPER_ORE,
                Items.GOLD_ORE,
                Items.DEEPSLATE_GOLD_ORE,
                Items.DIAMOND_ORE,
                Items.DEEPSLATE_DIAMOND_ORE,
                Items.EMERALD_ORE,
                Items.DEEPSLATE_EMERALD_ORE,
                Items.LAPIS_ORE,
                Items.DEEPSLATE_LAPIS_ORE,
                Items.REDSTONE_ORE,
                Items.DEEPSLATE_REDSTONE_ORE,
                Items.NETHER_QUARTZ_ORE,
                Items.NETHER_GOLD_ORE,
                Items.ANCIENT_DEBRIS,
            )

        // 石系統
        val stoneItems =
            listOf(
                Items.COBBLESTONE,
                Items.COBBLED_DEEPSLATE,
                Items.STONE,
                Items.DEEPSLATE,
                Items.GRANITE,
                Items.DIORITE,
                Items.ANDESITE,
                Items.TUFF,
                Items.CALCITE,
                Items.DRIPSTONE_BLOCK,
                Items.NETHERRACK,
                Items.BLACKSTONE,
            )

        val allItems = oreItems + stoneItems

        for (item in allItems) {
            total += storeItemInChest(item)
        }

        return total
    }

    /**
     * プレイヤーのインベントリに空きスロットが何個あるか確認します
     * @return 空きスロット数
     */
    fun getEmptySlotCount(): Int {
        val playerInv = inventory ?: return 0
        var count = 0

        // ホットバー (0-8) とバックパック (9-35) をチェック
        for (i in 0..35) {
            if (playerInv.getStack(i).isEmpty) {
                count++
            }
        }

        return count
    }
}
