package org.infinite.features.automatic.branch

import net.minecraft.block.Blocks
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.libs.ai.AiInterface
import org.infinite.libs.ai.actions.block.MineBlockAction
import org.infinite.libs.ai.actions.movement.PathMovementAction
import org.infinite.libs.ai.interfaces.AiAction
import org.infinite.libs.client.inventory.ContainerManager
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.settings.FeatureSetting
import kotlin.math.abs

private val Direction.stepX: Int
    get() =
        when (this) {
            Direction.WEST -> 1
            Direction.EAST -> -1
            else -> 0
        }
private val Direction.stepY: Int
    get() =
        when (this) {
            Direction.UP -> 1
            Direction.DOWN -> -1
            else -> 0
        }
private val Direction.stepZ: Int
    get() =
        when (this) {
            Direction.NORTH -> -1
            Direction.SOUTH -> 1
            else -> 0
        }

class BranchMiner : ConfigurableFeature() {
    enum class State {
        Initialize,
        Scan,
        Branch,
        Mining,
        Check,
        Next,
        Idle,
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
    val minEmptySlots =
        FeatureSetting.IntSetting(
            name = "MinEmptySlots",
            defaultValue = 5,
            min = 1,
            max = 27,
        )
    val detectSpaceThreshold =
        FeatureSetting.IntSetting(
            name = "DetectSpaceThreshold",
            defaultValue = 64,
            min = 32,
            max = 128,
        )
    val chestSearchRadius =
        FeatureSetting.IntSetting(
            name = "ChestSearchRadius",
            defaultValue = 16,
            min = 8,
            max = 64,
        )
    val itemCollectionWaitTicks =
        FeatureSetting.IntSetting(
            name = "ItemCollectionWaitTicks",
            defaultValue = 40,
            min = 20,
            max = 100,
        )
    val enableTorchPlacement =
        FeatureSetting.BooleanSetting(
            name = "EnableTorchPlacement",
            defaultValue = false,
        )
    val torchInterval =
        FeatureSetting.IntSetting(
            name = "TorchInterval",
            defaultValue = 8,
            min = 4,
            max = 16,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            branchLength,
            branchInterval,
            minEmptySlots,
            detectSpaceThreshold,
            chestSearchRadius,
            itemCollectionWaitTicks,
            enableTorchPlacement,
            torchInterval,
        )

    // 状態変数
    private var state: State = State.Idle
    private var initialPosition: BlockPos? = null
    private var initialDirection: Direction? = null
    private var branchStartPosition: BlockPos? = null
    private var branchEndPosition: BlockPos? = null
    private var nearestChest: BlockPos? = null
    private var branchBlocksToMine: MutableList<BlockPos> = mutableListOf()
    private var exposedOres: MutableList<BlockPos> = mutableListOf()
    private var currentOreIndex = 0
    private var collectedItems: MutableMap<String, Int> = mutableMapOf()
    private var waitTicks = 0

    override fun enabled() {
        state = State.Initialize
        clearState()
    }

    override fun disabled() {
        AiInterface.actions.clear()
        clearState()
        state = State.Idle
    }

    private fun clearState() {
        initialPosition = null
        initialDirection = null
        branchStartPosition = null
        branchEndPosition = null
        nearestChest = null
        branchBlocksToMine.clear()
        exposedOres.clear()
        currentOreIndex = 0
        collectedItems.clear()
        waitTicks = 0
    }

    override fun tick() {
        if (state == State.Idle) return

        // 安全チェック
        if (!safetyCheck()) {
            handleEmergency()
            return
        }

        when (state) {
            State.Initialize -> handleInitialize()
            State.Scan -> handleScan()
            State.Branch -> handleBranch()
            State.Mining -> handleMining()
            State.Check -> handleCheck()
            State.Next -> handleNext()
            State.Idle -> {}
        }
    }

