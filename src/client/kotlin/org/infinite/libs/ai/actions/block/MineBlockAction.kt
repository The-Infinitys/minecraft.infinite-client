package org.infinite.libs.ai.actions.block

import net.minecraft.block.BlockState
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.infinite.libs.ai.interfaces.AiAction
import org.infinite.utils.block.BlockUtils // ブロック破壊に必要なユーティリティを仮定

class MineBlockAction(
    // 採掘対象のブロック位置を BlockPos で受け取る (BaritoneはBlockオブジェクトを受け取るが、破壊処理には位置が必須)
    val targetPos: BlockPos,
    // 外部からの状態レジスタ（オプション）
    val stateRegister: () -> AiActionState? = { null },
    // 失敗時、成功時に実行するアクション
    val onFailureAction: () -> Unit = {},
    val onSuccessAction: () -> Unit = {},
) : AiAction() {
    // --- 内部状態管理 ---
    private var isBreaking: Boolean = false // 破壊を開始したか
    private var breakingSide: Direction? = null // 叩いている面
    private var breakingProgress: Float = 0.0f // 破壊進行度

    /**
     * 現在のターゲットブロックの状態を取得
     */
    private fun getCurrentBlockState(): BlockState? = world?.getBlockState(targetPos)

    override fun tick() {
        val im = interactionManager ?: return
        val p = player ?: return
        val w = world ?: return

        val state = getCurrentBlockState()

        // 1. 成功チェック (ブロックが既に空気、または破壊不可能であれば失敗とみなし、キャンセル処理を行う)
        if (state == null || state.isAir || state.getHardness(w, targetPos) < 0) {
            // 破壊が完了した（空気になった）場合は、state()でSuccessと判定される
            // ここでは破壊が不要になったり、不可能な状態になった場合の中止処理を行う
            if (isBreaking) {
                // 進行中の破壊を中止
                im.cancelBlockBreaking()
            }
            return
        }

        // 2. 破壊開始または進行
        if (!isBreaking) {
            // 破壊パラメータ（叩く面、ヒットベクトルなど）を取得
            // このメソッドは、Baritoneのようなツール選択や視点変更は含まないシンプルなものを想定
            val params = BlockUtils.getBlockBreakingParams(targetPos)

            if (params != null) {
                // サーバーに視点変更パケットを送信 (必須ではないが、チートクライアントでは一般的)
                BlockUtils.faceVectorPacket(params.hitVec)

                // 破壊開始パケットを送信
                im.updateBlockBreakingProgress(params.pos, params.side)

                // 状態更新
                breakingSide = params.side
                isBreaking = true
            } else {
                // パラメータが取得できない（例: 遠すぎる、無効な位置など）場合は失敗
                // これを onFailure() に繋げたいが、tick() 内では state() で判定するのが基本。
                // 処理を継続させずに終了。
                return
            }
        } else {
            // 破壊進行パケットを送信
            im.updateBlockBreakingProgress(targetPos, breakingSide ?: return)

            // クライアント側の破壊進行度を更新 (これは interactionManager の内部値に依存)
            breakingProgress = im.currentBreakingProgress

            // 手を振る (視覚的なフィードバック)
            p.swingHand(Hand.MAIN_HAND)
        }
    }

    override fun state(): AiActionState =
        stateRegister() ?: run {
            val im = interactionManager ?: return AiActionState.Failure // InteractionManagerがない場合は失敗

            val state = getCurrentBlockState()
            // 1. 成功判定: ターゲット位置のブロックが空気になった場合
            if (state?.isAir == true) {
                // ブロックが破壊された
                return AiActionState.Success
            }

            // 2. 失敗判定: 破壊を開始したが、進行度がリセットされた、または破壊不可能
            // Baritone非依存では、移動やツール選択の失敗を考慮しないため、シンプルに進行中か否か
            if (isBreaking && !im.isBreakingBlock) {
                // 破壊を開始したが、何らかの理由でインタラクションマネージャーが破壊を中止した場合
                // 例: 距離が離れた、プレイヤーが手動でアイテムを変えた、サーバーが拒否したなど
                return AiActionState.Failure
            }

            // 3. 進行中判定
            if (isBreaking && im.isBreakingBlock) {
                return AiActionState.Progress
            }

            // 4. まだ開始していない、または再開待ち
            return AiActionState.Progress
        }

    // 内部的なキャンセル処理
    private fun cleanupAndCancel() {
        interactionManager?.cancelBlockBreaking()
        isBreaking = false
        breakingSide = null
        breakingProgress = 0.0f
    }

    override fun onFailure() {
        // 失敗時に内部状態をリセットし、ユーザー定義のアクションを実行
        cleanupAndCancel()
        onFailureAction()
    }

    override fun onSuccess() {
        // 成功時に内部状態をリセットし、ユーザー定義のアクションを実行
        cleanupAndCancel()
        onSuccessAction()
    }
}
