package org.infinite.features.movement.move

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.vehicle.BoatEntity
import net.minecraft.util.math.Vec3d
import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

// 基準となる各環境の移動速度（ブロック/秒）を定義
class QuickMove : ConfigurableFeature() {
    override val tickTiming: TickTiming = TickTiming.End
    private val currentMode: MoveMode
        get() {
            val player = player ?: return MoveMode.None
            return when {
                // 最も優先度の高い状態からチェック
                player.hasVehicle() && allowWithVehicle.value -> MoveMode.Vehicle
                allowOnSwimming.value && player.isSwimming -> MoveMode.Swimming // 泳ぎを水よりも優先
                allowOnGliding.value && player.isGliding -> MoveMode.Gliding
                player.isOnGround && allowOnGround.value -> MoveMode.Ground
                player.isInLava && allowInLava.value -> MoveMode.Lava
                player.isTouchingWater && allowInWater.value -> MoveMode.Water
                !player.isOnGround && !player.isInLava && !player.isTouchingWater && allowInAir.value -> MoveMode.Air
                else -> MoveMode.None
            }
        }
    private val currentMaxSpeed: Double
        get() {
            val player = player ?: return 0.0
            val world = world ?: return 0.0
            val attributes = player.attributes
            val acceleration =
                when (currentMode) {
                    MoveMode.Vehicle -> {
                        val vehicle = player.vehicle ?: return 0.0
                        when (vehicle) {
                            is LivingEntity -> {
                                vehicle.attributes.getValue(EntityAttributes.MOVEMENT_SPEED)
                            }

                            is BoatEntity -> {
                                1.0
                            }

                            else -> {
                                0.0
                            }
                        }
                    }

                    else ->
                        attributes.getValue(
                            EntityAttributes.MOVEMENT_SPEED,
                        )
                }
            val entity = player.vehicle ?: player
            val friction = world.getBlockState(entity.blockPos.add(0, -1, 0)).block.slipperiness
            return acceleration / (1.0 - friction) * if (player.isSneaking) attributes.getValue(EntityAttributes.SNEAKING_SPEED) else 1.0
        }

    // 移動モードを定義し、処理の優先順位と状態を明確にする
    private enum class MoveMode {
        None,
        Ground,
        Swimming,
        Water,
        Lava,
        Air,
        Gliding,
        Vehicle,
    }

    private val acceleration =
        FeatureSetting.DoubleSetting(
            "Acceleration",
            0.02,
            0.0,
            1.0,
        )

    private val friction =
        FeatureSetting.DoubleSetting("Friction", 1.0, 0.0, 1.0)

    // --- 速度設定値 ---
    private val speed =
        FeatureSetting.DoubleSetting(
            "Speed",
            0.75,
            0.0,
            2.0,
        )

    // --- Allow設定値 ---
    private val allowOnGround =
        FeatureSetting.BooleanSetting("AllowOnGround", true)
    private val allowInWater =
        FeatureSetting.BooleanSetting("AllowInWater", false)
    private val allowInLava =
        FeatureSetting.BooleanSetting("AllowInLava", false)
    private val allowWithVehicle =
        FeatureSetting.BooleanSetting(
            "AllowWithVehicle",
            false,
        )

    private val allowInAir =
        FeatureSetting.BooleanSetting(
            "AllowInAir",
            false,
        )
    private val allowOnGliding =
        FeatureSetting.BooleanSetting(
            "AllowOnGliding",
            false,
        )
    private val allowOnSwimming =
        FeatureSetting.BooleanSetting(
            "AllowOnSwimming",
            false,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            acceleration,
            friction,
            speed,
            allowOnGround,
            allowOnSwimming,
            allowInWater,
            allowInLava,
            allowInAir,
            allowOnGliding,
            allowWithVehicle,
        )

    override fun tick() {
        val player = player ?: return
        val options = options
        val vehicle = player.vehicle

        // 車両が有効な場合は、プレイヤーのYawを車両に適用
        vehicle?.yaw = player.yaw

        var forwardInput = 0.0
        var strafeInput = 0.0

        if (options.forwardKey.isPressed) forwardInput++
        if (options.backKey.isPressed) forwardInput--
        if (options.leftKey.isPressed) strafeInput++
        if (options.rightKey.isPressed) strafeInput--

        var velocity = velocity ?: return

        if (forwardInput == 0.0 && strafeInput == 0.0) {
            // 入力がない場合は摩擦を適用
            velocity = Vec3d(velocity.x * friction.value, velocity.y, velocity.z * friction.value)
        } else {
            val tickSpeedLimit = currentMaxSpeed * speed.value

            // 1. グローバル速度をプレイヤーのローカル座標系に変換
            val yaw = Math.toRadians(player.yaw.toDouble())
            val sinYaw = sin(yaw)
            val cosYaw = cos(yaw)

            // 現在の水平ベロシティ
            val currentVelX = velocity.x
            val currentVelZ = velocity.z

            // ローカル速度 (Forward, Strafe) への変換
            var localVelForward = -sinYaw * currentVelX + cosYaw * currentVelZ
            var localVelStrafe = cosYaw * currentVelX + sinYaw * currentVelZ

            // 2. 減速ロジックの適用 (キー入力とベロシティの符号が異なる場合に摩擦を適用)

            // Forward (前後方向) の減速
            if (localVelForward != 0.0) {
                // localVelForwardとforwardInputの符号が異なる場合
                if (sign(localVelForward) != sign(forwardInput)) {
                    localVelForward *= friction.value
                }
            }

            // Strafe (左右方向) の減速
            if (localVelStrafe != 0.0) {
                // localVelStrafeとstrafeInputの符号が異なる場合
                if (sign(localVelStrafe) != sign(strafeInput)) {
                    localVelStrafe *= friction.value
                }
            }

            // 3. 入力に基づいた加速の適用
            val inputMagnitude = sqrt(forwardInput * forwardInput + strafeInput * strafeInput).coerceAtLeast(1.0)
            val normalizedForward = forwardInput / inputMagnitude
            val normalizedStrafe = strafeInput / inputMagnitude

            // ローカルベロシティに加速を加算
            localVelForward += normalizedForward * acceleration.value
            localVelStrafe += normalizedStrafe * acceleration.value

            // 4. ローカル速度のクランプ (最大速度制限)
            val currentMoveSpeed = sqrt(localVelForward * localVelForward + localVelStrafe * localVelStrafe)

            if (currentMoveSpeed > tickSpeedLimit) {
                // 制限速度の大きさに正規化
                val scale = tickSpeedLimit / currentMoveSpeed
                localVelForward *= scale
                localVelStrafe *= scale
            }

            // 5. ローカル速度をグローバル座標系に戻す
            val newVelX = -sinYaw * localVelForward + cosYaw * localVelStrafe
            val newVelZ = cosYaw * localVelForward + sinYaw * localVelStrafe

            velocity = Vec3d(newVelX, velocity.y, newVelZ)
        }
        this.velocity = velocity
    }
}
