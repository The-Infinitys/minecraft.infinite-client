package org.infinite.libs.ai.actions.movement

import baritone.api.BaritoneAPI
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import org.infinite.InfiniteClient
import org.infinite.libs.ai.interfaces.AiAction
import org.infinite.libs.client.aim.AimInterface
import org.infinite.libs.client.aim.camera.CameraRoll
import org.infinite.libs.client.aim.task.AimTask
import org.infinite.libs.client.aim.task.condition.AimTaskConditionByFrame
import org.infinite.libs.client.aim.task.config.AimCalculateMethod
import org.infinite.libs.client.aim.task.config.AimPriority
import org.infinite.libs.client.aim.task.config.AimTarget
import org.infinite.utils.toRadians
import kotlin.math.abs
import kotlin.math.atan2

class PathMovementAction(
    val x: Int,
    val y: Int? = null,
    val z: Int,
    val radius: Int? = 2,
    val height: Int = 2,
    val stateRegister: () -> AiActionState? = { null },
    val onFailureAction: () -> Unit = {},
    val onSuccessAction: () -> Unit = {},
) : AiAction() {
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

    private val api
        get() = BaritoneAPI.getProvider()
    private val baritone
        get() = api.getBaritoneForMinecraft(client)

    var registered = false

    override fun tick() {
        if (!baritoneCheck()) return
        if (registered) {
            handleAim()
        } else {
            goal =
                Vec3iGoal(x, y, z, radius, height)
            baritone.customGoalProcess.setGoalAndPath(
                goal as Vec3iGoal,
            )
            registered = true
        }
    }

    private fun handleAim() {
        val target = MovementRoll()
        val condition = AimTaskConditionByFrame(0, 1, false)
        AimInterface.addTask(AimTask(AimPriority.Normally, target, condition, AimCalculateMethod.EaseInOut))
    }

    val pos: BlockPos
        get() = player!!.blockPos

    fun baritoneCheck(): Boolean =
        try {
            Class.forName("baritone.api.BaritoneAPI")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

    var goal: Any? = null

    override fun state(): AiActionState =
        if (!baritoneCheck()) {
            InfiniteClient.error("You have to import Baritone for this Feature!")
            AiActionState.Failure
        } else {
            stateRegister() ?: when {
                !registered || baritone.pathingBehavior.goal == goal -> AiActionState.Progress // 既に何かしらのアクションが行われている
                registered && (
                    if (radius == null) {
                        baritone.pathingBehavior.goal != goal
                    } else {
                        (goal as Vec3iGoal).isInGoal(
                            pos.x,
                            pos.y,
                            pos.z,
                        )
                    }
                ) -> AiActionState.Success

                else -> AiActionState.Failure
            }
        }

    private fun cancelTask() {
        if (baritoneCheck()) {
            baritone.pathingBehavior.cancelEverything()
        }
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
