package org.infinite.features.automatic.pilot

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.MathHelper
import org.infinite.InfiniteClient
import org.infinite.libs.client.player.fighting.aim.AimCalculateMethod
import org.infinite.libs.client.player.fighting.aim.AimPriority
import org.infinite.libs.client.player.fighting.aim.AimTarget
import org.infinite.libs.client.player.fighting.aim.AimTask
import org.infinite.libs.client.player.fighting.aim.AimTaskCondition
import org.infinite.libs.client.player.fighting.aim.AimTaskConditionReturn
import org.infinite.libs.client.player.fighting.aim.CameraRoll
import kotlin.math.sqrt

/**
 * 自動操縦のための AimTask 定義。
 */
class AutoPilotAimTask(
    state: PilotState,
    location: Location,
    bestLandingSpot: LandingSpot? = null, // 【変更】追加
) : AimTask(
        AimPriority.Normally,
        PilotAimTarget(
            state,
            location,
            bestLandingSpot, // 【変更】
        ),
        AutoPilotCondition(state),
        AimCalculateMethod.Linear,
        if (state == PilotState.Landing) 4.0 else 2.0,
    )

/**
 * AimTask の実行条件を定義するクラス。
 * 目標速度/条件に達したかをチェックし、AimTask の継続/終了を決定します。
 */
class AutoPilotCondition(
    val state: PilotState,
) : AimTaskCondition {
    private val autoPilot: AutoPilot
        get() = InfiniteClient.getFeature(AutoPilot::class.java)!!
    val player: ClientPlayerEntity?
        get() = MinecraftClient.getInstance().player

    /**
     * 実行条件をチェックします。
     */
    override fun check(): AimTaskConditionReturn =
        when (state) {
            PilotState.Idle, PilotState.SuperFlying -> AimTaskConditionReturn.Failure
            PilotState.RiseFlying -> handleRiseFlying()
            PilotState.FallFlying -> handleFallFlying()
            PilotState.Gliding -> handleGliding()
            PilotState.Circling -> handleCircling() // 【新規】
            PilotState.Landing -> handleLanding() // 【変更】
        }

    private fun handleGliding(): AimTaskConditionReturn {
        val heightThreshold = autoPilot.standardHeight.value
        autoPilot.aimTaskCallBack =
            if (autoPilot.isDisabled()) {
                AimTaskConditionReturn.Failure
            } else if (autoPilot.height > heightThreshold) {
                AimTaskConditionReturn.Exec
            } else {
                AimTaskConditionReturn.Success
            }
        return autoPilot.aimTaskCallBack!!
    }

    /**
     * 上昇 (RiseFlying) 中の条件処理。
     */
    private fun handleRiseFlying(): AimTaskConditionReturn {
        val minSpeedThreshold = 1.0 // 速度が目標値を超えたと解釈

        autoPilot.aimTaskCallBack =
            if (autoPilot.isDisabled()) {
                AimTaskConditionReturn.Failure
            } else if (autoPilot.flySpeed > minSpeedThreshold) {
                AimTaskConditionReturn.Exec
            } else {
                AimTaskConditionReturn.Success
            }
        return autoPilot.aimTaskCallBack!!
    }

    /**
     * 下降 (FallFlying) 中の条件処理。
     */
    private fun handleFallFlying(): AimTaskConditionReturn {
        val maxSpeedThreshold = 2.2 // 速度が目標値に達したと解釈

        autoPilot.aimTaskCallBack =
            if (autoPilot.isDisabled()) {
                AimTaskConditionReturn.Failure
            } else if (autoPilot.flySpeed < maxSpeedThreshold) {
                AimTaskConditionReturn.Exec
            } else {
                AimTaskConditionReturn.Success
            }
        return autoPilot.aimTaskCallBack!!
    }

    /**
     * 【新規】旋回 (Circling) 中の条件処理。
     * 最適な着陸地点が見つかったら `Success` を返す。
     */
    private fun handleCircling(): AimTaskConditionReturn {
        autoPilot.aimTaskCallBack =
            if (autoPilot.isDisabled() || player == null) {
                AimTaskConditionReturn.Failure
            } else if (autoPilot.bestLandingSpot != null) {
                val landingSpot = autoPilot.bestLandingSpot!!
                val horizontalDistance = landingSpot.horizontalDistance()
                if (horizontalDistance < 100.0) { // 100ブロック以内に入ったら着陸フェーズへ移行
                    AimTaskConditionReturn.Success // Landing へ移行
                } else {
                    AimTaskConditionReturn.Exec
                }
            } else {
                AimTaskConditionReturn.Exec // 旋回を継続
            }
        return autoPilot.aimTaskCallBack!!
    }

    /**
     * 【変更】着陸 (Landing) 中の条件処理。
     * 速度と高度が十分に落ちた場合に成功とします。
     */
    private fun handleLanding(): AimTaskConditionReturn {
        autoPilot.aimTaskCallBack =
            if (autoPilot.isDisabled() || player == null) {
                AimTaskConditionReturn.Failure
            } else {
                val landingSpot = autoPilot.bestLandingSpot
                if (landingSpot != null) {
                    val verticalDistance = player!!.y - landingSpot.y
                    val verticalVelocity = player!!.velocity.y

                    // Check if player is close to the ground and vertical velocity is minimal
                    val isCloseToGround = kotlin.math.abs(verticalDistance) < 2.0 // Within 2 blocks of target Y
                    val isVerticalVelocityMinimal = kotlin.math.abs(verticalVelocity) < 0.1 // Close to 0 vertical speed

                    if (isCloseToGround && isVerticalVelocityMinimal) {
                        AimTaskConditionReturn.Success // Successfully landed
                    } else {
                        AimTaskConditionReturn.Exec // Continue landing
                    }
                } else {
                    // If no bestLandingSpot, fall back to original condition or fail
                    if (player!!.isOnGround && !player!!.isGliding) {
                        AimTaskConditionReturn.Success
                    } else {
                        AimTaskConditionReturn.Exec
                    }
                }
            }
        return autoPilot.aimTaskCallBack!!
    }
}

