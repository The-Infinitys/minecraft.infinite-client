package org.infinite.features.movement.braek

import net.minecraft.block.Block
import net.minecraft.registry.Registries
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.settings.FeatureSetting
import org.infinite.utils.block.BlockUtils
import java.util.LinkedList
import java.util.Queue

/**
 * 破壊対象のブロックから周囲の同じ種類の鉱石を探索し、一括で破壊する機能。
 */
class VeinBreak : ConfigurableFeature() {
    override val level: FeatureLevel = FeatureLevel.Cheat

    // --- 設定項目 ---

    // 鉱脈として破壊対象とするブロックのリスト
    private val blockList =
        FeatureSetting.BlockListSetting(
            "BlockList",
            "feature.movement.veinbreak.blocklist.description",
            mutableListOf(
                "minecraft:ancient_debris",
                "minecraft:coal_ore",
                "minecraft:copper_ore",
                "minecraft:deepslate_coal_ore",
                "minecraft:deepslate_copper_ore",
                "minecraft:deepslate_diamond_ore",
                "minecraft:deepslate_emerald_ore",
                "minecraft:deepslate_gold_ore",
                "minecraft:deepslate_iron_ore",
                "minecraft:deepslate_lapis_ore",
                "minecraft:deepslate_redstone_ore",
                "minecraft:diamond_ore",
                "minecraft:emerald_ore",
                "minecraft:gold_ore",
                "minecraft:iron_ore",
                "minecraft:lapis_ore",
                "minecraft:nether_gold_ore",
                "minecraft:nether_quartz_ore",
                "minecraft:redstone_ore",
            ),
        )

    private val breakRange =
        FeatureSetting.DoubleSetting(
            name = "Range",
            descriptionKey = "feature.movement.veinbreak.range.description",
            defaultValue = 5.0,
            min = 1.0,
            max = 6.0,
        )
    private val maxBlocks =
        FeatureSetting.DoubleSetting(
            name = "MaxBlocks",
            descriptionKey = "feature.movement.veinbreak.max_blocks.description",
            defaultValue = 64.0,
            min = 1.0,
            max = 500.0,
        )
    private val swingHand =
        FeatureSetting.BooleanSetting(
            name = "SwingHand",
            descriptionKey = "feature.movement.veinbreak.swing_hand.description",
            defaultValue = true,
        )

