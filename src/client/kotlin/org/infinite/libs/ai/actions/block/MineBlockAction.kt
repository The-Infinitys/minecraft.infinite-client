package org.infinite.libs.ai.actions.block

import baritone.api.BaritoneAPI
import net.minecraft.util.math.BlockPos
import org.infinite.InfiniteClient
import org.infinite.libs.ai.interfaces.AiAction

class MineBlockAction(
    val blockPosList: MutableList<BlockPos>,
    val stateRegister: () -> AiActionState? = { null },
    val onFailureAction: () -> Unit = {},
    val onSuccessAction: () -> Unit = {},
) : AiAction() {
    private val api
        get() = BaritoneAPI.getProvider()
    private val baritone
        get() = api.getBaritoneForMinecraft(client)
    private val baritoneSettings = BaritoneAPI.getSettings()

    // --- 【追加】オリジナル設定値を保持するプロパティ ---
    private var originalRandomLooking: Double? = null
    private var originalRandomLooking113: Double? = null

    // 設定が変更されたかどうかを追跡するフラグ
    private var isSettingsModified: Boolean = false

    // 現在Baritoneに指示しているBlockPosを保持
    private var currentTarget: BlockPos? = null

    override fun tick() {
        val playerPos = playerPos ?: return
        // 破壊対象のブロックが残っているか確認
        if (blockPosList.isEmpty()) {
            // リストが空であれば、特に何もせずに終了
            return
        }

        // --- 【追加】タスク開始時に一度だけ設定を保存・変更 ---
        if (!isSettingsModified) {
            // オリジナル値を保存
            originalRandomLooking = baritoneSettings.randomLooking.value
            originalRandomLooking113 = baritoneSettings.randomLooking113.value
            // 値を0.0に設定
            baritoneSettings.randomLooking.value = 0.0
            baritoneSettings.randomLooking113.value = 0.0
            isSettingsModified = true
        }
        // --- 【削除】元のコードにあった毎ティック実行の設定変更は不要になりました ---

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

    fun baritoneCheck(): Boolean =
        try {
            Class.forName("baritone.api.BaritoneAPI")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

    override fun state(): AiActionState =
        if (!baritoneCheck()) {
            InfiniteClient.error("You have to import Baritone for this Feature!")
            AiActionState.Failure
        } else {
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
        }

    private fun cancelActions() {
        baritone.pathingBehavior.cancelEverything()
    }

    // --- 【追加】設定を元に戻すメソッド ---

    /**
     * オリジナル値を復元し、設定変更フラグをリセットします。
     */
    private fun restoreSettings() {
        if (isSettingsModified) {
            // オリジナル値が存在すれば復元
            originalRandomLooking?.let {
                baritoneSettings.randomLooking.value = it
            }
            originalRandomLooking113?.let {
                baritoneSettings.randomLooking113.value = it
            }
            // フラグと保存した値をリセット
            isSettingsModified = false
            originalRandomLooking = null
            originalRandomLooking113 = null
        }
    }
    // --- 【追加】設定を元に戻すメソッド ---

    override fun onFailure() {
        cancelActions()
        restoreSettings() // 失敗時にも設定を元に戻す
        onFailureAction()
    }

    override fun onSuccess() {
        cancelActions()
        restoreSettings() // 成功時にも設定を元に戻す
        onSuccessAction()
    }
}
