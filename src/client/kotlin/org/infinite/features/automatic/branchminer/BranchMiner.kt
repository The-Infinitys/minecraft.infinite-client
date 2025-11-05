package org.infinite.features.automatic.branchminer

import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.infinite.ConfigurableFeature
import org.infinite.libs.ai.AiInterface
import org.infinite.libs.ai.actions.block.MineBlockAction
import org.infinite.libs.ai.actions.movement.PathMovementAction
import org.infinite.libs.ai.interfaces.AiAction
import org.infinite.libs.client.inventory.ChestManager
import org.infinite.settings.FeatureSetting
import kotlin.math.abs
import kotlin.math.sqrt

class BranchMiner : ConfigurableFeature() {
    enum class MiningState {
        Initializing, // チェスト検索、開始点決定
        Scanning, // ブランチのスキャン中
        ApproachingMining, // 採掘位置に接近中
        MiningBranch, // ブランチ掘削中
        CollectingItems, // アイテム回収中
        ScanningWalls, // 壁面を全スキャン
        MovingToOre, // 鉱石採掘位置へ移動
        MiningOre, // 鉱石採掘中
        CollectingOreItems, // 鉱石採掘後のアイテム回収
        MovingToNextBranch, // 次のブランチ位置へ移動
        ApproachingNextBranch, // 次のブランチ掘削位置へ接近
        MiningNextBranchPath, // 次のブランチへの道を掘削
        CollectingPathItems, // 道掘削後のアイテム回収
        ReturningToChest, // チェストへ戻る
        StoringItems, // アイテム格納中
        Error, // エラー発生、初期位置へ戻る
        Idle, // 無効化後の状態
    }

    // 設定
    val branchLength =
        FeatureSetting.IntSetting(
            name = "BranchLength",
            defaultValue = 32,
            min = 8,
            max = 128,
        )
    val branchInterval =
        FeatureSetting.IntSetting(
            name = "BranchInterval",
            defaultValue = 3,
            min = 1,
            max = 5,
        )
    val checkInventoryInterval =
        FeatureSetting.IntSetting(
            name = "CheckInventoryInterval",
            defaultValue = 20,
            min = 1,
            max = 100,
        )
    val chestSearchRadius =
        FeatureSetting.IntSetting(
            name = "ChestSearchRadius",
            defaultValue = 16,
            min = 8,
            max = 64,
        )
    val minEmptySlots =
        FeatureSetting.IntSetting(
            name = "MinEmptySlots",
            defaultValue = 5,
            min = 1,
            max = 27,
        )
    val itemCollectionRadius =
        FeatureSetting.IntSetting(
            name = "ItemCollectionRadius",
            defaultValue = 16,
            min = 8,
            max = 32,
        )
    val itemCollectionWaitTicks =
        FeatureSetting.IntSetting(
            name = "ItemCollectionWaitTicks",
            defaultValue = 40,
            min = 20,
            max = 100,
        )
    val miningApproachDistance = FeatureSetting.IntSetting("MiningApproachDistance", 0, 1, 2)
    val detectSpaceThreshold = FeatureSetting.IntSetting("DetectSpaceThreshold", 64, 32, 128)
    override val settings: List<FeatureSetting<*>> =
        listOf(
            branchLength,
            branchInterval,
            checkInventoryInterval,
            chestSearchRadius,
            minEmptySlots,
            miningApproachDistance,
            itemCollectionRadius,
            itemCollectionWaitTicks,
            detectSpaceThreshold,
        )

    // 状態管理
    private var currentState: MiningState = MiningState.Idle
    private var initialPosition: BlockPos? = null
    private var initialDirection: Direction? = null
    private var branchStartPosition: BlockPos? = null
    private var nearestChest: BlockPos? = null
    private var currentBranchEndPos: BlockPos? = null
    private var currentBranchBlocks: MutableList<BlockPos> = mutableListOf()
    private var currentMiningGroup: MutableList<BlockPos> = mutableListOf() // 現在採掘中のグループ
    private var miningGroups: MutableList<MutableList<BlockPos>> = mutableListOf() // 採掘グループのリスト
    private var currentGroupIndex: Int = 0
    private var scannedOres: MutableList<BlockPos> = mutableListOf() // スキャンした全鉱石
    private var currentOreIndex: Int = 0
    private var tickCounter = 0
    private var branchesCompleted = 0
    private var chestOperationTicks = 0
    private var itemCollectionTicks = 0
    private var lastMiningCenter: BlockPos? = null // 最後に採掘した場所の中心

