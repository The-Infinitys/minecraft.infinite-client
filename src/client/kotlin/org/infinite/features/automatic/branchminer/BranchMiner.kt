package org.infinite.features.automatic.branchminer

import net.minecraft.block.Blocks
import net.minecraft.entity.ItemEntity
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.movement.braek.VeinBreak
import org.infinite.libs.ai.AiInterface
import org.infinite.libs.ai.actions.movement.LinearMovementAction
import org.infinite.libs.ai.actions.movement.PathMovementAction
import org.infinite.libs.ai.interfaces.AiAction
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.settings.FeatureSetting
import org.infinite.utils.block.BlockUtils

// ConfigurableFeature は ClientInterface を継承しているため、player, worldなどにアクセス可能
class BranchMiner : ConfigurableFeature() {
    fun baritoneCheck(): Boolean =
        try {
            Class.forName("baritone.api.BaritoneAPI")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

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
            defaultValue = 20, // 20 tick = 1 second
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

    override val settings: List<FeatureSetting<*>> =
        listOf(
            branchLength,
            branchInterval,
            checkInventoryInterval,
            chestSearchRadius,
        )

    sealed class State {
        class Idle : State()

        class DiggingBranch(
            val startPos: BlockPos,
            val direction: Direction,
            val currentBranchLength: Int,
        ) : State()

        class MiningVein(
            val veinStartPos: BlockPos,
        ) : State()

        class CollectItem(
            val itemPos: Vec3d,
        ) : State()

        class GoToChest(
            val chestPos: BlockPos,
        ) : State()

        class OpenChest(
            val chestPos: BlockPos,
        ) : State()

        class DepositItems(
            val chestPos: BlockPos,
        ) : State()
    }

    var state: State = State.Idle()
    private var currentBranchStartPos: BlockPos? = null
    private var currentBranchDirection: Direction? = null
    private var currentBranchLengthCount: Int = 0
    private var inventoryCheckTimer: Int = 0
    private var foundChestPos: BlockPos? = null
    private var chestOpenAttempted: Boolean = false

    override fun start() = disable()

    override fun enabled() {
        state = State.Idle()
        currentBranchStartPos = null
        currentBranchDirection = null
        currentBranchLengthCount = 0
        inventoryCheckTimer = 0
        foundChestPos = null
        chestOpenAttempted = false
        searchForChest()
    }

    override fun tick() {
        if (!baritoneCheck()) {
            InfiniteClient.error("You have to import Baritone for this Feature!")
            disable()
            return
        }

        inventoryCheckTimer++
        if (inventoryCheckTimer >= checkInventoryInterval.value) {
            inventoryCheckTimer = 0
            if (shouldDepositItems()) {
                if (foundChestPos != null) {
                    state = State.GoToChest(foundChestPos!!)
                    return
                } else {
                    InfiniteClient.warn("Inventory full, but no chest found to deposit items! Mining paused.")
                    state = State.Idle()
                    return
                }
            }
        }

        when (state) {
            is State.Idle -> handleIdle()
            is State.DiggingBranch -> handleDiggingBranch(state as State.DiggingBranch)
            is State.MiningVein -> handleMiningVein(state as State.MiningVein)
            is State.CollectItem -> handleCollectItem(state as State.CollectItem)
            is State.GoToChest -> handleGoToChest(state as State.GoToChest)
            is State.OpenChest -> handleOpenChest(state as State.OpenChest)
            is State.DepositItems -> handleDepositItems(state as State.DepositItems)
        }
    }

    // プレイヤーの向きを基に初期のブランチ掘削方向を決定する
    private fun getInitialBranchDirection(): Direction {
        val yaw = player?.yaw ?: return Direction.NORTH
        val direction = Direction.fromHorizontalDegrees(yaw.toDouble())
        return if (direction.axis.isHorizontal) direction else Direction.NORTH
    }

    private fun isOre(pos: BlockPos): Boolean {
        val block = world?.getBlockState(pos)?.block ?: return false
        // チェック対象の鉱石ブロックを定義（一般的な鉱石）
        return block == Blocks.DIAMOND_ORE || block == Blocks.IRON_ORE || block == Blocks.GOLD_ORE ||
            block == Blocks.REDSTONE_ORE || block == Blocks.LAPIS_ORE || block == Blocks.EMERALD_ORE ||
            block == Blocks.COAL_ORE || block == Blocks.NETHER_QUARTZ_ORE || block == Blocks.ANCIENT_DEBRIS
    }

    // 現在のブランチセグメントをスキップし、次のブランチの開始地点に移る
    private fun skipCurrentBranch() {
        val nextBranchStartPos =
            currentBranchStartPos!!.offset(currentBranchDirection!!, branchLength.value + branchInterval.value)
        currentBranchStartPos = nextBranchStartPos
        currentBranchLengthCount = 0 // 新しいブランチなのでリセット
        state = State.DiggingBranch(currentBranchStartPos!!, currentBranchDirection!!, currentBranchLengthCount)
        InfiniteClient.info("Skipped current branch. Moving to next branch start pos: $currentBranchStartPos")
    }

    // ブランチを1セグメント進める (またはブランチの終わりに達した場合は次のブランチへ移行する)
    private fun advanceBranchSegment() {
        currentBranchLengthCount++
        if (currentBranchLengthCount < branchLength.value) {
            state = State.DiggingBranch(currentBranchStartPos!!, currentBranchDirection!!, currentBranchLengthCount)
        } else {
            // ブランチの終わりに達したため、次のブランチの開始地点に移る
            val nextBranchStartPos =
                currentBranchStartPos!!.offset(currentBranchDirection!!, branchLength.value + branchInterval.value)
            currentBranchStartPos = nextBranchStartPos
            currentBranchLengthCount = 0
            state = State.DiggingBranch(currentBranchStartPos!!, currentBranchDirection!!, currentBranchLengthCount)
        }
    }

    private fun handleIdle() {
        val playerPos = player?.blockPos ?: return
        if (currentBranchStartPos == null) {
            currentBranchStartPos = playerPos.down()
            // 変更点: プレイヤーの向きから方向を決定
            currentBranchDirection = getInitialBranchDirection()
            currentBranchLengthCount = 0
        }
        state = State.DiggingBranch(currentBranchStartPos!!, currentBranchDirection!!, currentBranchLengthCount)
    }

    private fun handleDiggingBranch(digging: State.DiggingBranch) {
        val playerPos = player?.blockPos ?: return
        val targetBlockPos = digging.startPos.offset(digging.direction, digging.currentBranchLength + 1)
        val world = world ?: return

        // --- 異常検出と回避ロジック ---
        // 採掘目標の周囲をチェック (左右、上下、目標地点、目標地点の上)
        val leftDir = digging.direction.rotateYCounterclockwise()
        val rightDir = digging.direction.rotateYClockwise()

        val blocksToCheckForDanger =
            listOf(
                targetBlockPos,
                targetBlockPos.up(),
                targetBlockPos.offset(Direction.UP, 2), // 2段目の上
                targetBlockPos.offset(Direction.DOWN), // 1段目の下
                targetBlockPos.offset(leftDir), // 1段目の左
                targetBlockPos.offset(rightDir), // 1段目の右
                targetBlockPos.up().offset(leftDir), // 2段目の左
                targetBlockPos.up().offset(rightDir), // 2段目の右
            )

        for (pos in blocksToCheckForDanger) {
            val block = world.getBlockState(pos).block
            if (block == Blocks.LAVA || block == Blocks.WATER || block == Blocks.CAVE_AIR || block == Blocks.AIR) {
                InfiniteClient.warn("Danger/Air detected (${block.name.string}) at $pos. Skipping current branch.")
                skipCurrentBranch()
                return
            }
        }
        // -----------------------------

        val blocksToMine = mutableListOf<BlockPos>()
        blocksToMine.add(targetBlockPos)
        blocksToMine.add(targetBlockPos.up())

        // --- 側面鉱石チェックとVeinBreakへの追加 ---
        val blocksToCheckForSideOre =
            listOf(
                targetBlockPos.offset(leftDir), // 1段目の左壁
                targetBlockPos.offset(rightDir), // 1段目の右壁
                targetBlockPos.offset(Direction.DOWN), // 1段目の床
                targetBlockPos.up().offset(leftDir), // 2段目の左壁
                targetBlockPos.up().offset(rightDir), // 2段目の右壁
                targetBlockPos.up().offset(Direction.UP), // 2段目の天井
            )
        // -----------------------------

        val veinBreak = InfiniteClient.getFeature(VeinBreak::class.java)
        if (veinBreak != null && veinBreak.isEnabled()) {
            // 採掘対象ブロックをVeinBreakに追加
            blocksToMine.forEach { veinBreak.add(it) }

            // 側面鉱石をチェックし、VeinBreakに追加
            blocksToCheckForSideOre.forEach { pos ->
                if (isOre(pos)) {
                    InfiniteClient.info("Found side ore at $pos. Adding to VeinBreak.")
                    veinBreak.add(pos)
                }
            }

            if (playerPos.isWithinDistance(targetBlockPos, 4.0)) {
                state = State.MiningVein(targetBlockPos)
            } else {
                AiInterface.add(
                    LinearMovementAction(
                        targetBlockPos.toCenterPos(),
                        2.0,
                        1,
                        stateRegister = { if (isEnabled()) null else AiAction.AiActionState.Failure },
                        onSuccessAction = {
                            state =
                                State.DiggingBranch(digging.startPos, digging.direction, digging.currentBranchLength)
                        },
                        onFailureAction = {
                            InfiniteClient.error("Failed to reach target block for digging. Skipping current branch.")
                            // Path失敗時はブランチをスキップして次のブランチへ
                            skipCurrentBranch()
                        },
                    ),
                )
            }
        } else {
            InfiniteClient.error("VeinBreak is not enabled or not found! Mining paused.")
            disable()
        }
    }

    private fun handleMiningVein(mining: State.MiningVein) {
        val veinBreak =
            InfiniteClient.getFeature(VeinBreak::class.java) ?: run {
                InfiniteClient.error("VeinBreak is not enabled or not found! Mining paused.")
                disable()
                return
            }

        if (!veinBreak.isWorking) {
            val itemEntities =
                world?.entities?.filterIsInstance<ItemEntity>()?.filter {
                    it.blockPos.isWithinDistance(playerPos, 10.0)
                } ?: emptyList()

            if (itemEntities.isNotEmpty()) {
                val closestItem = itemEntities.minByOrNull { it.squaredDistanceTo(player) }
                if (closestItem != null) {
                    state = State.CollectItem(closestItem.getLerpedPos(1.0f))
                    return
                }
            }
            // アイテムがない場合、次のセグメントへ進む
            advanceBranchSegment()
        }
    }

    private fun handleCollectItem(collect: State.CollectItem) {
        AiInterface.add(
            LinearMovementAction(
                pos = collect.itemPos,
                movementRange = 1.5,
                onSuccessAction = {
                    // アイテム回収成功後、次のセグメントへ進む
                    advanceBranchSegment()
                },
                onFailureAction = {
                    InfiniteClient.warn("Failed to collect item at ${collect.itemPos}. Skipping to next branch segment.")
                    // アイテム回収失敗後、次のセグメントへ進む
                    advanceBranchSegment()
                },
            ),
        )
    }

    private fun shouldDepositItems(): Boolean {
        val inventory = player?.inventory ?: return false
        val totalSlots = 9 + 27
        var filledSlots = 0
        for (i in 0 until totalSlots) {
            if (!inventory.getStack(i).isEmpty) {
                filledSlots++
            }
        }
        return (filledSlots.toDouble() / totalSlots.toDouble()) > 0.8
    }

    private fun searchForChest() {
        val playerPos = player?.blockPos ?: return
        val r = chestSearchRadius.value
        foundChestPos = null

        for (x in (playerPos.x - r)..(playerPos.x + r)) {
            for (y in (playerPos.y - r)..(playerPos.y + r)) {
                for (z in (playerPos.z - r)..(playerPos.z + r)) {
                    val currentPos = BlockPos(x, y, z)
                    val blockState = world?.getBlockState(currentPos)
                    if (blockState?.block == Blocks.CHEST) {
                        foundChestPos = currentPos
                        InfiniteClient.info("Found chest at $currentPos")
                        return
                    }
                }
            }
        }
        InfiniteClient.warn("No chest found within search radius.")
    }

    private fun handleGoToChest(goToChest: State.GoToChest) {
        AiInterface.add(
            PathMovementAction(
                goToChest.chestPos.x,
                goToChest.chestPos.y,
                goToChest.chestPos.z,
                1,
                0,
                onSuccessAction = {
                    state = State.OpenChest(goToChest.chestPos)
                },
                onFailureAction = {
                    InfiniteClient.error("Failed to reach chest at ${goToChest.chestPos}. Resuming mining.")
                    state = State.Idle()
                },
            ),
        )
    }

    private fun handleOpenChest(openChest: State.OpenChest) {
        val player = player ?: return
        val world = world ?: return
        val interactionManager = interactionManager ?: return

        if (!chestOpenAttempted) {
            val chestBlockState = world.getBlockState(openChest.chestPos)
            if (chestBlockState.block == Blocks.CHEST) {
                // 視線合わせ
                BlockUtils.faceVectorPacket(Vec3d.ofCenter(openChest.chestPos))
                // BlockHitResult を作成して interactBlock に渡す
                val hitResult =
                    BlockHitResult(Vec3d.ofCenter(openChest.chestPos), Direction.UP, openChest.chestPos, false)
                interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult)
                chestOpenAttempted = true
            } else {
                InfiniteClient.error("Block at ${openChest.chestPos} is no longer a chest. Resuming mining.")
                state = State.Idle()
                return
            }
        }

        // 画面が開かれたことを確認
        if (player.currentScreenHandler is GenericContainerScreenHandler) {
            state = State.DepositItems(openChest.chestPos)
            chestOpenAttempted = false // リセット
        } else {
            // 画面が開かれるまで待機 (次のティックで再度チェック)
        }
    }