    private fun handleInitialize() {
        val pos = player?.blockPos ?: return
        val yaw = player?.yaw ?: return

        // 初期情報を記録
        initialPosition = pos
        initialDirection = Direction.fromHorizontalDegrees(yaw.toDouble())
        branchStartPosition = pos.offset(initialDirection!!)

        // チェストを検索
        nearestChest = findNearestChest(pos, chestSearchRadius.value)

        InfiniteClient.log(Text.literal("§a[BranchMiner] Initialized at ${pos.toShortString()}"))
        if (nearestChest != null) {
            InfiniteClient.log(Text.literal("§a[BranchMiner] Chest found at ${nearestChest?.toShortString()}"))
        }

        state = State.Scan
    }

    private fun handleScan() {
        val startPos = branchStartPosition ?: return
        val direction = initialDirection ?: return
        val currentWorld = world ?: return

        branchBlocksToMine.clear()
        var scanDistance = 0
        val maxLength = branchLength.value
        val opposite = direction.opposite

        // ブランチをスキャン
        for (distance in 1..maxLength) {
            val checkPos1 = startPos.offset(direction, distance)
            val checkPos2 = checkPos1.up()

            // 現在のブロック状態
            val state1 = currentWorld.getBlockState(checkPos1)
            val state2 = currentWorld.getBlockState(checkPos2)

            // 隣接ブロックをチェック（水・溶岩・洞窟検出）、背面を除外
            var shouldStop = false
            for (dir in Direction.entries) {
                if (dir == opposite) continue

                val adjacentLower = checkPos1.offset(dir)
                val adjacentUpper = checkPos2.offset(dir)
                val adjacentStateLower = currentWorld.getBlockState(adjacentLower)
                val adjacentStateUpper = currentWorld.getBlockState(adjacentUpper)

                // 水・溶岩チェック
                if (adjacentStateLower?.block == Blocks.WATER || adjacentStateLower?.block == Blocks.LAVA ||
                    adjacentStateUpper?.block == Blocks.WATER || adjacentStateUpper?.block == Blocks.LAVA
                ) {
                    shouldStop = true
                    break
                }

                // 洞窟チェック
                if (adjacentStateLower?.isAir == true) {
                    val airCount = countConnectedAir(adjacentLower, detectSpaceThreshold.value)
                    if (airCount >= detectSpaceThreshold.value) {
                        shouldStop = true
                        break
                    }
                }
                if (adjacentStateUpper?.isAir == true) {
                    val airCount = countConnectedAir(adjacentUpper, detectSpaceThreshold.value)
                    if (airCount >= detectSpaceThreshold.value) {
                        shouldStop = true
                        break
                    }
                }
            }

            if (shouldStop) {
                scanDistance = distance - 1
                break
            }

            // 掘るブロックを追加
            if (state1?.isAir == false) {
                branchBlocksToMine.add(checkPos1)
            }
            if (state2?.isAir == false) {
                branchBlocksToMine.add(checkPos2)
            }

            scanDistance = distance
        }

        branchEndPosition = startPos.offset(direction, scanDistance)
        InfiniteClient.log(
            Text.literal("§a[BranchMiner] Scanned branch: ${branchBlocksToMine.size} blocks, distance: $scanDistance"),
        )

        if (scanDistance == 0) {
            InfiniteClient.log(Text.literal("§c[BranchMiner] Unable to find safe path for branch. Disabling..."))
            disable()
            return
        }

        state = State.Branch
    }

    private fun handleBranch() {
        if (AiInterface.actions.isNotEmpty()) return

        if (branchBlocksToMine.isEmpty()) {
            // 採掘完了、終端へ移動
            moveToBranchEnd()
            return
        }

        // MineBlockActionを追加
        AiInterface.add(
            MineBlockAction(
                blockPosList = branchBlocksToMine,
                stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                onSuccessAction = {
                    InfiniteClient.log(Text.literal("§a[BranchMiner] Branch mining completed"))
                    moveToBranchEnd()
                },
                onFailureAction = {
                    InfiniteClient.log(Text.literal("§c[BranchMiner] Branch mining failed"))
                    handleEmergency()
                },
            ),
        )

        // TODO: 松明設置ロジック（enableTorchPlacementがtrueの場合）
        // torchIntervalごとに松明を設置
    }