    override fun enabled() {
        currentState = MiningState.Initializing
        tickCounter = 0
        branchesCompleted = 0
        currentGroupIndex = 0
        currentOreIndex = 0
        chestOperationTicks = 0
        itemCollectionTicks = 0
        lastMiningCenter = null
        clearState()
    }

    override fun disabled() {
        // 全てのアクションをクリア
        AiInterface.actions.clear()
        currentState = MiningState.Idle
        clearState()
    }

    private fun clearState() {
        initialPosition = null
        initialDirection = null
        branchStartPosition = null
        nearestChest = null
        currentBranchEndPos = null
        currentBranchBlocks.clear()
        currentMiningGroup.clear()
        miningGroups.clear()
        scannedOres.clear()
    }

    override fun tick() {
        // Idle状態の場合は何もしない
        if (currentState == MiningState.Idle) return

        tickCounter++

        // インベントリチェック（定期的に）
        if (tickCounter % checkInventoryInterval.value == 0) {
            if (shouldReturnToChest()) {
                if (currentState != MiningState.ReturningToChest &&
                    currentState != MiningState.StoringItems
                ) {
                    currentState = MiningState.ReturningToChest
                    AiInterface.actions.clear()
                }
            }
        }

        when (currentState) {
            MiningState.Initializing -> handleInitializing()
            MiningState.Scanning -> handleScanning()
            MiningState.ApproachingMining -> handleApproachingMining()
            MiningState.MiningBranch -> handleMiningBranch()
            MiningState.CollectingItems -> handleCollectingItems()
            MiningState.ScanningWalls -> handleScanningWalls()
            MiningState.MovingToOre -> handleMovingToOre()
            MiningState.MiningOre -> handleMiningOre()
            MiningState.CollectingOreItems -> handleCollectingOreItems()
            MiningState.MovingToNextBranch -> handleMovingToNextBranch()
            MiningState.ApproachingNextBranch -> handleApproachingNextBranch()
            MiningState.MiningNextBranchPath -> handleMiningNextBranchPath()
            MiningState.CollectingPathItems -> handleCollectingPathItems()
            MiningState.ReturningToChest -> handleReturningToChest()
            MiningState.StoringItems -> handleStoringItems()
            MiningState.Error -> handleError()
            MiningState.Idle -> {} // 何もしない
        }
    }

    private fun handleInitializing() {
        val pos = player?.blockPos ?: return
        val yaw = player?.yaw ?: return

        // 初期位置を記録
        if (initialPosition == null) {
            initialPosition = pos
            initialDirection = getDirectionFromYaw(yaw)

            // チェストを検索
            nearestChest = findNearestChest(pos, chestSearchRadius.value)

            // ブランチ開始点を決定（現在の位置から1ブロック前方）
            branchStartPosition = pos.offset(initialDirection!!)

            currentState = MiningState.Scanning
        }
    }

