package org.infinite.features.rendering.camera

import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.InfiniteClient
import org.infinite.features.movement.freeze.Freeze
import org.infinite.settings.FeatureSetting
import kotlin.math.cos
import kotlin.math.sin

class FreeCamera : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.EXTEND
    override val depends: List<Class<out ConfigurableFeature>> = listOf(Freeze::class.java)
    private val speed: FeatureSetting.FloatSetting =
        FeatureSetting.FloatSetting(
            "Speed",
            "カメラの移動速度を設定します。",
            1.0f,
            0.1f,
            5.0f,
        )
    override val settings: List<FeatureSetting<*>> =
        listOf(
            speed,
        )
    private val client: MinecraftClient
        get() = MinecraftClient.getInstance()
    private var currentMode: GameMode? = GameMode.SURVIVAL

    override fun disabled() {
        InfiniteClient.getFeature(Freeze::class.java)?.forceCancel()
    }

    override fun tick() {
        if (currentMode == null) {
            disable()
            return
        }
        val player = client.player ?: return
        val options = client.options ?: return
        player.velocity = Vec3d.ZERO
        player.abilities.flying = false
        player.isOnGround = false
        val moveForward = options.forwardKey.isPressed
        val moveBackward = options.backKey.isPressed
        val moveLeft = options.leftKey.isPressed
        val moveRight = options.rightKey.isPressed
        val moveTop = options.jumpKey.isPressed
        val moveBottom = options.sneakKey.isPressed
        // 水平方向の速度は0
        // 現在のY軸の回転角度 (Yaw) をラジアンに変換
        val yawRadians = Math.toRadians(player.yaw.toDouble())
        var deltaX = 0.0
        var deltaY = 0.0
        var deltaZ = 0.0
        if (moveForward) deltaZ += 1.0
        if (moveBackward) deltaZ -= 1.0
        if (moveLeft) deltaX += 1.0 // Minecraftの左移動は、Z軸に対する回転として計算される
        if (moveRight) deltaX -= 1.0
        if (moveTop) deltaY += 1.0
        if (moveBottom) deltaY -= 1.0
        // 移動ベクトルを正規化 (斜め移動時に速くなりすぎないように)
        val magnitude = kotlin.math.sqrt(deltaX * deltaX + deltaZ * deltaZ + deltaY * deltaY)
        if (magnitude > 0) {
            deltaX /= magnitude
            deltaY /= magnitude
            deltaZ /= magnitude
        }

        // プレイヤーの視線方向に合わせて移動ベクトルを回転
        // cos(yaw) と sin(yaw) は方向ベクトルをワールド座標に変換するために使用される
        val velocityX = deltaX * cos(yawRadians) - deltaZ * sin(yawRadians)
        val velocityZ = deltaZ * cos(yawRadians) + deltaX * sin(yawRadians)
        // 速度設定を適用してプレイヤーに速度を設定
        val currentSpeed = speed.value.toDouble()
        player.velocity =
            Vec3d(
                velocityX * currentSpeed,
                deltaY * currentSpeed,
                velocityZ * currentSpeed,
            )
    }
}
