package org.infinite.libs.ai.actions.block

import baritone.api.BaritoneAPI
import net.minecraft.util.math.BlockPos
import org.infinite.libs.ai.interfaces.AiAction

class MineBlockAction(
    // MutableListに変更
    val blockPosList: MutableList<BlockPos>,
    val stateRegister: () -> AiActionState? = { null },
    val onFailureAction: () -> Unit = {},
    val onSuccessAction: () -> Unit = {},
) : AiAction() {
    private val api
        get() = BaritoneAPI.getProvider()
    private val baritone
        get() = api.getBaritoneForMinecraft(client)

    // 現在Baritoneに指示しているBlockPosを保持
    private var currentTarget: BlockPos? = null

    override fun tick() {
        val playerPos = playerPos ?: return
        // 破壊対象のブロックが残っているか確認
        if (blockPosList.isEmpty()) {
            // リストが空であれば、特に何もせずに終了
            return
        }

        // 現在Baritoneが何か処理中かを確認し、処理中であれば新しいタスクを与えない
        // (builderProcessのキャンセルは、意図しない中断を防ぐためここでは行わない)
        if (baritone.builderProcess.isActive && currentTarget != null) {
            return
        }

        // プレイヤーに最も近いブロックを選択
        val nearestBlock: BlockPos =
            blockPosList.minByOrNull { pos ->
                // プレイヤー位置とブロック位置の距離の二乗を計算（sqrtを省略して高速化）
                pos.getSquaredDistance(playerPos.x, playerPos.y, playerPos.z)
            } ?: return

        // 最も近いブロックが現在のターゲットと同じ場合は再設定しない
        if (nearestBlock == currentTarget) {
            return
        }

        // BaritoneのBuilderProcessをクリアし、新しいタスクを設定
        // 一度に一つだけ破壊を試みる
        baritone.builderProcess.clearArea(nearestBlock, nearestBlock)
        currentTarget = nearestBlock

        // 既に破壊されたブロックをリストから削除する処理は、state()で行います
    }

    override fun state(): AiActionState =
        stateRegister() ?: run {
            // Baritoneに指示したブロックが破壊されたか確認し、リストから削除
            currentTarget?.let { target ->
                // targetのブロックの状態を確認 (既に空気ブロックになっているか)
                val isCleared = world?.getBlockState(target)?.isAir ?: false
                if (isCleared) {
                    // 破壊が完了したらリストから削除
                    blockPosList.remove(target)
                    // currentTargetをクリアし、次のtickで新しいターゲットが選ばれるようにする
                    currentTarget = null
                }
            }

            // 残りのブロックがあるかによって状態を決定
            return if (blockPosList.isEmpty()) AiActionState.Success else AiActionState.Progress
        }

    override fun onFailure() {
        // 現在のターゲットがまだリストに残っている場合、それをリストに残したままにする
        onFailureAction()
    }

    override fun onSuccess() {
        // 全てのブロックが正常に破壊された場合にのみ呼ばれる
        onSuccessAction()
    }
}
