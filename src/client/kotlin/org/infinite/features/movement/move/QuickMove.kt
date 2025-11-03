package org.infinite.features.movement.move

import net.minecraft.util.math.Vec3d
import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// 基準となる各環境の移動速度（ブロック/秒）を定義
class QuickMove : ConfigurableFeature() {
    override val tickTiming: TickTiming = TickTiming.End
    private val baseGroundSpeed = 4.3
    private val baseSwimmingSpeed = 5.45
    private val baseWaterWalkSpeed = 4.3 * 0.7 // 水中での歩行は地上速度の約70%
    private val baseLavaWalkSpeed = 4.3 * 0.5 // 溶岩中での歩行は地上速度の約50%
    private val baseFlightSpeed = baseGroundSpeed * 1.4
    private val acceleration =
        FeatureSetting.DoubleSetting(
            "Acceleration",
            "feature.movement.quickmove.acceleration.description",
            0.05,
            0.0,
            1.0,
        )

    private val friction =
        FeatureSetting.DoubleSetting("Friction", "feature.movement.quickmove.friction.description", 0.5, 0.0, 1.0)

    // --- 設定値は変更なし ---
    private val speedOnGround =
        FeatureSetting.DoubleSetting(
            "SpeedOnGround",
            "feature.movement.quickmove.speedonground.description",
            baseGroundSpeed * 1.4,
            baseGroundSpeed,
            baseGroundSpeed * 2.0,
        )
    private val speedInWater =
        FeatureSetting.DoubleSetting(
            "SpeedInWater",
            "feature.movement.quickmove.speedinwater.description",
            baseWaterWalkSpeed * 1.4,
            baseWaterWalkSpeed,
            baseWaterWalkSpeed * 2.0,
        )
    private val speedInLava =
        FeatureSetting.DoubleSetting(
            "SpeedInLava",
            "feature.movement.quickmove.speedinlava.description",
            baseLavaWalkSpeed * 1.4,
            baseLavaWalkSpeed,
            baseLavaWalkSpeed * 2.0,
        )
    private val speedInAir =
        FeatureSetting.DoubleSetting(
            "SpeedInAir",
            "feature.movement.quickmove.speedinair.description",
            baseFlightSpeed * 1.4,
            baseFlightSpeed,
            baseFlightSpeed * 2.0,
        )
    private val speedOnGliding =
        FeatureSetting.DoubleSetting(
            "SpeedOnGliding",
            "feature.movement.quickmove.speedongliding.description",
            baseFlightSpeed * 1.4,
            baseFlightSpeed,
            baseFlightSpeed * 2.0,
        )
    private val speedOnSwimming =
        FeatureSetting.DoubleSetting(
            "SpeedOnSwimming",
            "feature.movement.quickmove.speedonswimming.description",
            baseSwimmingSpeed * 1.4,
            baseSwimmingSpeed,
            baseSwimmingSpeed * 2.0,
        )
    private val speedWithVehicle =
        FeatureSetting.DoubleSetting(
            "SpeedWithVehicle",
            "feature.movement.quickmove.speedwithvehicle.description",
            baseFlightSpeed * 1.4,
            baseFlightSpeed,
            baseFlightSpeed * 2.0,
        )

    // --- Allow設定とSettingsリストは変更なし ---
    private val allowOnGround =
        FeatureSetting.BooleanSetting("AllowOnGround", "feature.movement.quickmove.allowonground.description", true)
    private val allowInWater =
        FeatureSetting.BooleanSetting("AllowInWater", "feature.movement.quickmove.allowinwater.description", false)
    private val allowInLava =
        FeatureSetting.BooleanSetting("AllowInLava", "feature.movement.quickmove.allowinlava.description", false)
    private val allowWithVehicle =
        FeatureSetting.BooleanSetting(
            "AllowWithVehicle",
            "feature.movement.quickmove.allowwithvehicle.description",
            false,
        )

    private val allowInAir =
        FeatureSetting.BooleanSetting(
            "AllowInAir",
            "feature.movement.quickmove.allowinair.description",
            false,
        )
    private val allowOnGliding =
        FeatureSetting.BooleanSetting(
            "AllowOnGliding",
            "feature.movement.quickmove.allowongliding.description",
            false,
        )
    private val allowOnSwimming =
        FeatureSetting.BooleanSetting(
            "AllowOnSwimming",
            "feature.movement.quickmove.allowonswimming.description",
            false,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            acceleration,
            friction,
            allowOnGround,
            speedOnGround,
            allowOnSwimming,
            speedOnSwimming,
            allowInWater,
            speedInWater,
            allowInLava,
            speedInLava,
            allowInAir,
            speedInAir,
            allowOnGliding,
            speedOnGliding,
            allowWithVehicle,
            speedWithVehicle,
        )

