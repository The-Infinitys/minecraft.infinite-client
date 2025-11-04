package org.infinite.features.automatic.tunnel

import net.minecraft.item.Item
import net.minecraft.registry.Registries
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.movement.braek.FastBreak
import org.infinite.libs.ai.AiInterface
import org.infinite.libs.ai.actions.movement.MovementAction
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.settings.FeatureSetting
import org.infinite.settings.FeatureSetting.IntSetting
import org.infinite.utils.block.BlockUtils
import org.infinite.utils.rendering.transparent

class ShieldMachine : ConfigurableFeature() {
    // --- 設定 ---
    val tunnelWidth = IntSetting("Width", 3, 1, 6)
    val tunnelHeight = IntSetting("Height", 3, 2, 5)
    val tunnelLength = IntSetting("Length", 100, 0, 256)
    val tunnelOffset = IntSetting("ForwardOffset", 2, 1, 5) // 掘削オフセット設定
    val autoPlaceFloor = FeatureSetting.BooleanSetting("AutoPlaceFloor", true)
    val floorBlockList = FeatureSetting.BlockListSetting("FloorBlock", mutableListOf("minecraft:cobblestone"))
    override val settings: List<FeatureSetting<*>> =
        listOf(tunnelWidth, tunnelHeight, tunnelLength, tunnelOffset, autoPlaceFloor, floorBlockList) // 設定リストを更新

    // --- 内部状態と破壊/設置の管理 ---
    private open class State {
        class Idle : State()

        class Walking(
            val pos: Vec3d,
        ) : State()

        // 破壊対象を保持。LinkedHashSetで処理順を維持
        class Mining(
            val pos: LinkedHashSet<BlockPos>,
        ) : State()

        class Placing(
            val pos: MutableList<BlockPos>,
        ) : State()
    }

    enum class Direction {
        East,
        West,
        North,
        South,
    }

    // 初期化後に方向と開始位置を固定
    var direction: Direction? = null
    var startPos: Vec3d? = null

    private var fixedTunnelY: Int? = null

    // 💡 修正点: 初期位置からの移動ブロック数を保持
    private var movedBlocksCount: Int = 0

    private var state: State = State.Idle()
    var aiActionCallback: Boolean? = null

    var currentBreakingPos: BlockPos? = null
    private var currentBreakingSide: net.minecraft.util.math.Direction? = null
    private var currentBreakingProgress: Float = 0.0f

    var walkingCallBack = true

    override fun enabled() {
        // 全てリセットし、初期化を待つ
        aiActionCallback = null
        state = State.Idle()
        startPos = null
        direction = null
        currentBreakingPos = null
        currentBreakingSide = null
        currentBreakingProgress = 0.0f
        fixedTunnelY = null
        movedBlocksCount = 0 // 💡 修正点: カウンターもリセット
        walkingCallBack = true
    }

    override fun tick() {
        when (state) {
            is State.Idle -> initialization()
            is State.Walking -> handleWalking(state as State.Walking)
            is State.Mining -> handleMining(state as State.Mining)
            is State.Placing -> handlePlacing(state as State.Placing)
        }
    }

    private fun handlePlacing(placing: State.Placing) {
        val player = player ?: return
        val blocksToPlace = placing.pos

        val targetPos =
            blocksToPlace.firstOrNull() ?: run {
                state = State.Idle()
                return
            }

        val blockId =
            floorBlockList.value.firstOrNull() ?: run {
                state = State.Idle()
                return
            }
        val item: Item = Registries.ITEM.get(Identifier.of(blockId))

        val inventoryIndex = InventoryManager.findFirstInMain(item)

        if (inventoryIndex !is InventoryManager.InventoryIndex.Hotbar) {
            return
        }

        val hotbarSlot = inventoryIndex.index
        player.inventory.selectedSlot = hotbarSlot

        val world = client.world ?: return
        if (!world.getBlockState(targetPos).isReplaceable) {
            blocksToPlace.remove(targetPos)
            return
        }

        val neighbor = targetPos.down()
        val side = net.minecraft.util.math.Direction.UP
        val hitVec = Vec3d(targetPos.x + 0.5, targetPos.y + 0.5, targetPos.z + 0.5)

        // 設置成功判定ロジックを修正
        val success = BlockUtils.placeBlock(neighbor, side, hitVec, hotbarSlot)

        if (success) {
            blocksToPlace.remove(targetPos)
            player.swingHand(Hand.MAIN_HAND)
        }
    }