    private fun handleScanning() {
        val startPos = branchStartPosition ?: return
        val direction = initialDirection ?: return
        val currentWorld = world ?: return

        // ブランチをスキャン
        val blocksToMine = mutableListOf<BlockPos>()
        val maxLength = branchLength.value
        var scanDistance = 0

        for (distance in 1..maxLength) {
            val checkPos1 = startPos.offset(direction, distance)
            val checkPos2 = checkPos1.up()

            val state1 = currentWorld.getBlockState(checkPos1)
            val state2 = currentWorld.getBlockState(checkPos2)

            // 次のブロックをチェック（1歩先）
            val nextPos1 = startPos.offset(direction, distance + 1)
            val nextPos2 = nextPos1.up()
            val nextState1 = currentWorld.getBlockState(nextPos1)
            val nextState2 = currentWorld.getBlockState(nextPos2)

            // 次が水・溶岩の場合、現在のブロックで停止
            if (nextState1?.block == Blocks.WATER || nextState1?.block == Blocks.LAVA ||
                nextState2?.block == Blocks.WATER || nextState2?.block == Blocks.LAVA
            ) {
                // 現在のブロックまで掘って終了
                if (state1?.isAir == false) blocksToMine.add(checkPos1)
                if (state2?.isAir == false) blocksToMine.add(checkPos2)
                scanDistance = distance
                break
            }

            // 次が洞窟（64ブロック以上の空気）の場合も停止
            if (nextState1?.isAir == true || nextState2?.isAir == true) {
                val airCount = countConnectedAir(nextPos1, detectSpaceThreshold.value)
                if (airCount >= detectSpaceThreshold.value) {
                    // 現在のブロックまで掘って終了
                    if (state1?.isAir == false) blocksToMine.add(checkPos1)
                    if (state2?.isAir == false) blocksToMine.add(checkPos2)
                    scanDistance = distance
                    break
                }
            }

            // 掘るべきブロックを追加
            if (state1?.isAir == false) {
                blocksToMine.add(checkPos1)
            }
            if (state2?.isAir == false) {
                blocksToMine.add(checkPos2)
            }

            scanDistance = distance
        }

        // スキャン結果を保存
        currentBranchBlocks = blocksToMine
        currentBranchEndPos = startPos.offset(direction, scanDistance)

        // ブロックをグループ化（近い位置ごと）
        miningGroups.clear()
        groupBlocksByProximity(blocksToMine, miningGroups, miningApproachDistance.value)
        currentGroupIndex = 0

        currentState =
            if (miningGroups.isNotEmpty()) {
                MiningState.ApproachingMining
            } else {
                // 掘るブロックがない場合、壁面スキャンへ
                MiningState.ScanningWalls
            }
    }

    private fun handleApproachingMining() {
        if (AiInterface.actions.isNotEmpty()) return

        if (currentGroupIndex >= miningGroups.size) {
            // 全グループの採掘完了
            currentState = MiningState.ScanningWalls
            return
        }

        val group = miningGroups[currentGroupIndex]
        if (group.isEmpty()) {
            currentGroupIndex++
            return
        }

        // グループの中心位置を計算
        val centerPos = calculateCenter(group)
        val direction = initialDirection ?: return

        // 接近位置を計算（採掘グループから少し離れた位置）
        val approachPos = centerPos.offset(direction.opposite, miningApproachDistance.value)

        // 接近位置に移動
        AiInterface.add(
            PathMovementAction(
                x = approachPos.x,
                y = approachPos.y,
                z = approachPos.z,
                radius = 1,
                stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                onSuccessAction = {
                    currentMiningGroup = group
                    currentState = MiningState.MiningBranch
                },
                onFailureAction = {
                    // 移動失敗した場合、より近い位置から採掘を試みる
                    currentMiningGroup = group
                    currentState = MiningState.MiningBranch
                },
            ),
        )
    }

    private fun handleMiningBranch() {
        if (AiInterface.actions.isNotEmpty()) return

        if (currentMiningGroup.isEmpty()) {
            currentGroupIndex++
            currentState = MiningState.ApproachingMining
            return
        }

        // 採掘の中心位置を記録
        lastMiningCenter = calculateCenter(currentMiningGroup)

        // MineBlockActionを追加
        AiInterface.add(
            MineBlockAction(
                blockPosList = currentMiningGroup,
                stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                onSuccessAction = {
                    itemCollectionTicks = 0
                    currentState = MiningState.CollectingItems
                },
                onFailureAction = {
                    currentState = MiningState.Error
                },
            ),
        )
    }

    private fun handleCollectingItems() {
        itemCollectionTicks++

        if (itemCollectionTicks >= itemCollectionWaitTicks.value) {
            // アイテム回収完了、次のグループへ
            val center =
                lastMiningCenter ?: run {
                    currentGroupIndex++
                    currentState = MiningState.ApproachingMining
                    return
                }

            // アイテムを回収
            collectNearbyItems(center, itemCollectionRadius.value)

            currentGroupIndex++
            currentState = MiningState.ApproachingMining
        }
    }

