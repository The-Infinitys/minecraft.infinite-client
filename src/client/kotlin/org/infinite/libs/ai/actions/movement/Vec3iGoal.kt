package org.infinite.libs.ai.actions.movement

import baritone.api.pathing.goals.Goal
import kotlin.math.abs
import kotlin.math.sqrt

class Vec3iGoal(
    val x: Int,
    val y: Int? = null,
    val z: Int,
    val radius: Int? = 2,
    val height: Int = 2,
) : Goal {
    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val inX = abs(this.x - x) <= (this.radius ?: 0)
        val inY = if (this.y == null) true else abs(this.y - y) <= this.height
        val inZ = abs(this.z - z) <= (this.radius ?: 0)
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

        // BaritoneはisInGoalがtrueになると自動的に探索を停止します。
        return euclideanDistance
    }
}
