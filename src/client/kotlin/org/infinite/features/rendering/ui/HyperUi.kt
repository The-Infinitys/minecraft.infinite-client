package org.infinite.features.rendering.ui

import net.minecraft.client.MinecraftClient
import net.minecraft.util.math.MathHelper
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.libs.client.player.PlayerStatsManager
import org.infinite.libs.client.player.PlayerStatsManager.PlayerStats
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.settings.FeatureSetting
import org.infinite.utils.rendering.transparent
import kotlin.math.max
import kotlin.math.min

class HyperUi : ConfigurableFeature() {
    // --- 設定 ---
    private val easeSpeedSetting = FeatureSetting.DoubleSetting("EasingSpeed", 0.25, 0.0, 1.0)
    private val alphaSetting = FeatureSetting.DoubleSetting("Transparency", 0.5, 0.0, 1.0)
    val heightSetting = FeatureSetting.IntSetting("Height", 24, 8, 32)
    private val paddingSetting = FeatureSetting.IntSetting("Padding", 4, 0, 16)
    override val settings: List<FeatureSetting<*>> =
        listOf(easeSpeedSetting, alphaSetting, heightSetting, paddingSetting)

    // --- 依存オブジェクト ---
    private val statsManager = PlayerStatsManager
    private val easingManager = EasingManager(easeSpeedSetting)
    private val damageCalculator = DamageCalculator()
    private val rayCastRenderer = RayCastRenderer()
    private val flightUiRenderer = ElytraFlightUiRenderer()

    // --- 内部状態 ---
    private var currentStats: PlayerStats? = null
    private var fireTicks = 0

    // --- ライフサイクル/Tick処理 ---

    // tick()関数をオーバーライドし、ゲームティックごとにデータを更新する
    override fun tick() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        // 1. 統計情報の更新
        currentStats = statsManager.stats() ?: return