    private fun handleScanningWalls() {
        // ブランチ全体の壁面をスキャンして鉱石を抽出
        val startPos = branchStartPosition ?: return
        val endPos = currentBranchEndPos ?: return
        val direction = initialDirection ?: return
        val currentWorld = world ?: return

        scannedOres.clear()
        val allOrePositions = mutableSetOf<BlockPos>()

        // ブランチの各位置で壁面をスキャン
        val distance = calculateDistance(startPos, endPos, direction)

        for (i in 0..distance) {
            val centerPos = startPos.offset(direction, i)

            // 周囲6方向をチェック
            for (checkDirection in Direction.entries) {
                val checkPos = centerPos.offset(checkDirection)
                if (checkPos !in allOrePositions) {
                    val state = currentWorld.getBlockState(checkPos)
                    if (isOreBlock(state?.block)) {
                        // 連結している鉱石を全て追加
                        val connectedOres = mutableListOf<BlockPos>()
                        findConnectedOres(checkPos, connectedOres, allOrePositions)
                        scannedOres.addAll(connectedOres)
                    }
                }
            }

            // 上のブロックも同様にチェック
            val upperPos = centerPos.up()
            for (checkDirection in Direction.entries) {
                val checkPos = upperPos.offset(checkDirection)
                if (checkPos !in allOrePositions) {
                    val state = currentWorld.getBlockState(checkPos)
                    if (isOreBlock(state?.block)) {
                        val connectedOres = mutableListOf<BlockPos>()
                        findConnectedOres(checkPos, connectedOres, allOrePositions)
                        scannedOres.addAll(connectedOres)
                    }
                }
            }
        }

        // 鉱石採掘の準備
        currentOreIndex = 0
        currentState =
            if (scannedOres.isNotEmpty()) {
                MiningState.MovingToOre
            } else {
                // 鉱石がない場合、次のブランチへ
                MiningState.MovingToNextBranch
            }
    }

    private fun handleMovingToOre() {
        if (AiInterface.actions.isNotEmpty()) return

        if (currentOreIndex >= scannedOres.size) {
            // 全鉱石採掘完了
            currentState = MiningState.MovingToNextBranch
            return
        }

        val orePos = scannedOres[currentOreIndex]
        val direction = initialDirection ?: return

        // 鉱石の近くまで移動（2ブロック手前）
        val approachPos = orePos.offset(direction.opposite, 2)

        AiInterface.add(
            PathMovementAction(
                x = approachPos.x,
                y = approachPos.y,
                z = approachPos.z,
                radius = 2,
                stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                onSuccessAction = {
                    currentState = MiningState.MiningOre
                },
                onFailureAction = {
                    // 移動失敗しても採掘を試みる
                    currentState = MiningState.MiningOre
                },
            ),
        )
    }

    private fun handleMiningOre() {
        if (AiInterface.actions.isNotEmpty()) return

        if (currentOreIndex >= scannedOres.size) {
            currentState = MiningState.MovingToNextBranch
            return
        }

        val orePos = scannedOres[currentOreIndex]
        lastMiningCenter = orePos

        // 単一の鉱石を採掘
        AiInterface.add(
            MineBlockAction(
                blockPosList = mutableListOf(orePos),
                stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                onSuccessAction = {
                    itemCollectionTicks = 0
                    currentState = MiningState.CollectingOreItems
                },
                onFailureAction = {
                    currentOreIndex++
                    currentState = MiningState.MovingToOre
                },
            ),
        )
    }

    private fun handleCollectingOreItems() {
        itemCollectionTicks++

        if (itemCollectionTicks >= itemCollectionWaitTicks.value) {
            val center =
                lastMiningCenter ?: run {
                    currentOreIndex++
                    currentState = MiningState.MovingToOre
                    return
                }

            // アイテムを回収
            collectNearbyItems(center, itemCollectionRadius.value)

            // 採掘後、新たに露出した鉱石をチェック
            checkNewlyExposedOres(center)

            currentOreIndex++
            currentState = MiningState.MovingToOre
        }
    }

