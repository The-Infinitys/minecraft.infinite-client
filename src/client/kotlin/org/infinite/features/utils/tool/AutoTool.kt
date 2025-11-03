package org.infinite.features.utils.tool

import net.minecraft.block.Block
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.movement.braek.LinearBreak
import org.infinite.features.movement.braek.VeinBreak
import org.infinite.features.rendering.detailinfo.ToolChecker
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
            "feature.utils.autotool.method.description",
            Method.HotBar,
            Method.entries,
        )

    private val fineToolStrategy =
        FeatureSetting.EnumSetting<FineToolStrategy>(
            "FineToolStrategy",
            "feature.utils.autotool.fine_tool_strategy.description",
            FineToolStrategy.SharpTool,
            FineToolStrategy.entries,
        )

    override val level: FeatureLevel = FeatureLevel.Extend

    override val settings: List<FeatureSetting<*>> =
        listOf(
            method,
            fineToolStrategy,
        )

    private var previousSelectedSlot: Int = -1

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
        if (!isMining || blockPos == null) {
            resetTool()
            return
        }
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
                    resetTool()
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
            resetTool()
            return
        }

        // ----------------------------------------------------
        // 2. メインのツール検索処理 (グレード付きのツール)
        //    requiredToolLevel >= 0 に緩和し、レベル0のツルハシブロックも対象とする
        // ----------------------------------------------------

        if (requiredToolLevel >= 0) { // レベル0も含め、ツール検索を行う
            if (toolKind == null) {
                resetTool()
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
                resetTool()
            }
        } else {
            // Special Blockではないが、requiredToolLevelが負の値のブロック（理論上はありえないが安全のため）
            resetTool()
        }
    }

    /**
     * 実際にツールを切り替えるロジックを分離。
     */
    private fun handleToolSwitch(bestToolIndex: InventoryIndex) {
        val player = player ?: return
        val currentSlot = player.inventory.selectedSlot

        // ツール切り替え前のスロットを保存（まだ保存されていなければ）
        if (previousSelectedSlot == -1) {
            previousSelectedSlot = currentSlot
        }

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

    /**
     * ツールを元のホットバーのスロットに戻す。
     */
    private fun resetTool() {
        val player = player ?: return

        if (previousSelectedSlot != -1) {
            val currentSlot = player.inventory.selectedSlot
            val originalIndex = InventoryIndex.Hotbar(previousSelectedSlot)
            val currentIndex = InventoryIndex.Hotbar(currentSlot)

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
