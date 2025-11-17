package org.infinite.features.utils.tool

import net.minecraft.block.Block
import net.minecraft.client.MinecraftClient
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.movement.braek.LinearBreak
import org.infinite.features.movement.braek.VeinBreak
import org.infinite.features.rendering.detailinfo.ToolChecker
import org.infinite.features.utils.backpack.BackPackManager
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.libs.client.inventory.InventoryManager.InventoryIndex
import org.infinite.settings.FeatureSetting

/**
 * プレイヤーがブロックを掘り始めた際に、そのブロックに対する最適なツールを自動で手に持ちます。
 * 最低限必要なツールレベル以上のグレードのツールを、最高グレードから優先的に選択します。
 */
class AutoTool : ConfigurableFeature(initialEnabled = false) {
    enum class Method {
        Swap,
        HotBar,
    }

    enum class FineToolStrategy {
        Shears,
        SharpTool,
        Hand,
    }

    private val method =
        FeatureSetting.EnumSetting<Method>(
            "Method",
            Method.HotBar,
            Method.entries,
        )

    private val fineToolStrategy =
        FeatureSetting.EnumSetting<FineToolStrategy>(
            "FineToolStrategy",
            FineToolStrategy.SharpTool,
            FineToolStrategy.entries,
        )

    // ツールを元に戻すまでのディレイ設定
    private val switchDelay =
        FeatureSetting.IntSetting(
            "SwitchDelay",
            5, // デフォルト5ティック (0.25秒)
            0,
            20, // 最大20ティック (1秒)
        )

    override val level: FeatureLevel = FeatureLevel.Extend

    override val settings: List<FeatureSetting<*>> =
        listOf(
            method,
            fineToolStrategy,
            switchDelay, // 設定リストに追加
        )

    private var previousSelectedSlot: Int = -1

    // 修正: 最後に採掘していたティックを記録する変数
    private var lastMiningTick: Long = 0

    private val materialLevels =
        mapOf(
            "netherite" to 5,
            "diamond" to 4,
            "iron" to 3,
            "stone" to 2,
            "golden" to 1,
            "wooden" to 0,
        )

    private fun getToolIdForLevel(
        level: Int,
        kind: ToolChecker.ToolKind,
    ): String? {
        val material =
            when (level) {
                5 -> "netherite"
                4 -> "diamond"
                3 -> "iron"
                2 -> "stone"
                1 -> "golden"
                0 -> "wooden"
                else -> return null
            }
        val toolSuffix =
            when (kind) {
                ToolChecker.ToolKind.PickAxe -> "pickaxe"
                ToolChecker.ToolKind.Axe -> "axe"
                ToolChecker.ToolKind.Shovel -> "shovel"
                ToolChecker.ToolKind.Hoe -> "hoe"
                ToolChecker.ToolKind.Sword -> "sword"
            }
        return "minecraft:${material}_$toolSuffix"
    }

    /**
     * 葉っぱ、蜘蛛の巣など、ハサミや剣が最適なブロックであるかを判定するヘルパー関数。
     */
    private fun isFineToolTarget(block: Block): Boolean {
        val blockId = Registries.BLOCK.getId(block).toString()
        return blockId.contains("leaves") ||
            blockId.contains("cobweb") ||
            blockId.contains("wool")
    }

    /**
     * 指定されたツール種類と最低レベル以上のツールを最高グレードから検索します。
     */
    private fun findBestTool(
        kind: ToolChecker.ToolKind,
        minRequiredLevel: Int,
    ): InventoryIndex? {
        val searchLevels =
            materialLevels.values
                .filter { it >= minRequiredLevel }
                .distinct()
                .sortedDescending()

        for (level in searchLevels) {
            val toolId = getToolIdForLevel(level, kind) ?: continue
            val toolItem = ToolChecker.getItemStackFromId(toolId).item

            if (toolItem == Items.BARRIER || toolItem == Items.AIR) continue

            val foundIndex = InventoryManager.findFirstInMain(toolItem)

            if (foundIndex != null) {
                return foundIndex
            }
        }
        return null
    }