    private fun handleMovingToNextBranch() {
        val startPos = branchStartPosition ?: return
        val direction = initialDirection ?: return

        if (AiInterface.actions.isEmpty()) {
            // 左方向に移動（ブランチ間隔+1）
            val leftDirection = direction.rotateYCounterclockwise()
            val moveDistance = branchInterval.value + 1
            val nextStartPos = startPos.offset(leftDirection, moveDistance)

            // まず開始点に戻る
            AiInterface.add(
                PathMovementAction(
                    x = startPos.x,
                    y = startPos.y,
                    z = startPos.z,
                    radius = 1,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = {
                        branchStartPosition = nextStartPos
                        currentState = MiningState.ApproachingNextBranch
                    },
                    onFailureAction = {
                        currentState = MiningState.Error
                    },
                ),
            )
        }
    }

    private fun handleApproachingNextBranch() {
        val startPos = branchStartPosition ?: return
        val direction = initialDirection ?: return
        val leftDirection = direction.rotateYCounterclockwise()

        if (AiInterface.actions.isEmpty()) {
            // 次のブランチの直前まで移動（1ブロック手前）
            val approachPos = startPos.offset(leftDirection.opposite, 1)

            AiInterface.add(
                PathMovementAction(
                    x = approachPos.x,
                    y = approachPos.y,
                    z = approachPos.z,
                    radius = 1,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = {
                        currentState = MiningState.MiningNextBranchPath
                    },
                    onFailureAction = {
                        // 移動失敗してもそのまま掘る
                        currentState = MiningState.MiningNextBranchPath
                    },
                ),
            )
        }
    }

    private fun handleMiningNextBranchPath() {
        val currentStartPos = branchStartPosition ?: return
        val oldStartPos = currentStartPos.offset(initialDirection!!.rotateYClockwise(), branchInterval.value + 1)
        val direction = initialDirection ?: return
        val leftDirection = direction.rotateYCounterclockwise()
        val moveDistance = branchInterval.value + 1

        if (AiInterface.actions.isEmpty()) {
            // 移動先のブロックをスキャン
            val blocksToMine = mutableListOf<BlockPos>()
            for (i in 1..moveDistance) {
                val checkPos = oldStartPos.offset(leftDirection, i)
                val checkPosUp = checkPos.up()

                val state1 = world?.getBlockState(checkPos)
                val state2 = world?.getBlockState(checkPosUp)

                if (state1?.isAir == false) {
                    blocksToMine.add(checkPos)
                }
                if (state2?.isAir == false) {
                    blocksToMine.add(checkPosUp)
                }
            }

            // 必要なら掘る
            if (blocksToMine.isNotEmpty()) {
                lastMiningCenter = calculateCenter(blocksToMine)
                AiInterface.add(
                    MineBlockAction(
                        blockPosList = blocksToMine,
                        stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                        onSuccessAction = {
                            itemCollectionTicks = 0
                            currentState = MiningState.CollectingPathItems
                        },
                        onFailureAction = {
                            currentState = MiningState.Error
                        },
                    ),
                )
            } else {
                // 掘る必要がない場合、直接次のスキャンへ
                branchesCompleted++
                currentState = MiningState.Scanning
            }
        }
    }

    private fun handleCollectingPathItems() {
        itemCollectionTicks++

        if (itemCollectionTicks >= itemCollectionWaitTicks.value) {
            val center =
                lastMiningCenter ?: run {
                    branchesCompleted++
                    currentState = MiningState.Scanning
                    return
                }

            // アイテムを回収
            collectNearbyItems(center, itemCollectionRadius.value)

            branchesCompleted++
            currentState = MiningState.Scanning
        }
    }

    private fun handleReturningToChest() {
        val chest =
            nearestChest ?: run {
                // チェストが見つからない場合、エラー状態へ
                currentState = MiningState.Error
                return
            }

        if (AiInterface.actions.isEmpty()) {
            AiInterface.add(
                PathMovementAction(
                    x = chest.x,
                    y = chest.y,
                    z = chest.z,
                    radius = 2,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = {
                        chestOperationTicks = 0
                        currentState = MiningState.StoringItems
                    },
                    onFailureAction = {
                        currentState = MiningState.Error
                    },
                ),
            )
        }
    }

