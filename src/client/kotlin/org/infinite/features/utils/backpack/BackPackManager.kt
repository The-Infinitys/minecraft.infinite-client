package org.infinite.features.utils.backpack

import net.minecraft.client.MinecraftClient
import net.minecraft.item.ItemStack
import org.infinite.ConfigurableFeature
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.libs.client.inventory.InventoryManager.InventoryIndex
import org.infinite.settings.FeatureSetting

class BackPackManager : ConfigurableFeature() {
    override val level: FeatureLevel = FeatureLevel.Extend

    private val sortEnabled =
        FeatureSetting.BooleanSetting(
            "SortEnabled",
            true,
        )

    // autoMoveToBackpackEnabledの動作を「空きホットバーへの自動移動」に変更します
    private val autoMoveToBackpackEnabled =
        FeatureSetting.BooleanSetting(
            "AutoMoveToBackpack",
            true,
        )
    private val autoReplenishHotbarEnabled =
        FeatureSetting.BooleanSetting(
            "AutoReplenishHotbar",
            true,
        )
    private val sortInterval =
        FeatureSetting.IntSetting(
            "SortInterval",
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

    fun updateSlotsInfo(): List<ItemStack> {
        val player = player ?: return emptyList()
        previousInventory = (0 until player.inventory.size()).map { player.inventory.getStack(it).copy() }
        emptyHotbarSlots =
            (0 until 9)
                .filter { player.inventory.getStack(it).isEmpty }
                .toSet()
        return previousInventory
    }

    // 起動時/リセット時に空だったホットバーのスロットインデックス (0-8) を保持
    private var emptyHotbarSlots: Set<Int> = emptySet()

    fun register(registeredProcess: () -> Unit) {
        registeredProcess()
        updateSlotsInfo()
    }

    override fun onStart() {
        // 【修正点】: 起動時に空きホットバーを登録
        updateSlotsInfo()
    }

    private fun process() {
        val player = player ?: return
        val world = world ?: return
        // --- 1. 定期ソート機能 ---
        if (sortEnabled.value && world.time.minus(lastSortTick) >= sortInterval.value) {
            InventoryManager.sort()
            lastSortTick = world.time
            updateSlotsInfo()
        }

        // --- 2. 空であるはずの場所への自動移動機能 (autoMoveToBackpackEnabledの新しい動作) ---
        if (autoMoveToBackpackEnabled.value && emptyHotbarSlots.isNotEmpty()) {
            // ホットバーのスロット (0-8) をチェック
            for (i in 0 until 9) {
                val currentStack = player.inventory.getStack(i)

                // 本来空であるべきスロットに、アイテムが入っている場合
                if (i in emptyHotbarSlots && !currentStack.isEmpty) {
                    val emptyBackpackSlot = InventoryManager.findFirstEmptyBackpackSlot()

                    if (emptyBackpackSlot != null) {
                        // ホットバーのスロット (i) のアイテムをバックパックの空きスロットに移動 (スワップ)
                        InventoryManager.swap(emptyBackpackSlot, InventoryIndex.Hotbar(i))

                        // インベントリ状態を更新
                        updateSlotsInfo()
                    }
                }
            }
        }
        // --- 3. ホットバーのアイテム補充機能 (既存) ---
        if (autoReplenishHotbarEnabled.value) {
            val currentInventory = updateSlotsInfo()

            if (previousInventory.isNotEmpty()) {
                for (i in 0 until 9) { // ホットバーのスロット
                    val currentStack = currentInventory.getOrNull(i) ?: ItemStack.EMPTY
                    val previousStack = previousInventory.getOrNull(i) ?: ItemStack.EMPTY
                    if ((currentStack.item == previousStack.item && currentStack.count < previousStack.count) ||
                        (currentStack.isEmpty && !previousStack.isEmpty)
                    ) {
                        val itemToReplenish = previousStack.item
                        // ホットバー以外の場所から同じアイテムを探す
                        val foundIndex = InventoryManager.findFirstFromBackPack(itemToReplenish)
                        if (foundIndex != null) {
                            // ホットバーのスロットと見つかったアイテムのスロットをスワップ
                            InventoryManager.replenish(foundIndex, InventoryIndex.Hotbar(i))
                            updateSlotsInfo()
                        }
                    }
                }
            }
            previousInventory = currentInventory // ホットバー補充後に更新
        }
        // 何も操作が行われなかった場合、次のティックのために状態を更新
        updateSlotsInfo()
    }

    override fun onTick() {
        val currentScreen = client.currentScreen
        if (isInventoryOpen && currentScreen == null) {
            updateSlotsInfo()
            return
        }
        isInventoryOpen = (currentScreen != null)
        if (isInventoryOpen) {
            return
        }

        process()
    }

    override fun onEnabled() {
        // Featureが有効になったときにインベントリの状態を初期化
        val player = MinecraftClient.getInstance().player
        previousInventory =
            (0 until (player?.inventory?.size() ?: 0)).map {
                player?.inventory?.getStack(it)?.copy() ?: ItemStack.EMPTY
            }
        isInventoryOpen = (MinecraftClient.getInstance().currentScreen != null)
        updateSlotsInfo() // 有効化時にも初期化
    }
}
