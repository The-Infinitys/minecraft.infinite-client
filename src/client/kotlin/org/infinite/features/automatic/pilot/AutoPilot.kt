package org.infinite.features.automatic.pilot

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.world.ClientWorld
import net.minecraft.text.Text
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.Heightmap
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.libs.client.player.fighting.AimInterface
import org.infinite.libs.client.player.fighting.aim.AimTaskConditionReturn
import org.infinite.libs.client.player.fighting.aim.CameraRoll
import org.infinite.libs.client.player.inventory.InventoryManager.Armor
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.settings.FeatureSetting
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

// ターゲットの X, Z 座標を保持するデータクラス
class Location(
    val x: Int,
    val z: Int,
) {
    private val client = MinecraftClient.getInstance()
    private val player: ClientPlayerEntity?
        get() = client.player

    /** プレイヤーからターゲットまでの水平距離を計算します。 */
    fun distance(): Double {
        if (player == null) return 0.0
        val cx = player!!.x
        val cz = player!!.z
        val diffX = x - cx
        val diffZ = z - cz
        return sqrt(diffX * diffX + diffZ * diffZ)
    }
}

// 自動操縦の状態を定義する列挙型
enum class PilotState {
    Idle, // 待機中
    JetFlying, // SuperFlyモードで飛行中
    FallFlying, // 速度を落とすために下降中
    RiseFlying, // 速度を上げるために上昇中
    Gliding, // 一定以上の高さになったので滑空中
    Circling, // 【追加】目標点の周りを旋回し、着陸地点を探索中
    Landing, // 着陸中
    EmergencyLanding, // 緊急着陸中
}

// AutoPilot メイン機能クラス
class AutoPilot : ConfigurableFeature(initialEnabled = false) {
    val defaultFallDir = 40.0
    val defaultRiseDir = -45.0
    val bestGlidingDir = 5.7
    val fallDir =
        FeatureSetting.DoubleSetting(
            "FallDirection",
            "feature.automatic.autopilot.falldirection.description",
            defaultFallDir,
            defaultFallDir - 10,
            defaultFallDir + 10,
        )
    val riseDir =
        FeatureSetting.DoubleSetting(
            "RiseDirection",
            "feature.automatic.autopilot.risingdirection.description",
            defaultRiseDir,
            defaultRiseDir - 10,
            defaultRiseDir + 10,
        )
    val glidingDir =
        FeatureSetting.DoubleSetting(
            "GlidingDirection",
            "feature.automatic.autopilot.glidingdirection.description",
            20.0,
            bestGlidingDir,
            defaultFallDir,
        )
    val elytraThreshold =
        FeatureSetting.IntSetting(
            "ElytraThreshold",
            "feature.automatic.autopilot.elytrathreshold.description",
            // デフォルト値を5%に設定
            5,
            1,
            50,
        )
    private val swapElytra =
        FeatureSetting.BooleanSetting(
            "SwapElytra",
            "feature.automatic.autopilot.swapelytra.description",
            true,
        )
    val standardHeight =
        FeatureSetting.IntSetting(
            "StandardHeight",
            "feature.automatic.autopilot.standardheight.description",
            512,
            256,
            1024,
        )
    val landingDir =
        FeatureSetting.DoubleSetting(
            "LandingDirectory",
            "feature.automatic.autopilot.landingdirectory.description",
            -14.0,
            -45.0,
            0.0,
        )
    val emergencyLandingThreshold =
        FeatureSetting.IntSetting(
            "EmergencyLandingThreshold",
            "feature.automatic.autopilot.emergencylandingthreshold.description",
            60, // 60 seconds of remaining flight time
            10,
            300,
        )
    val collisionDetectionDistance =
        FeatureSetting.IntSetting(
            "CollisionDetectionDistance",
            "feature.automatic.autopilot.collisiondetectiondistance.description",
            10, // Check 10 blocks ahead for collision
            3,
            30,
        )
    val jetFlightMode =
        FeatureSetting.BooleanSetting(
            "JetFlight",
            "feature.automatic.autopilot.jetflight.description",
            false,
        )
    val jetSpeedLimit =
        FeatureSetting.DoubleSetting(
            "JetSpeedLimit",
            "feature.automatic.autopilot.jetspeedlimit.description",
            30.0,
            10.0,
            50.0,
        )

