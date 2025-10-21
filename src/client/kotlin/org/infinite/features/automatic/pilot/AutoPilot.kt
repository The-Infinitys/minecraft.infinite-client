package org.infinite.features.automatic.pilot

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.movement.fly.SuperFly
import org.infinite.libs.client.player.fighting.AimInterface
import org.infinite.libs.client.player.fighting.aim.AimCalculateMethod
import org.infinite.libs.client.player.fighting.aim.AimPriority
import org.infinite.libs.client.player.fighting.aim.AimTarget
import org.infinite.libs.client.player.fighting.aim.AimTask
import org.infinite.libs.client.player.fighting.aim.AimTaskCondition
import org.infinite.libs.client.player.fighting.aim.AimTaskConditionReturn
import org.infinite.libs.client.player.fighting.aim.CameraRoll
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.settings.FeatureSetting
import org.infinite.utils.rendering.getRainbowColor
import kotlin.math.abs
import kotlin.math.sqrt

// ターゲットの X, Z 座標を保持するデータクラス (変更なし)
class Location(
    val x: Int,
    val z: Int,
) {
    private val client = MinecraftClient.getInstance()
    private val player: ClientPlayerEntity
        get() = client.player!!

    /** プレイヤーからターゲットまでの水平距離を計算します。 */
    fun distance(): Double {
        val cx = player.x
        val cz = player.z
        val diffX = x - cx
        val diffZ = z - cz
        return sqrt(diffX * diffX + diffZ * diffZ)
    }
}

// 自動操縦の状態を定義する列挙型 (Landing をより詳細に定義)
enum class PilotState {
    Idle, // 待機中
    Takeoff, // 離陸中 (火薬ブースト)
    SuperFlying, // SuperFlyモードで飛行中 (現時点では使用しないが維持)
    FallFlying, // 加速のために下降中 (PullDown)
    RiseFlying, // 減速/高度維持のために上昇中 (PullUp)
    Approaching, // ターゲットへの接近中
    Landing, // 着陸中 (最終降下)
}

// AutoPilot メイン機能クラス
class AutoPilot : ConfigurableFeature(initialEnabled = false) {
    val minSpeed =
        FeatureSetting.DoubleSetting(
            "MinSpeed",
            "feature.automatic.autopilot.minspeed.description",
            10.0,
            5.0,
            50.0,
        ) // RiseFlyingからFallFlyingへの切り替え速度
    val targetSpeed =
        FeatureSetting.DoubleSetting(
            "TargetSpeed",
            "feature.automatic.autopilot.targetspeed.description",
            32.0,
            10.0,
            60.0,
        ) // FallFlyingからRiseFlyingへの切り替え速度
    private val landingDistance =
        FeatureSetting.DoubleSetting(
            "LandingDistance",
            "feature.automatic.autopilot.landingdistance.description",
            150.0,
            50.0,
            500.0,
        ) // 着陸プロセスを開始する距離
    private val standardHeight =
        FeatureSetting.IntSetting(
            "StandardHeight",
            "feature.automatic.autopilot.standardheight.description",
            256,
            128,
            1024,
        ) // 基準高度 (未使用だが設定として維持)

    // 機能設定リスト
    override val settings: List<FeatureSetting<*>> =
        listOf(minSpeed, targetSpeed, landingDistance, standardHeight)

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

    // 現在の飛行速度 (m/s)。ここでは速度をベクトル長で計算する
    val flySpeed: Double
        get() {
            if (player == null) return 0.0
            val v = player!!.velocity
            // 水平方向の速度 (Javaコードの currentVelocityHorizontal に相当)
            return sqrt(v.x * v.x + v.z * v.z) * 20.0 // Velocity (block/tick) * 20 ticks/sec
        }

    // Y方向の速度
    val riseSpeed: Double
        get() = (player?.velocity?.y ?: 0.0) * 20.0 // 実際には速度の正負のみが重要
    val height: Double
        get() = player?.y ?: 0.0
    val pitch: Double
        get() = player?.pitch?.toDouble() ?: 0.0

    private var target: Location? = null
    private var state: PilotState = PilotState.Idle

    // AimTask の結果を受け取るコールバック変数
    var aimTaskCallBack: AimTaskConditionReturn? = null

