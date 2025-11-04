package org.infinite.features.automatic.wood

import net.minecraft.block.Block
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.infinite.ConfigurableFeature
import org.infinite.libs.ai.AiInterface
import org.infinite.libs.ai.actions.block.MineBlockAction
import org.infinite.libs.ai.actions.movement.PathMovementAction
import org.infinite.libs.ai.interfaces.AiAction
import org.infinite.settings.FeatureSetting

class WoodMiner : ConfigurableFeature() {
    // State定義の修正 (targetはTreeオブジェクトを渡すように戻します)
    open class State {
        class Idle : State()

        class Goto(
            val target: Tree, // Tree全体をターゲットに戻す
            val destinationPos: BlockPos, // 近くの移動先ブロック位置
        ) : State()

        class Mine(
            val target: Tree,
        ) : State()
    }

    class Tree(
        val block: Block,
        val blockPos: BlockPos,
    ) {
        val pos: Vec3d = blockPos.toCenterPos()
    }

    var state: State = State.Idle()
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

    // 採掘対象の丸太ブロックの位置を保持するスタック
    private var miningTargets: MutableList<BlockPos> = mutableListOf()

    /**
     * countTreeBlocksのロジックを流用し、カウントではなく位置を収集するヘルパー関数
     */
    private fun collectLogPositions(
        startPos: BlockPos,
        targetBlock: Block,
        visited: MutableSet<BlockPos>,
    ) {
        val currentWorld = world ?: return
        if (visited.contains(startPos)) return

        val blockState = currentWorld.getBlockState(startPos)
        if (blockState.block != targetBlock) return

        visited.add(startPos)

        val offsets =
            listOf(
                BlockPos(0, 1, 0),
                BlockPos(0, -1, 0),
                BlockPos(1, 0, 0),
                BlockPos(-1, 0, 0),
                BlockPos(0, 0, 1),
                BlockPos(0, 0, -1),
            )

        for (offset in offsets) {
            collectLogPositions(startPos.add(offset), targetBlock, visited)
        }
    }

    /**
     * 指定された開始位置から、隣接するすべての同じ木材タイプの丸太ブロックを探索し、その総数をカウントします。
     * 深さ優先探索 (DFS) を使用します。
     */
    private fun countTreeBlocks(
        startPos: BlockPos,
        targetBlock: Block,
        visited: MutableSet<BlockPos>,
    ): Int {
        val currentWorld = world ?: return 0
        if (visited.contains(startPos)) return 0
        val blockState = currentWorld.getBlockState(startPos)
        if (blockState.block != targetBlock) return 0

        visited.add(startPos)
        var count = 1

        val offsets =
            listOf(
                BlockPos(0, 1, 0),
                BlockPos(0, -1, 0),
                BlockPos(1, 0, 0),
                BlockPos(-1, 0, 0),
                BlockPos(0, 0, 1),
                BlockPos(0, 0, -1),
            )

        for (offset in offsets) {
            val neighborPos = startPos.add(offset)
            count += countTreeBlocks(neighborPos, targetBlock, visited)
        }
        return count
    }

