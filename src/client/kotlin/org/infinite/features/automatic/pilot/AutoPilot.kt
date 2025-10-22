package org.infinite.features.automatic.pilot

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.world.Heightmap
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.movement.fly.SuperFly
import org.infinite.libs.client.player.fighting.AimInterface
import org.infinite.libs.client.player.fighting.aim.AimTaskConditionReturn
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
    SuperFlying, // SuperFlyモードで飛行中
    FallFlying, // 速度を落とすために下降中
    RiseFlying, // 速度を上げるために上昇中
    Gliding, // 一定以上の高さになったので滑空中
    Circling, // 【追加】目標点の周りを旋回し、着陸地点を探索中
    Landing, // 着陸中
}

// AutoPilot メイン機能クラス
class AutoPilot : ConfigurableFeature(initialEnabled = false) {
    val defaultFallDir = 40.0
    val defaultRiseDir = -45.0
    val bestGlidingDir = 5.7
    val fallDir =
        FeatureSetting.DoubleSetting(
            "FallDirectory",
            "feature.automatic.autopilot.falldirectory.description",
            defaultFallDir,
            defaultFallDir - 10,
            defaultFallDir + 10,
        )
    val riseDir =
        FeatureSetting.DoubleSetting(
            "RiseDirectory",
            "feature.automatic.autopilot.risingdirectory.description",
            defaultRiseDir,
            defaultRiseDir - 10,
            defaultRiseDir + 10,
        )
    val glidingDir =
        FeatureSetting.DoubleSetting(
            "GlidingDirectory",
            "feature.automatic.autopilot.glidingdirectory.description",
            20.0,
            bestGlidingDir,
            defaultFallDir,
        )
    private val elytraThreshold =
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

    // 機能設定リスト
    override val settings: List<FeatureSetting<*>> =
        listOf(elytraThreshold, swapElytra, standardHeight, riseDir, glidingDir, fallDir, landingDir)
    private var fallHeight: Double = 0.0
    private var riseHeight: Double = 0.0

    // Minecraft クライアント/プレイヤー/ワールドの簡易参照
    private val client: MinecraftClient
        get() = MinecraftClient.getInstance()
    private val player: ClientPlayerEntity?
        get() = client.player
    private val world: ClientWorld
        get() = client.world!!

    // SuperFly の有効状態
    private val isSuperFlyEnabled: Boolean
        get() = InfiniteClient.isFeatureEnabled(SuperFly::class.java)

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

    private var target: Location? = null
    private var state: PilotState = PilotState.Idle

    // 【新規】着陸に最適な地点を保持
    var bestLandingSpot: LandingSpot? = null

    // AimTask の結果を受け取るコールバック変数
    var aimTaskCallBack: AimTaskConditionReturn? = null

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

        if (target == null) {
            InfiniteClient.error("[AutoPilot] ターゲットを `/infinite feature Automatic AutoPilot target {x} {z}` で設定してください。")
            disable()
            return
        }
        val currentTarget = target!!

        if (player?.isGliding != true) {
            // エリトラ飛行中でなければ、交換ロジックは実行しない
            InfiniteClient.error("[AutoPilot] エリトラで飛行を開始してください。")
            disable()
            return
        }

        // ----------------------------------------------------------------------
        // エリトラの自動交換ロジック
        // ----------------------------------------------------------------------
        if (swapElytra.value) {
            checkAndSwapElytra()
        }
        // ----------------------------------------------------------------------

        // ----------------------------------------------------------------------
        // 【変更】ターゲットに到達したかの判定 (Circling へ移行)
        // ----------------------------------------------------------------------
        val distance = currentTarget.distance()
        val landingStartDistance = 256.0

        if (distance < 32 && state == PilotState.Landing) {
            // 非常に近づいたら成功として無効化（最終的な着地はプレイヤーに任せる）
            InfiniteClient.info("[AutoPilot] ターゲット地点への着陸を完了しました。")
            disable()
            return
        } else if (distance < landingStartDistance && state != PilotState.Circling && state != PilotState.Landing) {
            InfiniteClient.info("[AutoPilot] ターゲット圏内に到達しました。着陸地点の探索を開始します。")
            // AimTaskを中断し、状態を旋回に移行
            aimTaskCallBack = null
            state = PilotState.Circling
        }
        // ----------------------------------------------------------------------