    // 機能設定リスト
    override val settings: List<FeatureSetting<*>> =
        listOf(
            elytraThreshold,
            swapElytra,
            standardHeight,
            riseDir,
            glidingDir,
            fallDir,
            landingDir,
            emergencyLandingThreshold,
            collisionDetectionDistance,
            jetFlightMode,
            jetSpeedLimit,
        )
    private var fallHeight: Double = 0.0
    private var riseHeight: Double = 0.0

    /**
     * エリトラ飛行を再開するためにジャンプをシミュレートします。
     */
    private fun redeployElytra() {
        if (player == null) return
        // ジャンプキーを短時間押すことでエリトラを再展開
        client.options.jumpKey.isPressed = true
        // 少し待ってからキーを離す (次のティックで自動的に離されることを期待)
        // または、より制御された方法でキーを離すロジックを追加することも可能
    }

    // Minecraft クライアント/プレイヤー/ワールドの簡易参照
    private val client: MinecraftClient
        get() = MinecraftClient.getInstance()
    private val player: ClientPlayerEntity?
        get() = client.player
    private val world: ClientWorld
        get() = client.world!!

    // 現在の飛行速度 (m/s)
    val flySpeed: Double
        get() {
            if (player == null) return 0.0
            val moveVelocity = player!!.velocity
            val lookVelocity = player!!.rotationVector
            return moveVelocity.dotProduct(lookVelocity)
        }
    val flySpeedDisplay: Double
        get() = flySpeed * 20
    var risingTime = System.currentTimeMillis()
    var fallingTime = System.currentTimeMillis()

    // Y方向の速度 (m/s)
    val riseSpeed: Double
        get() = (player?.velocity?.y ?: 0.0) * 20.0
    val riseSpeedDisplay: Double
        get() = riseSpeed

    // EMA の平滑化係数 (alpha)。
    private val alpha = 0.0015
    var moveSpeedAverage: Double = 0.0 // 平均移動速度 (m/s)
    private var riseSpeedAverage: Double = 0.0 // 平均上昇速度 (m/s)

    val height: Double
        get() = player?.y ?: 0.0
    private val moveSpeed: Double
        get() {
            if (player == null) {
                return 0.0
            }
            val x = player!!.velocity.x
            val z = player!!.velocity.z
            return sqrt(x * x + z * z) * 20.0 // m/s に変換
        }

    var target: Location? = null
    var state: PilotState = PilotState.Idle

    // 【新規】着陸に最適な地点を保持
    var bestLandingSpot: LandingSpot? = null

    // AimTask の結果を受け取るコールバック変数
    var aimTaskCallBack: AimTaskConditionReturn? = null

    private fun isCollidingWithTerrain(): Boolean {
        if (player == null) return false

        val currentPos = player!!.blockPos
        val lookVec = player!!.rotationVector
        val checkDistance = collisionDetectionDistance.value

        // Check blocks directly in front of the player
        for (i in 1..checkDistance) {
            val checkPos =
                currentPos.add((lookVec.x * i).roundToInt(), (lookVec.y * i).roundToInt(), (lookVec.z * i).roundToInt())
            val blockState = world.getBlockState(checkPos)
            if (!blockState.isAir) {
                return true
            }
        }

        // Check blocks directly below the player (for sudden drops)
        val blockBelow = currentPos.down()
        val blockStateBelow = world.getBlockState(blockBelow)
        return !blockStateBelow.isAir
    }

