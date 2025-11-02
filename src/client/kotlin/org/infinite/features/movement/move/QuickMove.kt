package org.infinite.features.movement.move

import net.minecraft.util.math.Vec3d
import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class QuickMove : ConfigurableFeature() {
    private val speed = FeatureSetting.DoubleSetting("speed", "feature.movement.move.quickmove.speed", 0.1, 0.0, 1.0)
    private val friction = FeatureSetting.DoubleSetting("friction", "feature.movement.move.quickmove.friction", 0.5, 0.0, 1.0)
    override val settings: List<FeatureSetting<*>> = listOf(speed, friction)

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
            // 移動入力がない場合、既存の摩擦を適用
            player.velocity = Vec3d(player.velocity.x * friction.value, player.velocity.y, player.velocity.z * friction.value)
        } else {
            // 2. ローカル座標系での速度計算
            val yaw = Math.toRadians(player.yaw.toDouble())
            val sinYaw = sin(yaw)
            val cosYaw = cos(yaw)

            // 移動キー入力のベクトルを正規化
            val inputMagnitude = sqrt(forward * forward + strafe * strafe).coerceAtLeast(1.0) // 1.0未満にはしない
            val normalizedForward = forward / inputMagnitude
            val normalizedStrafe = strafe / inputMagnitude

            // 進行方向 (Z) に沿った追加速度のワールド座標系成分
            val forwardMotionX = -sinYaw * normalizedForward * speed.value
            val forwardMotionZ = cosYaw * normalizedForward * speed.value

            // 横移動方向 (X) に沿った追加速度のワールド座標系成分
            val strafeMotionX = cosYaw * normalizedStrafe * speed.value
            val strafeMotionZ = sinYaw * normalizedStrafe * speed.value
            player.velocity = player.velocity.add(forwardMotionX, 0.0, forwardMotionZ)

            player.velocity = player.velocity.add(strafeMotionX, 0.0, strafeMotionZ)
        }
    }
}
