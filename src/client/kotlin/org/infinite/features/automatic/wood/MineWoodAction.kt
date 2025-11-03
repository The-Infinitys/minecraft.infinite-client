package org.infinite.features.automatic.wood

import net.minecraft.util.math.BlockPos
import org.infinite.libs.ai.actions.block.MineBlockAction
import org.infinite.libs.ai.interfaces.AiAction
import java.util.LinkedList

/**
 * MineBlockActionを拡張し、一本の木全体を採掘するアクション。
 * 採掘順序（Y座標が低い順）を管理し、個々の原木採掘をMineBlockActionに委任します。
 * * @param wood 採掘対象のWoodオブジェクト
 * @param onLogMined 個々の原木が採掘されたときに実行されるコールバック
 */
class MineWoodAction(
    wood: WoodMiner.Wood,
    private val onLogMined: (BlockPos) -> Unit = {},
) : AiAction() {
    // 採掘を待っている原木のキュー（低いY座標から高いY座標へ）
    private val logQueue: LinkedList<BlockPos> =
        LinkedList(wood.logPositions.sortedBy { it.y })

    // 現在実行中の個々の採掘アクション
    private var currentMineBlockAction: MineBlockAction? = null

    override fun state(): AiActionState {
        // すべての原木の採掘が完了したらSuccess
        if (logQueue.isEmpty() && currentMineBlockAction == null) {
            return AiActionState.Success
        }

        // 現在のアクションが失敗したらFailure
        if (currentMineBlockAction?.state() == AiActionState.Failure) {
            return AiActionState.Failure
        }

        return AiActionState.Progress
    }

    override fun tick() {
        if (currentMineBlockAction != null) {
            // 進行中のアクションがあればそれをtick()
            val actionState = currentMineBlockAction!!.state()

            if (actionState == AiActionState.Success) {
                // 成功したらログを削除し、次のアクションへ
                val minedPos = currentMineBlockAction!!.targetBlockPos
                onLogMined(minedPos) // コールバックを実行
                currentMineBlockAction = null
                return // 次のtick()で新しいアクションを開始
            } else if (actionState == AiActionState.Failure) {
                // 失敗したらこのアクションも失敗として処理
                return
            }

            // 進行中であればtick()を続行
            currentMineBlockAction!!.tick()
        } else if (logQueue.isNotEmpty()) {
            // 次の原木があれば新しいアクションを開始
            val nextLogPos = logQueue.poll()

            // MineBlockActionは移動ロジックも内包しているため、そのまま実行
            currentMineBlockAction = MineBlockAction(nextLogPos)
            // このアクションはAiInterfaceのキューではなく、MineWoodAction内部で実行される
            // 最初のtick()を呼び出し、すぐに実行を開始
            currentMineBlockAction!!.tick()
        }
    }

    override fun onSuccess() {
        // 全ての原木の採掘が完了
    }

    override fun onFailure() {
        // 採掘プロセスが途中で失敗
    }
}