    override fun tick() {
        val client = MinecraftClient.getInstance()
        val currentTime = client.world?.time ?: 0L // 現在のティックを取得

        val linearBreak = InfiniteClient.getFeature(LinearBreak::class.java)
        val veinBreak = InfiniteClient.getFeature(VeinBreak::class.java)
        val isLinearBreakWorking = linearBreak?.isWorking ?: false
        val isVeinBreakWorking = veinBreak?.isWorking ?: false
        val isInteractingToBlock = interactionManager?.isBreakingBlock ?: false
        val blockPos =
            when {
                isLinearBreakWorking -> linearBreak.currentBreakingPos
                isVeinBreakWorking -> veinBreak.currentBreakingPos
                else -> interactionManager?.currentBreakingPos
            }

        val isMining = isInteractingToBlock || isLinearBreakWorking || isVeinBreakWorking

        // 修正: 採掘中でない場合のツールリセットロジックを修正
        if (!isMining || blockPos == null) {
            // 採掘中でない場合

            // lastMiningTickからswitchDelay分の時間が経過しているかチェック
            if (currentTime - lastMiningTick >= switchDelay.value) {
                resetTool()
            }
            // 採掘中でないが、ディレイ期間中の場合は何もしない（ツール切り替えを維持）
            return
        }

        // 採掘中の場合、lastMiningTickを更新
        lastMiningTick = currentTime

        // --- ツール選択ロジック（以下、変更なし） ---
        val blockState = world?.getBlockState(blockPos) ?: return
        val block = blockState.block
        val correctToolInfo = ToolChecker.getCorrectTool(block)
        val toolKind = correctToolInfo.toolKind
        val requiredToolLevel = correctToolInfo.toolLevel
        var bestToolIndex: InventoryIndex?
        var bestToolItem = Items.AIR

        // ----------------------------------------------------
        // 1. 特殊ブロック処理: 最低レベルが0以下 かつ 特殊ツールの対象ブロックの場合のみ
        // ----------------------------------------------------
        val isSpecialBlock = requiredToolLevel <= 0 && isFineToolTarget(block)

        if (isSpecialBlock) {
            when (fineToolStrategy.value) {
                FineToolStrategy.Shears -> {
                    bestToolIndex = InventoryManager.findFirstInMain(Items.SHEARS)
                    if (bestToolIndex != null) {
                        bestToolItem = Items.SHEARS
                    }
                }

                FineToolStrategy.SharpTool -> {
                    var bestSharpToolIndex: InventoryIndex? = null
                    var highestLevel = -1

                    for (kind in listOf(ToolChecker.ToolKind.Sword, ToolChecker.ToolKind.Hoe)) {
                        val foundIndex = findBestTool(kind, 1)
                        if (foundIndex != null) {
                            val toolItem = InventoryManager.get(foundIndex).item
                            val toolId = toolItem.toString()

                            val currentLevel = materialLevels.entries.find { toolId.contains(it.key) }?.value ?: 0

                            if (currentLevel > highestLevel) {
                                highestLevel = currentLevel
                                bestSharpToolIndex = foundIndex
                            }
                        }
                    }

                    bestToolIndex = bestSharpToolIndex
                    if (bestToolIndex != null) {
                        bestToolItem = InventoryManager.get(bestToolIndex).item
                    }
                }

                FineToolStrategy.Hand -> {
                    // resetTool()を呼ばない。採掘は継続されている
                    return
                }
            }

            // 特殊ツールが見つかった場合
            if (bestToolIndex != null) {
                val currentlyHeldItem = InventoryManager.get(InventoryIndex.MainHand())

                if (currentlyHeldItem.item == bestToolItem) {
                    return
                }

                handleToolSwitch(bestToolIndex)
                return // 特殊ツールの切り替えが完了
            }

            // 特殊ブロックだが、特殊ツールがインベントリにない場合
            // resetTool()を呼ばない。採掘は継続されている
            return
        }

        // ----------------------------------------------------
        // 2. メインのツール検索処理 (グレード付きのツール)
        // ----------------------------------------------------

        if (requiredToolLevel >= 0) { // レベル0も含め、ツール検索を行う
            if (toolKind == null) {
                // resetTool()を呼ばない。採掘は継続されている
                return
            }

            bestToolIndex = findBestTool(toolKind, requiredToolLevel)

            if (bestToolIndex != null) {
                bestToolItem = InventoryManager.get(bestToolIndex).item

                val currentlyHeldItem = InventoryManager.get(InventoryIndex.MainHand())

                if (currentlyHeldItem.item == bestToolItem) {
                    return
                }

                handleToolSwitch(bestToolIndex)
            } else {
                // 最適なツールが見つからない
                // resetTool()を呼ばない。採掘は継続されている
            }
        } else {
            // Special Blockではないが、requiredToolLevelが負の値のブロック
            // resetTool()を呼ばない。採掘は継続されている
        }
    }

    /**
     * 実際にツールを切り替えるロジックを分離。
     */
    private fun handleToolSwitch(bestToolIndex: InventoryIndex) {
        val player = player ?: return
        val currentSlot = player.inventory.selectedSlot
        val backPackManager = InfiniteClient.getFeature(BackPackManager::class.java)

        // ツール切り替え前のスロットを保存（まだ保存されていなければ）
        if (previousSelectedSlot == -1) {
            previousSelectedSlot = currentSlot
        }

        backPackManager?.register {
            when (method.value) {
                Method.Swap -> {
                    InventoryManager.swap(InventoryIndex.Hotbar(currentSlot), bestToolIndex)
                }

                Method.HotBar -> {
                    if (bestToolIndex is InventoryIndex.Hotbar) {
                        player.inventory.selectedSlot = bestToolIndex.index
                        InfiniteClient.getFeature(LinearBreak::class.java)?.autoToolCallBack = bestToolIndex.index
                        InfiniteClient.getFeature(VeinBreak::class.java)?.autoToolCallBack = bestToolIndex.index
                    }
                }
            }
        }

        // ツールを切り替えたので、lastMiningTickを更新（この行は不要だが、意図を尊重しここでは残します）
        // lastSwitchTickは削除したので、不要な更新は行いません
    }

    /**
     * ツールを元のホットバーのスロットに戻す。
     */
    private fun resetTool() {
        val player = player ?: return
        val backPackManager = InfiniteClient.getFeature(BackPackManager::class.java)

        if (previousSelectedSlot != -1) {
            val currentSlot = player.inventory.selectedSlot
            val originalIndex = InventoryIndex.Hotbar(previousSelectedSlot)
            val currentIndex = InventoryIndex.Hotbar(currentSlot)

            backPackManager?.register {
                when (method.value) {
                    Method.Swap -> {
                        try {
                            // 現在手に持っているアイテム（ツール）と、元のスロットにあるアイテムをスワップ
                            InventoryManager.swap(currentIndex, originalIndex)
                            previousSelectedSlot = -1 // リセット完了
                        } catch (e: Exception) {
                            // スワップ失敗時
                            InfiniteClient.error("[AutoTool] Error: $e")
                            previousSelectedSlot = -1
                        }
                    }

                    Method.HotBar -> {
                        // ホットバーモードの場合、元のホットバーのスロットを選択
                        player.inventory.selectedSlot = previousSelectedSlot
                        previousSelectedSlot = -1 // リセット完了
                    }
                }
            }
        }
    }
}
