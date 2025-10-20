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
    private val power: FeatureSetting.FloatSetting =
        FeatureSetting.FloatSetting("Power", "feature.movement.superfly.power.description", 1.0f, 0.5f, 5.0f)
    override val settings: List<FeatureSetting<*>> =
        listOf(
            method,
            power,
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
                // ロケットモードでは、急停止や全方向への移動を可能にするため、キーのチェックをcontrolRocket内部で行います。
                controlRocket(client)
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
        val movementPower = 0.05 + power.value / 100.0
        val forwardVelocity =
            Vec3d(
                -sin(yaw) * movementPower,
                0.0,
                cos(yaw) * movementPower,
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
        val movementPower = 0.06 + power.value / 100
        val gravity = 0.02
        if (client.options.jumpKey.isPressed) {
            player.setVelocity(velocity.x, velocity.y + movementPower + gravity, velocity.z)
        }
        if (client.options.sneakKey.isPressed) {
            player.setVelocity(velocity.x, velocity.y - movementPower + gravity, velocity.z)
        }
    }

    private fun applyAccelerationHyperBoost(client: MinecraftClient) {
        val player = client.player ?: return
        val yaw = toRadians(player.yaw)
        val velocity = player.velocity
        val movementPower = 0.3 + power.value / 100.0
        // HyperBoost: Significantly increase forward speed and add slight upward boost
        val hyperBoostVelocity =
            Vec3d(
                -sin(yaw) * movementPower, // Increased speed (0.05 -> 0.3)
                0.1, // Slight upward boost
                cos(yaw) * movementPower, // Increased speed (0.05 -> 0.3)
            )
        player.velocity = velocity.add(hyperBoostVelocity)
    }

    /**
     * Rocketモードの操作を制御します。
     * - 前/後キー: 視線方向に沿って移動
     * - 左/右キー: 水平にストレイフ移動
     * - ジャンプキー: 真上へ移動 (ワールドY+)
     * - スニークキー (Shift): 即座に停止 (急停止)
     */
    private fun controlRocket(client: MinecraftClient) {
        val player = client.player ?: return
        val options = client.options ?: return
        // power.valueに応じて速度を設定します。2.0を乗算してデフォルトの速度を調整します。
        val movementMultiplier = power.value * 2.0

        // SHIFTキー (Sneak Key) が押されている場合、速度をゼロにして即座に停止します。
        if (options.sneakKey.isPressed) {
            player.velocity = Vec3d.ZERO
            return
        }

        val yaw = toRadians(player.yaw)

        var moveVector = Vec3d.ZERO
        var moving = false

        // 前後移動 (W/S) - 視線方向
        if (options.forwardKey.isPressed || options.backKey.isPressed) {
            // CameraRollを使用して、ピッチ（上下方向）も考慮した視線方向のベクトルを取得
            val forwardDirection = CameraRoll(player.yaw.toDouble(), player.pitch.toDouble()).vec()
            if (options.forwardKey.isPressed) {
                moveVector = moveVector.add(forwardDirection)
            }
            if (options.backKey.isPressed) {
                moveVector = moveVector.subtract(forwardDirection)
            }
            moving = true
        }

        // 左右移動 (A/D) - 水平方向のストレイフ
        if (options.leftKey.isPressed || options.rightKey.isPressed) {
            // 水平方向の左右移動ベクトルを計算 (視線方向のYawに90度回転)
            // rightX = cos(yaw), rightZ = sin(yaw)
            val strafeX = cos(yaw).toDouble()
            val strafeZ = sin(yaw).toDouble()
            val strafeVec = Vec3d(strafeX, 0.0, strafeZ)

            if (options.rightKey.isPressed) {
                moveVector = moveVector.subtract(strafeVec)
            }
            if (options.leftKey.isPressed) {
                moveVector = moveVector.add(strafeVec)
            }
            moving = true
        }

        // 上移動 (Jump Key) - 真上 (ワールドY軸)
        if (options.jumpKey.isPressed) {
            moveVector = moveVector.add(Vec3d(0.0, 1.0, 0.0))
            moving = true
        }

        // 移動キーが押されている場合のみ速度を更新
        if (moving) {
            // 速度ベクトルを正規化し、設定されたパワーを適用
            // 正規化することで、斜めや複数キー同時押しの場合でも一定の速度を保ちます
            val finalVelocity = moveVector.normalize().multiply(movementMultiplier)
            player.velocity = finalVelocity
        } else {
            // 移動キーが何も押されていない場合 (スニークキーは上記で処理済み)、
            // 新しい速度を設定しないことで、ゲームの物理演算（重力、空気抵抗）に速度の減衰を任せます。
        }
    }

    private fun controlCreativeFlight(client: MinecraftClient) {
        val player = client.player ?: return
        if (!player.isGliding) return
        val options = client.options ?: return
        val baseSpeed = power.value
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
