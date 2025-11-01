package org.infinite.libs.client.inventory

import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket
import net.minecraft.screen.slot.SlotActionType
import org.infinite.libs.client.player.ClientInterface

object InventoryManager : ClientInterface() {
    private val isCreative: Boolean
        get() = player?.isCreative ?: false // nullの場合falseを返す

    sealed class InventoryIndex {
        open class Armor : InventoryIndex() {
            class Head : Armor()

            class Chest : Armor()

            class Legs : Armor()

            class Feet : Armor()
        }

        data class Hotbar(
            val index: Int,
        ) : InventoryIndex() {
            init {
                require(index in 0..8) { "Hotbar index must be between 0 and 8" }
            }
        }

        data class Backpack(
            val index: Int,
        ) : InventoryIndex() {
            init {
                require(index in 0..26) { "Backpack index must be between 0 and 26" }
            }
        }

        class MainHand : InventoryIndex()

        class OffHand : InventoryIndex()
    }

    fun get(index: InventoryIndex): ItemStack {
        val playerInv = inventory ?: return ItemStack.EMPTY // nullチェック
        return when (index) {
            is InventoryIndex.Armor ->
                playerInv.getStack(
                    when (index) {
                        is InventoryIndex.Armor.Head -> 39
                        is InventoryIndex.Armor.Chest -> 38
                        is InventoryIndex.Armor.Legs -> 37
                        is InventoryIndex.Armor.Feet -> 36
                        else -> {
                            throw IllegalStateException("Illegal State on InventoryIndex.Armor")
                        }
                    },
                ) ?: ItemStack.EMPTY

            is InventoryIndex.Hotbar -> playerInv.getStack(index.index) ?: ItemStack.EMPTY
            is InventoryIndex.Backpack -> playerInv.getStack(9 + index.index) ?: ItemStack.EMPTY
            is InventoryIndex.MainHand -> playerInv.getStack(40) ?: ItemStack.EMPTY
            is InventoryIndex.OffHand -> playerInv.getStack(playerInv.selectedSlot) ?: ItemStack.EMPTY
        }
    }

    fun set(
        index: InventoryIndex,
        stack: ItemStack,
    ): Boolean {
        val currentPlayer = player ?: return false // nullチェック
        val playerInv = inventory ?: return false
        val internal = indexToSlot(index) ?: return false
        if (isCreative) {
            playerInv.setStack(internal, stack)
            val packet = CreativeInventoryActionC2SPacket(toNetworkSlot(internal), stack)
            client.networkHandler?.sendPacket(packet)
            return true
        } else {
            try {
                // サバイバルモードでの set は複雑なため、アイテムを探してスワップするロジックを使用
                if (stack.isEmpty) {
                    // 空にする場合はドロップ（THROW）操作をシミュレート
                    interactionManager?.clickSlot(0, toNetworkSlot(internal), 1, SlotActionType.THROW, currentPlayer)
                    return true
                }

                val sourceInternal = findSlotWithItem(stack.item) ?: return false
                if (sourceInternal == internal) return true

                val targetNet = toNetworkSlot(internal)
                val sourceNet = toNetworkSlot(sourceInternal)
                interactionManager?.clickSlot(0, sourceNet, 0, SlotActionType.PICKUP, currentPlayer)
                interactionManager?.clickSlot(0, targetNet, 0, SlotActionType.PICKUP, currentPlayer)
                interactionManager?.clickSlot(0, sourceNet, 0, SlotActionType.PICKUP, currentPlayer)
                return true
            } catch (_: Exception) {
                return false
            }
        }
    }

    fun count(item: Item): Int {
        val playerInv = inventory ?: return 0 // nullチェック
        var total = 0
        for (i in 0 until playerInv.size()) {
            val stack = playerInv.getStack(i)
            if (stack.item == item) {
                total += stack.count
            }
        }
        return total
    }

