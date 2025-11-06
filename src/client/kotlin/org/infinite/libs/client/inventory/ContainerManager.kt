package org.infinite.libs.client.inventory

import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.ScreenHandlerType
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Identifier
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.infinite.libs.client.player.ClientInterface

// -------------------------------------------------------------------------
// ユーティリティクラス（ContainerManagerで使用）
// -------------------------------------------------------------------------

/**
 * プレイヤーインベントリのスロット構造を定義。
 * ContainerManagerで開いているコンテナのスロットインデックスを計算するのに使用。
 * @param containerSize 開いているコンテナのスロット数
 */
class ContainerIndexHelper(
    private val containerSize: Int,
) {
    private val playerInventoryStart: Int = containerSize

    // ホットバーの開始グローバルインデックス
    private val hotbarStart: Int = containerSize + 27

    /**
     * コンテナ内の特定スロットのグローバルインデックスを返します。
     * @param index コンテナ内のローカルインデックス (0から始まる)
     */
    fun container(index: Int): Int {
        require(index in 0 until containerSize) { "Container index must be between 0 and ${containerSize - 1}" }
        return index
    }

    /**
     * プレイヤーインベントリ内の通常スロット（バックパック）のグローバルインデックスを返します。
     * @param index バックパックのローカルインデックス (0〜26)
     */
    fun backpack(index: Int): Int {
        require(index in 0..26) { "Backpack index must be between 0 and 26" }
        return playerInventoryStart + index
    }

    /**
     * ホットバー内の特定スロットのグローバルインデックスを返します。
     * @param index ホットバーのローカルインデックス (0〜8)
     */
    fun hotbar(index: Int): Int {
        require(index in 0..8) { "Hotbar index must be between 0 and 8" }
        return hotbarStart + index
    }

    /**
     * 全てのプレイヤーインベントリ（バックパックとホットバー）のグローバルインデックス範囲 (containerSize から containerSize + 35)
     */
    val allPlayerSlots: IntRange
        get() = playerInventoryStart..playerInventoryStart + 35
}

// -------------------------------------------------------------------------
// ContainerManager オブジェクトの定義
// -------------------------------------------------------------------------

/**
 * クライアントサイドでのインベントリおよびコンテナ操作を管理するユーティリティ。
 */
object ContainerManager : ClientInterface() {
    // --- ContainerType定義 ---
    data class ContainerType(
        val id: Identifier,
        val handlerType: ScreenHandlerType<*>,
        val containerSize: Int,
        val indexHelper: ContainerIndexHelper,
    ) {
        override fun toString(): String = id.toString()
    }

    // --- 動的なマッピング ---
    private val ALL_CONTAINER_TYPES: Map<ScreenHandlerType<*>, ContainerType> =
        Registries.SCREEN_HANDLER.entrySet.associate { entry ->
            val id = entry.key.value
            val type = entry.value

            val size =
                when (type) {
                    ScreenHandlerType.GENERIC_9X1 -> 9
                    ScreenHandlerType.GENERIC_9X2 -> 18
                    ScreenHandlerType.GENERIC_9X3 -> 27
                    ScreenHandlerType.GENERIC_9X4 -> 36
                    ScreenHandlerType.GENERIC_9X5 -> 45
                    ScreenHandlerType.GENERIC_9X6 -> 54
                    ScreenHandlerType.GENERIC_3X3 -> 9
                    ScreenHandlerType.CRAFTING -> 10
                    ScreenHandlerType.FURNACE, ScreenHandlerType.BLAST_FURNACE, ScreenHandlerType.SMOKER -> 3
                    ScreenHandlerType.HOPPER -> 5
                    ScreenHandlerType.SHULKER_BOX -> 27
                    else -> 0
                }

            val indexHelper = ContainerIndexHelper(size)
            type to ContainerType(id, type, size, indexHelper)
        }

    val screen: Screen?
        get() = client.currentScreen
    val handler: ScreenHandler?
        get() = player?.currentScreenHandler

    /**
     * 指定された位置のコンテナを開く操作をサーバーに送信します。
     */
    fun openContainer(pos: BlockPos): Boolean {
        val currentPlayer = player ?: return false
        val interactionManager = interactionManager ?: return false
        world ?: return false

        val hitResult =
            BlockHitResult(
                pos.toCenterPos(),
                Direction.UP,
                pos,
                false,
            )

        interactionManager.interactBlock(
            currentPlayer,
            currentPlayer.activeHand,
            hitResult,
        )

        return true
    }

    /**
     * 現在開いているコンテナを閉じます
     */
    fun closeContainer() {
        player?.closeHandledScreen()
    }

    /**
     * 現在開いているコンテナの型とインデックスヘルパーを返します。
     */
    fun containerType(): ContainerType? {
        val currentHandler = handler ?: return null
        val screen = client.currentScreen ?: return null
        if (screen is InventoryScreen) return null
        return ALL_CONTAINER_TYPES[currentHandler.type]
    }

    /**
     * 開いているコンテナまたはプレイヤーインベントリ内のスロット間でアイテムを交換します。
     */
    fun swap(
        from: Int,
        to: Int,
    ) {
        val currentHandler = handler ?: return
        val interactionManager = interactionManager ?: return

        interactionManager.clickSlot(currentHandler.syncId, from, 0, SlotActionType.PICKUP, player)
        interactionManager.clickSlot(currentHandler.syncId, to, 0, SlotActionType.PICKUP, player)
        interactionManager.clickSlot(currentHandler.syncId, from, 0, SlotActionType.PICKUP, player)
    }

    /**
     * 指定されたスロット、またはカーソルのアイテムをドロップします。
     */
    fun drop(index: Int? = null) {
        val currentHandler = handler ?: return
        val interactionManager = interactionManager ?: return
        val button = 0

        if (index == null) {
            val cursorSlot = -999
            interactionManager.clickSlot(currentHandler.syncId, cursorSlot, button, SlotActionType.PICKUP, player)
        } else {
            interactionManager.clickSlot(currentHandler.syncId, index, button, SlotActionType.THROW, player)
        }
    }

    /**
     * 現在開いているコンテナ内で、最初に見つかった空のスロットのグローバルインデックスを返します。
     */
    fun firstEmptySlotId(): Int? {
        val currentHandler = handler ?: return null
        val size = containerType()?.containerSize ?: return null
        for (i in 0 until size) {
            val stack = currentHandler.getSlot(i).stack
            if (stack.isEmpty) {
                return i
            }
        }
        return null
    }

    /**
     * 指定されたグローバルインデックスのアイテムスタックを取得します。
     */
    fun get(index: Int): ItemStack? {
        val currentHandler = handler ?: return null
        val slots = currentHandler.slots
        return if (index in 0 until slots.size) {
            slots[index].stack
        } else {
            null
        }
    }

    /**
     * 現在、カーソル（マウス）が持っているアイテムスタックを取得します。
     */
    fun currentItem(): ItemStack = player?.currentScreenHandler?.cursorStack ?: ItemStack.EMPTY
}