    private fun handleDepositItems(deposit: State.DepositItems) {
        val player = player ?: return
        val interactionManager = interactionManager ?: return
        val screenHandler =
            player.currentScreenHandler as? GenericContainerScreenHandler ?: run {
                InfiniteClient.error("Chest screen is not open. Cannot deposit items. Resuming mining.")
                state = State.Idle()
                return
            }

        // 経験値ボトルやトーチなど、預けたくないアイテムを除外するためのロジックが必要になる場合があるが、ここでは全アイテムを対象とする。
        for (i in 0 until 27) {
            val stack =
                InventoryManager.get(
                    if (i < 9) InventoryManager.InventoryIndex.Hotbar(i) else InventoryManager.InventoryIndex.Backpack(i - 9),
                )
            if (!stack.isEmpty) {
                val playerNetworkSlot = InventoryManager.toNetworkSlot(i)
                val chestEmptySlot = findFirstEmptyChestSlot(screenHandler)
                if (chestEmptySlot != null) {
                    interactionManager.clickSlot(
                        screenHandler.syncId,
                        playerNetworkSlot,
                        0,
                        SlotActionType.PICKUP,
                        player,
                    )
                    interactionManager.clickSlot(screenHandler.syncId, chestEmptySlot, 0, SlotActionType.PICKUP, player)
                    if (!player.currentScreenHandler.cursorStack.isEmpty) {
                        interactionManager.clickSlot(
                            screenHandler.syncId,
                            playerNetworkSlot,
                            0,
                            SlotActionType.PICKUP,
                            player,
                        )
                    }
                } else {
                    InfiniteClient.warn("Chest is full. Cannot deposit all items.")
                    break
                }
            }
        }
        player.closeHandledScreen()
        InfiniteClient.info("Items deposited to chest at ${deposit.chestPos}. Resuming mining.")
        state = State.Idle()
    }

    private fun findFirstEmptyChestSlot(screenHandler: GenericContainerScreenHandler): Int? {
        // チェストのスロットは、画面ハンドラーのインベントリスロット配列の最初の部分に位置する
        for (i in 0 until screenHandler.slots.size - 36) {
            if (screenHandler.getSlot(i).stack.isEmpty) {
                return i
            }
        }
        return null
    }
}
