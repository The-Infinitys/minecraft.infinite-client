package org.infinite.libs.ai.actions.movement

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.Goal
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import org.infinite.libs.ai.interfaces.AiAction
import org.infinite.libs.client.aim.AimInterface
import org.infinite.libs.client.aim.camera.CameraRoll
import org.infinite.libs.client.aim.task.AimTask
import org.infinite.libs.client.aim.task.condition.AimTaskConditionInterface
import org.infinite.libs.client.aim.task.condition.AimTaskConditionReturn
import org.infinite.libs.client.aim.task.config.AimCalculateMethod
import org.infinite.libs.client.aim.task.config.AimPriority
import org.infinite.libs.client.aim.task.config.AimTarget
import org.infinite.utils.toRadians
import kotlin.math.abs
import kotlin.math.atan2
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
    // PathMovementActionのインスタンスへの参照を保持
    // MovementCondition内でこのアクションの状態を参照できるようにします。
    private val actionInstance = this

    // --- 1. 目標角度の定義 (移動方向) ---
    class MovementRoll : AimTarget.RollTarget(CameraRoll.Zero) {
        val client: MinecraftClient
            get() = MinecraftClient.getInstance()
        val player: ClientPlayerEntity?
            get() = client.player

        override val roll: CameraRoll
            get() {
                val player = this.player ?: return CameraRoll.Zero
                val velocity = player.velocity

                // 速度がほぼゼロの場合は、現在の向きを維持
                if (abs(velocity.x) < 0.001 && abs(velocity.z) < 0.001) {
                    return CameraRoll.Zero
                }

                // 移動方向を目標のYaw角度として計算
                // atan2(dz, dx) でラジアンを取得し、度数に変換（BaritoneやMinecraftは通常度数を使用）
                // MinecraftのYawは-180°から180°で、+Z軸が0°、+X軸が-90°です。
                // atan2は+X軸が0°、+Z軸が90°なので、変換が必要です。
                val targetYaw = toRadians(atan2(-velocity.x, velocity.z))
                // ピッチは基本的に0.0（水平）
                return CameraRoll(targetYaw, 0.0)
            }
    }

    // --- 2. AimTaskの実行条件の定義 ---
    inner class MovementCondition : AimTaskConditionInterface {
        override fun check(): AimTaskConditionReturn {
            // PathMovementActionの状態を取得
            val state = actionInstance.state()
            return when (state) {
                // Baritoneによる移動がまだ進行中の場合
                AiActionState.Progress -> {
                    AimTaskConditionReturn.Exec // AimTaskを継続実行
                }
                // Baritoneによる移動が成功した場合
                AiActionState.Success -> {
                    AimTaskConditionReturn.Success // AimTaskを成功として終了
                }
                // Baritoneによる移動が失敗またはキャンセルされた場合
                AiActionState.Failure -> {
                    AimTaskConditionReturn.Failure // AimTaskを失敗として終了
                }
            }
        }
    }

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
            handleAim()
        }
    }

    private fun handleAim() {
        val target = MovementRoll()
        val condition = MovementCondition()
        AimInterface.addTask(AimTask(AimPriority.Normally, target, condition, AimCalculateMethod.EaseInOut))
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