        // 2. fireTicksの更新
        fireTicks = max(fireTicks, player.fireTicks)
        if (fireTicks > 0) fireTicks-- // ティックごとに減少させる
        if (!player.isOnFire) fireTicks = 0
        // 3. スムージングのターゲットを更新
        currentStats?.let { easingManager.updateTarget(it) }
    }

    override fun respawn() {
        fireTicks = 0
        // リスポーン時にイージング値もリセット
        easingManager.reset()
    }
    // --- 描画処理 ---

    /**
     * 画面の左下・右下にプレイヤーの統計情報を描画します。
     * @param graphics2D 描画コンテキスト
     */
    override fun render2d(graphics2D: Graphics2D) {
        val s = currentStats ?: return
        easingManager.ease() // イージング値を計算
        val e = easingManager // 短く参照

        val p = paddingSetting.value.toDouble()
        val h = heightSetting.value.toDouble()
        val a = alphaSetting.value
        val colors = InfiniteClient.theme().colors

        // 描画に必要な推定ダメージを計算
        val player = MinecraftClient.getInstance().player ?: return
        val estimatedTotalDamage = damageCalculator.estimations(player, fireTicks)

        // ベースの背景 (全領域)
        renderBar(graphics2D, 1.0, h + p * 2.0, 0.0, colors.backgroundColor.transparent(255 * a), BarSide.Left)
        renderBar(graphics2D, 1.0, h, p, colors.backgroundColor.transparent(500 * a), BarSide.Right)

        // --- 左側 (体力、装甲) ---

        // 1. 装甲 (Armor) バー
        renderBar(
            graphics2D,
            max(s.armorProgress, e.easingArmor),
            h + p,
            p / 2.0,
            colors.aquaAccentColor.transparent(200 * a),
            BarSide.Left,
        )
        renderBar(
            graphics2D,
            min(s.armorProgress, e.easingArmor),
            h + p,
            p / 2.0,
            colors.aquaAccentColor.transparent(255 * a),
            BarSide.Left,
        )

        // 2. 装甲強度 (Toughness) バー
        renderBar(
            graphics2D,
            max(s.toughnessProgress, e.easingToughness),
            h + p,
            p / 2.0,
            colors.blueAccentColor.transparent(200 * a),
            BarSide.Left,
        )
        renderBar(
            graphics2D,
            min(s.toughnessProgress, e.easingToughness),
            h + p,
            p / 2.0,
            colors.blueAccentColor.transparent(255 * a),
            BarSide.Left,
        )

        // 3. 体力 (Health) バーの背景
        renderBar(graphics2D, 1.0, h, p, colors.backgroundColor.transparent(500 * a), BarSide.Left)

        // 4. 体力 (Health) バー
        // 推定ダメージを反映した体力プログレス
        val drawnHpProgress =
            ((s.hpProgress * s.maxHp - (estimatedTotalDamage - s.absorptionProgress * s.maxHp).coerceAtLeast(0.0)) / s.maxHp).coerceIn(
                0.0,
                1.0,
            )
        renderBar(
            graphics2D,
            max(drawnHpProgress, e.easingHp),
            h,
            p,
            colors.redAccentColor.transparent(200 * a),
            BarSide.Left,
        )
        renderBar(
            graphics2D,
            min(drawnHpProgress, e.easingHp),
            h,
            p,
            colors.redAccentColor.transparent(255 * a),
            BarSide.Left,
        )

        // 5. 吸収 (Absorption) バー
        renderBar(
            graphics2D,
            max(s.absorptionProgress, e.easingAbsorption),
            h,
            p,
            colors.yellowAccentColor.transparent(100 * a),
            BarSide.Left,
        )
        renderBar(
            graphics2D,
            min(s.absorptionProgress, e.easingAbsorption),
            h,
            p,
            colors.yellowAccentColor.transparent(100 * a),
            BarSide.Left,
        )

        // --- 右側 (乗り物、満腹度、空気) ---
        renderBar(graphics2D, 1.0, h + p * 2.0, 0.0, colors.backgroundColor.transparent(255 * a), BarSide.Right)

        // 1. 乗り物の体力 (Vehicle Health) バー
        renderBar(
            graphics2D,
            max(s.vehicleHealthProgress, e.easingVehicleHealth),
            h + p,
            p / 2.0,
            colors.greenAccentColor.transparent(200 * a),
            BarSide.Right,
        )
        renderBar(
            graphics2D,
            min(s.vehicleHealthProgress, e.easingVehicleHealth),
            h + p,
            p / 2.0,
            colors.greenAccentColor.transparent(255 * a),
            BarSide.Right,
        )

        // 3. 満腹度/飽和度 (Hunger/Saturation) バー
        val (nutritionInfo, saturationInfo) = statsManager.foodSaturation(player)
        val hungerProgress = (s.hungerProgress + nutritionInfo).coerceIn(0.0, 1.0)
        val saturationProgress = (s.saturationProgress + saturationInfo).coerceIn(0.0, hungerProgress)
        renderBar(
            graphics2D,
            max(hungerProgress, e.easingHunger),
            h,
            p,
            colors.yellowAccentColor.transparent(200 * a),
            BarSide.Right,
        )
        renderBar(
            graphics2D,
            min(hungerProgress, e.easingHunger),
            h,
            p,
            colors.yellowAccentColor.transparent(255 * a),
            BarSide.Right,
        )
        renderBar(
            graphics2D,
            max(saturationProgress, e.easingSaturation),
            h,
            p,
            colors.orangeAccentColor.transparent(200 * a),
            BarSide.Right,
        )
        renderBar(
            graphics2D,
            min(saturationProgress, e.easingSaturation),
            h,
            p,
            colors.orangeAccentColor.transparent(255 * a),
            BarSide.Right,
        )

        // 4. 空気 (Air) バー
        val transparentOfAirProgress = min(1.0, (1.0 - s.airProgress) * 10)
        val isSubmerged = if (player.isSubmergedInWater) 1.0 else 0.0
        renderBar(
            graphics2D,
            max(s.airProgress, e.easingAir),
            h / 2.0,
            p,
            colors.oceanAccentColor.transparent(200 * transparentOfAirProgress * a),
            BarSide.Right,
        )
        renderBar(
            graphics2D,
            min(s.airProgress, e.easingAir),
            h / 2.0,
            p,
            colors.oceanAccentColor.transparent(255 * transparentOfAirProgress * a),
            BarSide.Right,
        )
        val sprinting = player.isSprinting && !player.isSwimming && !player.isGliding
        if (sprinting) {
            val sprintTime = statsManager.sprintMeters(player)
            val sprintableLength = sprintTime.toString() + "m"
            graphics2D.drawText(
                sprintableLength,
                graphics2D.width - graphics2D.textWidth(sprintableLength),
                graphics2D.height - graphics2D.fontHeight(),
                colors.foregroundColor.transparent(255),
            )
        }
        // 5. 潜水時間テキスト
        val diveTime = statsManager.diveSeconds(player)
        val diveTimeString = diveTime.toString() + "s"
        if (diveTime > 0) {
            graphics2D.drawText(
                diveTimeString,
                graphics2D.width - graphics2D.textWidth(diveTimeString),
                graphics2D.height - graphics2D.fontHeight(),
                colors.foregroundColor.transparent(255 * isSubmerged * (1 + transparentOfAirProgress) / 2.0),
            )
        }
        val swimming = player.isSwimming && player.isSprinting
        if (swimming) {
            val swimTime = statsManager.swimMeters(player)
            val spaceString = " "
            val swimmableLength = swimTime.toString() + "m"
            graphics2D.drawText(
                swimmableLength,
                graphics2D.width - graphics2D.textWidth(swimmableLength + spaceString + diveTimeString),
                graphics2D.height - graphics2D.fontHeight(),
                colors.foregroundColor.transparent(255),
            )
        }
        val gliding = player.isGliding
        if (gliding) {
            flightUiRenderer.render(graphics2D)
        }

        // --- 方位計の描画を追加 ---
        renderCompassAtTopCenter(graphics2D, player.getLerpedYaw(graphics2D.tickProgress))
    }

    // --- 方位計の描画関数 ---
    private fun renderCompassAtTopCenter(
        graphics2D: Graphics2D,
        playerYaw: Float,
    ) {
        val width = graphics2D.width
        val colors = InfiniteClient.theme().colors

        val compassWidth = 200 // 方位計の全体の幅
        val compassHeight = 20 // 方位計の高さ
        val topY = paddingSetting.value.toDouble() // 画面上部からのパディング

        val centerX = width / 2

        // 現在のプレイヤーのヨー角を正規化
        val currentYaw = MathHelper.wrapDegrees(playerYaw)

        // 主要な方角
        val cardinalPoints =
            mapOf(
                0f to "S",
                90f to "W",
                180f to "N",
                270f to "E",
            )
        // 中間の方角
        val interCardinalPoints =
            mapOf(
                45f to "SW",
                135f to "NW",
                225f to "NE",
                315f to "SE",
            )

        // 方位計の表示範囲 (例: 中心から左右90度ずつ、合計180度)
        val displayRangeDegrees = 90f
        val pixelsPerDegree = compassWidth / (displayRangeDegrees * 2) // 1度あたりのピクセル数
        val majorMarkColor = colors.foregroundColor
        val minorMarkColor = colors.foregroundColor

        // 中央の現在位置を示すポインター
        graphics2D.drawLine(
            centerX.toDouble(),
            topY,
            centerX.toDouble(),
            topY + compassHeight,
            colors.primaryColor.transparent(255),
            2,
        )
        for (degree in 0..359 step 5) { // 5度刻みで目盛りをチェック
            val relativeDegree = MathHelper.wrapDegrees(degree - currentYaw)
            // 表示範囲内にある場合のみ描画
            if (relativeDegree >= -displayRangeDegrees && relativeDegree <= displayRangeDegrees) {
                val markX = centerX + (relativeDegree * pixelsPerDegree).toInt()
                var markLength = 5.0 // 小さい目盛りの長さ
                var displayChar: String? = null
                var markDrawColor = minorMarkColor.transparent(255)

                if (degree % 90 == 0) { // 主要な方角 (N, S, E, W)
                    markLength = 10.0
                    displayChar = cardinalPoints[degree.toFloat()]
                    markDrawColor = majorMarkColor.transparent(255)
                    if (displayChar == "N") markDrawColor = colors.purpleAccentColor.transparent(255) // 北だけ特別色
                } else if (degree % 45 == 0) { // 中間の方角 (NE, NW, SE, SW)
                    markLength = 8.0
                    displayChar = interCardinalPoints[degree.toFloat()]
                    markDrawColor = majorMarkColor.transparent(200)
                } else if (degree % 10 == 0) { // 10度ごとの目盛り
                    markLength = 7.0
                    markDrawColor = minorMarkColor.transparent(200)
                }

                // 目盛り線
                graphics2D.drawLine(
                    markX.toDouble(),
                    topY + compassHeight,
                    markX.toDouble(),
                    topY + compassHeight + markLength,
                    markDrawColor,
                    1,
                )

                // 方角テキスト
                if (displayChar != null) {
                    val textWidth = graphics2D.textWidth(displayChar)
                    graphics2D.drawText(
                        displayChar,
                        markX - textWidth / 2,
                        topY.toInt() + 2, // 目盛り線の上に表示
                        markDrawColor,
                        true, // shadow
                    )
                }
            }
        }
    }

    // --- 描画ユーティリティ（複雑な描画ロジックのため、このクラスに残す） ---

    enum class BarSide { Left, Right }

    // 描画処理に必要なGraphics2Dオブジェクトを引数に追加
    private fun renderBar(
        graphics2D: Graphics2D,
        progress: Double,
        height: Double = 22.0,
        padding: Double,
        color: Int,
        side: BarSide,
    ) {
        val progress = progress.coerceIn(0.0, 1.0)
        val screenWidth = graphics2D.width
        val screenHeight = graphics2D.height

        val hotBarWidth = 180
        val offHandSlotSize = 30
        val hasOffHand = player?.offHandStack?.isEmpty == false

        val barMaxWidth =
            (screenWidth - hotBarWidth) / 2.0 - 3 * padding -
                if (hasOffHand && side == BarSide.Left) offHandSlotSize else 0

        val cornerX =
            when (side) {
                BarSide.Right -> screenWidth - padding
                BarSide.Left -> padding
            }
        val cornerPos = cornerX to (screenHeight - padding)
        val topPos = cornerX to screenHeight - height * 1.5 - padding

        val turnPos =
            cornerX +
                when (side) {
                    BarSide.Left -> barMaxWidth * 0.9
                    BarSide.Right -> -barMaxWidth * 0.9
                } to screenHeight - height - padding

        val endPos =
            cornerX +
                when (side) {
                    BarSide.Left -> barMaxWidth
                    BarSide.Right -> -barMaxWidth
                } to screenHeight - padding

        // バーの描画ロジック... (元のコードと同じ)
        val coercedProgress09 = progress.coerceIn(0.0, 0.9)
        graphics2D.fillQuad(
            topPos.first,
            topPos.second,
            topPos.first,
            cornerPos.second,
            (endPos.first - topPos.first) * coercedProgress09 + topPos.first,
            cornerPos.second,
            (endPos.first - topPos.first) * coercedProgress09 + topPos.first,
            (turnPos.second - topPos.second) * coercedProgress09 / 0.9 + topPos.second,
            color,
        )
        if (progress > coercedProgress09) {
            val reducedProgress = (progress - 0.9) / (1.0 - 0.9)
            graphics2D.fillQuad(
                turnPos.first,
                turnPos.second,
                turnPos.first,
                cornerPos.second,
                (endPos.first - topPos.first) * progress + topPos.first,
                cornerPos.second,
                (endPos.first - topPos.first) * progress + topPos.first,
                (cornerPos.second - turnPos.second) * reducedProgress + turnPos.second,
                color,
            )
        }
    }

    override fun render3d(graphics3D: Graphics3D) {
        rayCastRenderer.render(graphics3D)
    }
}