    fun sort() {
        val playerInv = inventory ?: return
        if (isCreative) {
            val stacks = mutableListOf<ItemStack>()
            // バックパックのスロット番号は 9 から 35 (27スロット)
            for (i in 9..35) {
                stacks.add(playerInv.getStack(i).copy())
                playerInv.setStack(i, ItemStack.EMPTY)
            }
            stacks.sortBy { Item.getRawId(it.item) } // アイテムIDでソート

            // クリエイティブインベントリパケットを送信
            for (i in 0..26) {
                val internalSlot = 9 + i
                val stack = stacks.getOrElse(i) { ItemStack.EMPTY }
                playerInv.setStack(internalSlot, stack)
                val packet = CreativeInventoryActionC2SPacket(toNetworkSlot(internalSlot), stack)
                client.networkHandler?.sendPacket(packet)
            }
        } else {
            // サバイバルでのソートは複雑なスワップが必要
            for (i in 0..25) {
                var minIdx = i
                // バックパックの内部スロットは 9+i
                for (j in i + 1..26) {
                    val idxI = 9 + minIdx
                    val idxJ = 9 + j
                    val idI = Item.getRawId(playerInv.getStack(idxI).item)
                    val idJ = Item.getRawId(playerInv.getStack(idxJ).item)
                    if (idJ < idI) minIdx = j
                }
                if (minIdx != i) {
                    // バックパック内のスワップ
                    swap(InventoryIndex.Backpack(i), InventoryIndex.Backpack(minIdx))
                }
            }
        }
    }

    fun findFirst(item: Item): InventoryIndex? {
        val playerInv = inventory ?: return null // nullチェック
        // ホットバー (0-8)
        for (i in 0 until 9) {
            if (playerInv.getStack(i).item == item) {
                return InventoryIndex.Hotbar(i)
            }
        }
        // バックパック (9-35, Backpack index 0-26)
        for (i in 0 until 27) {
            if (playerInv.getStack(9 + i).item == item) {
                return InventoryIndex.Backpack(i)
            }
        }
        // 防具 (36-39)
        for (i in 36 until 40) {
            if (playerInv.getStack(i).item == item) {
                return when (i) {
                    36 -> InventoryIndex.Armor.Feet()
                    37 -> InventoryIndex.Armor.Legs()
                    38 -> InventoryIndex.Armor.Chest()
                    39 -> InventoryIndex.Armor.Head()
                    else -> null
                }
            }
        }
        // オフハンド (40)
        if (playerInv.getStack(40).item == item) {
            return InventoryIndex.OffHand()
        }
        return null
    }

    fun findFirstFromBackPack(item: Item): InventoryIndex.Backpack? {
        val playerInv = inventory ?: return null // nullチェック
        for (i in 0 until 27) {
            if (playerInv.getStack(9 + i).item == item) {
                return InventoryIndex.Backpack(i)
            }
        }
        return null
    }

    /**
     * 2つのインベントリスロット間でアイテムをスワップします。
     * マウスカーソルにアイテムが残る問題を回避するためのロジックを含みます。
     */
    fun swap(
        indexA: InventoryIndex,
        indexB: InventoryIndex,
    ): Boolean {
        val currentPlayer = player ?: return false // nullチェック
        val slotA = indexToSlot(indexA) ?: return false
        val slotB = indexToSlot(indexB) ?: return false
        if (isCreative) {
            val stackA = get(indexA).copy()
            set(indexA, get(indexB))
            set(indexB, stackA)
            return true
        } else {
            try {
                val netA = toNetworkSlot(slotA)
                val netB = toNetworkSlot(slotB)
                val currentScreenId = currentPlayer.currentScreenHandler.syncId

                // 3クリック スワップシーケンス
                interactionManager?.clickSlot(currentScreenId, netA, 0, SlotActionType.PICKUP, currentPlayer)
                interactionManager?.clickSlot(currentScreenId, netB, 0, SlotActionType.PICKUP, currentPlayer)
                interactionManager?.clickSlot(currentScreenId, netA, 0, SlotActionType.PICKUP, currentPlayer)

                // 【修正ロジック】: 3クリック後もカーソルにアイテムが残っている場合、空きスロットに戻す
                if (!currentPlayer.currentScreenHandler.cursorStack.isEmpty) {
                    val emptyBackpackSlot = findFirstEmptyBackpackSlot()

                    if (emptyBackpackSlot != null) {
                        // 空きスロットが見つかった場合、そこにカーソルのアイテムを配置する
                        val emptyNetSlot = toNetworkSlot(indexToSlot(emptyBackpackSlot)!!)

                        // 4クリック目: カーソルのアイテムを空きスロットに配置して操作を完了させる
                        interactionManager?.clickSlot(
                            currentScreenId,
                            emptyNetSlot,
                            0,
                            SlotActionType.PICKUP,
                            currentPlayer,
                        )
                    } else {
                        // 空きスロットがない場合:
                        // 現状ではドロップするしかありません。-999 (画面外)をクリックしてドロップします。
                        interactionManager?.clickSlot(currentScreenId, -999, 0, SlotActionType.PICKUP, currentPlayer)
                    }
                }

                return true
            } catch (_: Exception) {
                return false
            }
        }
    }

