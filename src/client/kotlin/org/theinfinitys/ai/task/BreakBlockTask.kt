package org.theinfinitys.ai.task

import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import org.theinfinitys.ai.PlayerController
import org.theinfinitys.ai.Task
import org.theinfinitys.ai.TaskTickResult

/**
 * 指定された位置のブロックを破壊するタスク。
 * @param targetPos 破壊するブロックの座標
 */
class BreakBlockTask(
    private val targetPos: BlockPos,
) : Task {
    // 破壊の試行回数をカウントしたり、タイムアウトを設定することもできますが、
    // ここではシンプルに、成功するまで試行を繰り返すものとします。

    override fun onTick(controller: PlayerController): TaskTickResult {
        // プレイヤーが死んでいる場合はタスクを中断
        if (controller.getPlayer().isDead) {
            controller.stopMovementControl()
            return TaskTickResult.Failure
        }

        // --- 修正箇所: ブロックの状態の取得 ---
        // PlayerControllerからワールド（クライアントワールド）を取得
        val world: ClientWorld =
            controller.client.world ?: // ワールド情報が取得できない場合は失敗として処理
                return TaskTickResult.Failure // 実際のメソッド名は環境に依存する場合があります

        // 指定された座標のBlockStateを取得
        val blockState = world.getBlockState(targetPos)

        // ブロックがすでに空気（Blocks.AIR）なら成功
        // BlockState#isAir() を使用するのが最も確実です
        if (blockState.isAir) {
            controller.stopMovementControl()
            return TaskTickResult.Success
        }

        // または、特定のIDと比較したい場合は、以下のようにします
        // val blockId = Registries.BLOCK.getId(blockState.block).toString()
        // if (blockId == "minecraft:air") { ... }
        // ------------------------------------

        // プレイヤーの操作を実行（ブロックに向きを合わせて、破壊操作を継続/開始）
        controller.lookAt(targetPos.toCenterPos()) // このメソッドはPlayerControllerに実装が必要です
        // 攻撃キーを押してブロック破壊を継続/開始
        controller.client.options.attackKey.isPressed = true
        return TaskTickResult.Progress
    }
}