    private fun handleMining(mining: State.Mining) {
        val interactionManager = client.interactionManager ?: return
        val player = player ?: return
        val blocksToMine = mining.pos

        if (blocksToMine.isEmpty()) {
            if (currentBreakingPos != null) {
                interactionManager.cancelBlockBreaking()
                currentBreakingPos = null
                currentBreakingSide = null
                currentBreakingProgress = 0.0f
            }

            // 掘削リストが空になったら、前方のエリアがクリアかチェックし、クリアなら移動
            if (isAreaClearForMovement()) {
                moveToNextChunk()
            } else {
                // まだクリアでない場合は再度初期化（次のtickでMiningリストが再計算されることを期待）
                state = State.Idle()
            }
            return
        }

        val targetPos = blocksToMine.first()
        val blockState = client.world?.getBlockState(targetPos)

        if (blockState?.isAir == true || blockState?.isReplaceable == true) {
            blocksToMine.remove(targetPos)
            currentBreakingPos = null
            currentBreakingProgress = 0.0f
            return
        }

        // 液体チェックはinitializationで実施済みのため、ここでは採掘処理に専念
        if (isLiquid(targetPos)) {
            blocksToMine.remove(targetPos) // 液体は無視（または埋め立てを試みる）
            state = State.Idle()
            return
        }

        val params =
            BlockUtils.getBlockBreakingParams(targetPos) ?: run {
                blocksToMine.remove(targetPos)
                currentBreakingPos = null
                currentBreakingProgress = 0.0f
                return
            }

        if (currentBreakingPos == null || currentBreakingPos != params.pos) {
            interactionManager.cancelBlockBreaking()
            BlockUtils.faceVectorPacket(params.hitVec)
            interactionManager.updateBlockBreakingProgress(params.pos, params.side)

            currentBreakingPos = params.pos
            currentBreakingSide = params.side
            currentBreakingProgress = 0.0f
        } else {
            val pos = currentBreakingPos!!
            val side = currentBreakingSide!!
            interactionManager.updateBlockBreakingProgress(pos, side)
            val fastBreak = InfiniteClient.getFeature(FastBreak::class.java) ?: return
            if (fastBreak.isEnabled()) {
                fastBreak.handle(pos)
            }
            // 破壊進捗の更新ロジックは省略されているため、ここでは1.0fになったと仮定したロジックを維持
            if (currentBreakingProgress >= 1.0f) {
                blocksToMine.remove(pos)
                currentBreakingPos = null
                currentBreakingProgress = 0.0f
            }
        }

        player.swingHand(Hand.MAIN_HAND)
    }

    private fun handleWalking(walking: State.Walking) {
        val targetPos = walking.pos
        if (walkingCallBack) {
            walkingCallBack = false
            val moveAction =
                MovementAction(
                    pos = targetPos,
                    movementRange = 0.80, // 移動の成功判定を緩く
                    heightRange = 4,
                    onSuccessAction = {
                        initialization()
                        walkingCallBack = true
                    },
                    onFailureAction = {
                        // 移動失敗時は無効化
                        disable()
                        walkingCallBack = true
                    },
                )
            // 既存のアクションがないか、MovementActionでない場合にのみ追加
            if (AiInterface.actions.isEmpty() || AiInterface.actions.firstOrNull() !is MovementAction) {
                AiInterface.add(moveAction)
            } else {
                walkingCallBack = true
            }
        }
    }

