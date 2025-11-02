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
            "feature.movement.move.quickmove.acceleration.description",
            0.05,
            0.0,
            1.0,
        )
    private val speed =
        FeatureSetting.DoubleSetting("Speed", "feature.movement.move.quickmove.speed.description", 8.0, 7.0, 15.0)
    private val friction =
        FeatureSetting.DoubleSetting("Friction", "feature.movement.move.quickmove.friction.description", 0.5, 0.0, 1.0)
    override val settings: List<FeatureSetting<*>> = listOf(acceleration, speed, friction)

    override fun tick() {
        val player = player ?: return
        val options = options

        // 1. 環境チェックと適用除外
        // 空中にいるとき (isFallFlyingは考慮外とする)、水中/溶岩中にいるときは処理を適用しない
        if (!player.isOnGround || player.isTouchingWater || player.isInLava) {
            return
        }

        var forward = 0.0 // 進行方向 (プレイヤーのローカルZ軸)
        var strafe = 0.0 // 横移動方向 (プレイヤーのローカルX軸)

        if (options.forwardKey.isPressed) forward++
        if (options.backKey.isPressed) forward--
        if (options.leftKey.isPressed) strafe++
        if (options.rightKey.isPressed) strafe--

        if (forward == 0.0 && strafe == 0.0) {
            player.velocity =
                Vec3d(player.velocity.x * friction.value, player.velocity.y, player.velocity.z * friction.value)
        } else {
            // 2. ローカル座標系での速度計算
            val yaw = Math.toRadians(player.yaw.toDouble())
            val sinYaw = sin(yaw)
            val cosYaw = cos(yaw)
            var velocity = player.velocity
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
            velocity = velocity.add(forwardMotionX, 0.0, forwardMotionZ)
            velocity = velocity.add(strafeMotionX, 0.0, strafeMotionZ)
            val tickSpeed = speed.value / 20
            velocity = if (velocity.length() < tickSpeed / 20) velocity else velocity.normalize().multiply(tickSpeed)
            player.velocity = velocity
        }
    }
}