        process(currentTarget)
    }

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
                            "[AutoPilot] エリトラの耐久値が ${currentDurability.roundToInt()}% になったため、より耐久値の高い (${bestElytra.durability.roundToInt()}%) エリトラと交換しました。"
                        } else {
                            "[AutoPilot] エリトラを装備していなかったため、最も耐久値の高い (${bestElytra.durability.roundToInt()}%) エリトラと交換しました。"
                        }
                    InfiniteClient.info(swapMessage)
                } else {
                    InfiniteClient.error("[AutoPilot] エリトラの交換に失敗しました。")
                }
            } else if (isElytraEquipped) {
                InfiniteClient.warn("[AutoPilot] エリトラの耐久値が ${currentDurability.roundToInt()}% ですが、予備のエリトラが見つかりませんでした。")
            } else {
                InfiniteClient.warn("[AutoPilot] エリトラを装備していませんが、予備のエリトラが見つかりませんでした。")
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
        if (isSuperFlyEnabled) {
            state = PilotState.SuperFlying
        }

        when (state) {
            PilotState.Idle, PilotState.SuperFlying -> {
                // 自動操縦開始時または SuperFly 時の初期状態決定ロジック
                val targetSpeed = 35
                // 現在の速度に応じて、減速が必要な下降状態か、加速が必要な上昇状態かを決定
                state =
                    if (moveSpeedAverage > targetSpeed) { // 平均速度で判定
                        PilotState.FallFlying // 速すぎる -> 下降して減速
                    } else {
                        PilotState.RiseFlying // 遅すぎる -> 上昇して加速
                    }
                aimTaskCallBack = null
            }

            // ----------------------------------------------------------------------
            // 【新規】旋回 (Circling) 状態の処理
            // ----------------------------------------------------------------------
            PilotState.Circling -> {
                when (aimTaskCallBack) {
                    null -> {
                        // 旋回と探索の AimTask を開始
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state, target, bestLandingSpot))
                    }

                    AimTaskConditionReturn.Success -> {
                        // 着陸地点が見つかり、着陸フェーズに移行
                        aimTaskCallBack = null
                        state = PilotState.Landing
                        InfiniteClient.info("[AutoPilot] 最適な着陸地点が見つかりました。着陸を開始します。")
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error("[AutoPilot] Circling 状態で予期せぬエラー。")
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
                        AimInterface.addTask(AutoPilotAimTask(state, target, bestLandingSpot))
                        InfiniteClient.info("[AutoPilot] Landing AimTaskを開始。")
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        InfiniteClient.info("[AutoPilot] 着陸条件を満たしました。自動操縦を終了します。")
                        disable()
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error("[AutoPilot] Landing 状態で予期せぬエラー。")
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
                        AimInterface.addTask(AutoPilotAimTask(state, target))
                    }

                    AimTaskConditionReturn.Success -> {
                        // AimTask が目標速度に達して成功した場合、状態を切り替え
                        aimTaskCallBack = null
                        state = PilotState.RiseFlying
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error("[AutoPilot] FallFlying 状態で予期せぬエラー。")
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
                        AimInterface.addTask(AutoPilotAimTask(state, target))
                    }

                    AimTaskConditionReturn.Success -> {
                        // AimTask が目標速度に達して成功した場合、状態を切り替え
                        aimTaskCallBack = null
                        state = if (height > standardHeight.value) PilotState.Gliding else PilotState.FallFlying
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error("[AutoPilot] RiseFlying 状態で予期せぬエラー。")
                        disable()
                    }

                    else -> {} // AimTaskConditionReturn.Exec (実行中) の場合は待機
                }
            }

            PilotState.Gliding -> {
                when (aimTaskCallBack) {
                    null -> {
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
                        AimInterface.addTask(AutoPilotAimTask(state, target))
                    }

                    AimTaskConditionReturn.Success -> {
                        aimTaskCallBack = null
                        state = PilotState.FallFlying
                    }

                    AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                        InfiniteClient.error("[AutoPilot] RiseFlying 状態で予期せぬエラー。")
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
                        InfiniteClient.info("[AutoPilot] ターゲットを設定: X: $x, Z: $z")
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
                PilotState.Idle -> "待機中"
                PilotState.SuperFlying -> "超速飛行"
                PilotState.FallFlying -> "減速下降 (Pitch: +40°)"
                PilotState.RiseFlying -> "加速上昇 (Pitch: -45°)"
                PilotState.Gliding -> "高度調整中"
                PilotState.Circling -> "旋回/地点探索"
                PilotState.Landing -> "着陸準備中"
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
        infoLines.add("AutoPilot | $stateText")
        infoLines.add("Target: X=${currentTarget.x}, Z=${currentTarget.z}")
        infoLines.add("Distance: ${"%.1f".format(distance / 1000.0)} km (%.0f blocks)".format(distance))
        infoLines.add("Average Speed: ${"%.1f".format(moveSpeedAverage)} m/s")
        infoLines.add("Current Speed: ${"%.1f".format(flySpeedDisplay)} m/s")
        infoLines.add("Average Rising: ${"%.1f".format(riseSpeedAverage)} m/s")
        infoLines.add("Current Rising: ${"%.1f".format(riseSpeedDisplay)} m/s")
        infoLines.add("Elytra: $durability") // 耐久値情報を追加
        infoLines.add("ETA: ${formatSecondsToDHMS(etaSeconds)}")
        infoLines.add("REMAIN: ${formatSecondsToDHMS(flightSeconds)}")

        // 【新規】着陸地点情報
        bestLandingSpot?.let {
            infoLines.add("Land Spot: Y=${it.y}, Score=${"%.1f".format(it.score)}")
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