    /**
     * 毎ティック実行されるメインロジック。
     */
    override fun tick() {
        val currentPlayer = player ?: return
        if (target == null) {
            InfiniteClient.error("[AutoPilot] ターゲットを `/infinite feature Automatic AutoPilot target {x} {z}` で設定してください。")
            disable()
            return
        }

        if (!currentPlayer.isGliding) {
            // エリトラ飛行中でなければ、ターゲットがある場合は離陸処理を試行
            if (state != PilotState.Takeoff) {
                state = PilotState.Takeoff
            }
            processTakeoff()
            return
        } else if (state == PilotState.Takeoff) {
            // 離陸処理を完了 (エリトラ飛行開始)
            state = PilotState.Idle
        }

        val currentTarget = target!!
        val distance = currentTarget.distance()

        // ターゲットに到達したかの判定と状態遷移
        if (distance < 20) {
            InfiniteClient.info("[AutoPilot] ターゲットに到達しました。着陸を開始します。")
            state = PilotState.Landing
        } else if (distance < landingDistance.value) {
            InfiniteClient.info("[AutoPilot] ターゲットに接近中。飛行モードを Approaching に切り替えます。")
            state = PilotState.Approaching
        } else if (state == PilotState.Approaching || state == PilotState.Landing) {
            // 距離が離れて Approaching/Landing から抜ける場合、速度調整に戻る
            state = PilotState.Idle
        }

        process(currentTarget)
    }

    /**
     * 機能が有効になったときに一度だけ実行されます。
     */
    override fun enabled() {
        state = PilotState.Idle
        aimTaskCallBack = null
        InfiniteClient.info("[AutoPilot] 有効化: 離陸または飛行中であれば自動操縦を開始します。")
    }

    /**
     * 機能が無効になったときに一度だけ実行されます。
     */
    override fun disabled() {
        client.options.useKey.isPressed = false // Firework/Rocketキーをオフにする
        client.options.jumpKey.isPressed = false // ジャンプ/エリトラ展開キーをオフにする
        target = null
        state = PilotState.Idle
        aimTaskCallBack = null
        // 既存の AimTask を強制終了 (実装によっては AimInterface.removeTask などが必要だが、ここでは AimTaskConditionReturn.Failure で終了させる)
    }

    /**
     * 離陸処理ロジック (Javaコードの takeoff() に相当)
     */
    private fun processTakeoff() {
        val currentPlayer = player ?: return
        if (state != PilotState.Takeoff) return

        // 離陸処理の簡略化: とりあえずジャンプキーを押してエリトラを開かせる
        client.options.jumpKey.isPressed = true

        // 離陸後の Firework ブーストを AimTask で管理
        if (currentPlayer.isGliding) {
            // エリトラ展開後、ピッチを -90° に向けるAimTaskを開始
            if (aimTaskCallBack == null) {
                aimTaskCallBack = AimTaskConditionReturn.Suspend
                AimInterface.addTask(AutoPilotAimTask(PilotState.Takeoff, target!!))
                InfiniteClient.log("[AutoPilot] Takeoff: AimTaskを開始 (Pitch -90°)")
            }

            if (aimTaskCallBack == AimTaskConditionReturn.Success) {
                // AimTaskが完了（-90°に到達）したら、火薬キーを押し続ける
                client.options.useKey.isPressed = true

                // 速度が十分になったら次の状態へ (ここでは RiseFlying に直接遷移)
                if (flySpeed > 10.0) {
                    client.options.useKey.isPressed = false
                    aimTaskCallBack = null
                    state = PilotState.RiseFlying // 最初の速度調整状態へ
                    InfiniteClient.log("[AutoPilot] Takeoff完了: RiseFlyingへ移行")
                }
            }
        }
    }

    /**
     * 状態に応じた処理を実行します。
     */
    private fun process(target: Location) {
        // SuperFly が有効な場合、状態を優先的に SuperFlying に設定
        if (isSuperFlyEnabled) {
            state = PilotState.SuperFlying
            // superFly(target) // SuperFly固有のロジックをここに書く
            return
        }

        when (state) {
            PilotState.Idle -> {
                // 自動操縦開始時の初期状態決定ロジック
                state =
                    if (flySpeed > targetSpeed.value) {
                        PilotState.FallFlying // 速すぎる -> 下降して減速
                    } else {
                        PilotState.RiseFlying // 遅すぎる -> 上昇して加速
                    }
            }

            PilotState.Approaching -> {
                // 接近モード: 速度を落とすために常に上昇モードに近い状態を維持
                handleAimTask(PilotState.RiseFlying, target) // RiseFlyingと同じAimTaskで制御
            }

            PilotState.Landing -> {
                handleAimTask(PilotState.Landing, target)
                // 着陸判定: 高度2ブロック以下になったら強制終了
                if (player?.isOnGround == true || height < world.seaLevel + 2) {
                    InfiniteClient.info("[AutoPilot] 着陸完了。")
                    disable()
                }
            }

            PilotState.FallFlying -> {
                // FallFlying -> RiseFlying への切り替え判定は AimTaskConditionで行う
                handleAimTask(PilotState.FallFlying, target)
            }

            PilotState.RiseFlying -> {
                // RiseFlying -> FallFlying への切り替え判定は AimTaskConditionで行う
                handleAimTask(PilotState.RiseFlying, target)
            }

            else -> {}
        }
    }

