import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.MathHelper

object CameraLogic {
    // プレイヤーの新しいYaw角度を計算するメソッド
    fun calculateMovementYaw(client: MinecraftClient): Float {
        val player = client.player ?: return 0f
        val options = client.options
        var targetYaw = player.yaw // デフォルトは現在の角度

        // WASD入力から移動ベクトルを計算
        var forward = 0
        var strafe = 0
        if (options.forwardKey.isPressed) forward++
        if (options.backKey.isPressed) forward--
        if (options.leftKey.isPressed) strafe++
        if (options.rightKey.isPressed) strafe--

        // 移動入力がある場合のみ角度を更新
        if (forward != 0 || strafe != 0) {
            val yaw = player.yaw
            targetYaw = MathHelper.atan2(strafe.toDouble(), forward.toDouble()).toFloat() * (180f / Math.PI.toFloat())
            targetYaw -= 45f // WASDを考慮したオフセット調整 (要調整)
            targetYaw += yaw // 現在のカメラの向きを基準にする (これがカメラ依存からの独立の鍵)

            val cameraYaw = client.gameRenderer.camera.yaw
            targetYaw = MathHelper.atan2(strafe.toDouble(), forward.toDouble()).toFloat() * (180f / Math.PI.toFloat())
            targetYaw = cameraYaw + targetYaw + 45f
        }

        return targetYaw
    }
}