    override fun tick() {
        val player = player ?: return
        val options = options
        val vehicle = player.vehicle

        // 1. 環境チェックと適用除外
        if (player.hasVehicle() && !allowWithVehicle.value) {
            return
        }

        val inAir = !player.isOnGround && !player.isInLava && !player.isTouchingWater

        val shouldApply =
            when {
                (player.isTouchingWater && allowInWater.value) || (player.isInLava && allowInLava.value) -> true
                (player.isOnGround && allowOnGround.value) -> true
                (player.hasVehicle() && allowWithVehicle.value) -> true
                (allowOnSwimming.value && player.isSwimming) -> true
                (allowOnGliding.value && player.isGliding) -> true
                (inAir && allowInAir.value) -> true
                else -> false
            }

        if (!shouldApply) {
            return
        }

        vehicle?.yaw = player.yaw

        var forward = 0.0
        var strafe = 0.0

        if (options.forwardKey.isPressed) forward++
        if (options.backKey.isPressed) forward--
        if (options.leftKey.isPressed) strafe++
        if (options.rightKey.isPressed) strafe--

        var velocity = velocity ?: return

        if (forward == 0.0 && strafe == 0.0) {
            // 入力がない場合は摩擦を適用
            velocity =
                Vec3d(velocity.x * friction.value, velocity.y, velocity.z * friction.value)
        } else {
            // 2. 速度制限の決定
            val currentSpeedLimitPerSecond =
                when {
                    player.isTouchingWater && allowInWater.value -> speedInWater.value
                    player.isInLava && allowInLava.value -> speedInLava.value
                    player.isOnGround && allowOnGround.value -> speedOnGround.value
                    player.hasVehicle() && allowWithVehicle.value -> speedWithVehicle.value
                    player.isSwimming && allowOnSwimming.value -> speedOnSwimming.value
                    player.isGliding && allowOnGliding.value -> speedOnGliding.value
                    !player.isOnGround && !player.isInLava && !player.isTouchingWater && allowInAir.value -> speedInAir.value
                    else -> 0.0
                }

            // ティックあたりの最大移動距離 (ブロック/ティック)
            val tickSpeedLimit = currentSpeedLimitPerSecond / 20

            // 現在の水平速度の大きさ
            val moveSpeed = sqrt((velocity.x * velocity.x) + (velocity.z * velocity.z))

            // ***【追加したロジック】***
            // 現在の速度が既に制限を超えている場合、加速を適用せずに戻る
            if (moveSpeed >= tickSpeedLimit) {
                return
            }

            // 3. ローカル座標系での加速ベクトルの計算
            val yaw = Math.toRadians(player.yaw.toDouble())
            val sinYaw = sin(yaw)
            val cosYaw = cos(yaw)
            var vel = Vec3d.ZERO

            val inputMagnitude = sqrt(forward * forward + strafe * strafe).coerceAtLeast(1.0)
            val normalizedForward = forward / inputMagnitude
            val normalizedStrafe = strafe / inputMagnitude

            val forwardMotionX = -sinYaw * normalizedForward * acceleration.value
            val forwardMotionZ = cosYaw * normalizedForward * acceleration.value

            val strafeMotionX = cosYaw * normalizedStrafe * acceleration.value
            val strafeMotionZ = sinYaw * normalizedStrafe * acceleration.value

            vel =
                vel.add(
                    forwardMotionX + strafeMotionX,
                    0.0,
                    forwardMotionZ + strafeMotionZ,
                )
            // 4. 速度制限の適用
            // 加速後の予測水平速度の大きさ
            val predictedMoveSpeed =
                sqrt((velocity.x + vel.x) * (velocity.x + vel.x) + (velocity.z + vel.z) * (velocity.z + vel.z))
            velocity = velocity.add(vel)
            if (predictedMoveSpeed > tickSpeedLimit) {
                velocity = vel.normalize().multiply(tickSpeedLimit).add(0.0, velocity.y, 0.0)
            }
        }
        this.velocity = velocity
    }
}