    private fun initialization() {
        val player = player ?: return
        val currentPos = playerPos ?: return
        val world = client.world ?: return
        walkingCallBack = true

        // 💡 ドリフト防止: fixedTunnelYとdirection, startPosを最初に一度だけ設定
        if (direction == null) {
            val yaw = MathHelper.wrapDegrees(player.yaw)
            direction =
                when {
                    (yaw >= -135 && yaw < -45) -> Direction.East
                    (yaw >= -45 && yaw < 45) -> Direction.South
                    (yaw in 45.0..<135.0) -> Direction.West
                    else -> Direction.North
                }
        }
        if (startPos == null) {
            startPos = currentPos
        }
        if (fixedTunnelY == null) {
            fixedTunnelY = currentPos.y.toInt() - 1 // プレイヤーの足元のブロックのY座標
            movedBlocksCount = 0 // 💡 修正点: 初期化時にリセット
        }

        val currentDirection = direction ?: return
        val preMineList = LinkedHashSet<BlockPos>()
        val width = tunnelWidth.value
        val height = tunnelHeight.value
        val forwardOffset = tunnelOffset.value

        // 💡 修正: 掘削の基準ブロックを理論上の位置に固定
        val centerBlockPos = getTheoreticalPlayerPosBlock(movedBlocksCount) ?: return

        // 掘削範囲: 理論上の現在位置(f=0)から前方オフセット(forwardOffset)までのブロックをチェック
        for (f in 0..forwardOffset) {
            for (y in 1..height) { // Y座標は1から高さまでループ: プレイヤーの足元(Y=fixedTunnelY)より上のみを掘る
                for (w in 0 until width) {
                    // 幅方向の相対オフセットを計算
                    val widthRelativeOffset = w - (width - 1) / 2

                    val xOffset: Int
                    val zOffset: Int
                    val forwardStep = f // fを進行方向のオフセットとする

                    // 進行方向に基づいて X, Z の幅オフセットと進行方向オフセットを計算
                    when (currentDirection) {
                        Direction.East -> {
                            xOffset = forwardStep
                            zOffset = widthRelativeOffset
                        }

                        Direction.West -> {
                            xOffset = -forwardStep
                            zOffset = -widthRelativeOffset
                        }

                        Direction.North -> {
                            xOffset = widthRelativeOffset
                            zOffset = -forwardStep
                        }

                        Direction.South -> {
                            xOffset = -widthRelativeOffset
                            zOffset = forwardStep
                        }
                    }

                    // 掘削対象の絶対座標
                    val targetPos: BlockPos = centerBlockPos.add(xOffset, y, zOffset)

                    val state = world.getBlockState(targetPos)
                    if (!state.isAir && !state.isReplaceable) {
                        if (isLiquid(targetPos)) {
                            // 液体ブロック発見 -> 処理して終了
                            handleLiquidEncounter(targetPos)
                            return
                        }
                        preMineList.add(targetPos)
                    }
                }
            }
        }

        if (preMineList.isNotEmpty()) {
            state = State.Mining(preMineList)
        } else {
            if (isAreaClearForMovement()) {
                moveToNextChunk()
            } else {
                state = State.Idle()
            }
        }
    }

    // --- ユーティリティ ---

    /**
     * 初期位置 (startPos) と指定された移動ブロック数 (moveCount) に基づいて、
     * プレイヤーがその位置にいるべき床レベルの BlockPos を返す。
     * @param moveCount 初期位置からの移動ブロック数 (通常は movedBlocksCount)
     */
    private fun getTheoreticalPlayerPosBlock(moveCount: Int): BlockPos? {
        val initialPos = startPos ?: return null
        val currentDirection = direction ?: return null
        val fixedY = fixedTunnelY ?: return null

        // startPosの整数部 (初期の床ブロックのX, Z)
        val initialBlockX = initialPos.x.toInt()
        val initialBlockZ = initialPos.z.toInt()

        val xOffset =
            when (currentDirection) {
                Direction.East -> moveCount
                Direction.West -> -moveCount
                else -> 0
            }
        val zOffset =
            when (currentDirection) {
                Direction.North -> -moveCount
                Direction.South -> moveCount
                else -> 0
            }

        // 理論上のプレイヤーの足元のブロック座標
        return BlockPos(initialBlockX + xOffset, fixedY, initialBlockZ + zOffset)
    }