    private fun handleStoringItems() {
        val chest = nearestChest ?: return

        chestOperationTicks++

        when (chestOperationTicks) {
            1 -> {
                // チェストを開く
                ChestManager.openChest(chest)
            }
            10 -> {
                // 10tick後にアイテムを格納
                ChestManager.storeMinedItems()
            }
            20 -> {
                // さらに10tick後にチェストを閉じる
                ChestManager.closeChest()
            }
            30 -> {
                // ブランチ開始位置に戻る
                val startPos = branchStartPosition ?: initialPosition ?: return
                AiInterface.add(
                    PathMovementAction(
                        x = startPos.x,
                        y = startPos.y,
                        z = startPos.z,
                        radius = 1,
                        stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                        onSuccessAction = {
                            currentState = MiningState.Scanning
                        },
                        onFailureAction = {
                            currentState = MiningState.Error
                        },
                    ),
                )
            }
        }
    }

    private fun handleError() {
        val initPos = initialPosition ?: return

        if (AiInterface.actions.isEmpty()) {
            AiInterface.add(
                PathMovementAction(
                    x = initPos.x,
                    y = initPos.y,
                    z = initPos.z,
                    radius = 2,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = {
                        disable()
                    },
                    onFailureAction = {
                        // エラーから復帰できない場合は強制無効化
                        disable()
                    },
                ),
            )
        }
    }

    // ユーティリティ関数

    private fun getDirectionFromYaw(yaw: Float): Direction = Direction.fromHorizontalDegrees(yaw.toDouble())