class PilotAimTarget(
    val state: PilotState,
    val target: Location,
    val bestLandingSpot: LandingSpot? = null, // 【変更】追加
) : AimTarget.RollTarget(CameraRoll(0.0, 0.0)) {
    // player の取得をゲッターに変更
    private val player: ClientPlayerEntity
        get() = MinecraftClient.getInstance().player!!
    private val autoPilot: AutoPilot
        get() = InfiniteClient.getFeature(AutoPilot::class.java)!!

    override val roll: CameraRoll
        get() {
            return CameraRoll(
                when (state) {
                    PilotState.Circling -> calculateCirclingYaw() // 【変更】旋回中は専用のヨー角
                    PilotState.Landing -> calculateLandingYaw() // 【変更】着陸中は専用のヨー角
                    else -> calculateTargetYaw()
                },
                when (state) {
                    PilotState.Landing -> handleLandingPitch()
                    PilotState.Circling -> autoPilot.glidingDir.value / 2.0 // 緩やかに降下しながら旋回
                    PilotState.FallFlying -> autoPilot.fallDir.value
                    PilotState.RiseFlying -> autoPilot.riseDir.value
                    PilotState.Gliding -> autoPilot.glidingDir.value
                    else -> 0.0 // その他の状態
                },
            )
        }

    /**
     * ターゲットへの方向を計算し、目標のヨー角 (YAW) を返します。
     */
    private fun calculateTargetYaw(): Double {
        val currentPlayer = player
        val d = target.x - currentPlayer.x
        val f = target.z - currentPlayer.z
        return MathHelper.wrapDegrees((MathHelper.atan2(f, d) * (180.0 / Math.PI)) - 90.0)
    }

    /**
     * 【新規】旋回時のヨー角 (YAW) を計算します。
     * ターゲット中心のヨー角にオフセットを加えることで旋回させます。
     */
    private fun calculateCirclingYaw(): Double {
        val currentPlayer = player
        val d = target.x - currentPlayer.x
        val f = target.z - currentPlayer.z

        // ターゲット中心のヨー角 (ターゲットを向く角度)
        val yawToCenter = MathHelper.wrapDegrees((MathHelper.atan2(f, d) * (180.0 / Math.PI)) - 90.0)

        // 旋回のためのオフセット (左手に見ながら回る = +90度)
        val circlingOffset = 90.0

        return MathHelper.wrapDegrees(yawToCenter + circlingOffset)
    }

    /**
     * 【新規】着陸時のヨー角 (YAW) を計算します。
     * 見つかった着陸地点 (bestLandingSpot) の方向を向きます。
     */
    private fun calculateLandingYaw(): Double {
        val landingSpot = bestLandingSpot ?: return calculateTargetYaw() // 見つからなければ通常のターゲットへ

        val currentPlayer = player
        val d = landingSpot.x - currentPlayer.x
        val f = landingSpot.z - currentPlayer.z

        return MathHelper.wrapDegrees((MathHelper.atan2(f, d) * (180.0 / Math.PI)) - 90.0)
    }

    /**
     * 【変更】着陸時のピッチ角を計算します。
     *
     * 【修正】着陸時のピッチ角を計算します。
     * 距離に応じて目標ピッチを調整します。
     *
     * 【改善】着陸地点への到達をシミュレートし、より正確なピッチ調整を行います。
     * 現在の速度を考慮して、予測される軌道に基づいてピッチを調整します。
     */
    private fun handleLandingPitch(): Double {
        val currentPlayer = player
        val landingSpot = bestLandingSpot ?: return autoPilot.fallDir.value

        val targetX = landingSpot.x.toDouble()
        val targetY = landingSpot.y.toDouble() // Aim directly at the landing spot's Y coordinate
        val targetZ = landingSpot.z.toDouble()

        val dX = targetX - currentPlayer.x
        val dY = targetY - currentPlayer.y
        val dZ = targetZ - currentPlayer.z

        // 水平距離
        val horizontalDistance = sqrt(dX * dX + dZ * dZ)

        // ----------------------------------------------------
        // 予測軌道に基づくピッチ調整
        // ----------------------------------------------------
        val predictionTimeTicks = 20.0 // 1秒先の予測 (20 ticks)
        val gravityPerTick = player.finalGravity // Minecraftの重力加速度 (blocks/tick^2)

        // 現在の速度を考慮した予測位置
        val predictedPlayerX = currentPlayer.x + currentPlayer.velocity.x * predictionTimeTicks
        val predictedPlayerY =
            currentPlayer.y + currentPlayer.velocity.y * predictionTimeTicks +
                0.5 * gravityPerTick * predictionTimeTicks * predictionTimeTicks
        val predictedPlayerZ = currentPlayer.z + currentPlayer.velocity.z * predictionTimeTicks

        // 予測位置から目標地点までの相対距離
        val dXPredicted = targetX - predictedPlayerX
        val dYPredicted = targetY - predictedPlayerY
        val dZPredicted = targetZ - predictedPlayerZ

        val horizontalDistancePredicted = sqrt(dXPredicted * dXPredicted + dZPredicted * dZPredicted)

        // Avoid division by zero if horizontalDistancePredicted is very small
        if (horizontalDistancePredicted < 0.1) {
            // If very close horizontally, focus on vertical alignment
            return MathHelper.clamp(
                -Math.toDegrees(MathHelper.atan2(dYPredicted, 0.1)), // Use a small horizontal distance to avoid NaN
                -60.0,
                45.0,
            )
        }

        // Calculate the direct pitch angle to the landing spot from the predicted position
        val directPitchToLandingSpot = Math.toDegrees(MathHelper.atan2(dYPredicted, horizontalDistancePredicted))

        // ----------------------------------------------------
        // 1. 速度に応じたピッチ調整 (既存ロジックを維持しつつ、予測ピッチとブレンド)
        // ----------------------------------------------------
        val maxSpeed = 20.0 // 目標速度 (調整可能)
        val speedFactor = MathHelper.clamp(autoPilot.moveSpeedAverage / maxSpeed, 0.0, 1.0)

        val speedAdjustedPitch = autoPilot.fallDir.value - (speedFactor * 20.0) // 20.0 は調整係数

        // ----------------------------------------------------
        // 2. 距離に応じたピッチ調整 (予測ピッチと速度調整ピッチをブレンド)
        // ----------------------------------------------------
        val minHorizontalDistance = 20.0 // この距離以下になると予測ピッチを優先 (調整可能)
        val maxHorizontalDistance = 100.0 // この距離以上は速度調整ピッチを優先 (調整可能)

        // 水平距離の係数。近いほど0に、遠いほど1に近づく
        val distanceFactor =
            MathHelper.clamp(
                (horizontalDistance - minHorizontalDistance) / (maxHorizontalDistance - minHorizontalDistance),
                0.0,
                1.0,
            )

        // Blend direct pitch (from predicted position) with speed-adjusted pitch based on distance
        // When far, prioritize speed adjustment. When close, prioritize direct pitch.
        var pitch = (directPitchToLandingSpot * (1.0 - distanceFactor)) + (speedAdjustedPitch * distanceFactor)

        // ----------------------------------------------------
        // 3. 垂直方向の調整 (微調整)
        // ----------------------------------------------------
        // This is a fine-tuning step, less aggressive now that directPitchToLandingSpot is predictive.
        val verticalAlignmentThreshold = 5.0 // This threshold is for fine-tuning near the target Y
        val verticalFactor = MathHelper.clamp(dY / verticalAlignmentThreshold, -1.0, 1.0)

        pitch -= verticalFactor * 5.0 // Reduced from 10.0 to 5.0 for subtle correction

        // ----------------------------------------------------
        // 4. 最終クランプ
        // ----------------------------------------------------
        val maxNegativePitch = -14.0 // 最大の上昇ピッチ (より急な上昇を許容)
        val minPositivePitch = 45.0 // 最大の下降ピッチ (より急な下降を許容)

        // 最終的なピッチをクランプ
        return MathHelper.clamp(pitch, maxNegativePitch, minPositivePitch)
    }
}
