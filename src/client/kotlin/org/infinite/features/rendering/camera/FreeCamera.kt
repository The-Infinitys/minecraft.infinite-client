package org.infinite.features.rendering.camera

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics3D
import org.infinite.settings.FeatureSetting
import org.infinite.settings.Property
import org.lwjgl.glfw.GLFW
import kotlin.math.cos
import kotlin.math.sin

class FreeCamera : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.EXTEND
    override val toggleKeyBind: Property<Int> = Property(GLFW.GLFW_KEY_U)
    private val speed: FeatureSetting.FloatSetting =
        FeatureSetting.FloatSetting(
            "Speed",
            "feature.rendering.freecamera.speed.description",
            1.0f,
            0.1f,
            5.0f,
        )
    override val settings: List<FeatureSetting<*>> =
        listOf(
            speed,
        )
    private var currentMode: GameMode? = GameMode.SURVIVAL

    override fun disabled() {
        // Teleport player back to original position
        client.player?.setPos(originalPos.x, originalPos.y, originalPos.z)
        client.player?.yaw = originalYaw
        client.player?.pitch = originalPitch
    }

    override fun start() {
        disable()
    }

    private var originalPos = Vec3d.ZERO
    private var originalYaw: Float = 0.0f
    private var originalPitch: Float = 0.0f
    private var originalIsOnGround: Boolean = false
    private var originalHorizontalCollision: Boolean = false
    private var lastHealth: Float = 0.0f // Store player's health from previous tick

    override fun enabled() {
        originalPos = player?.eyePos ?: Vec3d.ZERO
        originalYaw = player?.yaw ?: 0.0f
        originalPitch = player?.pitch ?: 0.0f
        originalIsOnGround = player?.isOnGround ?: true
        originalHorizontalCollision = player?.horizontalCollision ?: false
        lastHealth = player?.health ?: 0.0f // Capture initial health
    }

    override fun tick() {
        if (currentMode == null) {
            disable()
            return
        }
        val player = client.player ?: return
        val options = client.options ?: return

        // Damage detection
        val currentHealth = player.health
        if (currentHealth < lastHealth) {
            disable() // Disable FreeCamera if player takes damage
            return
        }
        lastHealth = currentHealth // Update lastHealth for the next tick

        // Send a "still" packet with original position and current rotation
        if (player.networkHandler != null) {
            player.networkHandler.sendPacket(
                PlayerMoveC2SPacket.Full(
                    originalPos.x,
                    originalPos.y,
                    originalPos.z,
                    player.yaw, // Use current player yaw
                    player.pitch, // Use current player pitch
                    originalIsOnGround,
                    originalHorizontalCollision,
                ),
            )
        }

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

    override fun render3d(graphics3D: Graphics3D) {
        val lineColor = InfiniteClient.theme().colors.infoColor
        val currentPos = client.player?.getLerpedPos(graphics3D.tickCounter.getTickProgress(true)) ?: return
        graphics3D.renderLine(originalPos, currentPos, lineColor, true)
    }
}
