package org.infinite.libs.client.aim.task

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.infinite.libs.client.aim.camera.CameraRoll
import org.infinite.libs.client.aim.task.condition.AimTaskConditionInterface
import org.infinite.libs.client.aim.task.condition.AimTaskConditionReturn
import org.infinite.libs.client.aim.task.config.AimCalculateMethod
import org.infinite.libs.client.aim.task.config.AimPriority
import org.infinite.libs.client.aim.task.config.AimProcessResult
import org.infinite.libs.client.aim.task.config.AimTarget
import kotlin.math.sqrt

open class AimTask(
    open val priority: AimPriority,
    open val target: AimTarget,
    open val condition: AimTaskConditionInterface,
    open val calcMethod: AimCalculateMethod,
    open val multiply: Double = 1.0,
) {
    private fun mouseSensitivity(): Double =
        MinecraftClient
            .getInstance()
            .options.mouseSensitivity.value

    private fun calcLookAt(target: Vec3d): CameraRoll {
        val vec3d: Vec3d = MinecraftClient.getInstance().player!!.eyePos
        val d = target.x - vec3d.x
        val e = target.y - vec3d.y
        val f = target.z - vec3d.z
        val g = sqrt(d * d + f * f)
        val pitch =
            MathHelper.wrapDegrees(
                (
                    -(
                        MathHelper.atan2(
                            e,
                            g,
                        ) * (180.0 / Math.PI)
                    )
                ),
            )
        val yaw =
            MathHelper.wrapDegrees(
                (
                    MathHelper.atan2(
                        f,
                        d,
                    ) * (180.0 / Math.PI)
                ) - 90.0,
            )
        return CameraRoll(yaw, pitch)
    }

    private var rollVelocity = CameraRoll(0.0, 0.0)
    private var duration = (1000 / 30).toLong()
    private var time = System.currentTimeMillis()

    open fun atSuccess() {}

    open fun atFailure() {}

    /**
     * AimTaskを1ティック（フレーム）処理します。
     * @param client MinecraftClientインスタンス
     * @return エイム処理の結果
     */
    fun process(client: MinecraftClient): AimProcessResult {
        val currentTime = System.currentTimeMillis()
        duration = currentTime - time
        time = currentTime
        val condition = condition.check()
        val player = client.player ?: return AimProcessResult.Failure
        val targetPos = target.pos() ?: return AimProcessResult.Failure
        when (condition) {
            AimTaskConditionReturn.Success -> {
                return AimProcessResult.Success
            }

            AimTaskConditionReturn.Failure -> {
                return AimProcessResult.Failure
            }

            AimTaskConditionReturn.Suspend -> {
                return AimProcessResult.Progress
            }

            AimTaskConditionReturn.Exec -> {
                val rollDiff =
                    if (target is AimTarget.RollTarget) {
                        ((target as AimTarget.RollTarget).roll - playerRoll(player)).diffNormalize()
                    } else {
                        calculateRotation(player, targetPos)
                    }
                rollVelocity = calcExecRotation(rollDiff, calcMethod)
                rollAim(player, rollVelocity)
                return AimProcessResult.Progress
            }

            AimTaskConditionReturn.Force -> {
                val targetRoll = calcLookAt(targetPos)
                setAim(player, targetRoll)
                return AimProcessResult.Success
            }
        }
    }

    private fun calcExecRotation(
        rollDiff: CameraRoll,
        calcMethod: AimCalculateMethod,
    ): CameraRoll {
        // 【修正点2】マウス感度にmultiply値を乗算し、エイム速度を調整する
        val scaledSensitivity = (mouseSensitivity().coerceAtLeast(0.1)) * multiply
        val result =
            when (calcMethod) {
                AimCalculateMethod.EaseOut -> {
                    val scaler = 200
                    val diffMultiply = (duration * scaledSensitivity / scaler).coerceAtMost(1.0)
                    rollDiff * diffMultiply
                }

                AimCalculateMethod.Linear -> {
                    val scaler = 10
                    val maxSpeed = (duration * scaledSensitivity / scaler)
                    rollDiff.limitedBySpeed(maxSpeed)
                }

                AimCalculateMethod.EaseIn -> {
                    val currentMagnitude = rollVelocity.magnitude()
                    val targetMagnitude = rollDiff.magnitude()
                    val acceleration = scaledSensitivity / 2
                    if (currentMagnitude < targetMagnitude) {
                        rollDiff.limitedBySpeed(currentMagnitude + acceleration)
                    } else {
                        rollDiff
                    }
                }

                AimCalculateMethod.EaseInOut -> {
                    val easeIn = calcExecRotation(rollDiff, AimCalculateMethod.EaseIn)
                    val easeOut = calcExecRotation(rollDiff, AimCalculateMethod.EaseOut)
                    val easeInMagnitude = easeIn.magnitude()
                    val easeOutMagnitude = easeOut.magnitude()
                    if (easeOutMagnitude > easeInMagnitude) {
                        easeIn
                    } else {
                        easeOut
                    }
                }

                AimCalculateMethod.Immediate -> rollDiff
            }
        return result.diffNormalize()
    }

    /**
     * プレイヤーの現在の視線と目標座標から必要な回転量を計算します。
     * @return (Target Yaw, Target Pitch)
     */
    private fun calculateRotation(
        player: ClientPlayerEntity,
        targetPos: Vec3d,
    ): CameraRoll {
        val t = calcLookAt(targetPos)
        val c = playerRoll(player)
        return (t - c).diffNormalize()
    }

    private fun playerRoll(player: ClientPlayerEntity): CameraRoll = CameraRoll(player.yaw.toDouble(), player.pitch.toDouble())

    /**
     * 進行度に基づき、開始角度から目標角度へプレイヤーの視線を補間・設定します。
     */
    private fun setAim(
        player: ClientPlayerEntity,
        roll: CameraRoll,
    ) {
        player.yaw = roll.yaw.toFloat()
        player.pitch = roll.pitch.toFloat()
    }

    private fun rollAim(
        player: ClientPlayerEntity,
        roll: CameraRoll,
    ) {
        val currentYaw = player.yaw
        val currentPitch = player.pitch
        setAim(player, CameraRoll(currentYaw + roll.yaw, currentPitch + roll.pitch))
    }
}