    private val ignoreHotbarChange =
        FeatureSetting.BooleanSetting(
            name = "IgnoreHotbarChange",
            descriptionKey = "feature.movement.veinbreak.ignore_hotbar_change.description",
            defaultValue = false,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(breakRange, maxBlocks, swingHand, ignoreHotbarChange, blockList)

    // 破壊対象のブロックリスト（LinkedHashSetで処理順を維持）
    private val blocksToMine = LinkedHashSet<BlockPos>()

    // 現在破壊中のブロックの位置と、そのブロックを叩いている面を保持
    var currentBreakingPos: BlockPos? = null
    private var currentBreakingSide: Direction? = null

    // 破壊進行度 (0.0 から 1.0) を保持
    private var currentBreakingProgress: Float = 0.0f

    // 破壊開始時のホットバーのスロットを保持
    private var startHotbarSlot: Int = -1

    override fun tick() {
        rayCast()
        mine()
    }

    private fun rayCast() {
        val interactionManager = interactionManager ?: return
        if (interactionManager.isBreakingBlock) {
            val pos = interactionManager.currentBreakingPos
            add(pos)
        }
    }

    /**
     * 外部から破壊対象のブロックを追加する。
     * リストが空の場合、このブロックを起点に鉱脈探索を開始する。
     */
    fun add(pos: BlockPos) {
        if (blocksToMine.isEmpty() || currentBreakingPos == null) { // 最初のブロックが追加されたとき、または破壊が完了したときに鉱脈を探索
            findVein(pos)
        }
    }

    /**
     * ブロックが設定リストに含まれている鉱石ブロックであるかを確認する。
     */
    private fun isOreBlock(block: Block): Boolean {
        val blockId = Registries.BLOCK.getId(block).toString()
        return blockList.value.contains(blockId)
    }

    /**
     * 破壊を試みたブロックから鉱石の塊を探索し、blocksToMineに追加する（BFSを使用）。
     * - 探索は `startPos` から開始し、`breakRange` の範囲内に限定される。
     * - 探索数は `maxBlocks` の設定値を超えない。
     */
    private fun findVein(startPos: BlockPos) {
        blocksToMine.clear()
        val world = client.world ?: return
        val queue: Queue<BlockPos> = LinkedList()
        val visited = mutableSetOf<BlockPos>()
        if (!isOreBlock(world.getBlockState(startPos).block)) {
            return
        }

        queue.offer(startPos)
        visited.add(startPos)

        val rangeSq = breakRange.value * breakRange.value

        while (queue.isNotEmpty() && blocksToMine.size < maxBlocks.value.toInt()) {
            val currentPos = queue.poll()

            // 破壊範囲のチェック（distanceSq < rangeSq で、プレイヤーからではなくstartPosからの距離で判定）
            if (currentPos.getSquaredDistance(startPos) > rangeSq) {
                continue // 範囲外のブロックはスキップ
            }

            val blockState = world.getBlockState(currentPos)
            val block = blockState.block

            if (isOreBlock(block)) {
                blocksToMine.add(currentPos)

                // 隣接ブロックを探索キューに追加
                for (direction in Direction.entries) {
                    val neighborPos = currentPos.offset(direction)

                    if (
                        !visited.contains(neighborPos) &&
                        neighborPos.getSquaredDistance(startPos) <= rangeSq // 探索範囲内のチェック
                    ) {
                        val neighborBlock = world.getBlockState(neighborPos).block
                        if (isOreBlock(neighborBlock)) {
                            queue.offer(neighborPos)
                            visited.add(neighborPos)
                        } else {
                            // 鉱石ではないブロックでも訪問済みリストに追加し、再度チェックされるのを防ぐ
                            visited.add(neighborPos)
                        }
                    }
                }
            }
        }
    }

    /**
     * 破壊リスト内のブロックをサーバーに破壊させる。
     * LinearBreakとほぼ同じ破壊ロジックを使用。
     */
    private fun mine() {
        val interactionManager = client.interactionManager ?: return
        val player = player ?: return

        // 修正点: 設定が無効な場合のみ、ホットバー切り替え時のキャンセルチェックを行う
        if (!ignoreHotbarChange.value) {
            if (currentBreakingPos != null && startHotbarSlot != -1 && player.inventory.selectedSlot != startHotbarSlot) {
                if (autoToolCallBack == player.inventory.selectedSlot) {
                    startHotbarSlot = autoToolCallBack
                } else {
                    // ホットバーが切り替わった場合、破壊をキャンセルしリストから削除
                    interactionManager.cancelBlockBreaking()
                    blocksToMine.remove(currentBreakingPos)
                    // 状態をリセット
                    currentBreakingPos = null
                    currentBreakingSide = null
                    currentBreakingProgress = 0.0f
                    startHotbarSlot = -1
                    return
                }
            }
            // ----------------------------------------------------
        }

        // 1. 破壊対象のブロックがない場合、現在の破壊を中止
        if (blocksToMine.isEmpty()) {
            if (currentBreakingPos != null) {
                interactionManager.cancelBlockBreaking()
                currentBreakingPos = null
                currentBreakingSide = null
                currentBreakingProgress = 0.0f
            }
            return
        }

        // 2. ターゲットの決定とブロック状態のチェック
        val targetPos = blocksToMine.first()
        val blockState = client.world?.getBlockState(targetPos)

        // ターゲットブロックが空気または置換可能なら、破壊リストから削除
        if (blockState?.isAir == true || blockState?.isReplaceable == true) {
            blocksToMine.remove(targetPos)
            currentBreakingPos = null
            currentBreakingProgress = 0.0f
            return
        }

        // 3. 破壊パラメータの取得と有効性のチェック
        val params = BlockUtils.getBlockBreakingParams(targetPos)

        // パラメータが取得できない、または距離が離れすぎた場合
        if (params == null || params.distanceSq > breakRange.value * breakRange.value) {
            blocksToMine.remove(targetPos)
            currentBreakingPos = null
            currentBreakingProgress = 0.0f
            return
        }

        // 4. 破壊開始またはターゲット変更
        if (currentBreakingPos == null || currentBreakingPos != params.pos) {
            // 前の破壊を中止
            interactionManager.cancelBlockBreaking()

            // サーバーに視点変更パケットを送信
            BlockUtils.faceVectorPacket(params.hitVec)

            // 破壊開始
            interactionManager.updateBlockBreakingProgress(params.pos, params.side)

            // 状態の更新
            currentBreakingPos = params.pos
            currentBreakingSide = params.side
            currentBreakingProgress = 0.0f // 新しいブロックなので0から開始

            // IgnoreHotbarChangeが有効でない場合にのみスロットを保存
            startHotbarSlot =
                if (!ignoreHotbarChange.value) {
                    player.inventory.selectedSlot // ホットバーの状態を保存
                } else {
                    -1 // 無視する場合は状態を保存しない
                }
        } else {
            // 5. 進行中: 進行パケットを送信し、進行度を更新
            val pos = currentBreakingPos!!
            val side = currentBreakingSide!!
            interactionManager.updateBlockBreakingProgress(pos, side)
            currentBreakingProgress = interactionManager.currentBreakingProgress

            val fastBreak = InfiniteClient.getFeature(FastBreak::class.java) ?: return
            if (fastBreak.isEnabled() && !fastBreak.safeMode.value) {
                fastBreak.handle(pos)
            }
            // 破壊が完了したと見なせる場合（次のtickでブロックが消えるはずだが、念のため）
            if (currentBreakingProgress >= 1.0f) {
                currentBreakingPos = null
                currentBreakingProgress = 0.0f
            }
        }

        // 6. 手を振る
        if (swingHand.value) {
            player.swingHand(Hand.MAIN_HAND)
        }
    }

    override fun render3d(graphics3D: Graphics3D) {
        val color = InfiniteClient.theme().colors.primaryColor

        // 破壊対象ブロックのアウトライン描画
        val boxes =
            blocksToMine.map { pos ->
                val box = Box(pos)
                RenderUtils.ColorBox(color, box)
            }
        graphics3D.renderLinedColorBoxes(boxes, true)

        // 破壊中のブロックに進行度に応じたハイライトを追加
        currentBreakingPos?.let { pos ->
            val progress = currentBreakingProgress.coerceIn(0.0f, 1.0f)
            val offset = (1.0 - progress) * 0.5 // ブロックの中心から外側へ拡大するためのオフセット
            val minX = pos.x + offset
            val minY = pos.y + offset
            val minZ = pos.z + offset
            val maxX = pos.x + 1.0 - offset
            val maxY = pos.y + 1.0 - offset
            val maxZ = pos.z + 1.0 - offset

            // 進行度に応じて縮小/拡大するボックス
            val dynamicBox = Box(minX, minY, minZ, maxX, maxY, maxZ).contract(0.005)

            val boxes = listOf(RenderUtils.ColorBox(color, dynamicBox))
            graphics3D.renderSolidColorBoxes(boxes, true)
        }
    }

    /**
     * VeinBreakが有効で、破壊対象のブロックリストにブロックがある場合にtrueを返す。
     */
    val isWorking: Boolean
        get() = isEnabled() && !blocksToMine.isEmpty()

    /**
     * AutoToolなどによるホットバー変更のコールバックを保持する変数
     */
    var autoToolCallBack: Int = -1

    // VeinBreakの有効化/無効化時に状態をリセット
    override fun enabled() {
        blocksToMine.clear()
        currentBreakingPos = null
        currentBreakingSide = null
        currentBreakingProgress = 0.0f
        startHotbarSlot = -1
    }

    override fun disabled() {
        val interactionManager = client.interactionManager ?: return
        interactionManager.cancelBlockBreaking()
        blocksToMine.clear()
        currentBreakingPos = null
        currentBreakingSide = null
        currentBreakingProgress = 0.0f
        startHotbarSlot = -1
    }
}
