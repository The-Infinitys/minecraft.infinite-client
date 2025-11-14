package org.infinite.features.rendering.ui

import net.minecraft.client.network.ClientPlayerEntity
import org.infinite.libs.client.player.PlayerStatsManager
import org.infinite.libs.client.player.PlayerStatsManager.PlayerStats
import org.infinite.settings.FeatureSetting
import kotlin.math.max

// PlayerStatsModel: 描画に必要なデータとその計算ロジックをカプセル化

class PlayerStatsModel(
    private val statsManager: PlayerStatsManager,
    private val damageCalculator: DamageCalculator, // 仮にDamageCalculatorが外部クラスとする
    easeSpeedSetting: FeatureSetting.DoubleSetting,
) {
    // EasingManagerは内部で持つ
    val easingManager = EasingManager(easeSpeedSetting)

    var currentStats: PlayerStats? = null
        private set
    private var fireTicks = 0

    fun tick(player: ClientPlayerEntity) {
        // 1. 統計情報の更新
        currentStats = statsManager.stats() ?: return

        // 2. fireTicksの更新 (元のロジックを保持)
        fireTicks = max(fireTicks, player.fireTicks)
        if (fireTicks > 0) fireTicks--
        if (!player.isOnFire) fireTicks = 0

        // 3. スムージングのターゲットを更新
        currentStats?.let { easingManager.updateTarget(it) }
    }

    fun ease() {
        easingManager.ease()
    }

    fun reset() {
        fireTicks = 0
        // リスポーン時にイージング値もリセット
        easingManager.reset()
    }

    // 推定ダメージの計算 (HyperUiから移動)
    fun estimatedTotalDamage(player: ClientPlayerEntity): Double = damageCalculator.estimations(player, fireTicks)

    // 描画用の体力プログレスの計算 (HyperUiから移動)
    fun calculateDrawnHpProgress(estimatedTotalDamage: Double): Double {
        val s = currentStats ?: return 0.0
        return ((s.hpProgress * s.maxHp - (estimatedTotalDamage - s.absorptionProgress * s.maxHp).coerceAtLeast(0.0)) / s.maxHp).coerceIn(
            0.0,
            1.0,
        )
    }

    // 満腹度/飽和度の計算 (HyperUiから移動)
    fun getFoodProgress(player: ClientPlayerEntity): Pair<Double, Double> {
        val s = currentStats ?: return 0.0 to 0.0
        val (nutritionInfo, saturationInfo) = statsManager.foodSaturation(player)
        val hungerProgress = (s.hungerProgress + nutritionInfo).coerceIn(0.0, 1.0)
        val saturationProgress = (s.saturationProgress + saturationInfo).coerceIn(0.0, hungerProgress)
        return hungerProgress to saturationProgress
    }
}
