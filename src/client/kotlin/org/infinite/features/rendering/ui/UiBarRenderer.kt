package org.infinite.features.rendering.ui

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.util.Arm
import org.infinite.InfiniteClient
import org.infinite.gui.theme.ThemeColors
import org.infinite.libs.client.player.ClientInterface
import org.infinite.libs.client.player.PlayerStatsManager.PlayerStats
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.rendering.transparent
import kotlin.math.max
import kotlin.math.min

class UiBarRenderer(
    private val config: UiRenderConfig,
) : ClientInterface() {
    enum class BarSide { Left, Right }

    fun renderBars(
        graphics2D: Graphics2D,
        s: PlayerStats,
        e: EasingManager,
        colors: ThemeColors,
        player: ClientPlayerEntity,
        drawnHpProgress: Double,
        hungerProgress: Double,
        saturationProgress: Double,
    ) {
        val p = config.padding.toDouble()
        val h = config.height.toDouble()
        val a = config.alpha
        val hasOffHand = player.offHandStack?.isEmpty == false

        // 状態異常のオーバーレイ設定
        val (hpOverlayColor, hpOverlayAlpha) = getHpStatusEffectOverlay(player)
        val (hungerOverlayColor, hungerOverlayAlpha) = getHungerStatusEffectOverlay(player)

        // ベースの背景 (全領域)
        renderBar(
            graphics2D,
            1.0,
            h + p * 2.0,
            0.0,
            colors.backgroundColor.transparent((255 * a).toInt()),
            BarSide.Left,
            hasOffHand,
        )
        renderBar(
            graphics2D,
            1.0,
            h + p * 2.0,
            0.0,
            colors.backgroundColor.transparent((255 * a).toInt()),
            BarSide.Right,
            hasOffHand,
        )
        renderBar(
            graphics2D,
            1.0,
            h,
            p,
            colors.backgroundColor.transparent((500 * a).toInt()),
            BarSide.Right,
            hasOffHand,
        )

        // --- 左側 (体力、装甲) ---

        // 1. 装甲 (Armor) バー
        renderBarPair(
            graphics2D,
            max(s.armorProgress, e.easingArmor),
            min(s.armorProgress, e.easingArmor),
            h + p,
            p / 2.0,
            colors.aquaAccentColor,
            a,
            BarSide.Left,
            hasOffHand,
        )
        // 2. 装甲強度 (Toughness) バー
        renderBarPair(
            graphics2D,
            max(s.toughnessProgress, e.easingToughness),
            min(s.toughnessProgress, e.easingToughness),
            h + p,
            p / 2.0,
            colors.blueAccentColor,
            a,
            BarSide.Left,
            hasOffHand,
        )

        // 3. 体力 (Health) バーの背景
        renderBar(
            graphics2D,
            1.0,
            h,
            p,
            colors.backgroundColor.transparent((500 * a).toInt()),
            BarSide.Left,
            hasOffHand,
        )

        // ★ 3.5. 回復予測バーの描画
        if (s.canNaturallyRegenerate) {
            val recoverSpeedFactor = 1 / 8.0
            // 現在のHP (drawnHpProgress) から、満腹度と飽和度から導かれる最大回復可能量 (totalFoodProgress) を加算したプログレスまでを描画
            val recoverProgress =
                when {
                    s.hungerProgress >= 1.0 -> s.saturationProgress + (s.hungerProgress - 0.9) * recoverSpeedFactor
                    s.hungerProgress > 0.9 -> (s.totalFoodProgress - 0.9) * recoverSpeedFactor
                    else -> 0.0
                }.coerceIn(0.0, 1.0)
            val recoveryEndProgress = min(1.0, max(drawnHpProgress, e.easingHp) + recoverProgress)

            renderBar(
                graphics2D,
                recoveryEndProgress, // 予測量を含めた進捗
                h,
                p,
                colors.redAccentColor.transparent((100 * a).toInt()), // 非常に薄い赤（回復予測）
                BarSide.Left,
                hasOffHand,
            )
        }

        // 4. 体力 (Health) バー (推定ダメージ反映)
        renderBarPair(
            graphics2D,
            max(drawnHpProgress, e.easingHp),
            min(drawnHpProgress, e.easingHp),
            h,
            p,
            colors.redAccentColor,
            a,
            BarSide.Left,
            hasOffHand,
        )
        // 5. 吸収 (Absorption) バー
        renderBarPair(
            graphics2D,
            max(s.absorptionProgress, e.easingAbsorption),
            min(s.absorptionProgress, e.easingAbsorption),
            h,
            p,
            colors.yellowAccentColor,
            a * 0.4,
            BarSide.Left,
            hasOffHand,
        )

        // ★ 6. 体力オーバーレイの描画 (炎、毒、衰弱、凍結)
        renderBar(
            graphics2D,
            1.0, // 常にバー全体を覆う
            h + p * 2.0,
            0.0,
            hpOverlayColor.transparent((255 * a * hpOverlayAlpha).toInt()),
            BarSide.Left,
            hasOffHand,
        )

        // --- 右側 (乗り物、満腹度、空気) ---

        // 1. 乗り物の体力 (Vehicle Health) バー
        renderBarPair(
            graphics2D,
            max(s.vehicleHealthProgress, e.easingVehicleHealth),
            min(s.vehicleHealthProgress, e.easingVehicleHealth),
            h + p,
            p / 2.0,
            colors.greenAccentColor,
            a,
            BarSide.Right,
            hasOffHand,
        )

        // 2. 満腹度/飽和度 (Hunger/Saturation) バー
        renderBarPair(
            graphics2D,
            max(hungerProgress, e.easingHunger),
            min(hungerProgress, e.easingHunger),
            h,
            p,
            colors.yellowAccentColor,
            a,
            BarSide.Right,
            hasOffHand,
        )
        renderBarPair(
            graphics2D,
            max(saturationProgress, e.easingSaturation),
            min(saturationProgress, e.easingSaturation),
            h,
            p,
            colors.orangeAccentColor,
            a,
            BarSide.Right,
            hasOffHand,
        )

        // ★ 3. 満腹度オーバーレイの描画 (空腹)
        renderBar(
            graphics2D,
            1.0, // 常にバー全体を覆う
            h + p * 2.0,
            0.0,
            hungerOverlayColor.transparent((255 * a * hungerOverlayAlpha).toInt()),
            BarSide.Right,
            hasOffHand,
        )

        // 4. 空気 (Air) バー
        val transparentOfAirProgress = min(1.0, (1.0 - s.airProgress) * 10)
        renderBarPair(
            graphics2D,
            max(s.airProgress, e.easingAir),
            min(s.airProgress, e.easingAir),
            h / 2.0,
            p,
            colors.oceanAccentColor,
            a * transparentOfAirProgress,
            BarSide.Right,
            hasOffHand,
        )

        // 5. 経験値バー
        renderExperienceBar(graphics2D, e, colors, player)
    }

    // 経験値バーのレンダリング関数
    private fun renderExperienceBar(
        graphics2D: Graphics2D,
        e: EasingManager,
        colors: ThemeColors,
        player: ClientPlayerEntity,
    ) {
        val screenWidth = graphics2D.width
        val screenHeight = graphics2D.height
        val hotBarWidth = 180.0 // ホットバーの幅
        val barHeight = 8.0 // 経験値バーの高さ
        val barOffset = 16.0
        val padding = config.padding.toDouble()

        // 経験値バーの横幅はホットバーと同じくらいにする
        val barWidth = hotBarWidth

        // 経験値バーの開始X座標 (中央揃え)
        val startX = (screenWidth - barWidth) / 2.0
        // 経験値バーのY座標 (ホットバーの上あたり)
        val endX = startX + barWidth
        val endY = screenHeight - padding - barHeight - barOffset
        val startY = endY - barHeight

        val progress = e.easingExperienceProgress.coerceIn(0.0, 1.0)
        val level = e.easingExperienceLevel

        // バーの背景
        graphics2D.rect(
            startX,
            startY,
            endX,
            endY,
            colors.backgroundColor.transparent((255 * config.alpha).toInt()),
        )

        // 経験値バー本体
        graphics2D.rect(
            startX,
            startY,
            startX + barWidth * progress,
            endY,
            colors.limeAccentColor.transparent((255 * config.alpha).toInt()),
        )

        // 経験値レベルの表示
        if (level >= 0) { // レベル0でも表示するように変更
            val levelText = level.toString()
            val textWidth = graphics2D.textWidth(levelText)
            val textX = startX + (barWidth - textWidth) / 2.0 // バーの中央に配置
            val textY = startY + (barHeight - graphics2D.fontHeight()) / 2.0 // バーの垂直方向中央に配置

            graphics2D.drawText(
                levelText,
                textX,
                textY - graphics2D.fontHeight() / 2,
                colors.limeAccentColor,
                true, // shadow
            )

            // バーの左端に経験値量を表示
            val currentExperienceAmount = (progress * player.nextLevelExperience).toInt()
            val totalExperienceAmount = player.nextLevelExperience
            val requiredForNextLevel = player.nextLevelExperience - currentExperienceAmount
            val experienceAmountText = "$currentExperienceAmount / $totalExperienceAmount"
            val requiredForNextLevelText = "($requiredForNextLevel)"
            val expTextX = startX + 2.0 // バーの左端から少し右にオフセット
            val expTextY = textY // レベルテキストと同じ高さ

            graphics2D.drawText(
                experienceAmountText,
                expTextX,
                expTextY,
                colors.limeAccentColor,
                true, // shadow
            )
            graphics2D.drawText(
                requiredForNextLevelText,
                endX - graphics2D.textWidth(requiredForNextLevelText),
                expTextY,
                colors.limeAccentColor,
                true, // shadow
            )
        }
    }

    // 描画を簡略化するためのヘルパー関数
    private fun renderBarPair(
        graphics2D: Graphics2D,
        progressMax: Double,
        progressMin: Double,
        height: Double,
        padding: Double,
        color: Int,
        alphaMultiplier: Double,
        side: BarSide,
        hasOffHand: Boolean,
    ) {
        // イージングが遅れる部分 (淡い色)
        renderBar(
            graphics2D,
            progressMax,
            height,
            padding,
            color.transparent((200 * alphaMultiplier).toInt()),
            side,
            hasOffHand,
        )
        // 現在値の部分 (濃い色)
        renderBar(
            graphics2D,
            progressMin,
            height,
            padding,
            color.transparent((255 * alphaMultiplier).toInt()),
            side,
            hasOffHand,
        )
    }

    // 元のrenderBar関数
    private fun renderBar(
        graphics2D: Graphics2D,
        progress: Double,
        height: Double = 22.0,
        padding: Double,
        color: Int,
        side: BarSide,
        hasOffHand: Boolean,
    ) {
        val progress = progress.coerceIn(0.0, 1.0)
        val screenWidth = graphics2D.width
        val screenHeight = graphics2D.height

        val hotBarWidth = 180
        val offHandSlotSize = 30.0
        val offHandsSide =
            when (options.mainArm.value) {
                Arm.LEFT -> BarSide.Right
                Arm.RIGHT -> BarSide.Left
            }
        // 最大幅の計算ロジック
        val barMaxWidth =
            (screenWidth - hotBarWidth) / 2.0 - 3 * padding -
                if (hasOffHand && side == offHandsSide) offHandSlotSize else 0.0

        // ... (続く描画ロジックは元のコードから変更なし) ...
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
        graphics2D.quad(
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
            graphics2D.quad(
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

    // ★ 状態異常オーバーレイを取得するヘルパー関数 (体力)
    private fun getHpStatusEffectOverlay(player: ClientPlayerEntity): Pair<Int, Double> {
        val defaultColor = 0 // 透明
        val colors = InfiniteClient.theme().colors
        // 毒 (Poison)
        if (player.hasStatusEffect(StatusEffects.POISON)) {
            return Pair(colors.emeraldAccentColor, 0.6)
        }
        // 衰弱 (Wither)
        if (player.hasStatusEffect(StatusEffects.WITHER)) {
            return Pair(colors.foregroundColor, 0.7)
        }
        // 炎 (Fire)
        if (player.isOnFire) {
            return Pair(colors.orangeAccentColor, 0.8)
        }
        // 凍結 (Frozen)
        if (player.isFrozen) {
            return Pair(colors.oceanAccentColor, 0.5)
        }

        return Pair(defaultColor, 0.0)
    }

    // ★ 状態異常オーバーレイを取得するヘルパー関数 (満腹度)
    private fun getHungerStatusEffectOverlay(player: ClientPlayerEntity): Pair<Int, Double> {
        val defaultColor = 0
        val colors = InfiniteClient.theme().colors

        // 空腹 (Hunger)
        if (player.hasStatusEffect(StatusEffects.HUNGER)) {
            return Pair(colors.greenAccentColor, 0.7)
        }

        return Pair(defaultColor, 0.0)
    }
}