    private fun moveToBranchEnd() {
        val endPos = branchEndPosition ?: return

        AiInterface.add(
            PathMovementAction(
                x = endPos.x,
                y = endPos.y,
                z = endPos.z,
                radius = null,
                stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                onSuccessAction = {
                    state = State.Mining
                },
                onFailureAction = {
                    state = State.Mining // 移動失敗しても採掘を試みる
                },
            ),
        )
    }

    private fun handleMining() {
        if (AiInterface.actions.isNotEmpty()) return

        // 初回のみ壁面スキャン
        if (exposedOres.isEmpty() && currentOreIndex == 0) {
            scanWallsForOres()
            if (exposedOres.isEmpty()) {
                InfiniteClient.log(Text.literal("§a[BranchMiner] No ores found"))
                state = State.Check
                return
            }

            // ブランチ終端から開始点へ向かう順にソート
            sortOresByDistance()
            InfiniteClient.log(Text.literal("§a[BranchMiner] Found ${exposedOres.size} ore blocks"))
        }

        // 全ての鉱石を採掘完了
        if (currentOreIndex >= exposedOres.size) {
            // 最終的なアイテム回収（残り物）
            collectAllItems()
            state = State.Check
            return
        }

        // 現在の鉱石を採掘
        val orePos = exposedOres[currentOreIndex]
        AiInterface.add(
            PathMovementAction(
                x = orePos.x,
                y = orePos.y,
                z = orePos.z,
                radius = null,
                stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                onSuccessAction = {
                    mineCurrentOre()
                },
                onFailureAction = {
                    mineCurrentOre() // 移動失敗しても採掘を試みる
                },
            ),
        )
    }

    private fun mineCurrentOre() {
        val orePos = exposedOres[currentOreIndex]

        AiInterface.add(
            MineBlockAction(
                blockPosList = mutableListOf(orePos),
                stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                onSuccessAction = {
                    // 新たに露出した鉱石をチェック
                    checkNewlyExposedOres(orePos)
                    // 掘った直後に近くのアイテムを回収
                    collectNearbyItems(orePos)
                    currentOreIndex++
                },
                onFailureAction = {
                    currentOreIndex++
                },
            ),
        )
    }

    private fun collectNearbyItems(centerPos: BlockPos) {
        val currentWorld = world ?: return

        val box =
            net.minecraft.util.math.Box(
                centerPos.x - 3.0,
                centerPos.y - 3.0,
                centerPos.z - 3.0,
                centerPos.x + 3.0,
                centerPos.y + 3.0,
                centerPos.z + 3.0,
            )

        val itemEntities =
            currentWorld.getEntitiesByClass(
                net.minecraft.entity.ItemEntity::class.java,
                box,
            ) { true }

        for (itemEntity in itemEntities) {
            val itemPos = itemEntity.blockPos
            AiInterface.add(
                PathMovementAction(
                    x = itemPos.x,
                    y = itemPos.y,
                    z = itemPos.z,
                    radius = null,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = {},
                    onFailureAction = {},
                ),
            )
        }
    }

    private fun handleCheck() {
        if (AiInterface.actions.isNotEmpty()) return

        // インベントリ内のアイテムを集計
        countInventoryItems()
        reportCollectedItems()

        // インベントリ容量チェック
        if (InventoryManager.emptySlots < minEmptySlots.value && nearestChest != null) {
            storeItemsInChest()
        } else {
            state = State.Next
        }
    }

    private fun storeItemsInChest() {
        val chest =
            nearestChest ?: run {
                state = State.Next
                return
            }

        AiInterface.add(
            PathMovementAction(
                x = chest.x,
                y = chest.y,
                z = chest.z,
                radius = null,
                stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                onSuccessAction = {
                    waitTicks = 0
                    performChestStorage()
                },
                onFailureAction = {
                    InfiniteClient.log(Text.literal("§c[BranchMiner] Failed to reach chest"))
                    state = State.Next
                },
            ),
        )
    }

