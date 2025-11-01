package org.infinite.features.automatic.pilot

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.vehicle.BoatEntity
import net.minecraft.util.math.MathHelper
import org.infinite.InfiniteClient
import org.infinite.libs.client.player.aim.AimCalculateMethod
import org.infinite.libs.client.player.aim.AimPriority
import org.infinite.libs.client.player.aim.AimTarget
import org.infinite.libs.client.player.aim.AimTask
import org.infinite.libs.client.player.aim.AimTaskCondition
import org.infinite.libs.client.player.aim.AimTaskConditionReturn
import org.infinite.libs.client.player.aim.CameraRoll
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 自動操縦のための AimTask 定義。
 */
class AutoPilotAimTask(
    state: PilotState,
    bestLandingSpot: LandingSpot? = null,
) : AimTask(
        if (state == PilotState.EmergencyLanding) AimPriority.Preferentially else AimPriority.Normally,
        PilotAimTarget(
            state,
            bestLandingSpot,
        ),
        AutoPilotCondition(state),
        AimCalculateMethod.Linear,
        when (state) {
            PilotState.EmergencyLanding -> 16.0
            PilotState.Landing -> 4.0
            PilotState.TakingOff -> 2.0 // 【新規】離陸時のスムーズな移動
            else -> 2.0
        },
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
    private val player: ClientPlayerEntity?
        get() = MinecraftClient.getInstance().player
    private var tickCounter = 0

    /**
     * 実行条件をチェックします。
     */
    override fun check(): AimTaskConditionReturn =
        if (state != autoPilot.state) {
            AimTaskConditionReturn.Success
        } else if (tickCounter >= 20) {
            autoPilot.aimTaskCallBack = null
            AimTaskConditionReturn.Success
        } else {
            tickCounter++
            when (state) {
                PilotState.Idle -> AimTaskConditionReturn.Failure
                PilotState.RiseFlying -> handleRiseFlying()
                PilotState.FallFlying -> handleFallFlying()
                PilotState.Gliding -> handleGliding()
                PilotState.Circling -> handleCircling()
                PilotState.Landing -> handleLanding()
                PilotState.EmergencyLanding -> handleEmergencyLanding()
                PilotState.JetFlying, PilotState.HoverFlying -> handleJetFlying()
                PilotState.TakingOff -> handleTakingOff() // 【新規】離陸処理
            }
        }

    private fun handleJetFlying(): AimTaskConditionReturn {
        val distanceThreshold = autoPilot.landingStartDistance
        val currentDistance = autoPilot.target.distance()

        if (autoPilot.jetAcceleration.value == 0.0) {
            autoPilot.aimTaskCallBack = AimTaskConditionReturn.Success
        } else if (currentDistance < distanceThreshold) {
            autoPilot.aimTaskCallBack = AimTaskConditionReturn.Success
        } else {
            autoPilot.aimTaskCallBack = AimTaskConditionReturn.Exec
        }
        return autoPilot.aimTaskCallBack!!
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

    private fun handleRiseFlying(): AimTaskConditionReturn {
        val minSpeedThreshold = 1.0
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

    private fun handleFallFlying(): AimTaskConditionReturn {
        val maxSpeedThreshold = 2.2
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

    private fun handleCircling(): AimTaskConditionReturn {
        autoPilot.aimTaskCallBack =
            if (autoPilot.isDisabled() || player == null) {
                AimTaskConditionReturn.Failure
            } else if (autoPilot.bestLandingSpot != null) {
                val landingSpot = autoPilot.bestLandingSpot!!
                val horizontalDistance = landingSpot.horizontalDistance()
                if (horizontalDistance < 100.0) {
                    AimTaskConditionReturn.Success
                } else {
                    AimTaskConditionReturn.Exec
                }
            } else {
                AimTaskConditionReturn.Exec
            }
        return autoPilot.aimTaskCallBack!!
    }

    /** 【修正適用】着陸判定のしきい値を緩和し、ボートの静止判定を強化 */
    private fun handleLanding(): AimTaskConditionReturn {
        autoPilot.aimTaskCallBack =
            if (autoPilot.isDisabled() || player == null) {
                AimTaskConditionReturn.Failure
            } else {
                // 水平速度のしきい値を 0.3 に緩和 (静止判定の改善)
                val minimalSpeedThreshold = 0.3

                val isHorizontalVelocityMinimal =
                    if (player!!.vehicle is BoatEntity) {
                        player!!.vehicle!!.velocity.horizontalLength() < minimalSpeedThreshold
                    } else {
                        player!!.velocity.horizontalLength() < minimalSpeedThreshold
                    }

                // 垂直速度の最小判定 (ボート/非ボート共通)
                val isVerticalVelocityMinimal = kotlin.math.abs(player!!.velocity.y) < 0.1

                // 1. bestLandingSpot が設定されている場合の精密着陸判定
                if (autoPilot.bestLandingSpot != null) {
                    // ボートの場合: 垂直距離が 1.5 ブロック以内、または陸地に乗っている
                    // 非ボートの場合: SafeFallDistance 以内、または陸地に乗っている
                    val isCloseToLandingSpot =
                        (player!!.vehicle is BoatEntity && player!!.velocity.length() < 1.5) || player!!.isOnGround

                    if (isCloseToLandingSpot && isVerticalVelocityMinimal && isHorizontalVelocityMinimal) {
                        AimTaskConditionReturn.Success
                    } else {
                        AimTaskConditionReturn.Exec
                    }
                } else {
                    // 速度が最小限であり、かつ以下のいずれかを満たす場合に成功:
                    //   a) プレイヤーが陸上にいる (isOnGround)
                    //   b) ボートに乗っており、水に触れている (isTouchingWater)
                    if (isHorizontalVelocityMinimal &&
                        isVerticalVelocityMinimal &&
                        (player!!.isOnGround || (player!!.vehicle is BoatEntity && player!!.isTouchingWater))
                    ) {
                        AimTaskConditionReturn.Success
                    } else {
                        AimTaskConditionReturn.Exec
                    }
                }
            }
        return autoPilot.aimTaskCallBack!!
    }

    private fun handleEmergencyLanding(): AimTaskConditionReturn {
        autoPilot.aimTaskCallBack =
            if (autoPilot.isDisabled() || player == null) {
                AimTaskConditionReturn.Failure
            } else {
                val isCloseToGround = player!!.isOnGround || player!!.isTouchingWater
                if (isCloseToGround) {
                    AimTaskConditionReturn.Success
                } else {
                    AimTaskConditionReturn.Exec
                }
            }
        if (autoPilot.aimTaskCallBack != AimTaskConditionReturn.Exec) {
            MinecraftClient
                .getInstance()
                .options
                ?.sneakKey
                ?.isPressed = false
        }
        return autoPilot.aimTaskCallBack!!
    }

    /** 【新規】離陸時の条件処理。標準高度に達したら成功。 */
    private fun handleTakingOff(): AimTaskConditionReturn {
        autoPilot.aimTaskCallBack =
            if (autoPilot.isDisabled() || player == null || player!!.vehicle !is BoatEntity) {
                AimTaskConditionReturn.Failure
            } else if (autoPilot.height >= autoPilot.standardHeight.value) {
                AimTaskConditionReturn.Success
            } else {
                AimTaskConditionReturn.Exec
            }
        return autoPilot.aimTaskCallBack!!
    }
}

class PilotAimTarget(
    val state: PilotState,
    val bestLandingSpot: LandingSpot? = null,
) : AimTarget.RollTarget(CameraRoll(0.0, 0.0)) {
    val target: Location
        get() = autoPilot.target
    private val player: ClientPlayerEntity
        get() = MinecraftClient.getInstance().player!!
    private val autoPilot: AutoPilot
        get() = InfiniteClient.getFeature(AutoPilot::class.java)!!

    override val roll: CameraRoll
        get() {
            return CameraRoll(
                when (state) {
                    PilotState.Circling -> calculateCirclingYaw()
                    PilotState.Landing -> calculateLandingYaw()
                    PilotState.EmergencyLanding -> calculateEmergencyYaw()
                    PilotState.TakingOff -> calculateTargetYaw() // 【新規】離陸時はターゲット方向
                    else -> calculateTargetYaw()
                },
                when (state) {
                    PilotState.Landing -> handleLandingPitch()
                    PilotState.EmergencyLanding -> handleEmergencyLandingPitch()
                    PilotState.Circling -> autoPilot.glidingDir.value / 2.0
                    PilotState.FallFlying -> autoPilot.fallDir.value
                    PilotState.RiseFlying -> autoPilot.riseDir.value
                    PilotState.Gliding -> autoPilot.glidingDir.value
                    PilotState.TakingOff -> autoPilot.riseDir.value // 【新規】離陸時は上昇ピッチ
                    else -> 0.0
                },
            )
        }

    private fun calculateEmergencyYaw(): Double = player.yaw + if (emergencyLandFlag) 60.0 else 0.0

    private fun calculateTargetYaw(): Double {
        val currentPlayer = player
        val d = target.x - currentPlayer.x
        val f = target.z - currentPlayer.z
        return MathHelper.wrapDegrees((MathHelper.atan2(f, d) * (180.0 / Math.PI)) - 90.0)
    }

    private fun calculateCirclingYaw(): Double {
        val currentPlayer = player
        val d = target.x - currentPlayer.x
        val f = target.z - currentPlayer.z
        val yawToCenter = MathHelper.wrapDegrees((MathHelper.atan2(f, d) * (180.0 / Math.PI)) - 90.0)
        val circlingOffset = 90.0
        return MathHelper.wrapDegrees(yawToCenter + circlingOffset)
    }

    private fun calculateLandingYaw(): Double {
        val landingSpot = bestLandingSpot ?: return calculateTargetYaw()
        val currentPlayer = player
        val d = landingSpot.x - currentPlayer.x
        val f = landingSpot.z - currentPlayer.z
        return MathHelper.wrapDegrees((MathHelper.atan2(f, d) * (180.0 / Math.PI)) - 90.0)
    }

    private fun handleLandingPitch(): Double {
        val currentPlayer = player
        val landingSpot = bestLandingSpot ?: return autoPilot.fallDir.value

        val targetX = landingSpot.x.toDouble()
        val targetY = landingSpot.y.toDouble()
        val targetZ = landingSpot.z.toDouble()

        val dX = targetX - currentPlayer.x
        val dY = targetY - currentPlayer.y
        val dZ = targetZ - currentPlayer.z

        val horizontalDistance = sqrt(dX * dX + dZ * dZ)
        val predictionTimeTicks = 20.0
        val gravityPerTick = if (currentPlayer.vehicle is BoatEntity) 0.0 else currentPlayer.finalGravity // ボートでは重力を無視

        val predictedPlayerX = currentPlayer.x + currentPlayer.velocity.x * predictionTimeTicks
        val predictedPlayerY =
            currentPlayer.y + currentPlayer.velocity.y * predictionTimeTicks +
                0.5 * gravityPerTick * predictionTimeTicks * predictionTimeTicks
        val predictedPlayerZ = currentPlayer.z + currentPlayer.velocity.z * predictionTimeTicks

        val dXPredicted = targetX - predictedPlayerX
        val dYPredicted = targetY - predictedPlayerY
        val dZPredicted = targetZ - predictedPlayerZ

        val horizontalDistancePredicted = sqrt(dXPredicted * dXPredicted + dZPredicted * dZPredicted)

        if (horizontalDistancePredicted < 0.1) {
            return MathHelper.clamp(
                -Math.toDegrees(MathHelper.atan2(dYPredicted, 0.1)),
                -60.0,
                45.0,
            )
        }

        val directPitchToLandingSpot = Math.toDegrees(MathHelper.atan2(dYPredicted, horizontalDistancePredicted))
        val maxSpeed = 20.0
        val speedFactor = MathHelper.clamp(autoPilot.moveSpeedAverage / maxSpeed, 0.0, 1.0)
        val speedAdjustedPitch = autoPilot.fallDir.value - (speedFactor * 20.0)

        val minHorizontalDistance = 20.0
        val maxHorizontalDistance = 100.0
        val distanceFactor =
            MathHelper.clamp(
                (horizontalDistance - minHorizontalDistance) / (maxHorizontalDistance - minHorizontalDistance),
                0.0,
                1.0,
            )

        var pitch = (directPitchToLandingSpot * (1.0 - distanceFactor)) + (speedAdjustedPitch * distanceFactor)
        val verticalAlignmentThreshold = if (currentPlayer.vehicle is BoatEntity) 2.0 else 5.0 // ボートではより近い高度で調整
        val verticalFactor = MathHelper.clamp(dY / verticalAlignmentThreshold, -1.0, 1.0)
        pitch -= verticalFactor * (if (currentPlayer.vehicle is BoatEntity) 2.0 else 5.0) // ボートではより繊細な調整

        val maxNegativePitch = -14.0
        val minPositivePitch = 45.0
        return MathHelper.clamp(pitch, maxNegativePitch, minPositivePitch)
    }

    private var emergencyLandFlag = false

    private fun handleEmergencyLandingPitch(): Double {
        val currentPlayer = player
        val currentY = currentPlayer.y
        val verticalVelocity = currentPlayer.velocity.y
        val gravity = if (currentPlayer.vehicle is BoatEntity) 0.0 else currentPlayer.finalGravity // ボートでは重力を無視

        val groundY = bestLandingSpot?.y?.toDouble() ?: findDynamicGroundY()

        var timeToImpactTicks = Double.MAX_VALUE
        if (gravity > 0 && verticalVelocity < 0) {
            val a = 0.5 * gravity
            val c = currentY - groundY
            val discriminant = verticalVelocity * verticalVelocity - 4 * a * c
            if (discriminant >= 0) {
                val sqrtDiscriminant = sqrt(discriminant)
                val t1 = (-verticalVelocity - sqrtDiscriminant) / (2 * a)
                val t2 = (-verticalVelocity + sqrtDiscriminant) / (2 * a)
                if (t1 > 0) timeToImpactTicks = t1
                if (t2 > 0 && t2 < timeToImpactTicks) timeToImpactTicks = t2
            }
        } else if (verticalVelocity > 0) {
            timeToImpactTicks = Double.MAX_VALUE
        } else if (verticalVelocity <= 0 && currentY <= groundY) {
            timeToImpactTicks = 0.0
        } else if (verticalVelocity < 0 && gravity == 0.0) {
            timeToImpactTicks = (groundY - currentY) / verticalVelocity
        }

        val timeToImpactSeconds = timeToImpactTicks / 20.0
        val minPitch = 0.0
        val maxPitch = 90.0
        val criticalTTI = 1.0
        val safeTTI = 2.0

        var pitch: Double
        if (timeToImpactSeconds <= criticalTTI || emergencyLandFlag) {
            emergencyLandFlag = true
            pitch = minPitch
            if (currentPlayer.vehicle !is BoatEntity) {
                MinecraftClient
                    .getInstance()
                    .options
                    ?.sneakKey
                    ?.isPressed = true
            }
        } else if (timeToImpactSeconds < safeTTI) {
            val factor = (timeToImpactSeconds - criticalTTI) / (safeTTI - criticalTTI)
            pitch = minPitch + (maxPitch - minPitch) * factor
        } else {
            pitch = maxPitch
        }

        return MathHelper.clamp(pitch, minPitch, maxPitch)
    }

    private fun findDynamicGroundY(): Double {
        val currentPlayer = player
        val world = MinecraftClient.getInstance().world ?: return 320.0
        val currentX = currentPlayer.blockX
        val currentZ = currentPlayer.blockZ
        val horizontalSpeed = autoPilot.moveSpeedAverage
        val searchDistanceBlocks = (horizontalSpeed).roundToInt().coerceAtLeast(5)

        var highestGroundY = 0.0
        for (dx in -searchDistanceBlocks..searchDistanceBlocks) {
            for (dz in -searchDistanceBlocks..searchDistanceBlocks) {
                val checkX = currentX + dx
                val checkZ = currentZ + dz
                val y = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, checkX, checkZ)
                if (y > highestGroundY) {
                    highestGroundY = y.toDouble()
                }
            }
        }
        return highestGroundY
    }
}
