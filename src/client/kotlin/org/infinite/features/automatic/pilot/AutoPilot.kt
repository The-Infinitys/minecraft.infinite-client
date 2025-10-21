package org.infinite.features.automatic.pilot

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.client.world.ClientWorld
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
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
import org.infinite.libs.client.player.inventory.InventoryManager
import org.infinite.libs.client.player.inventory.InventoryManager.Armor
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.settings.FeatureSetting
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ターゲットの X, Z 座標を保持するデータクラス
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

// 自動操縦の状態を定義する列挙型
enum class PilotState {
    Idle, // 待機中
    SuperFlying, // SuperFlyモードで飛行中
    FallFlying, // 速度を落とすために下降中
    RiseFlying, // 速度を上げるために上昇中
    Landing, // 着陸中
}

// AutoPilot メイン機能クラス
class AutoPilot : ConfigurableFeature(initialEnabled = false) {
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
    private val standardHeight =
        FeatureSetting.IntSetting(
            "StandardHeight",
            "feature.automatic.autopilot.standardheight.description",
            256,
            128,
            1024,
        )

    // 機能設定リスト
    override val settings: List<FeatureSetting<*>> =
        listOf(elytraThreshold, swapElytra, standardHeight)

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
        get() = InfiniteClient.playerInterface.movement.speed()

    // Y方向の速度
    val riseSpeed: Double
        get() = (player?.velocity?.y ?: 0.0)
    val height: Double
        get() = player?.y ?: 0.0
    private val moveSpeed: Double
        get() {
            if (player == null) {
                return 0.0
            }
            val x = player!!.velocity.x
            val z = player!!.velocity.z
            return sqrt(x * x + z * z)
        }

    private var target: Location? = null
    private var state: PilotState = PilotState.Idle

    // AimTask の結果を受け取るコールバック変数
    var aimTaskCallBack: AimTaskConditionReturn? = null