    private fun performChestStorage() {
        waitTicks++
        when {
            waitTicks == 1 -> {
                // nearestChestがBlockPosであると仮定
                ContainerManager.open(nearestChest!!)
            }

            waitTicks == 10 -> storeMinedItems()
            waitTicks == 30 -> ContainerManager.close()
            waitTicks > 30 -> {
                InfiniteClient.log(Text.literal("§a[BranchMiner] Items stored in chest"))
                returnToBranchStart()
            }
        }
    }

    /**
     * プレイヤーのバックパック内のアイテムを現在開いているコンテナに格納します。
     * QUICK_MOVE (Shift+Click相当) を使用して、アイテムを自動でコンテナの空きスロットに移動させます。
     */
    private fun storeMinedItems() {
        val currentPlayer = player ?: return
        val interaction = interactionManager ?: return
        val screenHandler = currentPlayer.currentScreenHandler ?: return

        // 1. 現在開いているコンテナがアイテムを格納できる汎用タイプであることを確認
        val currentType = ContainerManager.containerType()

        // Inventory や None ではなく、Generic または ShulkerBox であることを確認（その他の特殊コンテナは除外）
        if (currentType != ContainerManager.ContainerType.ShulkerBox && currentType !is ContainerManager.ContainerType.Generic) {
            // 現在開いている画面が格納に適したコンテナではない
            InfiniteClient.log(Text.literal("§c[BranchMiner] Not a generic storage container."))
            return
        }

        val syncId = screenHandler.syncId
        val playerInvSize = 36 // ホットバー9スロット + バックパック27スロット
        val hotbarSize = 9

        // 2. プレイヤーのバックパック (内部スロット 9 から 35 / ネットワークスロット 9 から 35) をループ
        // ネットワークスロットIDは 9 (バックパックの最初のスロット) から 35 (最後のスロット)
        // プレイヤーのインベントリは ScreenHandler のスロットリストの後半に位置します。

        // コンテナスロットの数 (N) を取得。プレイヤーインベントリのスロットIDは N から始まります。
        val containerSlotCount =
            when (currentType) {
                is ContainerManager.ContainerType.Generic -> currentType.size
                is ContainerManager.ContainerType.ShulkerBox -> 27
                else -> 0 // 既に上でチェック済みだが念のため
            }

        // 格納対象のスロット範囲:
        // バックパックの最初 (ネットワークスロット 9) は、画面ハンドラー上では (N + 9) に位置します。
        // バックパックの最後 (ネットワークスロット 35) は、画面ハンドラー上では (N + 35) に位置します。

        val startSlot = containerSlotCount + hotbarSize // ホットバーの終わり + 1 (例: チェスト27の場合 27+9=36)
        val endSlot = containerSlotCount + playerInvSize // バックパックの終わり + 1 (例: チェスト27の場合 27+36=63)

        for (screenSlotId in startSlot until endSlot) {
            val stack = screenHandler.slots.getOrNull(screenSlotId)?.stack ?: continue

            // 空のスタックはスキップ
            if (stack.isEmpty) {
                continue
            }

            // 3. QUICK_MOVE (Shift+Click) 操作を実行
            // QUICK_MOVEは、クリックしたアイテムを自動的に反対側の空きスロットまたはスタック可能なスロットに移動させます。
            // ここでは、プレイヤーインベントリのスロットをクリックし、コンテナへ移動させます。
            interaction.clickSlot(
                syncId,
                screenSlotId, // クリックするのはプレイヤーインベントリ内のスロット
                0, // マウスボタン (左クリック)
                SlotActionType.QUICK_MOVE,
                currentPlayer,
            )
        }
    }

    private fun returnToBranchStart() {
        val startPos = branchStartPosition ?: return

        AiInterface.add(
            PathMovementAction(
                x = startPos.x,
                y = startPos.y,
                z = startPos.z,
                radius = null,
                stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                onSuccessAction = {
                    state = State.Next
                },
                onFailureAction = {
                    state = State.Next
                },
            ),
        )
    }