    /**
     * 【変更点】Tick ごとに移動速度と上昇速度の EMA を更新
     */
    override fun tick() {
        // 現在の移動速度 (m/s)
        val currentMoveSpeed = moveSpeed
        // 現在の上昇速度 (m/s)
        val currentRiseSpeed = riseSpeed

        // 指数移動平均 (EMA) の計算
        moveSpeedAverage = alpha * currentMoveSpeed + (1.0 - alpha) * moveSpeedAverage
        riseSpeedAverage = alpha * currentRiseSpeed + (1.0 - alpha) * riseSpeedAverage

        // ----------------------------------------------------------------------
        // 緊急着陸の判定
        // ----------------------------------------------------------------------
        val remainingFlightTime = flightTime()
        if (remainingFlightTime < emergencyLandingThreshold.value || isCollidingWithTerrain()) {
            if (state != PilotState.EmergencyLanding) {
                InfiniteClient.warn(
                    Text
                        .translatable(
                            "autopilot.emergency_landing.start",
                            remainingFlightTime.roundToInt(),
                            isCollidingWithTerrain(),
                        ).string,
                )
                state = PilotState.EmergencyLanding
                aimTaskCallBack = null // 現在のAimTaskを中断
            }
        }
        // ----------------------------------------------------------------------

        if (target == null) {
            InfiniteClient.error(Text.translatable("autopilot.target.not_set").string)
            disable()
            return
        }
        val currentTarget = target!!

        // ----------------------------------------------------------------------
        // エリトラの自動交換ロジック
        // ----------------------------------------------------------------------
        if (swapElytra.value) {
            checkAndSwapElytra()
        }
        // ----------------------------------------------------------------------

        if (player?.isGliding != true) {
            // エリトラ飛行中でなければ、エリトラを再展開または装備を試みる
            if (isElytra(InfiniteClient.playerInterface.inventory.get(Armor.CHEST))) {
                InfiniteClient.warn(Text.translatable("autopilot.elytra.flight_interrupted").string)
                redeployElytra()
            } else {
                InfiniteClient.error(Text.translatable("autopilot.elytra.not_equipped").string)
                disable()
                return
            }
        }
        // ----------------------------------------------------------------------

        // ----------------------------------------------------------------------
        // 【変更】ターゲットに到達したかの判定 (Circling へ移行)
        // ----------------------------------------------------------------------
        val distance = currentTarget.distance()

        if (distance < 32 && state == PilotState.Landing) {
            // 非常に近づいたら成功として無効化（最終的な着地はプレイヤーに任せる）
            InfiniteClient.info(Text.translatable("autopilot.landing.completed").string)
            disable()
            return
        } else if (distance < landingStartDistance && state != PilotState.Circling && state != PilotState.Landing) {
            InfiniteClient.info(Text.translatable("autopilot.circling.start").string)
            // AimTaskを中断し、状態を旋回に移行
            aimTaskCallBack = null
            state = PilotState.Circling
        }
        // ----------------------------------------------------------------------
        // JetFlying
        if (jetFlightMode.value &&
            listOf(PilotState.FallFlying, PilotState.Gliding, PilotState.RiseFlying).contains(
                state,
            )
        ) {
            state = PilotState.JetFlying
        }
        // ----------------------------------------------------------------------
        process(currentTarget)
    }

    val landingStartDistance = 256.0

    /**
     * 現在装備しているエリトラの残り耐久値をパーセンテージで返します。
     * エリトラを装備していない、またはエリトラでない場合は 100% を返します。
     */
    private fun elytraDurability(): Double {
        val chestStack = InfiniteClient.playerInterface.inventory.get(Armor.CHEST)
        return InfiniteClient.playerInterface.inventory.durabilityPercentage(chestStack) * 100
    }

    /**
     * エリトラの耐久値をチェックし、閾値以下であれば最も耐久値の高いエリトラと交換を試みます。
     */
    private fun checkAndSwapElytra() {
        if (player == null) return

        val invManager = InfiniteClient.playerInterface.inventory
        val equippedElytraStack = invManager.get(Armor.CHEST)
        val isElytraEquipped = isElytra(equippedElytraStack)
        val currentDurability = if (isElytraEquipped) elytraDurability() else 0.0

        // 交換が必要な条件:
        // 1. エリトラを装備していない (isElytraEquipped == false)
        // 2. エリトラを装備しているが、耐久値が閾値以下である (currentDurability <= elytraThreshold.value)
        val needsSwap = !isElytraEquipped || (currentDurability <= elytraThreshold.value)

        if (needsSwap) {
            // 最も耐久値の高い予備のエリトラをインベントリから探す
            val bestElytra = findBestElytraInInventory()

            if (bestElytra != null) {
                // 交換ロジック:
                // 1. チェストスロットと予備のエリトラスロットをスワップする
                if (invManager.swap(Armor.CHEST, bestElytra.index)) {
                    val swapMessage =
                        if (isElytraEquipped) {
                            Text
                                .translatable(
                                    "autopilot.elytra.swapped.low_durability",
                                    currentDurability.roundToInt(),
                                    bestElytra.durability.roundToInt(),
                                ).string
                        } else {
                            Text
                                .translatable(
                                    "autopilot.elytra.swapped.not_equipped",
                                    bestElytra.durability.roundToInt(),
                                ).string
                        }
                    InfiniteClient.info(swapMessage)
                } else {
                    InfiniteClient.error(Text.translatable("autopilot.elytra.swap_failed").string)
                }
            } else if (isElytraEquipped) {
                if (state != PilotState.EmergencyLanding) {
                    InfiniteClient.warn(
                        Text
                            .translatable(
                                "autopilot.elytra.no_spare.low_durability",
                                currentDurability.roundToInt(),
                            ).string,
                    )
                    state = PilotState.EmergencyLanding
                    aimTaskCallBack = null
                }
            } else {
                if (state != PilotState.EmergencyLanding) {
                    InfiniteClient.warn(Text.translatable("autopilot.elytra.no_spare.not_equipped").string)
                    state = PilotState.EmergencyLanding
                    aimTaskCallBack = null
                }
            }
        }
    }

