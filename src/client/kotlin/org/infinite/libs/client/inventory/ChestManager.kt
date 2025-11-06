package org.infinite.libs.client.inventory

import net.minecraft.block.entity.ChestBlockEntity
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
        val blockState = currentWorld.getBlockState(pos)
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
     * 指定されたアイテムをチェストに格納（手動クリック方式）
     * @param item 格納するアイテム
     * @return 格納した個数
     */
    fun storeItemInChest(item: Item): Int {
        val currentPlayer = player ?: return 0
        val screenHandler = currentPlayer.currentScreenHandler
        val interactionManager = interactionManager ?: return 0

        // プレイヤーインベントリ画面（syncId == 0）なら処理しない
        if (screenHandler.syncId == 0) return 0

        var totalStored = 0

        // 1. プレイヤーのスロット（9-44）から該当アイテムを探す
        val playerSlots = (9..35) + (36..44)
        val playerSlotList =
            playerSlots.mapNotNull { slotIndex ->
                val stack = screenHandler.getSlot(slotIndex).stack
                if (stack.item == item && !stack.isEmpty) slotIndex to stack.count else null
            }

        if (playerSlotList.isEmpty()) return 0

        // 2. チェストの空きスロット or 同じアイテムがあるスロットを探す
        val chestSlotRange = 0 until screenHandler.slots.size - 36 // コンテナ部分（36はプレイヤー分）
        val availableChestSlots =
            chestSlotRange
                .mapNotNull { slotIndex ->
                    val slot = screenHandler.getSlot(slotIndex)
                    val stack = slot.stack
                    if (stack.isEmpty) {
                        slotIndex to 64 // 空きスロット
                    } else if (stack.item == item && stack.count < stack.maxCount) {
                        slotIndex to (stack.maxCount - stack.count) // 追加可能数
                    } else {
                        null
                    }
                }.toMutableList()

        if (availableChestSlots.isEmpty()) return 0

        // 3. 手動でクリック処理（カーソルにアイテムを持った状態をシミュレート）
        for ((playerSlot, itemCount) in playerSlotList) {
            var remaining = itemCount

            // カーソルが空であることを前提（実際は前処理でクリアすべき）
            // → ここでは簡易的に1スタックずつ処理

            while (remaining > 0 && availableChestSlots.isNotEmpty()) {
                val (chestSlot, space) = availableChestSlots.first()
                val take = minOf(remaining, space, 64) // 1回のクリックで最大64
                // --- クリックシーケンス ---
                // ① プレイヤースロットを左クリック → カーソルにアイテム
                interactionManager.clickSlot(screenHandler.syncId, playerSlot, 0, SlotActionType.PICKUP, currentPlayer)
                // ② チェストスロットを左クリック → アイテムを置く
                interactionManager.clickSlot(screenHandler.syncId, chestSlot, 0, SlotActionType.PICKUP, currentPlayer)
                // --- 終了 ---

                totalStored += take
                remaining -= take
                // チェストスロットが満杯になったら削除
                if (take >= space) {
                    availableChestSlots.drop(1)
                } else {
                    // まだ空きがある → 更新
                    availableChestSlots[0] = chestSlot to (space - take)
                }
            }

            if (availableChestSlots.isEmpty()) break
        }

        // 最後にカーソルが残っていたら、元のスロットに戻す（省略可）
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
