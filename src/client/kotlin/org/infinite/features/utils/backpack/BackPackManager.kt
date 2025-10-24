package org.infinite.features.utils.backpack

import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.InfiniteClient
import org.infinite.libs.client.player.inventory.InventoryManager
import org.infinite.settings.FeatureSetting

class BackPackManager : ConfigurableFeature() {
    override val level: FeatureLevel = FeatureLevel.EXTEND

    private val sortEnabled =
        FeatureSetting.BooleanSetting(
            "SortEnabled",
            "feature.utils.backpackmanager.sort_enabled.description",
            true,
        )

    // autoMoveToBackpackEnabledの動作を「空きホットバーへの自動移動」に変更します
    private val autoMoveToBackpackEnabled =
        FeatureSetting.BooleanSetting(
            "AutoMoveToBackpack",
            "feature.utils.backpackmanager.auto_move_to_backpack_enabled.description",
            true,
        )
    private val autoReplenishHotbarEnabled =
        FeatureSetting.BooleanSetting(
            "AutoReplenishHotbar",
            "feature.utils.backpackmanager.auto_replenish_hotbar_enabled.description",
            true,
        )
    private val sortInterval =
        FeatureSetting.IntSetting(
            "SortInterval",
            "feature.utils.backpackmanager.sort_interval.description",
            20 * 5,
            20,
            20 * 60,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            sortEnabled,
            autoMoveToBackpackEnabled,
            autoReplenishHotbarEnabled,
            sortInterval,
        )

    private var lastSortTick = 0L
    private var previousInventory: List<ItemStack> = emptyList()
    private var isInventoryOpen = false // インベントリが開いているかどうかを追跡するフラグ

    // 起動時/リセット時に空だったホットバーのスロットインデックス (0-8) を保持
    private var emptyHotbarSlots: Set<Int> = emptySet()

    /**
     * 現在のホットバーの状態に基づいて `emptyHotbarSlots` を初期化/更新します。
     * Hotbar: 0-8
     */
    private fun registerEmptyHotbarSlots() {
        val player = MinecraftClient.getInstance().player
        if (player != null) {
            // ホットバー (0-8) の空きスロットインデックスを収集
            emptyHotbarSlots =
                (0 until 9)
                    .filter { player.inventory.getStack(it).isEmpty }
                    .toSet()
        }
    }

    override fun start() {
        // 【修正点】: 起動時に空きホットバーを登録
        registerEmptyHotbarSlots()
    }

    override fun tick() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val manager = InfiniteClient.playerInterface.inventory

        val currentScreen = client.currentScreen

        // インベントリが閉じられた瞬間を検出
        if (isInventoryOpen && currentScreen == null) {
            // インベントリが閉じられたので、previousInventoryを更新し、空きホットバーを再登録
            previousInventory = (0 until player.inventory.size()).map { player.inventory.getStack(it).copy() }
            registerEmptyHotbarSlots() // 【修正点】: インベントリが閉じられたら、ホットバーの情報を登録し直し
        }
        isInventoryOpen = (currentScreen != null)

        // インベントリが開いている間は処理を停止
        if (currentScreen != null) {
            return
        }
        // --- 1. 定期ソート機能 ---
        // バックパックのアイテムを左上から順番にソートします。
        if (sortEnabled.value && (client.world?.time?.minus(lastSortTick) ?: 0) >= sortInterval.value) {
            manager.sort()
            lastSortTick = client.world?.time ?: 0
            previousInventory =
                (0 until player.inventory.size()).map { player.inventory.getStack(it).copy() }
            return // 1回のtickで1つの操作のみ
        }

        // --- 2. 空であるはずの場所への自動移動機能 (autoMoveToBackpackEnabledの新しい動作) ---
        // 【修正点】: 毎ティックホットバーを調べて、空であるはずの場所にアイテムがあったらバックパックに移動
        if (autoMoveToBackpackEnabled.value && emptyHotbarSlots.isNotEmpty()) {
            // ホットバーのスロット (0-8) をチェック
            for (i in 0 until 9) {
                val currentStack = player.inventory.getStack(i)

                // 本来空であるべきスロットに、アイテムが入っている場合
                if (i in emptyHotbarSlots && !currentStack.isEmpty) {
                    val emptyBackpackSlot = manager.findFirstEmptyBackpackSlot()

                    if (emptyBackpackSlot != null) {
                        // ホットバーのスロット (i) のアイテムをバックパックの空きスロットに移動 (スワップ)
                        manager.swap(emptyBackpackSlot, InventoryManager.Hotbar(i))

                        // インベントリ状態を更新
                        previousInventory =
                            (0 until player.inventory.size()).map { player.inventory.getStack(it).copy() }
                        return // 1回のtickで1つの移動のみ
                    }
                }
            }
        }
        // --- 3. ホットバーのアイテム補充機能 (既存) ---
        if (autoReplenishHotbarEnabled.value) {
            val currentInventory = (0 until player.inventory.size()).map { player.inventory.getStack(it).copy() }

            if (previousInventory.isNotEmpty()) {
                for (i in 0 until 9) { // ホットバーのスロット
                    val currentStack = currentInventory.getOrNull(i) ?: ItemStack.EMPTY
                    val previousStack = previousInventory.getOrNull(i) ?: ItemStack.EMPTY
                    if ((currentStack.item == previousStack.item && currentStack.count < previousStack.count) ||
                        (currentStack.isEmpty && !previousStack.isEmpty)
                    ) {
                        val itemToReplenish = previousStack.item
                        // ホットバー以外の場所から同じアイテムを探す
                        val foundIndex = manager.findFirstFromBackPack(itemToReplenish)
                        if (foundIndex != null) {
                            // ホットバーのスロットと見つかったアイテムのスロットをスワップ
                            manager.replenish(foundIndex, InventoryManager.Hotbar(i))
                            previousInventory =
                                (0 until player.inventory.size()).map { player.inventory.getStack(it).copy() }
                            return // 1回のtickで1つの補充のみ
                        }
                    }
                }
            }
            previousInventory = currentInventory // ホットバー補充後に更新
        }
        // 何も操作が行われなかった場合、次のティックのために状態を更新
        previousInventory = (0 until player.inventory.size()).map { player.inventory.getStack(it).copy() }
    }

    override fun enabled() {
        // Featureが有効になったときにインベントリの状態を初期化
        val player = MinecraftClient.getInstance().player
        previousInventory =
            (0 until (player?.inventory?.size() ?: 0)).map {
                player?.inventory?.getStack(it)?.copy() ?: ItemStack.EMPTY
            }
        isInventoryOpen = (MinecraftClient.getInstance().currentScreen != null)
        registerEmptyHotbarSlots() // 有効化時にも初期化
    }
}
