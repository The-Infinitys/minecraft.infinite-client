package org.theinfinitys.ai.task

import net.minecraft.registry.Registries // ブロックID取得のために追加
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.theinfinitys.ai.PlayerController
import org.theinfinitys.ai.Task
import org.theinfinitys.ai.TaskTickResult
import org.theinfinitys.ai.TaskTickResult.Failure
import org.theinfinitys.ai.TaskTickResult.Interrupt
import org.theinfinitys.ai.TaskTickResult.Progress
import org.theinfinitys.ai.TaskTickResult.Success
import org.theinfinitys.ai.utils.minus

/**
 * 指定された目標位置への移動を処理するタスク。
 * 進行方向にある移動を妨げるブロックがあれば、`BreakBlockTask`を割り込みとして要求します。
 * @param targetPos 移動目標のワールド座標
 * @param requiredDistance 目標位置に到達したと見なす距離 (デフォルトは0.5ブロック)
 * @param breakableBlock 破壊を許可するブロックを判定する関数（引数: ブロックID, 戻り値: Boolean）
 */
class MoveTask(
    private val targetPos: Vec3d,
    private val requiredDistance: Double = 0.5,
    private val breakableBlock: (String) -> Boolean = { false },
    private val blockCheckDistance: Double = 1.0,
) : Task {
    override fun onTick(controller: PlayerController): TaskTickResult {
        // プレイヤーが死んでいる場合はタスクを中断
        if (controller.getPlayer().isDead) {
            controller.stopMovementControl()
            return Failure
        }

        val player = controller.getPlayer()
        val playerPos = Vec3d(player.x, player.y, player.z)
        val distanceSq = playerPos.squaredDistanceTo(targetPos)

        // 目標に十分近づいたらタスク完了
        if (distanceSq < requiredDistance * requiredDistance) {
            controller.stopMovementControl()
            return Success
        }

        // 進行方向のブロックのチェックと破壊ロジック
        val direction = (targetPos - playerPos).normalize()
        val checkPos = playerPos.add(direction.multiply(blockCheckDistance))

        // チェックする位置（ブロックの整数座標）
        // 頭の高さと足元の2箇所をチェック
        val blocksToCheck =
            listOf(
                BlockPos.ofFloored(checkPos.x, playerPos.y, checkPos.z), // 進行方向の足元
                BlockPos.ofFloored(checkPos.x, playerPos.y + 1.0, checkPos.z), // 進行方向の頭の高さ
            )

        // PlayerControllerに getWorld() メソッドが存在すると仮定
        val world = controller.client.world ?: return Failure
        // nullチェックを追加することが望ましいですが、ここでは簡略化のため省略します。

        for (blockPos in blocksToCheck) {
            val blockState = world.getBlockState(blockPos)

            // --- 修正箇所: ブロックIDの正しい取得 ---
            // Blockオブジェクトから名前空間付きのID文字列を取得
            val blockId = Registries.BLOCK.getId(blockState.block).toString()
            // ------------------------------------

            // 移動を妨げないブロック（空気、水など）は無視
            // isAirはBlockStateのメソッドとして存在します。
            // isLiquid は BlockState.getMaterial().isLiquid() などでチェックできますが、
            // 簡略化のためここでは isAir のみでチェックします。
            // 破壊対象にしたい場合は、下の breakableBlock(blockId) でフィルターします。
            if (blockState.isAir) {
                continue
            }

            // プレイヤーが移動を妨げられる一般的なブロックかどうかを判断
            // ここでは、衝突箱が存在するブロックを移動を妨げるブロックと見なします
            if (blockState.getCollisionShape(world, blockPos).isEmpty) {
                continue // 衝突箱がない、つまり通り抜けられるブロック
            }

            // 破壊可能かチェック
            if (breakableBlock(blockId)) {
                // 破壊可能なブロックを発見！
                controller.stopMovementControl() // 移動を停止
                val breakTask = BreakBlockTask(blockPos)
                // BreakBlockTask のコンストラクタが BlockPos を引数に取ることを確認
                return Interrupt(breakTask) // 割り込みタスクを返す
            }
        }

        // 割り込みタスクが要求されなかった場合、プレイヤーの操作を実行（向き変更と前進）
        controller.moveTo(targetPos)

        return Progress
    }
}