    fun drop(index: InventoryIndex) {
        val currentPlayer = player ?: return // nullチェック
        val internal = indexToSlot(index) ?: return
        val stack = get(index)
        if (!stack.isEmpty) {
            if (isCreative) {
                set(index, ItemStack.EMPTY)
            } else {
                // slot 1 はスタック全体のドロップ
                interactionManager?.clickSlot(0, toNetworkSlot(internal), 1, SlotActionType.THROW, currentPlayer)
            }
        }
    }

    fun findFirstEmptyBackpackSlot(): InventoryIndex.Backpack? {
        val playerInv = inventory ?: return null // nullチェック
        // バックパックの内部スロット 9 から 35
        for (i in 0 until 27) {
            if (playerInv.getStack(9 + i).isEmpty) {
                return InventoryIndex.Backpack(i)
            }
        }
        return null
    }

    // -- ユーティリティ関数 --

    /**
     * InventoryIndexをプレイヤーインベントリ内の内部スロットインデックス (0-40) に変換します。
     * ホットバー: 0-8, バックパック: 9-35, 防具: 36-39, オフハンド: 40
     */
    private fun indexToSlot(index: InventoryIndex): Int? =
        when (index) {
            is InventoryIndex.Armor ->
                when (index) {
                    is InventoryIndex.Armor.Head -> 39
                    is InventoryIndex.Armor.Chest -> 38
                    is InventoryIndex.Armor.Legs -> 37
                    is InventoryIndex.Armor.Feet -> 36
                    else -> null
                }

            is InventoryIndex.Hotbar -> index.index
            is InventoryIndex.Backpack -> 9 + index.index
            is InventoryIndex.OffHand -> 40
            is InventoryIndex.MainHand -> inventory?.selectedSlot // nullチェックを追加
        }

    /**
     * 内部スロットインデックスをネットワークパケットで使用されるスロットID (0-45) に変換します。
     * 通常のインベントリ画面 (ID 0) での使用を想定しています。
     */
    private fun toNetworkSlot(internalSlot: Int): Int =
        when (internalSlot) {
            // ホットバー (内部 0-8 -> ネットワーク 36-44)
            in 0..8 -> internalSlot + 36
            // 防具 (内部 36-39 -> ネットワーク 5-8: 逆順)
            in 36..39 -> 44 - internalSlot
            // オフハンド (内部 40 -> ネットワーク 45)
            40 -> 45
            // バックパック (内部 9-35 -> ネットワーク 9-35)
            else -> internalSlot
        }

    /**
     * 指定されたアイテムを持つ最初の内部スロットインデックス (0-40) を検索します。
     */
    private fun findSlotWithItem(item: Item): Int? {
        val playerInv = inventory ?: return null // nullチェック
        for (i in 0 until playerInv.size()) {
            if (playerInv.getStack(i).item == item) {
                return i
            }
        }
        return null
    }

    fun durability(stack: ItemStack): Int {
        if (!stack.isDamageable || stack.isEmpty) {
            return 1
        }
        val maxDurability = stack.maxDamage
        val currentDamage = stack.damage
        return maxDurability - currentDamage
    }

    fun durabilityPercentage(stack: ItemStack): Double {
        if (!stack.isDamageable || stack.isEmpty) {
            return 100.0
        }
        val maxDurability = stack.maxDamage
        // (残り耐久値 / 最大耐久値) * 100
        return (durability(stack) / maxDurability.toDouble())
    }

