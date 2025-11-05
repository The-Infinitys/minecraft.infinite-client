package org.infinite.libs.ai.actions.movement

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.Goal
import net.minecraft.util.math.BlockPos
import org.infinite.libs.ai.interfaces.AiAction
import kotlin.math.abs
import kotlin.math.sqrt

class PathMovementAction(
    val x: Int,
    val y: Int? = null,
    val z: Int,
    radius: Int = 2,
    height: Int = 2,
    val stateRegister: () -> AiActionState? = { null },
    val onFailureAction: () -> Unit = {},
    val onSuccessAction: () -> Unit = {},
) : AiAction() {
    private val api
        get() = BaritoneAPI.getProvider()
    private val baritone
        get() = api.getBaritoneForMinecraft(client)

    class Vec3iGoal(
        val x: Int,
        val y: Int? = null,
        val z: Int,
        val radius: Int = 2,
        val height: Int = 2,
    ) : Goal {
        override fun isInGoal(
            x: Int,
            y: Int,
            z: Int,
        ): Boolean {
            val inX = abs(this.x - x) <= this.radius
            val inY = if (this.y == null) true else abs(this.y - y) <= this.height
            val inZ = abs(this.z - z) <= this.radius
            return inX && inY && inZ
        }

        override fun heuristic(
            x: Int,
            y: Int,
            z: Int,
        ): Double {
            // 目標のY座標を決定。yがnullの場合は、現在のy座標を目標の中心として使用（水平方向の移動を優先）
            // これにより、Y軸の探索コストが低く見積もられ、Baritoneが「高さの調整は後で良い」と判断しやすくなります。
            val targetY = this.y ?: y

            // XZ平面の距離を計算
            val dx = (this.x - x).toDouble()
            val dz = (this.z - z).toDouble()
            val distXZ = sqrt(dx * dx + dz * dz)
            // Y軸の距離を計算
            val dy = (targetY - y).toDouble()
            // 3Dユークリッド距離 (Manhattan Distanceやchebyshev Distanceも選択肢としてありえます)
            val euclideanDistance = sqrt(distXZ * distXZ + dy * dy)

            // ヒューリスティックの値は、目標範囲内に入った時点で0に近づくべきですが、
            // 探索効率を上げるため、ここでは単純に中心までの距離を返します。
            // BaritoneはisInGoalがtrueになると自動的に探索を停止します。
            return euclideanDistance
        }
    }

    var registered = false
    val goal = Vec3iGoal(x, y, z, radius, height)

    override fun tick() {
        if (!registered) {
            baritone.customGoalProcess.setGoalAndPath(goal)
            registered = true
        }
    }

    val pos: BlockPos
        get() = player!!.blockPos

    override fun state(): AiActionState =
        stateRegister() ?: when {
            !registered || baritone.pathingBehavior.goal == goal -> AiActionState.Progress // 既に何かしらのアクションが行われている
            registered && goal.isInGoal(pos.x, pos.y, pos.z) -> AiActionState.Success
            else -> AiActionState.Failure
        }

    private fun cancelTask() {
        baritone.pathingBehavior.cancelEverything()
    }

    override fun onFailure() {
        cancelTask()
        onFailureAction()
    }

    override fun onSuccess() {
        cancelTask()
        onSuccessAction()
    }
}
