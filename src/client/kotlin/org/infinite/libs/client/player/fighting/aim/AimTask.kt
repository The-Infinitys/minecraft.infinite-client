package org.infinite.libs.client.player.fighting.aim

import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

sealed class AimTarget {
    data class EntityTarget(
        val entity: Entity,
    ) : AimTarget()

    data class BlockTarget(
        val block: BlockEntity,
    ) : AimTarget()

    data class WaypointTarget(
        val pos: Vec3d,
    ) : AimTarget()
}

enum class AimPriority {
    Immediately,
    Preferentially,
    Normally,
}

enum class AimProcessResult {
    Progress, // 進行中
    Success, // 成功して完了
    Failure, // 失敗（ターゲット消失など）
}

enum class AimCalculateMethod {
    Linear, // 線形補間
    EaseIn, // 加速
    EaseOut, // 減速
    EaseInOut, // 両端での加速・減速
}

class CameraRoll(
    var yaw: Double,
    var pitch: Double,
) {
    /**
     * CameraRoll同士の足し算 (要素ごと)
     * operator fun plus(other: CameraRoll): CameraRoll
     */
    operator fun plus(other: CameraRoll): CameraRoll =
        CameraRoll(
            yaw = this.yaw + other.yaw,
            pitch = this.pitch + other.pitch,
        )

    /**
     * CameraRoll同士の引き算 (要素ごと)
     * operator fun minus(other: CameraRoll): CameraRoll
     */
    operator fun minus(other: CameraRoll): CameraRoll =
        CameraRoll(
            yaw = this.yaw - other.yaw,
            pitch = this.pitch - other.pitch,
        )

    operator fun times(scalar: Number): CameraRoll {
        val s = scalar.toDouble()
        return CameraRoll(
            yaw = this.yaw * s,
            pitch = this.pitch * s,
        )
    }

    operator fun div(scalar: Number): CameraRoll {
        val s = scalar.toDouble()
        if (s == 0.0) return CameraRoll(0.0, 0.0)
        return CameraRoll(
            yaw = this.yaw / s,
            pitch = this.pitch / s,
        )
    }

    fun magnitude(): Double = sqrt(this.yaw * this.yaw + this.pitch * this.pitch)

    /**
     * 最大回転速度 (maxSpeed) で移動量を制限したCameraRollを返します。
     * @param maxSpeed 最大回転速度
     * @return 制限後のCameraRoll
     */
    fun limitedBySpeed(maxSpeed: Double): CameraRoll {
        // maxSpeedが非負であることを保証 (念のため)
        require(maxSpeed >= 0.0) { "maxSpeed must be non-negative." }

        // 現在の移動量 (ノルム) を計算
        val magnitude = magnitude()
        // ノルムがmaxSpeed以下であれば、そのまま返す
        if (magnitude <= maxSpeed) {
            return this
        }

        // ノルムが0、またはmaxSpeedが0の場合は、(0, 0)を返す
        if (maxSpeed == 0.0) {
            return CameraRoll(0.0, 0.0)
        }

        // maxSpeedで制限するためにスケーリング係数を計算し、適用
        val scale = maxSpeed / magnitude
        return this * scale // 'times' operator (this.times(scale)) を使用
    }

    fun vec(): Vec3d {
        // 1. 角度をラジアンに変換 (度数で格納されていると仮定)
        // 既にラジアンで格納されている場合は、この行をコメントアウトしてください。
        val yawRad = this.yaw * PI / 180.0
        val pitchRad = this.pitch * PI / 180.0

        // 2. 角度から方向ベクトルを計算
        // Y軸が上方向、X-Z平面が水平面、+Xが初期前方と仮定した一般的な計算式
        val cosPitch = cos(pitchRad)
        val x = -sin(yawRad) * cosPitch
        val y = -sin(pitchRad) // Y軸は上下の回転(Pitch)のみに依存
        val z = cos(yawRad) * cosPitch

        // 結果は自動的に正規化されます (sin^2 + cos^2 = 1 のため)
        return Vec3d(x, y, z)
    }

    fun diffNormalize(): CameraRoll = CameraRoll(MathHelper.wrapDegrees(yaw), MathHelper.wrapDegrees(pitch))
}

enum class AimTaskConditionReturn {
    Suspend,
    Exec,
    Success,
    Failure,
    Force,
}

interface AimTaskCondition {
    fun check(): AimTaskConditionReturn
}

class ImmediatelyAimCondition : AimTaskCondition {
    override fun check(): AimTaskConditionReturn = AimTaskConditionReturn.Force
}

class AimConditionByFrame(
    val reactFrame: Int = 6,
    val totalFrame: Int = 9,
    val force: Boolean,
) : AimTaskCondition {
    private var frame = -1

    override fun check(): AimTaskConditionReturn {
        frame++
        return if (frame < reactFrame) {
            AimTaskConditionReturn.Suspend
        } else if (frame >= totalFrame) {
            if (force) {
                AimTaskConditionReturn.Force
            } else {
                AimTaskConditionReturn.Failure
            }
        } else {
            AimTaskConditionReturn.Exec
        }
    }
}

open class AimTask(
    open val priority: AimPriority,
    open val target: AimTarget,
    open val condition: AimTaskCondition,
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
        val targetPos = targetPos(target) ?: return AimProcessResult.Failure
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
                val rollDiff = calculateRotation(player, targetPos)
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
            }
        return result.diffNormalize()
    }

    /**
     * ターゲットの種類に応じて目標座標（Vec3d）を取得します。
     */
    private fun targetPos(target: AimTarget): Vec3d? =
        when (target) {
            is AimTarget.EntityTarget -> {
                target.entity.eyePos
            }

            is AimTarget.BlockTarget -> {
                target.block.pos.toCenterPos()
            }

            is AimTarget.WaypointTarget -> {
                target.pos
            }
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
        val c = CameraRoll(player.yaw.toDouble(), player.pitch.toDouble())
        return (t - c).diffNormalize()
    }

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
