package org.infinite.features.movement.move

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.vehicle.BoatEntity
import net.minecraft.util.math.Vec3d
import org.infinite.ConfigurableFeature
import org.infinite.libs.graphics.Graphics2D
import org.infinite.settings.FeatureSetting
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

class QuickMove : ConfigurableFeature() {
    override val tickTiming: TickTiming = TickTiming.End

    // 基準となる各環境の移動速度（ブロック/秒）を定義
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
                !player.isOnGround && allowInAir.value -> MoveMode.Air
                else -> MoveMode.None
            }
        }
    private val reductionThreshold =
        FeatureSetting.DoubleSetting("ReductionThreshold", 10.0, 0.0, 100.0)
    private val itemUseBoost =
        FeatureSetting.DoubleSetting("BoostWhenUseItem", 0.5, 0.0, 1.0)
    private val currentAcceleration: Double
        get() {
            val player = player ?: return 0.0
            val attributes = player.attributes
            return when (currentMode) {
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
        }
    private val currentFriction: Double
        get() {
            val player = player ?: return 0.0
            val world = world ?: return 0.0
            val entity = player.vehicle ?: player
            val attributes = player.attributes
            return when (currentMode) {
                MoveMode.Ground ->
                    world
                        .getBlockState(
                            entity.blockPos.add(
                                0,
                                -1,
                                0,
                            ),
                        ).block.slipperiness * if (player.isSneaking) attributes.getValue(EntityAttributes.SNEAKING_SPEED) else 1.0

                MoveMode.Swimming, MoveMode.Water ->
                    0.8

                MoveMode.Lava -> // 溶岩
                    0.5

                MoveMode.Air, MoveMode.Gliding -> // 空中/滑空
                    0.98 // 例: 空気抵抗に近い高い摩擦（低い減速）
                MoveMode.Vehicle -> // 乗り物
                    world
                        .getBlockState(
                            player.vehicle!!.blockPos.add(
                                0,
                                -1,
                                0,
                            ),
                        ).block.slipperiness
                        .toDouble()

                MoveMode.None ->
                    1.0 // 動きがない、またはデフォルト
            }
        }
    private val currentMaxSpeed: Double
        get() {
            val acceleration = currentAcceleration
            val friction = currentFriction
            return acceleration / (1.0 - friction)
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
    private val antiFrictionBoost =
        FeatureSetting.DoubleSetting("AntiFrictionBoost", 1.0, 0.0, 5.0)
    private val antiFrictionPoint =
        FeatureSetting.DoubleSetting("AntiFrictionPoint", 0.75, 0.0, 1.0)

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
            reductionThreshold,
            antiFrictionBoost,
            antiFrictionPoint,
            itemUseBoost,
            allowOnGround,
            allowOnSwimming,
            allowInWater,
            allowInLava,
            allowInAir,
            allowOnGliding,
            allowWithVehicle,
        )

    override fun tick() {
        if (currentMode == MoveMode.None) return
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

        val tickSpeedLimit = currentMaxSpeed * speed.value
        val baseAcceleration = acceleration.value // 設定された基本の加速度

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
        val currentMoveSpeed = sqrt(localVelForward * localVelForward + localVelStrafe * localVelStrafe)
        val delta = reductionThreshold.value / 100.0
        val currentFriction = currentFriction
        val antiFrictionBoost = antiFrictionBoost.value
        val antiFrictionPoint = antiFrictionPoint.value
        val antiFrictionFactor = (
            1 + (antiFrictionPoint - currentFriction) *
                (1.0 / antiFrictionPoint).coerceIn(
                    0.0,
                    1.0,
                ) * antiFrictionBoost
        )

        val isApplyingCorrection = player.isUsingItem && player.isOnGround
        val itemUseFactor =
            if (isApplyingCorrection) {
                val baseMovementReductionFactor = 0.15
                // boostの値を0.0から1.0の間に制限する（安全のため）
                val boost = itemUseBoost.value.coerceIn(0.0, 1.0)
                // アイテム使用後の最終的な移動速度 (FINAL_SPEED) を計算する
                // boost=0.0の時: FINAL_SPEED = 1.0 (緩和なし -> 速度低下なし)
                // boost=1.0の時: FINAL_SPEED = 0.15 (最大緩和 -> 速度を1.0に戻すための計算の土台)
                val finalSpeedDenominator = boost * (baseMovementReductionFactor - 1.0) + 1.0
                // 速度低下を打ち消すための乗数 (補正係数) を計算
                // 例: boost=1.0の時, 1.0 / 0.15 ≈ 6.667 となり、低下分を完全に打ち消す係数となる
                1.0 / finalSpeedDenominator
            } else {
                // 補正条件を満たさない場合は補正なし (係数1.0)
                1.0
            }

        val startSpeed = tickSpeedLimit * antiFrictionFactor * (1 - delta) // 減速開始速度
        val endSpeed = tickSpeedLimit * antiFrictionFactor // 加速0到達速度

        val accelerationFactor: Double =
            when {
                // 最高速度制限未満の場合はフル加速
                currentMoveSpeed <= startSpeed -> 1.0
                // 減速区間: 速度が startSpeed と endSpeed の間
                currentMoveSpeed < endSpeed -> {
                    // 線形補間: (1.0 - (現在速度 - 開始速度) / (終了速度 - 開始速度))
                    val ratio = (currentMoveSpeed - startSpeed) / (endSpeed - startSpeed)
                    1.0 - ratio
                }
                // 速度が endSpeed 以上になったら加速はゼロ
                else -> 0.0
            }
        val accelerationLimit = (endSpeed - currentMoveSpeed).coerceAtLeast(0.0)
        val currentAcceleration =
            (
                baseAcceleration * antiFrictionFactor *
                    accelerationFactor.coerceIn(
                        0.0,
                        1.0,
                    ) * itemUseFactor
            ).coerceAtMost(
                accelerationLimit,
            )
        // -----------------------------------------------------------
        // 3. 入力に基づいた加速の適用 (計算された加速度を使用)
        if (currentAcceleration > 0.0) {
            val inputMagnitude = sqrt(forwardInput * forwardInput + strafeInput * strafeInput).coerceAtLeast(1.0)
            val normalizedForward = forwardInput / inputMagnitude
            val normalizedStrafe = strafeInput / inputMagnitude
            // ローカルベロシティに計算された加速度を加算
            localVelForward += normalizedForward * currentAcceleration
            localVelStrafe += normalizedStrafe * currentAcceleration
        }

        // 4. ローカル速度のクランプ (最大速度制限) - 加速ロジックが速度超過を抑止しているため、ここでは**不要**
        // 5. ローカル速度をグローバル座標系に戻す
        val newVelX = -sinYaw * localVelForward + cosYaw * localVelStrafe
        val newVelZ = cosYaw * localVelForward + sinYaw * localVelStrafe
        velocity = Vec3d(newVelX, velocity.y, newVelZ)
        this.velocity = velocity
    }

    override fun render2d(graphics2D: Graphics2D) {
    }
}
