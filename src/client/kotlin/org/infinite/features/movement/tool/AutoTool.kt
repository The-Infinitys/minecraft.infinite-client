package org.infinite.features.movement.tool

import net.minecraft.client.MinecraftClient
import net.minecraft.item.Items
import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.InfiniteClient
import org.infinite.features.rendering.detailinfo.ToolChecker
import org.infinite.libs.client.player.inventory.InventoryManager
import org.infinite.settings.FeatureSetting

/**
 * プレイヤーがブロックを掘り始めた際に、そのブロックに対する最適なツールを自動で手に持ちます。
 * 最低限必要なツールレベル以上のグレードのツールを、最高グレードから優先的に選択します。
 */
class AutoTool : ConfigurableFeature(initialEnabled = false) {
    // AutoToolは、プレイヤーのインベントリを操作し、サーバーに特定の動作を行わせるため、EXTENDレベルに設定します。
    override val level: FeatureLevel = FeatureLevel.EXTEND

    override val settings: List<FeatureSetting<*>> = emptyList()

    private var previousSelectedSlot: Int = -1

    // ツールのグレードとレベルの対応マップ。レベル順にソートして検索するために使用します。
    private val materialLevels =
        mapOf(
            "netherite" to 5,
            "diamond" to 4,
            "iron" to 3,
            "stone" to 2,
            "wooden" to 0,
            "golden" to 1,
        )

    /**
     * 指定されたレベルと種類に対応する Minecraft のツールID (例: "minecraft:diamond_pickaxe") を生成します。
     */
    private fun getToolIdForLevel(
        level: Int,
        kind: ToolChecker.ToolKind,
    ): String? {
        // レベルに対応するマテリアル文字列を決定
        val material =
            when (level) {
                4 -> "netherite"
                3 -> "diamond"
                2 -> "iron"
                1 -> "stone"
                0 -> "wooden" // レベル0の最低限は木製とする (Goldenもレベル0だが、ここでは検索リスト生成用にWoodenを使用)
                else -> return null
            }

        // ツール種類に対応するIDサフィックスを決定
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

    override fun tick() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val blockHitResult = client.crosshairTarget // プレイヤーが見ているエンティティやブロックを取得

        // ブロックのヒット結果でない場合は処理を中断
        if (blockHitResult?.type != net.minecraft.util.hit.HitResult.Type.BLOCK) {
            resetTool() // ターゲットがブロックでなくなった場合はツールを元に戻す
            return
        }

        // 現在ブロックを掘っているか（左クリックを長押ししているか）を確認
        val isMining = client.options.attackKey.isPressed

        if (isMining) {
            val blockPos = (blockHitResult as net.minecraft.util.hit.BlockHitResult).blockPos
            val blockState = client.world?.getBlockState(blockPos) ?: return
            val block = blockState.block

            // 破壊に必要な最適なツール情報を取得
            val correctToolInfo = ToolChecker.getCorrectTool(block)
            val toolKind = correctToolInfo.toolKind
            val requiredToolLevel = correctToolInfo.toolLevel

            // 最適なツールがない、またはツールが不要なブロックの場合は処理を中断
            if (toolKind == null) {
                resetTool()
                return
            }

            // プレイヤーのインベントリマネージャーを取得
            val manager = InfiniteClient.playerInterface.inventory
            val currentlyHeldItem = manager.get(InventoryManager.Other.MAIN_HAND)

            // 必要なレベル以上のツールを最高グレードから検索するためのレベルリストを生成
            // (例: requiredToolLevel=1(石)の場合, [4, 3, 2, 1] となる)
            val searchLevels =
                materialLevels.values
                    .filter { it >= requiredToolLevel }
                    .distinct() // 重複を削除 (wooden/golden=0)
                    .sortedDescending() // ネザライトから順に検索

            var bestToolIndex: InventoryManager.InventoryIndex? = null
            var bestToolId: String? = null

            // 必要なレベル以上のツールを最高グレードから順に検索
            for (level in searchLevels) {
                // レベルに対応するツールIDを生成
                val toolId = getToolIdForLevel(level, toolKind) ?: continue
                val toolItem = ToolChecker.getItemStackFromId(toolId).item

                // アイテムの取得に失敗した場合や空気の場合はスキップ
                if (toolItem == Items.BARRIER || toolItem == Items.AIR) continue

                // インベントリ内で該当アイテムを検索
                val foundIndex = manager.findFirst(toolItem)

                if (foundIndex != null) {
                    // 最初に発見した最もグレードの高いツールを採用し、検索を終了
                    bestToolIndex = foundIndex
                    bestToolId = toolId
                    break
                }
            }

            if (bestToolIndex != null && bestToolId != null) {
                // 既に最適なツールを持っているかチェック
                // Silk Touch のチェックは複雑になるため、ここでは一旦無視して、ツールID（グレード）の一致のみを確認します。
                if (currentlyHeldItem.item == ToolChecker.getItemStackFromId(bestToolId).item) {
                    // 最適なグレードのツールを既に持っているため、切り替えは不要
                    return
                }

                // ツール切り替え前のスロットを保存（まだ保存されていなければ）
                if (previousSelectedSlot == -1) {
                    previousSelectedSlot = player.inventory.selectedSlot
                }

                // 現在手に持っているアイテムと見つけたツールをスワップ
                // MainHandはHotbarのselectedSlotに対応
                manager.swap(InventoryManager.Hotbar(player.inventory.selectedSlot), bestToolIndex)
            } else {
                // 最適なツールがインベントリに見つからなかった場合は、元のツールに戻す
                resetTool()
            }
        } else {
            resetTool() // 掘るのをやめたらツールを元に戻す
        }
    }

    /**
     * ツールを元のホットバーのスロットに戻す。
     */
    private fun resetTool() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val manager = InfiniteClient.playerInterface.inventory

        // ツールを切り替えていた場合のみ元のスロットに戻す
        if (previousSelectedSlot != -1) {
            val currentSlot = player.inventory.selectedSlot
            val originalIndex = InventoryManager.Hotbar(previousSelectedSlot)
            val currentIndex = InventoryManager.Hotbar(currentSlot)

            try {
                // 現在手に持っているアイテム（ツール）と、元のスロットにあるアイテムをスワップ
                // これにより、元のアイテムがCurrentSlotに戻り、ツールはOriginalIndex（以前のアイテムがあった場所）に移動する
                manager.swap(currentIndex, originalIndex)
                previousSelectedSlot = -1 // リセット完了
            } catch (_: Exception) {
                // スワップ失敗時 (例: クリエイティブモードで元のスロットが空になったなど)
                previousSelectedSlot = -1
            }
        }
    }
}