    /**
     * 機能が有効になったときに一度だけ実行されます。
     */
    override fun enabled() {
        // 初期の平均を現在の瞬時速度に設定
        moveSpeedAverage = moveSpeed
        riseSpeedAverage = riseSpeed
        state = PilotState.Idle
        aimTaskCallBack = null
        riseHeight = height
        fallHeight = height
        bestLandingSpot = null // 【新規】着陸地点情報をリセット
    }

    /**
     * 状態に応じた処理を実行します。
     */
    private fun process(target: Location) {
        // SuperFly が有効な場合、状態を優先的に SuperFlying に設定
        when (state) {
            PilotState.Idle -> {
                // 自動操縦開始時または SuperFly 時の初期状態決定ロジック
                val targetSpeed = jetSpeedLimit.value
                // 現在の速度に応じて、減速が必要な下降状態か、加速が必要な上昇状態かを決定
                state =
                    if (moveSpeedAverage > targetSpeed) { // 平均速度で判定
                        PilotState.FallFlying // 速すぎる -> 下降して減速
                    } else {
                        PilotState.RiseFlying // 遅すぎる -> 上昇して加速
                    }
                aimTaskCallBack = null
            }

            PilotState.JetFlying -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state))
                    }

                    AimTaskConditionReturn.Exec -> {
                        if (player == null) return
                        val velocity = player!!.velocity
                        val yaw = player!!.yaw.toDouble()
                        val pitch = player!!.pitch.toDouble()
                        val moveVec = CameraRoll(yaw, pitch).vec()
                        val power = 0.04
                        val vecY = velocity.y + if (height < standardHeight.value) power else -power
                        val vecX = velocity.x + moveVec.x * (if (moveSpeed < jetSpeedLimit.value) power else 0.0)
                        val vecZ = velocity.z + moveVec.z * (if (moveSpeed < jetSpeedLimit.value) power else 0.0)
                        player!!.velocity = Vec3d(vecX, vecY, vecZ)
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error("[AutoPilot] Circling 状態で予期せぬエラー。")
                        disable()
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        state = PilotState.Circling
                    }

                    else -> {}
                }
            }
            // ----------------------------------------------------------------------
            // 【新規】旋回 (Circling) 状態の処理
            // ----------------------------------------------------------------------
            PilotState.Circling -> {
                when (aimTaskCallBack) {
                    null -> {
                        // 旋回と探索の AimTask を開始
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state, bestLandingSpot))
                    }

                    AimTaskConditionReturn.Success -> {
                        // 着陸地点が見つかり、着陸フェーズに移行
                        aimTaskCallBack = null
                        state = PilotState.Landing
                        InfiniteClient.info(Text.translatable("autopilot.landing.spot_found").string)
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.circling").string)
                        disable()
                    }

                    else -> {
                        // Exec 中は着陸地点の探索を行う
                        searchLandingSpot(target)
                    }
                }
            }
            // ----------------------------------------------------------------------

            // 【変更】着陸 (Landing) 状態の処理
            PilotState.Landing -> { // 着陸ロジック
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state, bestLandingSpot))
                        InfiniteClient.info(Text.translatable("autopilot.landing.aimtask_start").string)
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        InfiniteClient.info(Text.translatable("autopilot.landing.conditions_met").string)
                        disable()
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.landing").string)
                        disable()
                    }

                    else -> {} // AimTaskConditionReturn.Exec (実行中) の場合は待機
                }
            }
            // ----------------------------------------------------------------------

            // 【新規】緊急着陸 (EmergencyLanding) 状態の処理
            PilotState.EmergencyLanding -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state, bestLandingSpot))
                        InfiniteClient.info(Text.translatable("autopilot.emergency_landing.aimtask_start").string)
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        InfiniteClient.info(Text.translatable("autopilot.emergency_landing.completed").string)
                        disable()
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.emergency_landing").string)
                        disable()
                    }

                    else -> {} // AimTaskConditionReturn.Exec (実行中) の場合は待機
                }
            }
            // ----------------------------------------------------------------------

            // 下降 (FallFlying) 状態の処理
            PilotState.FallFlying -> {
                when (aimTaskCallBack) {
                    null -> {
                        // AimTask が未開始の場合、新しい AimTask を追加
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        riseHeight = height
                        risingTime = System.currentTimeMillis()
                        AimInterface.addTask(AutoPilotAimTask(state))
                    }

                    AimTaskConditionReturn.Success -> {
                        // AimTask が目標速度に達して成功した場合、状態を切り替え
                        aimTaskCallBack = null
                        state = PilotState.RiseFlying
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.fallflying").string)
                        disable()
                    }

                    else -> {} // AimTaskConditionReturn.Exec (実行中) の場合は待機
                }
            }

            // 上昇 (RiseFlying) 状態の処理
            PilotState.RiseFlying -> {
                when (aimTaskCallBack) {
                    null -> {
                        // AimTask が未開始の場合、新しい AimTask を追加
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        fallHeight = height
                        fallingTime = System.currentTimeMillis()
                        AimInterface.addTask(AutoPilotAimTask(state))
                    }

                    AimTaskConditionReturn.Success -> {
                        // AimTask が目標速度に達して成功した場合、状態を切り替え
                        aimTaskCallBack = null
                        InfiniteClient.error(Text.translatable("autopilot.error.riseflying").string)
                        disable()
                    }

                    else -> {} // AimTaskConditionReturn.Exec (実行中) の場合は待機
                }
            }

            PilotState.Gliding -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state))
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        state = PilotState.FallFlying
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error(Text.translatable("autopilot.error.gliding").string)
                        disable()
                    }

                    else -> {} // AimTaskConditionReturn.Exec (実行中) の場合は待機
                }
            }
        }
    }

    /**
     * 【新規】ターゲット周辺の着陸に最適な地点を探索します。
     * 最も高く、かつ平らな場所を優先します。
     */
    private fun searchLandingSpot(target: Location) {
        if (player == null) return

        val centerX = target.x
        val centerZ = target.z
        val searchRadius = 128 // 探索半径 (ブロック)

        var currentBestSpot: LandingSpot? = bestLandingSpot
        val currentBestScore = currentBestSpot?.score ?: -1.0

        // プレイヤーの周囲のグリッドをスキャン（簡略化のためランダムな点をチェック）
        repeat(5) {
            // 毎ティック5点チェック
            val x = centerX + MathHelper.nextBetween(player!!.random, -searchRadius, searchRadius)
            val z = centerZ + MathHelper.nextBetween(player!!.random, -searchRadius, searchRadius)

            // 地形のY座標を取得
            val y =
                world.getTopY(
                    Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    x,
                    z,
                )

            // 【新規】危険なブロックを避ける
            if (!isDangerousBlock(x, y, z)) {
                // 探索地点 (x, z) がどの程度平らであるかを判定
                val flatnessScore = calculateFlatnessScore(x, z)

                // スコア計算: 標高 (y) を重視し、平坦度をボーナスとして加算
                // 高い標高ほど良い。平坦なほどボーナス。平坦度ボーナスは最大で 10 * 1.0 = 10.0
                val score = y.toDouble() + (flatnessScore * 10.0)

                if (score > currentBestScore) {
                    currentBestSpot = LandingSpot(x, y, z, score)
                    bestLandingSpot = currentBestSpot
                }
            }
        }
    }

    /**
     * 【新規】指定されたX, Z座標周辺の平坦度を計算します。
     * 1.0 (完全に平ら) から 0.0 (非常に起伏が激しい)
     */
    private fun calculateFlatnessScore(
        x: Int,
        z: Int,
    ): Double {
        var totalDiff = 0.0
        val centerH = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z)
        var count = 0

        for (dx in -1..1) {
            for (dz in -1..1) {
                if (dx == 0 && dz == 0) continue

                val neighborH =
                    world.getTopY(
                        Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                        x + dx,
                        z + dz,
                    )
                totalDiff += abs(centerH - neighborH)
                count++
            }
        }

        if (count == 0) return 1.0

        // 平均標高差が 5.0ブロック以下の範囲でスコアを計算
        val avgDiff = totalDiff / count.toDouble()
        // スコア: 差が 0 のとき 1.0, 差が 5.0 のとき 0.0 に近づく
        return MathHelper.clamp(1.0 - (avgDiff / 5.0), 0.0, 1.0)
    }

    /**
     * 【新規】指定された座標が危険なブロック (溶岩、火、サボテン、マグマブロック) であるかを判定します。
     * また、その1ブロック上もチェックします。
     */
    private fun isDangerousBlock(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val blockPos = BlockPos(x, y, z)
        val blockState = world.getBlockState(blockPos)
        val block = blockState.block

        // Check for dangerous blocks at the given position
        if (block == Blocks.LAVA || block == Blocks.FIRE || block == Blocks.CACTUS || block == Blocks.MAGMA_BLOCK) {
            return true
        }

        // Also check the block directly above, as we don't want to land *on* a dangerous block
        val blockStateAbove = world.getBlockState(blockPos.up())
        val blockAbove = blockStateAbove.block
        return blockAbove == Blocks.LAVA || blockAbove == Blocks.FIRE || blockAbove == Blocks.CACTUS
    }

    /**
     * コマンド登録ロジック。
     * /infinite feature Automatic AutoPilot target <x> <z> コマンドを登録。
     */
    override fun registerCommands(builder: LiteralArgumentBuilder<FabricClientCommandSource>) {
        builder.then(
            ClientCommandManager.literal("target").then(
                ClientCommandManager.argument("x", IntegerArgumentType.integer()).then(
                    ClientCommandManager.argument("z", IntegerArgumentType.integer()).executes { context ->
                        val x = IntegerArgumentType.getInteger(context, "x")
                        val z = IntegerArgumentType.getInteger(context, "z")
                        this.target = Location(x, z)
                        InfiniteClient.info(Text.translatable("autopilot.command.target_set", x, z).string)
                        1
                    },
                ),
            ),
        )
    }

    override fun render2d(graphics2D: Graphics2D) {
        if (target == null) return // ターゲットが設定されていない場合は描画しない
        val currentTarget = target!!

        // ----------------------------------------------------------------------
        // 1. データ計算
        // ----------------------------------------------------------------------

        // A. 距離
        val distance = currentTarget.distance()

        // B. 平均速度 (moveSpeedAverageを使用)
        val speed = moveSpeedAverage
        val etaSeconds: Long =
            if (speed > 1.0) { // 速度が1m/s以上の場合のみ計算
                (distance / speed).roundToLong()
            } else {
                -1L // 飛行していない、または非常に遅い場合は計算不可
            }
        val flightSeconds: Long = flightTime().roundToLong()
        // D. 現在の状態
        val stateText =
            when (state) {
                PilotState.Idle -> Text.translatable("autopilot.state.idle").string
                PilotState.JetFlying -> Text.translatable("autopilot.state.jet_flying").string
                PilotState.FallFlying -> Text.translatable("autopilot.state.fall_flying").string
                PilotState.RiseFlying -> Text.translatable("autopilot.state.rise_flying").string
                PilotState.Gliding -> Text.translatable("autopilot.state.gliding").string
                PilotState.Circling -> Text.translatable("autopilot.state.circling").string
                PilotState.Landing -> Text.translatable("autopilot.state.landing").string
                PilotState.EmergencyLanding -> Text.translatable("autopilot.state.emergency_landing").string
            }

        // E. エリトラ耐久値
        val durability =
            if (swapElytra.value) {
                "${elytraDurability().roundToInt()}%"
            } else {
                "Disabled"
            }

        // ----------------------------------------------------------------------
        // 2. UI描画パラメータ
        // ----------------------------------------------------------------------
        val startX = 5 // 画面左端からのオフセット
        var currentY = 5 // 描画開始Y座標
        val lineHeight = graphics2D.fontHeight() + 2
        val bgColor = InfiniteClient.theme().colors.backgroundColor
        val white = InfiniteClient.theme().colors.foregroundColor
        val primaryColor = InfiniteClient.theme().colors.primaryColor
        val durabilityValue = elytraDurability()
        val isElytraEquipped = isElytra(InfiniteClient.playerInterface.inventory.get(Armor.CHEST))

        val durabilityColor =
            if (swapElytra.value && isElytraEquipped && durabilityValue <= elytraThreshold.value) {
                InfiniteClient.theme().colors.warnColor
            } else {
                white
            }

        // ----------------------------------------------------------------------
        // 3. 情報の描画 (背景とテキスト)
        // ----------------------------------------------------------------------
        val infoLines = mutableListOf<String>()
        infoLines.add(Text.translatable("autopilot.display.title", stateText).string)
        infoLines.add(Text.translatable("autopilot.display.target", currentTarget.x, currentTarget.z).string)
        infoLines.add(Text.translatable("autopilot.display.distance", "%.1f".format(distance / 1000.0), "%.0f".format(distance)).string)
        infoLines.add(Text.translatable("autopilot.display.average_speed", "%.1f".format(moveSpeedAverage)).string)
        infoLines.add(Text.translatable("autopilot.display.current_speed", "%.1f".format(flySpeedDisplay)).string)
        infoLines.add(Text.translatable("autopilot.display.average_rising", "%.1f".format(riseSpeedAverage)).string)
        infoLines.add(Text.translatable("autopilot.display.current_rising", "%.1f".format(riseSpeedDisplay)).string)
        infoLines.add(Text.translatable("autopilot.display.elytra", durability).string)
        infoLines.add(Text.translatable("autopilot.display.eta", formatSecondsToDHMS(etaSeconds)).string)
        infoLines.add(Text.translatable("autopilot.display.remain", formatSecondsToDHMS(flightSeconds)).string)

        // 【新規】着陸地点情報
        bestLandingSpot?.let {
            infoLines.add(Text.translatable("autopilot.display.land_spot", it.y, "%.1f".format(it.score)).string)
        }

        // 最大幅を計算
        val maxWidth = (infoLines.maxOfOrNull { graphics2D.textWidth(it) } ?: 128).coerceAtLeast(128)
        val boxWidth = maxWidth + 10
        val boxHeight = infoLines.size * lineHeight + 6

        // 背景ボックスの描画
        graphics2D.fill(startX, currentY, boxWidth, boxHeight, bgColor)
        // 枠線の描画
        graphics2D.drawBorder(startX, currentY, boxWidth, boxHeight, primaryColor, 1)

        currentY += 4 // テキストの開始位置

        // テキストの描画
        infoLines.forEachIndexed { index, line ->
            val color =
                when (index) {
                    0 -> primaryColor // 1行目はタイトルとしてプライマリカラー
                    6 -> durabilityColor // 7行目はエリトラ耐久値として耐久値の色
                    else -> white // それ以外は白
                }
            graphics2D.drawText(line, startX + 5, currentY, color)
            currentY += lineHeight
        }
    }

    /**
     * 3D レンダリングロジック。ターゲット地点と、見つかった着陸地点を描画します。
     */
    override fun render3d(graphics3D: Graphics3D) {
        if (target != null) {
            val x = target!!.x.toDouble()
            val y = world.bottomY.toDouble()
            val z = target!!.z.toDouble()
            val height = world.height * 10 // ワールドの高さの10倍の高さ
            val size = 2
            val box =
                RenderUtils.ColorBox(
                    InfiniteClient.theme().colors.primaryColor,
                    Box(x - size, y, z - size, x + size, y + height, z + size),
                )
            graphics3D.renderSolidColorBoxes(listOf(box))
        }

        // 【新規】最も良い着陸地点を視覚化
        bestLandingSpot?.let {
            val size = 5.0
            val color = if (state == PilotState.Circling) 0xAA00FFFF.toInt() else 0xAA00FF00.toInt() // 探索中はシアン、着陸中は緑
            val box =
                RenderUtils.ColorBox(
                    color,
                    Box(
                        it.x - size,
                        it.y.toDouble(),
                        it.z - size,
                        it.x + size,
                        it.y.toDouble() + 5.0, // 地面から5ブロックの高さでマーキング
                        it.z + size,
                    ),
                )
            graphics3D.renderSolidColorBoxes(listOf(box))
        }
    }
}