    /**
     * AimTask の追加と結果の処理を共通化
     */
    private fun handleAimTask(
        aimState: PilotState,
        target: Location,
    ) {
        when (aimTaskCallBack) {
            null -> {
                // AimTask が未開始の場合、新しい AimTask を追加
                aimTaskCallBack = AimTaskConditionReturn.Suspend
                AimInterface.addTask(AutoPilotAimTask(aimState, target))
            }

            AimTaskConditionReturn.Success -> {
                // AimTask が目標条件に達して成功した場合、状態を切り替え
                aimTaskCallBack = null
                state =
                    when (aimState) {
                        PilotState.FallFlying -> PilotState.RiseFlying // 減速達成 -> 加速へ
                        PilotState.RiseFlying -> PilotState.FallFlying // 加速達成 -> 減速へ
                        PilotState.Takeoff -> PilotState.RiseFlying // 離陸完了 -> 加速へ
                        PilotState.Approaching -> PilotState.FallFlying // 接近維持 -> 減速へ戻す
                        else -> PilotState.Idle // その他の成功は Idle に戻す
                    }
                InfiniteClient.log("[AutoPilot] AimTask Success: $aimState -> $state")
            }

            AimTaskConditionReturn.Failure, AimTaskConditionReturn.Force -> {
                InfiniteClient.error("[AutoPilot] $aimState 状態で AimTask が予期せぬ終了。")
                disable()
            }

            else -> {} // AimTaskConditionReturn.Exec (実行中) の場合は待機
        }
    }