    private fun findNearestChest(
        center: BlockPos,
        radius: Int,
    ): BlockPos? {
        val currentWorld = world ?: return null
        var nearest: BlockPos? = null
        var minDistance = Double.MAX_VALUE

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val checkPos = center.add(x, y, z)
                    val state = currentWorld.getBlockState(checkPos)

                    if (state?.block == Blocks.CHEST || state?.block == Blocks.TRAPPED_CHEST) {
                        val distance = center.getSquaredDistance(checkPos)
                        if (distance < minDistance) {
                            minDistance = distance
                            nearest = checkPos
                        }
                    }
                }
            }
        }

        return nearest
    }

    private fun countConnectedAir(
        startPos: BlockPos,
        maxCount: Int,
    ): Int {
        val currentWorld = world ?: return 0
        val visited = mutableSetOf<BlockPos>()
        val queue = ArrayDeque<BlockPos>()

        queue.add(startPos)
        visited.add(startPos)

        while (queue.isNotEmpty() && visited.size < maxCount) {
            val current = queue.removeFirst()

            for (direction in Direction.entries) {
                val neighbor = current.offset(direction)
                if (neighbor !in visited) {
                    val state = currentWorld.getBlockState(neighbor)
                    if (state?.isAir == true) {
                        visited.add(neighbor)
                        queue.add(neighbor)

                        if (visited.size >= maxCount) {
                            return visited.size
                        }
                    }
                }
            }
        }

        return visited.size
    }

    private fun groupBlocksByProximity(
        blocks: List<BlockPos>,
        groups: MutableList<MutableList<BlockPos>>,
        maxDistance: Int,
    ) {
        if (blocks.isEmpty()) return

        val sorted = blocks.sortedBy { it.x + it.y * 1000 + it.z * 1000000 }
        var currentGroup = mutableListOf<BlockPos>()
        var lastPos: BlockPos? = null

        for (pos in sorted) {
            if (lastPos == null) {
                currentGroup.add(pos)
                lastPos = pos
            } else {
                val distance = sqrt(lastPos.getSquaredDistance(pos))
                if (distance <= maxDistance) {
                    currentGroup.add(pos)
                } else {
                    groups.add(currentGroup)
                    currentGroup = mutableListOf(pos)
                }
                lastPos = pos
            }
        }

        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup)
        }
    }

    private fun calculateCenter(positions: List<BlockPos>): BlockPos {
        if (positions.isEmpty()) return BlockPos.ORIGIN

        val avgX = positions.map { it.x }.average().toInt()
        val avgY = positions.map { it.y }.average().toInt()
        val avgZ = positions.map { it.z }.average().toInt()

        return BlockPos(avgX, avgY, avgZ)
    }

    private fun calculateDistance(
        start: BlockPos,
        end: BlockPos,
        direction: Direction,
    ): Int =
        when (direction) {
            Direction.NORTH -> abs(start.z - end.z)
            Direction.SOUTH -> abs(start.z - end.z)
            Direction.EAST -> abs(start.x - end.x)
            Direction.WEST -> abs(start.x - end.x)
            else -> 0
        }

    private fun isOreBlock(block: net.minecraft.block.Block?): Boolean {
        if (block == null) return false

        val oreBlocks =
            setOf(
                Blocks.COAL_ORE,
                Blocks.DEEPSLATE_COAL_ORE,
                Blocks.IRON_ORE,
                Blocks.DEEPSLATE_IRON_ORE,
                Blocks.COPPER_ORE,
                Blocks.DEEPSLATE_COPPER_ORE,
                Blocks.GOLD_ORE,
                Blocks.DEEPSLATE_GOLD_ORE,
                Blocks.DIAMOND_ORE,
                Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.EMERALD_ORE,
                Blocks.DEEPSLATE_EMERALD_ORE,
                Blocks.LAPIS_ORE,
                Blocks.DEEPSLATE_LAPIS_ORE,
                Blocks.REDSTONE_ORE,
                Blocks.DEEPSLATE_REDSTONE_ORE,
                Blocks.NETHER_QUARTZ_ORE,
                Blocks.NETHER_GOLD_ORE,
                Blocks.ANCIENT_DEBRIS,
            )

        return block in oreBlocks
    }

    private fun findConnectedOres(
        startPos: BlockPos,
        oreList: MutableList<BlockPos>,
        globalVisited: MutableSet<BlockPos>,
    ) {
        val currentWorld = world ?: return
        val visited = mutableSetOf<BlockPos>()
        val queue = ArrayDeque<BlockPos>()

        queue.add(startPos)
        visited.add(startPos)
        globalVisited.add(startPos)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            oreList.add(current)

            for (direction in Direction.entries) {
                val neighbor = current.offset(direction)
                if (neighbor !in visited && neighbor !in globalVisited) {
                    val state = currentWorld.getBlockState(neighbor)
                    if (isOreBlock(state?.block)) {
                        visited.add(neighbor)
                        globalVisited.add(neighbor)
                        queue.add(neighbor)
                    }
                }
            }
        }
    }

    private fun checkNewlyExposedOres(minedPos: BlockPos) {
        val currentWorld = world ?: return

        // 採掘したブロックの周囲6方向をチェック
        for (direction in Direction.entries) {
            val checkPos = minedPos.offset(direction)
            if (checkPos !in scannedOres) {
                val state = currentWorld.getBlockState(checkPos)
                if (isOreBlock(state?.block)) {
                    // 新たに露出した鉱石を追加
                    val connectedOres = mutableListOf<BlockPos>()
                    val globalVisited = scannedOres.toMutableSet()
                    findConnectedOres(checkPos, connectedOres, globalVisited)
                    scannedOres.addAll(connectedOres)
                }
            }
        }
    }

    private fun shouldReturnToChest(): Boolean {
        if (nearestChest == null) return false
        return ChestManager.getEmptySlotCount() < minEmptySlots.value
    }

    private fun collectNearbyItems(
        center: BlockPos,
        radius: Int,
    ) {
        val currentWorld = world ?: return
        val currentPlayer = player ?: return

        // 周囲のアイテムエンティティを検索
        val box =
            net.minecraft.util.math.Box(
                center.x - radius.toDouble(),
                center.y - radius.toDouble(),
                center.z - radius.toDouble(),
                center.x + radius.toDouble(),
                center.y + radius.toDouble(),
                center.z + radius.toDouble(),
            )

        val itemEntities =
            currentWorld.getEntitiesByClass(
                net.minecraft.entity.ItemEntity::class.java,
                box,
            ) { true }

        // 各アイテムに向かって移動（プレイヤーが近づくと自動で回収される）
        for (itemEntity in itemEntities) {
            val itemPos = itemEntity.blockPos
            val distance = currentPlayer.blockPos.getSquaredDistance(itemPos)

            if (distance > 1.0) {
                // 少し離れている場合、近づく
                val pathAction =
                    PathMovementAction(
                        x = itemPos.x,
                        y = itemPos.y,
                        z = itemPos.z,
                        stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                        radius = 0,
                        onSuccessAction = {},
                        onFailureAction = {},
                    )
                AiInterface.add(pathAction)
            }
        }
    }
}