    private fun isLiquid(pos: BlockPos): Boolean {
        val world = client.world ?: return false
        val state = world.getBlockState(pos)
        // 水と溶岩（静止/流体）をチェック
        return state.fluidState.isStill || state.fluidState.isEmpty.not()
    }

    // 液体に遭遇した際の処理 (変更なし)
    private fun handleLiquidEncounter(liquidPos: BlockPos) {
        val player = player ?: return
        val blockId =
            floorBlockList.value.firstOrNull() ?: run {
                retreatAndDisable()
                return
            }
        val item: Item = Registries.ITEM.get(Identifier.of(blockId))
        val inventoryIndex = InventoryManager.findFirstInMain(item)

        if (autoPlaceFloor.value && inventoryIndex is InventoryManager.InventoryIndex.Hotbar) {
            val hotbarSlot = inventoryIndex.index
            player.inventory.selectedSlot = hotbarSlot

            val neighbor = liquidPos.down()
            val side = net.minecraft.util.math.Direction.UP
            val hitVec = Vec3d(liquidPos.x + 0.5, liquidPos.y + 0.5, liquidPos.z + 0.5)

            val world = client.world ?: return
            world.breakBlock(liquidPos, false)
            val placementSuccess = BlockUtils.placeBlock(neighbor, side, hitVec, hotbarSlot)

            if (placementSuccess && !isLiquid(liquidPos)) {
                player.swingHand(Hand.MAIN_HAND)
                state = State.Idle()
                return
            }
        }

        retreatAndDisable()
    }

    // 後退し、その移動が完了したらdisable()を呼び出す (変更なし)
    private fun retreatAndDisable() {
        val currentPos =
            playerPos ?: run {
                disable()
                return
            }
        val currentDirection =
            direction ?: run {
                disable()
                return
            }
        val fixedY =
            fixedTunnelY ?: run {
                disable()
                return
            }

        val moveVec =
            when (currentDirection) {
                Direction.East -> Vec3d(-1.0, 0.0, 0.0)
                Direction.West -> Vec3d(1.0, 0.0, 0.0)
                Direction.North -> Vec3d(0.0, 0.0, 1.0)
                Direction.South -> Vec3d(0.0, 0.0, -1.0)
            }

        val retreatPos =
            currentPos.add(moveVec.x, 0.0, moveVec.z).withAxis(
                net.minecraft.util.math.Direction.Axis.Y,
                fixedY.toDouble() + 1.0,
            )

        val moveAction =
            MovementAction(
                pos = retreatPos,
                movementRange = 0.80,
                heightRange = 4,
                onSuccessAction = {
                    disable()
                },
                onFailureAction = {
                    disable()
                },
            )

        AiInterface.actions.clear()
        AiInterface.add(moveAction)

        state = State.Idle()
    }