    /**
     * 毎ティック実行されるメインロジック。
     */
    override fun tick() {
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
        // 【追加】エリトラの自動交換ロジック
        // ----------------------------------------------------------------------
        if (swapElytra.value) {
            checkAndSwapElytra()
        }
        // ----------------------------------------------------------------------

        // ターゲットに到達したかの判定
        if (currentTarget.distance() < 256) {
            InfiniteClient.info("[AutoPilot] ターゲットに到達しました。")
            disable()
            return
        }

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
     * エリトラの耐久値をチェックし、閾値以下であれば交換を試みます。
     */
    private fun checkAndSwapElytra() {
        if (player == null) return

        val currentDurability = elytraDurability()
        val invManager = InfiniteClient.playerInterface.inventory
        val equippedElytraStack = invManager.get(Armor.CHEST)
        // エリトラを装備していない場合も交換を試みる
        val isElytraEquipped = isElytra(equippedElytraStack)

        if (currentDurability <= elytraThreshold.value || !isElytraEquipped) {
            // 予備のエリトラをインベントリから探す
            val nextElytraIndex = findElytraInInventory()

            if (nextElytraIndex != null) {
                // 交換ロジック:
                // 1. チェストスロットと予備のエリトラスロットをスワップする
                // 2. 破損したエリトラはインベントリの空きスロットに戻される
                if (invManager.swap(Armor.CHEST, nextElytraIndex)) {
                    InfiniteClient.info("[AutoPilot] エリトラの耐久値が $currentDurability% になったため、新しいエリトラと交換しました。")
                } else {
                    InfiniteClient.error("[AutoPilot] エリトラの交換に失敗しました。")
                }
            } else if (isElytraEquipped) {
                InfiniteClient.warn("[AutoPilot] エリトラの耐久値が $currentDurability% ですが、予備のエリトラが見つかりませんでした。")
            } else {
                InfiniteClient.warn("[AutoPilot] エリトラを装備していませんが、予備のエリトラが見つかりませんでした。")
            }
        }
    }

    /**
     * 機能が有効になったときに一度だけ実行されます。
     */
    override fun enabled() {
        state = PilotState.Idle
        aimTaskCallBack = null
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
                    if (flySpeed > targetSpeed) {
                        PilotState.FallFlying // 速すぎる -> 下降して減速
                    } else {
                        PilotState.RiseFlying // 遅すぎる -> 上昇して加速
                    }
            }

            PilotState.Landing -> { // 着陸ロジック
            }
            // 下降 (FallFlying) 状態の処理
            PilotState.FallFlying -> {
                when (aimTaskCallBack) {
                    null -> {
                        // AimTask が未開始の場合、新しい AimTask を追加
                        aimTaskCallBack = AimTaskConditionReturn.Suspend
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
                        AimInterface.addTask(AutoPilotAimTask(state, target))
                    }

                    AimTaskConditionReturn.Success -> {
                        // AimTask が目標速度に達して成功した場合、状態を切り替え
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

    // SuperFly 固有のロジック（現時点では空）
    private fun superFly() {
    }

    /**
     * コマンド登録ロジック。
     * /infinite feature Automatic AutoPilot target <x> <z> コマンドを登録。
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

    override fun render2d(graphics2D: Graphics2D) {
        // 機能が無効の場合、ターゲットが設定されていない場合、またはプレイヤーがいない場合は描画しない
        if (isDisabled() || target == null || player == null) {
            return
        }

        val currentTarget = target!!

        // ----------------------------------------------------------------------
        // 1. データ計算
        // ----------------------------------------------------------------------

        // A. 距離
        val distance = currentTarget.distance()

        // B. 速度 (水平速度を使用)
        val speed = moveSpeed * 20 // AutoPilot クラスの flySpeed プロパティを使用 (m/s)

        // C. 到着予想時刻 (Estimated Time of Arrival - ETA)
        val etaSeconds: Long =
            if (speed > 1.0) { // 速度が1m/s以上の場合のみ計算
                (distance / speed).roundToInt().toLong()
            } else {
                -1L // 飛行していない、または非常に遅い場合は計算不可
            }

        // D. 現在の状態
        val stateText =
            when (state) {
                PilotState.Idle -> "待機中"
                PilotState.SuperFlying -> "超速飛行"
                PilotState.FallFlying -> "減速下降 (Pitch: +40°)"
                PilotState.RiseFlying -> "加速上昇 (Pitch: -45°)"
                PilotState.Landing -> "着陸準備中"
            }

        // E. エリトラ耐久値
        val durability =
            if (swapElytra.value) {
                "${elytraDurability()}%"
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
        // 耐久値が閾値以下の場合は警告色、それ以外は通常色
        val durabilityValue = elytraDurability()
        val isElytraEquipped =
            isElytra(InfiniteClient.playerInterface.inventory.get(Armor.CHEST))

        val durabilityColor =
            if (swapElytra.value && isElytraEquipped && durabilityValue <= elytraThreshold.value) {
                0xFFFF0000.toInt() // 赤色 (警告)
            } else {
                white
            }

        // ----------------------------------------------------------------------
        // 3. 情報の描画 (背景とテキスト)
        // ----------------------------------------------------------------------

        val infoLines = mutableListOf<String>()
        infoLines.add("AutoPilot v1.0 | $stateText")
        infoLines.add("Target: X=${currentTarget.x}, Z=${currentTarget.z}")
        infoLines.add("Distance: ${"%.1f".format(distance / 1000.0)} km (%.0f blocks)".format(distance))
        infoLines.add("Speed: ${"%.1f".format(speed)} m/s")
        infoLines.add("Elytra: ${"%.2f".format(durability)}") // 耐久値情報を追加
        if (etaSeconds > 0) {
            val minutes = TimeUnit.SECONDS.toMinutes(etaSeconds)
            val seconds = etaSeconds - TimeUnit.MINUTES.toSeconds(minutes)
            infoLines.add("ETA: ${minutes}m ${seconds}s")
        } else {
            infoLines.add("ETA: Calculating...")
        }

        // 最大幅を計算
        val maxWidth = infoLines.maxOfOrNull { graphics2D.textWidth(it) } ?: 100
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
                    4 -> durabilityColor // 5行目はエリトラ耐久値として耐久値の色
                    else -> white // それ以外は白
                }
            graphics2D.drawText(line, startX + 5, currentY, color)
            currentY += lineHeight
        }
    }

    /**
     * 3D レンダリングロジック。ターゲット地点に縦の箱を描画します。
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
    }
}

/**
 * 自動操縦のための AimTask 定義。
 * AimCalculateMethod.EaseInOut と 5.0 の補正速度で、滑らかな視点移動を保証します。
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
        AimCalculateMethod.Linear,
        // Java ModConfig.turningSpeedDefault (3.0) や pullUpSpeed (2.16) に近い値を使用
        2.0,
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
    override fun check(): AimTaskConditionReturn =
        when (state) {
            PilotState.Idle, PilotState.Landing, PilotState.SuperFlying -> AimTaskConditionReturn.Failure // これらの状態では実行しない
            PilotState.RiseFlying -> handleRiseFlying()
            PilotState.FallFlying -> handleFallFlying()
        }

    /**
     * 上昇 (RiseFlying) 中の条件処理。
     */
    private fun handleRiseFlying(): AimTaskConditionReturn {
        val minSpeedThreshold = 1.0

        autoPilot.aimTaskCallBack =
            if (autoPilot.isDisabled()) {
                AimTaskConditionReturn.Failure
            } else if (autoPilot.flySpeed > minSpeedThreshold) {
                AimTaskConditionReturn.Exec
            } else {
                InfiniteClient.log("${autoPilot.height} - Rise Finished (Speed < $minSpeedThreshold)")
                AimTaskConditionReturn.Success
            }
        return autoPilot.aimTaskCallBack!!
    }

    /**
     * 下降 (FallFlying) 中の条件処理。
     */
    private fun handleFallFlying(): AimTaskConditionReturn {
        val maxSpeedThreshold = 2.2

        autoPilot.aimTaskCallBack =
            if (autoPilot.isDisabled()) {
                AimTaskConditionReturn.Failure
            } else if (autoPilot.flySpeed < maxSpeedThreshold) {
                AimTaskConditionReturn.Exec
            } else {
                InfiniteClient.log("${autoPilot.height} - Fall Finished (Speed >= $maxSpeedThreshold)")
                AimTaskConditionReturn.Success
            }
        return autoPilot.aimTaskCallBack!!
    }
}

class PilotAimTarget(
    val state: PilotState,
    val target: Location,
) : AimTarget.RollTarget(CameraRoll(0.0, 0.0)) {
    // player の取得をゲッターに変更
    private val player: ClientPlayerEntity
        get() = MinecraftClient.getInstance().player!!

    override val roll: CameraRoll
        get() {
            return CameraRoll(
                calculateTargetYaw(),
                when (state) {
                    PilotState.Landing -> 10.0
                    PilotState.FallFlying -> 40.0
                    PilotState.RiseFlying -> -45.0
                    else -> 0.0 // その他の状態
                },
            )
        }

    /**
     * ターゲットへの方向を計算し、目標のヨー角 (YAW) を返します。
     */
    private fun calculateTargetYaw(): Double {
        // player のゲッターを通じて安全にアクセス
        val currentPlayer = player
        val d = target.x - currentPlayer.x
        val f = target.z - currentPlayer.z
        // 逆タンジェントで角度を計算し、度数に変換後、Minecraft の座標系に合わせて -90.0 度オフセット
        return MathHelper.wrapDegrees((MathHelper.atan2(f, d) * (180.0 / Math.PI)) - 90.0)
    }
}

/**
 * アイテムスタックがエリトラであるかを判定します。
 */
private fun isElytra(stack: ItemStack): Boolean = stack.item == Items.ELYTRA

/**
 * インベントリ (ホットバーとバックパック) の中で最初に見つかったエリトラの InventoryIndex を返します。
 * チェストスロットに装備されているものは無視します。
 */
private fun findElytraInInventory(): InventoryManager.InventoryIndex? {
    val playerInv = MinecraftClient.getInstance().player?.inventory ?: return null
    // ホットバー (0-8)
    for (i in 0 until 9) {
        if (isElytra(playerInv.getStack(i))) {
            return InventoryManager.Hotbar(i)
        }
    }
    // バックパック (9-35, Backpack index 0-26)
    for (i in 0 until 27) {
        if (isElytra(playerInv.getStack(9 + i))) {
            return InventoryManager.Backpack(i)
        }
    }
    return null
}
