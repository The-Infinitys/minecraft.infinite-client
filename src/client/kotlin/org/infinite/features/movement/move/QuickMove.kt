package org.infinite.features.movement.move

import net.minecraft.util.math.Vec3d
import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class QuickMove : ConfigurableFeature() {
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

    private val speedOnGround =
        FeatureSetting.DoubleSetting(
            "SpeedOnGround",
            "feature.movement.quickmove.speedonground.description",
            9.6,
            8.0,
            16.0,
        )
    private val speedInWater =
        FeatureSetting.DoubleSetting(
            "SpeedInWater",
            "feature.movement.quickmove.speedinwater.description",
            9.6,
            8.0,
            16.0,
        )
    private val speedInLava =
        FeatureSetting.DoubleSetting(
            "SpeedInLava",
            "feature.movement.quickmove.speedinlava.description",
            9.6,
            8.0,
            16.0,
        )
    private val speedInAir =
        FeatureSetting.DoubleSetting("SpeedInAir", "feature.movement.quickmove.speedinair.description", 9.6, 8.0, 16.0)
    private val speedOnGliding =
        FeatureSetting.DoubleSetting(
            "SpeedOnGliding",
            "feature.movement.quickmove.speedongliding.description",
            9.6,
            8.0,
            16.0,
        )
    private val speedOnSwimming =
        FeatureSetting.DoubleSetting(
            "SpeedOnSwimming",
            "feature.movement.quickmove.speedonswimming.description",
            9.6,
            8.0,
            16.0,
        )
    private val speedWithVehicle =
        FeatureSetting.DoubleSetting(
            "SpeedWithVehicle",
            "feature.movement.quickmove.speedwithvehicle.description",
            9.6,
            8.0,
            16.0,
        )
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

        // 1. 環境チェックと適用除外

        // 乗り物に乗っている場合のチェック
        if (player.hasVehicle() && !allowWithVehicle.value) {
            return
        }

        val inAir = !player.isOnGround && !player.isInLava && !player.isTouchingWater
        // 地上、水中、溶岩中の許可設定に基づくチェック
        // - player.isSwimming は水中/溶岩中での移動判定にも使われるため、ここでは明確にisOnGround, isTouchingWater, isInLavaで判定
        val shouldApply =
            when {
                // 水中または溶岩中で、かつ許可されている場合
                (player.isTouchingWater && allowInWater.value) || (player.isInLava && allowInLava.value) -> true
                (player.isOnGround && allowOnGround.value) -> true
                (vehicle != null && allowWithVehicle.value) -> true
                (allowOnSwimming.value && player.isSwimming) -> true
                (allowOnGliding.value && player.isGliding) -> true
                (inAir && allowInAir.value) -> true
                else -> false
            }

        // 適用すべきでない場合はここでリターン
        if (!shouldApply) {
            return
        }
        // 素早い方向転換を可能にする
        vehicle?.yaw = player.yaw
        var forward = 0.0 // 進行方向 (プレイヤーのローカルZ軸)
        var strafe = 0.0 // 横移動方向 (プレイヤーのローカルX軸)

        if (options.forwardKey.isPressed) forward++
        if (options.backKey.isPressed) forward--
        if (options.leftKey.isPressed) strafe++
        if (options.rightKey.isPressed) strafe--
        velocity ?: return
        if (forward == 0.0 && strafe == 0.0) {
            velocity =
                Vec3d(velocity!!.x * friction.value, velocity!!.y, velocity!!.z * friction.value)
        } else {
            // 2. ローカル座標系での速度計算
            val yaw = Math.toRadians(player.yaw.toDouble())
            val sinYaw = sin(yaw)
            val cosYaw = cos(yaw)
            var vel = Vec3d.ZERO
            // 移動キー入力のベクトルを正規化
            val inputMagnitude = sqrt(forward * forward + strafe * strafe).coerceAtLeast(1.0) // 1.0未満にはしない
            val normalizedForward = forward / inputMagnitude
            val normalizedStrafe = strafe / inputMagnitude

            // 進行方向 (Z) に沿った追加速度のワールド座標系成分
            val forwardMotionX = -sinYaw * normalizedForward * acceleration.value
            val forwardMotionZ = cosYaw * normalizedForward * acceleration.value

            // 横移動方向 (X) に沿った追加速度のワールド座標系成分
            val strafeMotionX = cosYaw * normalizedStrafe * acceleration.value
            val strafeMotionZ = sinYaw * normalizedStrafe * acceleration.value
            vel = vel!!.add(forwardMotionX, 0.0, forwardMotionZ)
            vel = vel!!.add(strafeMotionX, 0.0, strafeMotionZ)
            val currentSpeedLimit =
                when {
                    player.isTouchingWater && allowInWater.value -> speedInWater.value
                    player.isInLava && allowInLava.value -> speedInLava.value
                    player.isOnGround && allowOnGround.value -> speedOnGround.value
                    player.hasVehicle() && allowWithVehicle.value -> speedWithVehicle.value
                    player.isSwimming && allowOnSwimming.value -> speedOnSwimming.value
                    player.isGliding && allowOnGliding.value -> speedOnGliding.value
                    !player.isOnGround && !player.isInLava && !player.isTouchingWater && allowInAir.value -> speedInAir.value
                    else -> 0.0 // Default to no quick move if no condition met
                }
            val tickSpeedLimit = currentSpeedLimit / 20
            val moveSpeed = sqrt((velocity!!.x * velocity!!.x) + (velocity!!.z * velocity!!.z))
            val speedResult = moveSpeed + vel.length()
            vel = if (speedResult < tickSpeedLimit / 20) vel else vel.normalize().multiply(tickSpeedLimit - moveSpeed)
            velocity = velocity!!.add(vel)
        }
    }
}