    /**
     * プレイヤーが移動すべき1ブロック前方のエリアが空洞であるかチェックする
     */
    private fun isAreaClearForMovement(): Boolean {
        val world = client.world ?: return false
        val currentDirection = direction ?: return false

        val width = tunnelWidth.value
        val height = tunnelHeight.value

        // 💡 修正: 掘削の基準ブロックを理論上の現在位置に設定
        val initialBlockPos = getTheoreticalPlayerPosBlock(movedBlocksCount) ?: return false

        // 進行方向へのオフセットは常に 1 (チェック対象は次の移動先エリア)
        val forwardOffset = 1
        val offsetRange = 0 until width

        // Y座標は1から高さまでループ: プレイヤーの足元(fixedY)より上のみをチェック
        for (y in 1..height) {
            for (w in offsetRange) {
                val xOffset: Int
                val zOffset: Int

                // 幅方向の相対オフセットを計算
                val widthRelativeOffset = w - (width - 1) / 2

                // 進行方向に基づいて X, Z の幅オフセットと進行方向オフセットを計算
                when (currentDirection) {
                    Direction.East -> {
                        xOffset = forwardOffset
                        zOffset = widthRelativeOffset
                    }

                    Direction.West -> {
                        xOffset = -forwardOffset
                        zOffset = -widthRelativeOffset
                    }

                    Direction.North -> {
                        xOffset = widthRelativeOffset
                        zOffset = -forwardOffset
                    }

                    Direction.South -> {
                        xOffset = -widthRelativeOffset
                        zOffset = forwardOffset
                    }
                }

                // 💡 修正: initialBlockPos を基準にオフセットを加算
                val targetPos: BlockPos = initialBlockPos.add(xOffset, y, zOffset)

                val state = world.getBlockState(targetPos)
                // 液体ブロックも含め、空気か置き換え可能なブロック以外はクリアでない
                if (!state.isAir && !state.isReplaceable && !isLiquid(targetPos)) {
                    return false
                }
            }
        }
        return true
    }

    private fun moveToNextChunk() {
        val initialPos =
            startPos ?: run {
                state = State.Idle()
                return
            }
        val currentDirection =
            direction ?: run {
                state = State.Idle()
                return
            }
        val fixedY =
            fixedTunnelY ?: run {
                state = State.Idle()
                return
            }

        // 💡 修正点: 移動ブロック数をインクリメント
        movedBlocksCount++

        // 進行方向への移動ベクトル（1ブロック分）
        val moveVec =
            when (currentDirection) {
                Direction.East -> Vec3d(1.0, 0.0, 0.0)
                Direction.West -> Vec3d(-1.0, 0.0, 0.0)
                Direction.North -> Vec3d(0.0, 0.0, -1.0)
                Direction.South -> Vec3d(0.0, 0.0, 1.0)
            }

        // 💡 修正点: startPosから (movedBlocksCount) ブロック進んだ位置を目標座標とする
        val targetPos =
            initialPos
                .add(moveVec.x * movedBlocksCount.toDouble(), 0.0, moveVec.z * movedBlocksCount.toDouble())
                .withAxis(net.minecraft.util.math.Direction.Axis.Y, fixedY.toDouble() + 1.0) // Y座標を床+1.0で固定

        state = State.Walking(targetPos)
    }

    // --- 描画処理 (省略) --- (変更なし)
    override fun render3d(graphics3D: Graphics3D) {
        val color = InfiniteClient.theme().colors.primaryColor
        val miningState = state as? State.Mining
        val placingState = state as? State.Placing

        miningState?.let {
            val boxes =
                it.pos.map { pos ->
                    val box = Box(pos)
                    RenderUtils.ColorBox(color.transparent(150), box)
                }
            graphics3D.renderLinedColorBoxes(boxes, true)
        }

        placingState?.let {
            val placingColor = color.transparent(150)
            val boxes =
                it.pos.map { pos ->
                    val box = Box(pos)
                    RenderUtils.ColorBox(placingColor, box)
                }
            graphics3D.renderLinedColorBoxes(boxes, true)
        }

        currentBreakingPos?.let { pos ->
            val progress = currentBreakingProgress.coerceIn(0.0f, 1.0f)
            val offset = (1.0 - progress) * 0.5
            val minX = pos.x + offset
            val minY = pos.y + offset
            val minZ = pos.z + offset
            val maxX = pos.x + 1.0 - offset
            val maxY = pos.y + 1.0 - offset
            val maxZ = pos.z + 1.0 - offset

            val dynamicBox = Box(minX, minY, minZ, maxX, maxY, maxZ).contract(0.005)
            val boxes = listOf(RenderUtils.ColorBox(color.transparent(200), dynamicBox))
            graphics3D.renderSolidColorBoxes(boxes, true)
        }
    }
}