    /**
     * 指定されたインデックスのアイテムをホットバーの指定されたスロットに補充します。
     * - 両スロットのアイテムが同じであること。
     * - 'to' スロットのアイテムがスタック可能なアイテムであり、かつ最大スタック数に達していないこと。
     * - 'from' のアイテムを 'to' に移動し、カーソルにアイテムが残った場合は 'from' に戻します。
     * @param from 補充元のインベントリインデックス
     * @param to 補充先のホットバーインデックス
     * @return 処理が成功した場合は true、それ以外は false
     */
    fun replenish(
        from: InventoryIndex,
        to: InventoryIndex,
    ): Boolean {
        val currentPlayer = player ?: return false // nullチェック
        val fromStack = get(from)
        val toStack = get(to)

        // 1. アイテムが同じか、かつ 'to' が空でないかを確認
        if (fromStack.isEmpty || toStack.item != fromStack.item) {
            if (!toStack.isEmpty) {
                return false
            }
        }

        // 2. 'to' スロットが最大スタック数に達していないかを確認
        if (toStack.count >= toStack.maxCount) {
            return false
        }

        // 3. 補充操作（スワップの応用）を実行
        val slotFrom = indexToSlot(from) ?: return false
        val slotTo = indexToSlot(to) ?: return false

        if (isCreative) {
            // クリエイティブモードでは、直接スタックを操作する方がシンプルで確実です。
            val amountToTransfer = minOf(fromStack.count, toStack.maxCount - toStack.count)

            if (amountToTransfer > 0) {
                // スタック数を更新
                val newToStack = toStack.copy().apply { count += amountToTransfer }
                val newFromStack = fromStack.copy().apply { count -= amountToTransfer }

                // set関数を使用してパケット送信
                set(to, newToStack)
                set(from, newFromStack)

                return true
            }
            return false
        } else {
            // サバイバルモードの場合、クリック操作で補充（右クリック相当）
            try {
                val netFrom = toNetworkSlot(slotFrom)
                val netTo = toNetworkSlot(slotTo)
                val currentScreenId = currentPlayer.currentScreenHandler.syncId
                // 【補充ロジック（3クリック）】
                // 1. from を左クリック: from のスタックを全てカーソルに移動
                interactionManager?.clickSlot(currentScreenId, netFrom, 0, SlotActionType.PICKUP, currentPlayer)
                // 2. to を左クリック: to のスタックとカーソルのスタックをマージ
                //    - カーソルのアイテムはマージされ、カーソルには残りのアイテムが保持される
                interactionManager?.clickSlot(currentScreenId, netTo, 0, SlotActionType.PICKUP, currentPlayer)
                // 3. from を左クリック: カーソルに残ったアイテムを from スロットに戻す
                //    - これにより、補充操作が完了し、カーソルが空になる
                interactionManager?.clickSlot(currentScreenId, netFrom, 0, SlotActionType.PICKUP, currentPlayer)

                // 4. 【カーソル残留アイテムのクリーンアップ】
                if (!currentPlayer.currentScreenHandler.cursorStack.isEmpty) {
                    // 3クリック後もカーソルにアイテムが残っている場合、空きスロットに戻す
                    // (swap関数と同じロジックを使用)
                    val emptyBackpackSlot = findFirstEmptyBackpackSlot()

                    if (emptyBackpackSlot != null) {
                        // 空きスロットが見つかった場合、そこにカーソルのアイテムを配置する
                        val emptyNetSlot = toNetworkSlot(indexToSlot(emptyBackpackSlot)!!)

                        // 4クリック目: カーソルのアイテムを空きスロットに配置して操作を完了させる
                        interactionManager?.clickSlot(
                            currentScreenId,
                            emptyNetSlot,
                            0,
                            SlotActionType.PICKUP,
                            currentPlayer,
                        )
                    } else {
                        // 空きスロットがない場合: ドロップする
                        interactionManager?.clickSlot(currentScreenId, -999, 0, SlotActionType.PICKUP, currentPlayer)
                    }
                }

                return true
            } catch (_: Exception) {
                return false
            }
        }
    }
}
