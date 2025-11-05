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
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.settings.FeatureSetting
import org.infinite.utils.block.BlockUtils

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

    private fun handleIdle() {
        val playerPos = player?.blockPos ?: return
        if (currentBranchStartPos == null) {
            currentBranchStartPos = playerPos.down()
            currentBranchDirection = listOf(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST).random()
            currentBranchLengthCount = 0
        }
        state = State.DiggingBranch(currentBranchStartPos!!, currentBranchDirection!!, currentBranchLengthCount)
    }

    private fun handleDiggingBranch(digging: State.DiggingBranch) {
        val playerPos = player?.blockPos ?: return
        val targetBlockPos = digging.startPos.offset(digging.direction, digging.currentBranchLength + 1)

        val blocksToMine = mutableListOf<BlockPos>()
        blocksToMine.add(targetBlockPos)
        blocksToMine.add(targetBlockPos.up())

        val veinBreak = InfiniteClient.getFeature(VeinBreak::class.java)
        if (veinBreak != null && veinBreak.isEnabled()) {
            if (playerPos.isWithinDistance(targetBlockPos, 4.0)) {
                blocksToMine.forEach { veinBreak.add(it) }
                state = State.MiningVein(targetBlockPos)
            } else {
                AiInterface.add(
                    PathMovementAction(
                        targetBlockPos.x,
                        targetBlockPos.y,
                        targetBlockPos.z,
                        1,
                        0,
                        onSuccessAction = {
                            state =
                                State.DiggingBranch(digging.startPos, digging.direction, digging.currentBranchLength)
                        },
                        onFailureAction = {
                            InfiniteClient.error("Failed to reach target block for digging. Skipping branch segment.")
                            moveToNextBranchSegment()
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
            moveToNextBranchSegment()
        }
    }

    private fun handleCollectItem(collect: State.CollectItem) {
        AiInterface.add(
            LinearMovementAction(
                pos = collect.itemPos,
                movementRange = 1.5,
                onSuccessAction = {
                    moveToNextBranchSegment()
                },
                onFailureAction = {
                    InfiniteClient.warn("Failed to collect item at ${collect.itemPos}. Skipping to next branch segment.")
                    moveToNextBranchSegment()
                },
            ),
        )
    }

    private fun moveToNextBranchSegment() {
        currentBranchLengthCount++
        if (currentBranchLengthCount < branchLength.value) {
            state = State.DiggingBranch(currentBranchStartPos!!, currentBranchDirection!!, currentBranchLengthCount)
        } else {
            val nextBranchStartPos =
                currentBranchStartPos!!.offset(currentBranchDirection!!, branchLength.value + branchInterval.value)
            currentBranchStartPos = nextBranchStartPos
            currentBranchLengthCount = 0
            state = State.DiggingBranch(currentBranchStartPos!!, currentBranchDirection!!, currentBranchLengthCount)
        }
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
            // タイムアウト処理も考慮すべきだが、ここでは簡易化
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
        for (i in 0 until screenHandler.slots.size - 36) {
            if (screenHandler.getSlot(i).stack.isEmpty) {
                return i
            }
        }
        return null
    }
}
