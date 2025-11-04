package org.infinite.libs.ai.actions.block

import baritone.api.BaritoneAPI
import net.minecraft.block.Block
import org.infinite.libs.ai.interfaces.AiAction

class MineBlockAction(
    // 採掘対象のブロック位置を引数として受け取る
    val block: Block,
    // 外部からの状態レジスタ（オプション）
    val stateRegister: () -> AiActionState? = { null },
    // 失敗時、成功時に実行するアクション
    val onFailureAction: () -> Unit = {},
    val onSuccessAction: () -> Unit = {},
) : AiAction() {
    // Baritone APIへのアクセス
    private val api
        get() = BaritoneAPI.getProvider()
    private val baritone
        get() = api.getBaritoneForMinecraft(client)

    // 採掘タスクがBaritoneに登録されたかを示すフラグ
    private var registered = false

    // 採掘タスクのキャンセル処理
    private fun cancelTask() {
        // Baritoneのパッシングとタスクをすべてキャンセル
        baritone.pathingBehavior.cancelEverything()
    }

    override fun tick() {
        if (!registered) {
            // 採掘タスクをBaritoneに登録
            // BaritoneのMineProcessを使って指定位置のブロックを採掘
            baritone.mineProcess.mine(block)
            registered = true
        }
    }

    override fun state(): AiActionState =
        stateRegister() ?: when {
            // 登録されていない、または採掘プロセスがアクティブな場合
            !registered || baritone.mineProcess.isActive -> AiActionState.Progress

            // 登録済みだが、採掘プロセスが非アクティブな場合
            // 成功の判定には、ターゲット位置のブロックが実際に破壊された（空気などになった）かを確認するのが確実です
            // ただし、Baritoneのプロセスが正常に終了した場合を「成功」と仮定します
            registered && !baritone.mineProcess.isActive -> AiActionState.Success

            // それ以外（通常、これは発生しないはずですが、念のため）
            else -> AiActionState.Failure
        }

    override fun onFailure() {
        // 失敗時にタスクをキャンセルし、ユーザー定義のアクションを実行
        cancelTask()
        onFailureAction()
    }

    override fun onSuccess() {
        // 成功時にタスクをキャンセルし、ユーザー定義のアクションを実行
        // Baritoneは成功時に自動的にプロセスを停止するはずですが、明示的にキャンセル
        cancelTask()
        onSuccessAction()
    }
}