    /**
     * 周囲のブロックを探索し、設定された木材タイプのブロックの「根本」を探して
     * 木全体をカウントし、Treeオブジェクトとしてリストアップします。
     */
    fun searchTrees(): List<Tree> {
        val currentWorld = world ?: return emptyList()
        val blockPos = player?.blockPos ?: return emptyList()
        val radius = searchRadius.value.toInt()
        val centerBlockPos = BlockPos(blockPos)

        val foundTrees = mutableListOf<Tree>()
        val targetWoodIds = woodTypes.value.toSet()
        val countedLogPositions = mutableSetOf<BlockPos>()

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val targetBlockPos = centerBlockPos.add(x, y, z)
                    if (countedLogPositions.contains(targetBlockPos)) continue

                    val blockState = currentWorld.getBlockState(targetBlockPos)
                    val block = blockState.block
                    val blockId = Registries.BLOCK.getId(block).toString()

                    if (blockId in targetWoodIds) {
                        val belowPos = targetBlockPos.down()
                        val belowBlockState = currentWorld.getBlockState(belowPos)

                        // 根本判定
                        if (belowBlockState.block != block) {
                            val treeCount = countTreeBlocks(targetBlockPos, block, countedLogPositions)

                            if (treeCount > 0) {
                                foundTrees.add(
                                    Tree(
                                        block = block,
                                        blockPos = targetBlockPos,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        return foundTrees.sortedBy { it.pos.distanceTo(blockPos.toCenterPos()) }
    }

// --- 状態ハンドラ ---

    override fun tick() {
        // AIアクションの処理はAiInterface側で行われるため、ここでは単に状態遷移に集中します
        if (AiInterface.actions.isNotEmpty()) return // 実行中のアクションがあれば待機

        when (state) {
            is State.Idle -> handleIdle()
            is State.Goto -> handleGoto(state as State.Goto)
            is State.Mine -> handleMine(state as State.Mine)
        }
    }

    private fun handleIdle() {
        val trees = searchTrees()
        val playerPos = playerPos ?: return

        if (trees.isNotEmpty()) {
            // 1. 木が見つかった場合
            val nearestTree = trees.first()
            val rootPos = nearestTree.blockPos

            // 根本の真上（プレイヤーが立てる位置）を移動先とする
            val destinationPos = rootPos.up(1)

            // 状態をGotoに遷移
            state = State.Goto(target = nearestTree, destinationPos = destinationPos)
            handleGoto(state as State.Goto) // 即座に次の状態の処理へ
        } else {
            // 2. 木が見つからなかった場合: 周囲を適当に歩く
            val currentX = playerPos.x.toInt()
            val currentZ = playerPos.z.toInt()
            val randomOffset = 15 // 適当な移動距離

            val randomTarget =
                Vec3d(
                    currentX + (Math.random() * randomOffset * 2 - randomOffset),
                    playerPos.y,
                    currentZ + (Math.random() * randomOffset * 2 - randomOffset),
                )

            val movementAction =
                PathMovementAction(
                    x = randomTarget.x.toInt(),
                    z = randomTarget.z.toInt(),
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = { state = State.Idle() },
                    onFailureAction = { state = State.Idle() },
                )

            AiInterface.add(movementAction)
        }
    }

    private fun handleGoto(goto: State.Goto) {
        val targetBlockPos = goto.destinationPos
        val playerCenterPos = playerPos ?: return

        // ターゲットに十分に近づいているか判定
        if (targetBlockPos.getSquaredDistance(
                playerCenterPos.x,
                playerCenterPos.y,
                playerCenterPos.z,
            ) < 16.0
        ) { // 距離4ブロック圏内
            // 到着: 採掘リストを作成し、Mine状態に遷移

            val tree = goto.target

            // 採掘リストを構築
            miningTargets.clear()
            val visited = mutableSetOf<BlockPos>()
            collectLogPositions(tree.blockPos, tree.block, visited)
            miningTargets.addAll(visited)

            // 距離の近い順に採掘するようにソート
            miningTargets.sortBy { it.getSquaredDistance(playerCenterPos.x, playerCenterPos.y, playerCenterPos.z) }

            state = State.Mine(target = tree)
            handleMine(state as State.Mine) // 即座に次の状態の処理へ
        } else {
            // 遠い場合: 移動アクションを開始
            val movementAction =
                PathMovementAction(
                    x = targetBlockPos.x,
                    z = targetBlockPos.z,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = { state = State.Goto(goto.target, goto.destinationPos) }, // 成功したら同じGotoを再評価
                    onFailureAction = { state = State.Idle() },
                )
            AiInterface.add(movementAction)
        }
    }

    private fun handleMine(mine: State.Mine) {
        val nextTarget = miningTargets.firstOrNull()
        if (nextTarget != null) {
            val block = world?.getBlockState(nextTarget)?.block ?: return
            // 採掘ブロックがある場合
            val mineAction =
                MineBlockAction(
                    block = block,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = {
                        miningTargets.remove(nextTarget)
                        state = State.Mine(mine.target) // 状態を維持して次のブロックを採掘
                    },
                    onFailureAction = {
                        // 失敗した場合は、そのブロックをリストから除外して次へ進む
                        miningTargets.remove(nextTarget)
                        state = State.Mine(mine.target)
                    },
                )
            AiInterface.add(mineAction)
        } else {
            // 掘るブロックが全て完了した場合

            // アイテム回収ロジック: 採掘した木の根本の真上に移動 (アイテムが散らばっている可能性のある場所)
            val pickupPos = mine.target.blockPos.up(1)

            val pickupAction =
                PathMovementAction(
                    x = pickupPos.x,
                    z = pickupPos.z,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = { state = State.Idle() }, // 回収地点に移動後、Idleに戻る
                    onFailureAction = { state = State.Idle() }, // 失敗してもIdleに戻る
                )
            AiInterface.add(pickupAction)
        }
    }

    override fun respawn() {
        disable()
    }
}