    private fun handleNext() {
        if (AiInterface.actions.isNotEmpty()) return

        val currentStartPos = branchStartPosition ?: return
        val mainStartPos = initialPosition?.offset(initialDirection!!) ?: return
        val direction = initialDirection ?: return
        val leftDirection = direction.rotateYCounterclockwise()

        // メイン通路の現在位置を計算（初期開始点からどれだけ左に移動したか）
        val currentOffset = calculateMainTunnelOffset(mainStartPos, currentStartPos, leftDirection)

        // 次のメイン通路位置（さらに左へ）
        val moveDistance = branchInterval.value + 1
        val nextMainPos = mainStartPos.offset(leftDirection, currentOffset + moveDistance)

        // メイン通路の現在位置から次の位置までの通路を掘る
        val pathBlocks = mutableListOf<BlockPos>()
        for (i in 1..moveDistance) {
            val checkPos = currentStartPos.offset(leftDirection, i)
            val checkPosUp = checkPos.up()

            val state1 = world?.getBlockState(checkPos)
            val state2 = world?.getBlockState(checkPosUp)

            if (state1?.isAir == false) {
                pathBlocks.add(checkPos)
            }
            if (state2?.isAir == false) {
                pathBlocks.add(checkPosUp)
            }
        }

        // 通路を掘る
        if (pathBlocks.isNotEmpty()) {
            AiInterface.add(
                MineBlockAction(
                    blockPosList = pathBlocks,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = {
                        // 次のブランチは、新しいメイン通路位置から前方へ
                        branchStartPosition = nextMainPos
                        exposedOres.clear()
                        currentOreIndex = 0
                        state = State.Scan
                    },
                    onFailureAction = {
                        handleEmergency()
                    },
                ),
            )
        } else {
            // 掘る必要がない場合、次のメイン通路位置へ移動
            AiInterface.add(
                PathMovementAction(
                    x = nextMainPos.x,
                    y = nextMainPos.y,
                    z = nextMainPos.z,
                    radius = null,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = {
                        branchStartPosition = nextMainPos
                        exposedOres.clear()
                        currentOreIndex = 0
                        state = State.Scan
                    },
                    onFailureAction = {
                        branchStartPosition = nextMainPos
                        exposedOres.clear()
                        currentOreIndex = 0
                        state = State.Scan
                    },
                ),
            )
        }
    }

    private fun calculateMainTunnelOffset(
        mainStart: BlockPos,
        currentPos: BlockPos,
        direction: Direction,
    ): Int {
        val dx = currentPos.x - mainStart.x
        val dy = currentPos.y - mainStart.y
        val dz = currentPos.z - mainStart.z
        return dx * direction.stepX + dy * direction.stepY + dz * direction.stepZ
    }

    private fun safetyCheck(): Boolean {
        val currentPlayer = player ?: return false

        // ダメージチェック
        if (currentPlayer.health < currentPlayer.maxHealth * 0.5f) {
            InfiniteClient.log(Text.literal("§c[BranchMiner] Low health detected!"))
            return false
        }

        // TODO: 周囲の水・溶岩チェック

        return true
    }

    private fun handleEmergency() {
        InfiniteClient.log(Text.literal("§c[BranchMiner] Emergency! Returning to initial position..."))
        val initPos =
            initialPosition ?: run {
                disable()
                return
            }

        AiInterface.add(
            PathMovementAction(
                x = initPos.x,
                y = initPos.y,
                z = initPos.z,
                radius = null,
                onSuccessAction = {
                    disable()
                },
                onFailureAction = {
                    disable()
                },
            ),
        )
    }

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

