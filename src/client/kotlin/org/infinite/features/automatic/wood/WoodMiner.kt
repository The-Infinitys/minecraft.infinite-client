package org.infinite.features.automatic.wood

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import org.infinite.ConfigurableFeature
import org.infinite.libs.ai.AiInterface
import org.infinite.settings.FeatureSetting
import java.util.LinkedList
import kotlin.math.abs

class WoodMiner : ConfigurableFeature() {
    override val togglable: Boolean = false
    val searchRadius =
        FeatureSetting.DoubleSetting(
            name = "SearchRadius",
            defaultValue = 10.0,
            min = 1.0,
            max = 64.0,
        )

    val woodTypes =
        FeatureSetting.BlockListSetting(
            name = "WoodTypes",
            defaultValue = mutableListOf("minecraft:oak_log", "minecraft:spruce_log"),
        )
    override val settings: List<FeatureSetting<*>> =
        listOf(
            searchRadius,
            woodTypes,
        )

    override fun start() = disable()
    // --- 内部状態と評価係数 ---

    private var targetWood: Wood? = null

    private val distanceWeight = 0.5
    private val countWeight = 1.0
    private val reachabilityPenalty = 100.0

    // --- データクラス ---
    // MineWoodActionがアクセスできるように、ここでは内部クラスのまま維持
    data class Wood(
        val rootPos: BlockPos,
        val type: Block,
        val count: Int,
        val logPositions: MutableSet<BlockPos>,
    )

    // --- メインループ ---
    override fun tick() {
        // AIアクションキューが空でない場合は、WoodMinerからの新規アクション追加を停止
        if (AiInterface.actions.isNotEmpty()) {
            return
        }

        if (player == null || world == null || !isEnabled()) return

        if (targetWood == null) {
            // 1 & 2: 近くの木を全て調べてリストアップし、最も良い木を選ぶ
            val nearbyTrees = listNearbyTrees(world!!, player!!.blockPos)
            targetWood = selectBestTree(nearbyTrees)

            if (targetWood == null) return
        }

        // 目標が設定されている場合の処理: 4. 木を全て採掘する のアクションをキューに詰める
        if (targetWood != null) {
            if (targetWood!!.logPositions.isNotEmpty()) {
                // MineWoodActionをAiInterfaceのキューに1つだけ追加
                mineTree(targetWood!!)

                // MineWoodActionが完了すると、次のtickで targetWood = null になる
            } else {
                // すべての原木がリストから削除された（＝採掘が完了した）
                targetWood = null // 5. 1へ戻る (次のtickで実行)
            }
        }
    }

    // --- 採掘処理 ---

    /**
     * 目標地点の原木を全て採掘するアクション (MineWoodAction) をキューに追加します。
     * * @param wood 採掘対象の木
     */
    private fun mineTree(wood: Wood) {
        // MineWoodActionのインスタンスを作成
        val mineWoodAction =
            MineWoodAction(wood) { minedPos ->
                // 個々の原木が採掘されるたびに、Woodオブジェクトのリストから削除
                wood.logPositions.remove(minedPos)
                println("WoodMiner: Removed log at $minedPos from target list.")
            }

        // AiInterfaceのキューにMineWoodActionを1つだけ追加
        AiInterface.actions.add(mineWoodAction)
    }

    // --- 評価ロジック ---

    private fun selectBestTree(trees: List<Wood>): Wood? {
        if (trees.isEmpty()) return null
        val playerPos = player?.blockPos ?: return null

        return trees.maxByOrNull { wood ->
            val distance = playerPos.getManhattanDistance(wood.rootPos)
            val isReachable = isReachable(wood.rootPos)

            var score = 0.0
            score += wood.count * countWeight
            score -= distance * distanceWeight

            if (!isReachable) {
                score -= reachabilityPenalty
            }
            score
        }
    }

    private fun isReachable(targetPos: BlockPos): Boolean {
        val playerPos = player?.blockPos ?: return false
        val yDiff = abs(playerPos.y - targetPos.y)
        val horizontalDistance = playerPos.getManhattanDistance(targetPos.withY(playerPos.y))

        return yDiff <= 5 && horizontalDistance <= searchRadius.value
    }

    private fun listNearbyTrees(
        world: net.minecraft.world.World,
        centerPos: BlockPos,
    ): List<Wood> {
        val trees = mutableListOf<Wood>()
        val visitedLogs = mutableSetOf<BlockPos>()
        val range = searchRadius.value.toInt()

        for (x in -range..range) {
            for (y in -range..range) {
                for (z in -range..range) {
                    val currentPos = centerPos.add(x, y, z)
                    if (visitedLogs.contains(currentPos)) continue

                    val block = world.getBlockState(currentPos).block

                    if (isWoodBlock(block)) {
                        var rootCandidatePos = currentPos
                        var logCount = 0
                        val currentTreeLogPositions = mutableSetOf<BlockPos>()

                        val queue = LinkedList<BlockPos>()
                        queue.add(currentPos)
                        visitedLogs.add(currentPos)
                        currentTreeLogPositions.add(currentPos)

                        while (queue.isNotEmpty()) {
                            val nextPos = queue.poll()

                            if (nextPos.y < rootCandidatePos.y) {
                                rootCandidatePos = nextPos
                            }

                            if (isWoodBlock(world.getBlockState(nextPos).block)) {
                                logCount++
                            }

                            for (xOffset in -1..1) {
                                for (yOffset in -1..1) {
                                    for (zOffset in -1..1) {
                                        if (xOffset == 0 && yOffset == 0 && zOffset == 0) continue

                                        val adjacentPos = nextPos.add(xOffset, yOffset, zOffset)

                                        if (!visitedLogs.contains(adjacentPos) && centerPos.getSquaredDistance(
                                                adjacentPos,
                                            ) <= range * range
                                        ) {
                                            val adjacentBlock = world.getBlockState(adjacentPos).block

                                            if (isWoodBlock(adjacentBlock) || isLeafBlock(adjacentBlock)) {
                                                if (isWoodBlock(adjacentBlock)) {
                                                    visitedLogs.add(adjacentPos.toImmutable())
                                                    currentTreeLogPositions.add(adjacentPos.toImmutable())
                                                    queue.add(adjacentPos.toImmutable())
                                                } else if (isLeafBlock(adjacentBlock)) {
                                                    queue.add(adjacentPos.toImmutable())
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (logCount > 0) {
                            trees.add(Wood(rootCandidatePos, block, logCount, currentTreeLogPositions))
                        }
                    }
                }
            }
        }
        return trees
    }

    private fun isWoodBlock(block: Block): Boolean {
        val blockId = Registries.BLOCK.getId(block).toString()
        return woodTypes.value.contains(blockId) ||
            listOf(
                Blocks.OAK_LOG,
                Blocks.SPRUCE_LOG,
                Blocks.BIRCH_LOG,
                Blocks.JUNGLE_LOG,
                Blocks.ACACIA_LOG,
                Blocks.DARK_OAK_LOG,
                Blocks.MANGROVE_LOG,
                Blocks.CHERRY_LOG,
                Blocks.CRIMSON_STEM,
                Blocks.WARPED_STEM,
            ).contains(block)
    }

    private fun isLeafBlock(block: Block): Boolean =
        listOf(
            Blocks.OAK_LEAVES,
            Blocks.SPRUCE_LEAVES,
            Blocks.BIRCH_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES,
            Blocks.MANGROVE_LEAVES,
            Blocks.CHERRY_LEAVES,
        ).contains(block)
}
