package org.infinite.libs.ai.actions.block

import baritone.api.BaritoneAPI
import baritone.api.schematic.ISchematic
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import org.infinite.libs.ai.interfaces.AiAction
// SingleBlockSchematicのimportが必要

// 🌟 PlaceBlockActionのコンストラクタを変更し、placeBlockStateを受け取るようにする
class PlaceBlockAction(
    val blockPosList: MutableList<BlockPos>,
    // 🌟 配置するBlockStateを受け取るように変更
    val placeBlockState: BlockState,
    val stateRegister: () -> AiActionState? = { null },
    val onFailureAction: () -> Unit = {},
    val onSuccessAction: () -> Unit = {},
) : AiAction() {
    // net.minecraft.world.level.block.state.BlockState; など、必要なimportをBaritoneの環境に合わせて追加してください

    /**
     * 1x1x1の単一のブロックを配置するためのISchematic実装
     *
     * @param desiredState 配置したいブロックの状態
     */
    class SingleBlockSchematic(
        private val desiredState: BlockState,
    ) : ISchematic {
        override fun widthX(): Int = 1

        override fun heightY(): Int = 1

        override fun lengthZ(): Int = 1

        override fun desiredState(
            x: Int,
            y: Int,
            z: Int,
            current: BlockState,
            approxPlaceable: List<BlockState>,
        ): BlockState {
            // スキマティックの原点(0, 0, 0)でのみ望ましい状態を返す
            if (x == 0 && y == 0 && z == 0) {
                return desiredState
            }
            // それ以外の位置はスキマティックの範囲外なので、現在の状態を維持（つまり変更不要）
            // ISchematicのinSchematic()がデフォルトで範囲外を無視するので、厳密には不要なチェック
            return current
        }
    }

    private val api get() = BaritoneAPI.getProvider()
    private val baritone get() = api.getBaritoneForMinecraft(client)

    private var currentTarget: BlockPos? = null
    private val schematic = SingleBlockSchematic(placeBlockState)

    override fun tick() {
        if (blockPosList.isEmpty()) {
            return
        }

        if (baritone.builderProcess.isActive && currentTarget != null) {
            return
        }

        val nearestBlock: BlockPos =
            blockPosList.minByOrNull { it ->
                val pos = it.toCenterPos()
                playerPos?.squaredDistanceTo(pos.x, pos.y, pos.z) ?: Double.MAX_VALUE
            } ?: return

        if (nearestBlock == currentTarget) {
            return
        }

        // 🌟 build(String var1, ISchematic var2, Vec3i var3)を利用
        // var1: ログ名（何でも良いが、ここでは "Scaffold"）
        // var2: 作成したSingleBlockSchematic
        // var3: スキマティックの原点（この場合、BlockPosをVec3iとして使用）
        baritone.builderProcess.build("Scaffold", schematic, nearestBlock)
        currentTarget = nearestBlock
    }

    override fun state(): AiActionState =
        stateRegister() ?: run {
            currentTarget?.let { target ->
                // 配置するBlockStateと同じ状態になったか確認
                val isPlaced = world?.getBlockState(target) == placeBlockState

                if (isPlaced) {
                    blockPosList.remove(target)
                    currentTarget = null
                }
            }

            return if (blockPosList.isEmpty()) AiActionState.Success else AiActionState.Progress
        }

    override fun onFailure() = onFailureAction()

    override fun onSuccess() = onSuccessAction()
}