    private fun scanWallsForOres() {
        val startPos = branchStartPosition ?: return
        val endPos = branchEndPosition ?: return
        val direction = initialDirection ?: return
        val currentWorld = world ?: return

        exposedOres.clear()
        val allOrePositions = mutableSetOf<BlockPos>()
        val distance = calculateDistance(startPos, endPos, direction)

        for (i in 0..distance) {
            val centerPos = startPos.offset(direction, i)

            // 下のブロックの周囲をチェック
            for (checkDirection in Direction.entries) {
                val checkPos = centerPos.offset(checkDirection)
                if (checkPos !in allOrePositions) {
                    val state = currentWorld.getBlockState(checkPos)
                    if (isOreBlock(state?.block)) {
                        val connectedOres = mutableListOf<BlockPos>()
                        findConnectedOres(checkPos, connectedOres, allOrePositions)
                        exposedOres.addAll(connectedOres)
                    }
                }
            }

            // 上のブロックの周囲をチェック
            val upperPos = centerPos.up()
            for (checkDirection in Direction.entries) {
                val checkPos = upperPos.offset(checkDirection)
                if (checkPos !in allOrePositions) {
                    val state = currentWorld.getBlockState(checkPos)
                    if (isOreBlock(state?.block)) {
                        val connectedOres = mutableListOf<BlockPos>()
                        findConnectedOres(checkPos, connectedOres, allOrePositions)
                        exposedOres.addAll(connectedOres)
                    }
                }
            }
        }
    }

    private fun sortOresByDistance() {
        val endPos = branchEndPosition ?: return
        exposedOres.sortBy { it.getSquaredDistance(endPos) }
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

        for (direction in Direction.entries) {
            val checkPos = minedPos.offset(direction)
            if (checkPos !in exposedOres) {
                val state = currentWorld.getBlockState(checkPos)
                if (isOreBlock(state?.block)) {
                    val connectedOres = mutableListOf<BlockPos>()
                    val globalVisited = exposedOres.toMutableSet()
                    findConnectedOres(checkPos, connectedOres, globalVisited)
                    exposedOres.addAll(connectedOres)
                }
            }
        }
    }

    private fun calculateDistance(
        start: BlockPos,
        end: BlockPos,
        direction: Direction,
    ): Int =
        when (direction) {
            Direction.NORTH, Direction.SOUTH -> abs(start.z - end.z)
            Direction.EAST, Direction.WEST -> abs(start.x - end.x)
            else -> 0
        }

    private fun collectAllItems() {
        val startPos = branchStartPosition ?: return
        val endPos = branchEndPosition ?: return
        val currentWorld = world ?: return

        val box =
            net.minecraft.util.math.Box(
                minOf(startPos.x, endPos.x) - 5.0,
                minOf(startPos.y, endPos.y) - 5.0,
                minOf(startPos.z, endPos.z) - 5.0,
                maxOf(startPos.x, endPos.x) + 5.0,
                maxOf(startPos.y, endPos.y) + 5.0,
                maxOf(startPos.z, endPos.z) + 5.0,
            )

        val itemEntities =
            currentWorld.getEntitiesByClass(
                net.minecraft.entity.ItemEntity::class.java,
                box,
            ) { true }

        for (itemEntity in itemEntities) {
            val itemPos = itemEntity.blockPos
            AiInterface.add(
                PathMovementAction(
                    x = itemPos.x,
                    y = itemPos.y,
                    z = itemPos.z,
                    radius = null,
                    stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                    onSuccessAction = {},
                    onFailureAction = {},
                ),
            )
        }
    }

    private fun countInventoryItems() {
        collectedItems.clear()
        val playerInv = inventory ?: return

        for (i in 0 until playerInv.size()) {
            val stack = playerInv.getStack(i)
            if (!stack.isEmpty) {
                val itemName = stack.item.toString()
                collectedItems[itemName] = collectedItems.getOrDefault(itemName, 0) + stack.count
            }
        }
    }

    private fun reportCollectedItems() {
        InfiniteClient.log(Text.literal("§e[BranchMiner] === Collected Items ==="))
        for ((item, count) in collectedItems) {
            InfiniteClient.log(Text.literal("§e  - $item: $count"))
        }
        InfiniteClient.log(Text.literal("§e[BranchMiner] Empty slots: ${InventoryManager.emptySlots}"))
    }
}