    /**
     * コマンド登録ロジック。(変更なし)
     */
    override fun registerCommands(builder: LiteralArgumentBuilder<FabricClientCommandSource>) {
        builder.then(
            ClientCommandManager.literal("target").then(
                ClientCommandManager
                    .argument("x", IntegerArgumentType.integer())
                    .then(
                        ClientCommandManager
                            .argument("z", IntegerArgumentType.integer())
                            .executes { context ->
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

    /**
     * 3D レンダリングロジック。(変更なし)
     */
    override fun render3d(graphics3D: Graphics3D) {
        if (target != null) {
            val x = target!!.x.toDouble()
            val y = world.bottomY.toDouble()
            val z = target!!.z.toDouble()
            val height = world.height * 10 // ワールドの高さの10倍の高さ
            val size = 2
            val box =
                RenderUtils.ColorBox(getRainbowColor(), Box(x - size, y, z - size, x + size, y + height, z + size))
            graphics3D.renderSolidColorBoxes(listOf(box))
        }
    }
}

// ----------------------------------------------------------------------
// AimTask 関連クラス
// ----------------------------------------------------------------------

/**
 * 自動操縦のための AimTask 定義。
 */
class AutoPilotAimTask(
    state: PilotState,
    location: Location,
) : AimTask(
        AimPriority.Normally,
        PilotAimTarget(
            state,
            location,
        ),
        AutoPilotCondition(state), // 実行条件
        AimCalculateMethod.EaseInOut,
        5.0, // 速度調整の滑らかさ
    )

/**
 * AimTask の実行条件を定義するクラス。
 * 目標速度に達したかをチェックし、AimTask の継続/終了を決定します。
 */
class AutoPilotCondition(
    val state: PilotState,
) : AimTaskCondition {
    private val autoPilot: AutoPilot
        get() = InfiniteClient.getFeature(AutoPilot::class.java)!!

    /**
     * 実行条件をチェックします。
     */
    override fun check(): AimTaskConditionReturn {
        if (autoPilot.isDisabled()) {
            autoPilot.aimTaskCallBack = AimTaskConditionReturn.Failure
            return AimTaskConditionReturn.Failure
        }

        return when (state) {
            PilotState.Takeoff -> handleTakeoff()
            PilotState.Landing -> handleLanding()
            PilotState.Approaching -> handleApproaching()
            PilotState.RiseFlying -> handleRiseFlying()
            PilotState.FallFlying -> handleFallFlying()
            else -> AimTaskConditionReturn.Failure
        }
    }

    /**
     * 離陸 (Takeoff) 中の条件処理。
     */
    private fun handleTakeoff(): AimTaskConditionReturn {
        // AimTask の目標 (Pitch -90°) に到達したら成功
        val pitchDifference = abs(autoPilot.pitch - (-90.0))
        autoPilot.aimTaskCallBack =
            if (pitchDifference < 1.0) {
                AimTaskConditionReturn.Success // -90°に到達
            } else {
                AimTaskConditionReturn.Exec
            }
        return autoPilot.aimTaskCallBack!!
    }

    /**
     * 上昇 (RiseFlying) 中の条件処理。(加速完了/速度維持モード)
     * Javaコードの pullUp = true の状態
     */
    private fun handleRiseFlying(): AimTaskConditionReturn {
        // 現在速度が目標速度 (TargetSpeed) を超えたら、減速状態へ切り替え
        autoPilot.aimTaskCallBack =
            if (autoPilot.flySpeed >= autoPilot.targetSpeed.value) {
                AimTaskConditionReturn.Success // 速度が速すぎる -> FallFlyingへ
            } else {
                AimTaskConditionReturn.Exec // まだ加速が必要 -> 実行を継続 (上昇を継続)
            }
        return autoPilot.aimTaskCallBack!!
    }

    /**
     * 下降 (FallFlying) 中の条件処理。(減速/加速モード)
     * Javaコードの isDescending = true の状態
     */
    private fun handleFallFlying(): AimTaskConditionReturn {
        // 現在速度が最低速度 (MinSpeed) を下回ったら、加速状態へ切り替え
        autoPilot.aimTaskCallBack =
            if (autoPilot.flySpeed <= autoPilot.minSpeed.value) {
                AimTaskConditionReturn.Success // 速度が遅すぎる -> RiseFlyingへ
            } else {
                AimTaskConditionReturn.Exec // まだ減速/加速が必要 -> 実行を継続 (下降を継続)
            }
        return autoPilot.aimTaskCallBack!!
    }

    /**
     * 接近中 (Approaching) の条件処理。
     */
    private fun handleApproaching(): AimTaskConditionReturn {
        // Approaching時は、速度に関係なく常に目標ピッチを維持し、距離で状態遷移する
        // 速度調整の成功判定をスキップするため、常にExecを返す
        autoPilot.aimTaskCallBack = AimTaskConditionReturn.Exec
        return autoPilot.aimTaskCallBack!!
    }

    /**
     * 着陸 (Landing) の条件処理。
     */
    private fun handleLanding(): AimTaskConditionReturn {
        // 着陸は AimTask 完了ではなく、tick() メソッドの高度判定で終了するため、常に実行を継続
        autoPilot.aimTaskCallBack = AimTaskConditionReturn.Exec
        return autoPilot.aimTaskCallBack!!
    }
}

class PilotAimTarget(
    val state: PilotState,
    val target: Location,
) : AimTarget.RollTarget(CameraRoll(0.0, 0.0)) {
    private val player: ClientPlayerEntity
        get() = MinecraftClient.getInstance().player!!

    override val roll: CameraRoll
        get() {
            // ピッチ角を PilotState に応じて決定
            val pitchAngle =
                when (state) {
                    PilotState.Takeoff -> -90.0 // 離陸: 真上
                    PilotState.Landing -> 45.0 // 着陸: 急降下
                    PilotState.FallFlying -> 35.0 // 下降: 加速/減速用 (Javaコードと同じ)
                    PilotState.RiseFlying, PilotState.Approaching -> -20.0 // 上昇/接近: 減速/高度維持用 (Javaコードの PullUpAngle に近い値)
                    else -> 0.0 // その他の状態
                }
            return CameraRoll(
                calculateTargetYaw(),
                pitchAngle,
            )
        }

    /**
     * ターゲットへの方向を計算し、目標のヨー角 (YAW) を返します。
     */
    private fun calculateTargetYaw(): Double {
        val currentPlayer = player
        val d = target.x - currentPlayer.x
        val f = target.z - currentPlayer.z
        // 逆タンジェントで角度を計算し、度数に変換後、Minecraft の座標系に合わせて -90.0 度オフセット
        return MathHelper.wrapDegrees((MathHelper.atan2(f, d) * (180.0 / Math.PI)) - 90.0)
    }
}
