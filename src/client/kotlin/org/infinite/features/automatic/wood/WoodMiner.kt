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
    open class State {
        class Idle : State()

        class Goto(
            val target: Tree,
            val destinationPos: BlockPos,
        ) : State()

        class Mine(
            val target: Tree,
            val blocksToMine: List<BlockPos>, // 今回破壊する丸太のリスト (通常は上から1〜2個)
        ) : State()

        class Climb(
            val target: Tree,
            val nextDestinationPos: BlockPos, // 登る先の座標 (次の採掘場所の1ブロック上、または回収地点)
        ) : State()
    }

    // --- Treeクラス定義 ---
    class Tree(
        val block: Block,
        val rootPos: BlockPos, // 根本（最初に発見された丸太）の位置
        // 木を構成する全丸太の位置
    ) {
        // toCenterPos()はカスタムメソッドを仮定
        val pos: Vec3d = rootPos.toCenterPos()
    }

    // --- フィールドと設定 ---
    var state: State = State.Idle()
    override val togglable: Boolean = false // 自動機能のため手動トグルを無効

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

    override fun start() = disable() // 安全のため自動で起動しない

    // --- ヘルパー関数 ---

    /**
     * 指定された開始位置から、隣接するすべての同じ木材タイプの丸太ブロックの位置を探索し、Setとして返す。
     * 深さ優先探索 (DFS) を使用します。
     */
    private fun collectLogPositions(
        startPos: BlockPos,
        targetBlock: Block,
        visited: MutableSet<BlockPos>,
    ): Set<BlockPos> {
        val currentWorld = world ?: return emptySet()
        val foundPositions = mutableSetOf<BlockPos>()

        fun dfs(currentPos: BlockPos) {
            if (visited.contains(currentPos)) return
            val blockState = currentWorld.getBlockState(currentPos)
            if (blockState.block != targetBlock) return

            visited.add(currentPos)
            foundPositions.add(currentPos)

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
                dfs(currentPos.add(offset))
            }
        }

        dfs(startPos)
        return foundPositions
    }

    /**
     * 周囲のブロックを探索し、設定された木材タイプのブロックの「根本」を探して
     * 木全体を探索し、Treeオブジェクトとしてリストアップします。
     */
    fun searchTrees(): List<Tree> {
        val currentWorld = world ?: return emptyList()
        val blockPos = player?.blockPos ?: return emptyList()
        val radius = searchRadius.value.toInt()
        val centerBlockPos = BlockPos(blockPos)

        val foundTrees = mutableListOf<Tree>()
        val targetWoodIds = woodTypes.value.toSet()
        val countedLogPositions = mutableSetOf<BlockPos>()

        // 探索範囲を限定 (Y軸はプレイヤーを中心に)
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

                        // 根本判定: 真下が同じ丸太でない、かつ真下が空気でないことを確認（より確実なのは真下が丸太・葉・空気・水でないこと）
                        // シンプルに「真下が丸太でない」を根本と見なす
                        if (belowBlockState.block != block) {
                            val allLogs = collectLogPositions(targetBlockPos, block, countedLogPositions)
                            countedLogPositions.addAll(allLogs)

                            if (allLogs.isNotEmpty()) {
                                foundTrees.add(
                                    Tree(
                                        block = block,
                                        rootPos = targetBlockPos,
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
        // 実行中のアクションがあれば待機
        if (AiInterface.actions.isNotEmpty()) return

        when (state) {
            is State.Idle -> handleIdle()
            is State.Goto -> handleGoto(state as State.Goto)
            is State.Mine -> handleMine(state as State.Mine)
            is State.Climb -> handleClimb(state as State.Climb)
        }
    }

    private fun handleIdle() {
        val trees = searchTrees()
        val playerPos = playerPos ?: return

        if (trees.isNotEmpty()) {
            // 1. 木が見つかった場合
            val nearestTree = trees.first()

            // 根本の真上（プレイヤーが立てる位置）を移動先とする
            val destinationPos = nearestTree.rootPos.up(1)

            // 状態をGotoに遷移
            state = State.Goto(target = nearestTree, destinationPos = destinationPos)
        } else {
            // 2. 木が見つからなかった場合: 周囲を適当に歩く（探索）
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

        // ターゲットに十分に近づいているか判定 (水平距離2ブロック圏内を優先)
        if (targetBlockPos.getSquaredDistance(
                playerCenterPos.x,
                targetBlockPos.y.toDouble(), // Y座標はターゲットに合わせる
                playerCenterPos.z,
            ) < 4.0 // 距離 2.0 ブロック
        ) {
            // 到着: 破壊フェーズの開始へ
            determineNextActionAfterMine(goto.target)
        } else {
            // 遠い場合: 移動アクションを開始
            val movementAction =
                PathMovementAction(
                    x = targetBlockPos.x,
                    z = targetBlockPos.z,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    // 成功したら次のアクションを評価させるため、Gotoを再評価
                    onSuccessAction = { state = State.Goto(goto.target, goto.destinationPos) },
                    onFailureAction = { state = State.Idle() },
                )
            AiInterface.add(movementAction)
        }
    }

    private fun handleMine(mine: State.Mine) {
        val tree = mine.target
        val targets = mine.blocksToMine

        if (targets.isNotEmpty()) {
            val nextTargetPos = targets.first()

            // 採掘アクションをセットアップ
            val mineAction =
                MineBlockAction(
                    // 以前のMineBlockActionの修正に従い、BlockPosを使用
                    targetPos = nextTargetPos,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = {
                        // 破壊成功: リストから削除
                        val remainingTargets = targets.drop(1)
                        if (remainingTargets.isEmpty()) {
                            // 今回の破壊フェーズが終了した場合、次の行動を決定
                            determineNextActionAfterMine(tree)
                        } else {
                            // まだ破壊する丸太が残っている場合
                            state = State.Mine(tree, remainingTargets)
                        }
                    },
                    onFailureAction = {
                        // 失敗: そのブロックはリストから除外して次へ進む
                        val remainingTargets = targets.drop(1)
                        if (remainingTargets.isEmpty()) {
                            determineNextActionAfterMine(tree)
                        } else {
                            state = State.Mine(tree, remainingTargets)
                        }
                    },
                )
            AiInterface.add(mineAction)
        } else {
            // 掘るブロックがない場合
            determineNextActionAfterMine(tree)
        }
    }

    /**
     * 破壊フェーズ終了後の次の状態を決定するヘルパー関数
     */
    private fun determineNextActionAfterMine(tree: Tree) {
        val world = world ?: return
        val playerBlockPos = player?.blockPos ?: return
        val playerY = playerBlockPos.y

        // 現在残っている丸太の位置を再取得
        // ※注意: collectLogPositionsはvisited Setを使用するため、この場所でインスタンス化が必要
        val remainingLogs =
            collectLogPositions(tree.rootPos, tree.block, mutableSetOf())
                .filter {
                    world.getBlockState(it).block == tree.block
                }.toSet()

        // 1. 根本処理: 木の丸太が全てなくなった、または根本ブロック（rootPos）が破壊された場合
        if (remainingLogs.isEmpty() || !remainingLogs.contains(tree.rootPos)) {
            // アイテム回収ロジック: 根本の真上へ移動
            val pickupPos = tree.rootPos.up(1)

            // アイテム回収地点への移動をClimb状態を利用して行う
            state = State.Climb(tree, pickupPos)
            return
        }

        // 2. 登る/継続判定: まだ破壊すべき丸太が上にある場合
        val maxLogY = remainingLogs.maxOfOrNull { it.y } ?: tree.rootPos.y

        // プレイヤーが届く範囲内の丸太をフィルタリング
        val targetsForNextMine =
            remainingLogs
                .filter { it.y >= playerY && it.y <= playerY + 2 } // プレイヤーが届く範囲内の丸太
                .sortedByDescending { it.y } // 上から順にソート
                .take(2) // 上から2つまでを破壊対象とする (効率的な破壊のため)

        if (targetsForNextMine.isNotEmpty()) {
            // まだ同じ高さで破壊できる丸太がある場合は、Mine状態を継続
            state = State.Mine(tree, targetsForNextMine)
        } else if (maxLogY > playerY + 1) {
            // 破壊すべき丸太が上にあり、現在の場所からは届かない場合 -> Climb

            // 次に掘る丸太のY座標の1ブロック上を移動先とする
            val nextDestinationY = maxLogY + 1

            // ✅ 修正: 登る先のX, Z座標は木の根本の位置を使用する
            val nextDestinationPos = BlockPos(tree.rootPos.x, nextDestinationY, tree.rootPos.z)

            state = State.Climb(tree, nextDestinationPos)
        } else {
            // どの条件にも当てはまらない (例: 根元の丸太だけが残り、プレイヤーが既にその真上にいる場合)
            state = State.Idle()
        }
    }

    private fun handleClimb(climb: State.Climb) {
        val targetBlockPos = climb.nextDestinationPos
        val playerCenterPos = playerPos ?: return

        // ターゲットに十分に近づいているか判定 (Y座標と水平距離をチェック)
        if (playerCenterPos.y.toInt() == targetBlockPos.y &&
            targetBlockPos.getSquaredDistance(
                playerCenterPos.x,
                targetBlockPos.y.toDouble(), // 水平距離のみを評価するためYをターゲットに固定
                playerCenterPos.z,
            ) < 25.0 // 距離 5.0 ブロック
        ) {
            // 到着: 次の破壊フェーズの開始、またはIdleに戻る

            // アイテム回収地点に到着したか (根本+1)
            if (targetBlockPos.y == climb.target.rootPos.y + 1) {
                state = State.Idle()
            } else {
                // 登頂地点に到着した場合、次のMineフェーズへ
                determineNextActionAfterMine(climb.target)
            }
        } else {
            // 遠い場合: 移動アクションを開始
            val movementAction =
                PathMovementAction(
                    x = targetBlockPos.x,
                    z = targetBlockPos.z,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = { state = State.Climb(climb.target, climb.nextDestinationPos) },
                    onFailureAction = { state = State.Idle() }, // 登ることに失敗したら、木を諦めてIdleに戻る
                )
            AiInterface.add(movementAction)
        }
    }

    override fun respawn() {
        disable()
    }
}
