package org.infinite.libs.client.player.inventory

import net.minecraft.client.MinecraftClient
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket
import net.minecraft.screen.slot.SlotActionType

class InventoryManager(
    private val client: MinecraftClient,
) {
    private val interactionManager
        get() = client.interactionManager ?: throw IllegalStateException("InteractionManager is not available")
    private val player
        get() = client.player ?: throw IllegalStateException("Player is not available")
    private val inv
        get() = player.inventory ?: throw IllegalStateException("Player inventory is not available")
    private val isCreative
        get() = player.isCreative

    sealed interface InventoryIndex

    enum class Armor : InventoryIndex {
        HEAD,
        CHEST,
        LEGS,
        FEET,
    }

    data class Hotbar(
        val index: Int,
    ) : InventoryIndex {
        init {
            require(index in 0..8) { "Hotbar index must be between 0 and 8" }
        }
    }

    data class Backpack(
        val index: Int,
    ) : InventoryIndex {
        init {
            require(index in 0..26) { "Backpack index must be between 0 and 26" }
        }
    }

    enum class Other : InventoryIndex {
        OFF_HAND,
        MAIN_HAND,
    }

    fun get(index: InventoryIndex): ItemStack =
        when (index) {
            is Armor ->
                inv.getStack(
                    when (index) {
                        Armor.HEAD -> 39
                        Armor.CHEST -> 38
                        Armor.LEGS -> 37
                        Armor.FEET -> 36
                    },
                ) ?: ItemStack.EMPTY

            is Hotbar -> inv.getStack(index.index) ?: ItemStack.EMPTY
            is Backpack -> inv.getStack(9 + index.index) ?: ItemStack.EMPTY
            is Other ->
                when (index) {
                    Other.OFF_HAND -> inv.getStack(40) ?: ItemStack.EMPTY
                    Other.MAIN_HAND -> inv.getStack(inv.selectedSlot) ?: ItemStack.EMPTY
                }
        }

    fun set(
        index: InventoryIndex,
        stack: ItemStack,
    ): Boolean {
        val internal = indexToSlot(index) ?: return false
        if (isCreative) {
            inv.setStack(internal, stack)
            val packet = CreativeInventoryActionC2SPacket(toNetworkSlot(internal), stack)
            client.networkHandler?.sendPacket(packet)
            return true
        } else {
            try {
                // サバイバルモードでの set は複雑なため、アイテムを探してスワップするロジックを使用
                if (stack.isEmpty) {
                    // 空にする場合はドロップ（THROUGH）操作をシミュレート
                    interactionManager.clickSlot(0, toNetworkSlot(internal), 1, SlotActionType.THROW, player)
                    return true
                }

                val sourceInternal = findSlotWithItem(stack.item) ?: return false
                if (sourceInternal == internal) return true

                // set操作は、基本的に元のアイテムをどこかの空きスロットに移動させながら、
                // 指定されたアイテムを配置するスワップの組み合わせで実現される
                // ここでは簡略化のため、元のアイテムを空きスロットに移動させ、
                // 次に sourceInternal から targetNet へ移動させる3クリックswapを使用
                val targetNet = toNetworkSlot(internal)

                // ターゲットスロットが空でない場合、元のアイテムを空きスロットに退避させる必要があるが、
                // それを自動で行う信頼性の高いAPIがないため、元の set ロジックを維持

                val sourceNet = toNetworkSlot(sourceInternal)
                interactionManager.clickSlot(0, sourceNet, 0, SlotActionType.PICKUP, player)
                interactionManager.clickSlot(0, targetNet, 0, SlotActionType.PICKUP, player)
                interactionManager.clickSlot(0, sourceNet, 0, SlotActionType.PICKUP, player)
                return true
            } catch (_: Exception) {
                return false
            }
        }
    }

    fun count(item: Item): Int {
        var total = 0
        for (i in 0 until inv.size()) {
            val stack = inv.getStack(i)
            if (stack.item == item) {
                total += stack.count
            }
        }
        return total
    }

    fun sort() {
        if (isCreative) {
            val stacks = mutableListOf<ItemStack>()
            // バックパックのスロット番号は 9 から 35 (27スロット)
            for (i in 9..35) {
                stacks.add(inv.getStack(i).copy())
                inv.setStack(i, ItemStack.EMPTY)
            }
            stacks.sortBy { Item.getRawId(it.item) } // アイテムIDでソート

            // クリエイティブインベントリパケットを送信
            for (i in 0..26) {
                val internalSlot = 9 + i
                val stack = stacks.getOrElse(i) { ItemStack.EMPTY }
                inv.setStack(internalSlot, stack)
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
                    val idI = Item.getRawId(inv.getStack(idxI).item)
                    val idJ = Item.getRawId(inv.getStack(idxJ).item)
                    if (idJ < idI) minIdx = j
                }
                if (minIdx != i) {
                    // バックパック内のスワップ
                    swap(Backpack(i), Backpack(minIdx))
                }
            }
        }
    }

    fun findFirst(item: Item): InventoryIndex? {
        // ホットバー (0-8)
        for (i in 0 until 9) {
            if (inv.getStack(i).item == item) {
                return Hotbar(i)
            }
        }
        // バックパック (9-35, Backpack index 0-26)
        for (i in 0 until 27) {
            if (inv.getStack(9 + i).item == item) {
                return Backpack(i)
            }
        }
        // 防具 (36-39)
        for (i in 36 until 40) {
            if (inv.getStack(i).item == item) {
                return when (i) {
                    36 -> Armor.FEET
                    37 -> Armor.LEGS
                    38 -> Armor.CHEST
                    39 -> Armor.HEAD
                    else -> null
                }
            }
        }
        // オフハンド (40)
        if (inv.getStack(40).item == item) {
            return Other.OFF_HAND
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
                val currentScreenId = player.currentScreenHandler.syncId

                // 3クリック スワップシーケンス
                interactionManager.clickSlot(currentScreenId, netA, 0, SlotActionType.PICKUP, player)
                interactionManager.clickSlot(currentScreenId, netB, 0, SlotActionType.PICKUP, player)
                interactionManager.clickSlot(currentScreenId, netA, 0, SlotActionType.PICKUP, player)

                // 【修正ロジック】: 3クリック後もカーソルにアイテムが残っている場合、空きスロットに戻す
                if (!player.currentScreenHandler.cursorStack.isEmpty) {
                    val emptyBackpackSlot = findFirstEmptyBackpackSlot()

                    if (emptyBackpackSlot != null) {
                        // 空きスロットが見つかった場合、そこにカーソルのアイテムを配置する
                        val emptyNetSlot = toNetworkSlot(indexToSlot(emptyBackpackSlot)!!)

                        // 4クリック目: カーソルのアイテムを空きスロットに配置して操作を完了させる
                        interactionManager.clickSlot(currentScreenId, emptyNetSlot, 0, SlotActionType.PICKUP, player)
                    } else {
                        // 空きスロットがない場合:
                        // 現状ではドロップするしかありません。-999 (画面外)をクリックしてドロップします。
                        interactionManager.clickSlot(currentScreenId, -999, 0, SlotActionType.PICKUP, player)
                    }
                }

                return true
            } catch (_: Exception) {
                return false
            }
        }
    }

    fun drop(index: InventoryIndex) {
        val internal = indexToSlot(index) ?: return
        val stack = get(index)
        if (!stack.isEmpty) {
            if (isCreative) {
                set(index, ItemStack.EMPTY)
            } else {
                // slot 1 はスタック全体のドロップ
                interactionManager.clickSlot(0, toNetworkSlot(internal), 1, SlotActionType.THROW, player)
            }
        }
    }

    fun findFirstEmptyBackpackSlot(): Backpack? {
        // バックパックの内部スロット 9 から 35
        for (i in 0 until 27) {
            if (inv.getStack(9 + i).isEmpty) {
                return Backpack(i)
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
            is Armor ->
                when (index) {
                    Armor.HEAD -> 39
                    Armor.CHEST -> 38
                    Armor.LEGS -> 37
                    Armor.FEET -> 36
                }
            is Hotbar -> index.index
            is Backpack -> 9 + index.index
            is Other ->
                when (index) {
                    Other.OFF_HAND -> 40
                    Other.MAIN_HAND -> inv.selectedSlot
                }
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
        for (i in 0 until inv.size()) {
            if (inv.getStack(i).item == item) {
                return i
            }
        }
        return null
    }
}
