package org.infinite.features.movement.fly

import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.util.math.Vec3d
import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.libs.client.player.fighting.aim.CameraRoll
import org.infinite.settings.FeatureSetting
import org.infinite.utils.toRadians
import kotlin.math.cos
import kotlin.math.sin

enum class FlyMethod {
    Acceleration,
    Rocket,
    CreativeFlight,
}

class SuperFly : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    private val method: FeatureSetting.EnumSetting<FlyMethod> =
        FeatureSetting.EnumSetting(
            "Method",
            "feature.movement.superfly.method.description",
            FlyMethod.Acceleration,
            FlyMethod.entries,
        )
    override val settings: List<FeatureSetting<*>> =
        listOf(
            method,
        )

    override fun tick() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return

        // Check if player is gliding (relevant mostly for Acceleration/Rocket methods)
        if (!player.isGliding && method.value != FlyMethod.CreativeFlight) return

        // 1. Manage gliding (e.g., start fall flying if in water)
        manageGliding(client)

        // 2. Control based on the selected method
        when (method.value) {
            FlyMethod.Acceleration -> {
                // Check HyperBoost conditions: HyperBoost enabled, forward key, jump key, and sneak key pressed
                val isHyperBoostActive =
                    client.options.forwardKey.isPressed &&
                        client.options.jumpKey.isPressed &&
                        client.options.sneakKey.isPressed

                if (isHyperBoostActive) {
                    // Apply HyperBoost effects
                    applyAccelerationHyperBoost(client)
                } else {
                    // Apply normal speed and height controls
                    controlAccelerationSpeed(client)
                    controlAccelerationHeight(client)
                }
            }

            FlyMethod.Rocket -> {
                if (client.options.jumpKey.isPressed) {
                    controlRocket(client)
                }
            }

            FlyMethod.CreativeFlight -> {
                controlCreativeFlight(client)
            }
        }
    }

    private fun manageGliding(client: MinecraftClient) {
        val player = client.player ?: return
        // Only apply this specific gliding management if we are in a method that relies on elytra gliding
        if (method.value == FlyMethod.Acceleration || method.value == FlyMethod.Rocket) {
            if (player.isTouchingWater) {
                val packet =
                    ClientCommandC2SPacket(
                        player,
                        ClientCommandC2SPacket.Mode.START_FALL_FLYING,
                    )
                player.networkHandler?.sendPacket(packet)
            }
        }
    }

// --- ACCELERATION METHOD IMPLEMENTATIONS (Refactored from original) ---

    private fun controlAccelerationSpeed(client: MinecraftClient) {
        val player = client.player ?: return
        val yaw = toRadians(player.yaw)
        val velocity = player.velocity
        val forwardVelocity =
            Vec3d(
                -sin(yaw) * 0.05,
                0.0,
                cos(yaw) * 0.05,
            )
        if (client.options.forwardKey.isPressed) {
            player.velocity = velocity.add(forwardVelocity)
        }
        if (client.options.backKey.isPressed) {
            player.velocity = velocity.subtract(forwardVelocity)
        }
    }

    private fun controlAccelerationHeight(client: MinecraftClient) {
        val player = client.player ?: return
        val velocity = player.velocity
        if (client.options.jumpKey.isPressed) {
            player.setVelocity(velocity.x, velocity.y + 0.08, velocity.z)
        }
        if (client.options.sneakKey.isPressed) {
            player.setVelocity(velocity.x, velocity.y - 0.04, velocity.z)
        }
    }

    private fun applyAccelerationHyperBoost(client: MinecraftClient) {
        val player = client.player ?: return
        val yaw = toRadians(player.yaw)
        val velocity = player.velocity

        // HyperBoost: Significantly increase forward speed and add slight upward boost
        val hyperBoostVelocity =
            Vec3d(
                -sin(yaw) * 0.3, // Increased speed (0.05 -> 0.3)
                0.1, // Slight upward boost
                cos(yaw) * 0.3, // Increased speed (0.05 -> 0.3)
            )
        player.velocity = velocity.add(hyperBoostVelocity)
    }

    private fun controlRocket(client: MinecraftClient) {
        val player = client.player ?: return
        val yaw = player.yaw.toDouble()
        val pitch = player.pitch.toDouble()
        val velocity = CameraRoll(yaw, pitch).vec().multiply(3.0)
        player.velocity = velocity
    }

    private fun controlCreativeFlight(client: MinecraftClient) {
        val player = client.player ?: return
        if (!player.isGliding) return
        val options = client.options ?: return
        val baseSpeed = 1.0 // 基本速度
        val boostMultiplier = if (player.isSprinting) 2.0 else 1.0 // スプリント（Ctrl）で速度ブースト
        val gravity = 0.02
        var deltaX = 0.0
        var deltaY = 0.0
        var deltaZ = 0.0

        // 2. 移動キーのチェック
        if (options.forwardKey.isPressed) deltaZ += 1.0
        if (options.backKey.isPressed) deltaZ -= 1.0
        if (options.leftKey.isPressed) deltaX += 1.0
        if (options.rightKey.isPressed) deltaX -= 1.0

        // 上下移動 (Jump Key for Up, Sneak Key for Down)
        if (options.jumpKey.isPressed) deltaY += 1.0
        if (options.sneakKey.isPressed) deltaY -= 1.0

        // 移動ベクトルを正規化 (斜め移動時に速くなりすぎないように)
        val magnitude = kotlin.math.sqrt(deltaX * deltaX + deltaZ * deltaZ + deltaY * deltaY)
        if (magnitude > 0) {
            deltaX /= magnitude
            deltaY /= magnitude
            deltaZ /= magnitude
        }

        // 3. プレイヤーの視線方向に合わせて水平移動ベクトルを回転
        // Yaw (Y軸回転) をラジアンに変換
        val yawRadians = toRadians(player.yaw)

        // Yawに基づいて水平方向の速度をワールド座標に変換 (FreeCameraのロジックと同様)
        val velocityX = deltaX * cos(yawRadians) - deltaZ * sin(yawRadians)
        val velocityZ = deltaZ * cos(yawRadians) + deltaX * sin(yawRadians)

        // 4. 速度を適用
        val currentSpeed = baseSpeed * boostMultiplier
        // クリエイティブ飛行は、速度を設定するだけでなく、プレイヤーの慣性（既存の速度）を徐々に減衰させる特性があります。
        // 完全なバニラ動作をエミュレートするには、既存の速度を考慮しつつ新しい速度を加える必要があります。
        // ここでは単純に速度を設定することで、常に一定の速度で移動できるようにします。
        player.velocity =
            Vec3d(
                velocityX * currentSpeed,
                deltaY * currentSpeed + gravity,
                velocityZ * currentSpeed,
            )
    }
}
