package org.infinite.features.rendering.ui

import org.infinite.libs.client.player.PlayerStatsManager.PlayerStats
import org.infinite.settings.FeatureSetting

/**
 * 描画値のスムージング（イージング）を管理するクラス。
 * 値の変化を滑らかにし、UIの視覚的な快適性を向上させます。
 */
class EasingManager(
    private val easeSpeedSetting: FeatureSetting.DoubleSetting,
) {
    // イージングされた値
    var easingHp = 0.0
        private set
    var easingAbsorption = 0.0
        private set
    var easingArmor = 0.0
        private set
    var easingToughness = 0.0
        private set
    var easingHunger = 0.0
        private set
    var easingSaturation = 0.0
        private set
    var easingAir = 0.0
        private set
    var easingVehicleHealth = 0.0
        private set
    var easingExperienceProgress = 0.0
        private set
    var easingExperienceLevel = 0
        private set

    // 目標値
    private var targetHp = 0.0
    private var targetAbsorption = 0.0
    private var targetArmor = 0.0
    private var targetToughness = 0.0
    private var targetHunger = 0.0
    private var targetSaturation = 0.0
    private var targetAir = 0.0
    private var targetVehicleHealth = 0.0
    private var targetExperienceProgress = 0.0
    private var targetExperienceLevel = 0

    /**
     * 最新のPlayerStatsを目標値として設定します。
     */
    fun updateTarget(s: PlayerStats) {
        targetHp = s.hpProgress
        targetAbsorption = s.absorptionProgress
        targetArmor = s.armorProgress
        targetToughness = s.toughnessProgress
        targetHunger = s.hungerProgress
        targetSaturation = s.saturationProgress
        targetAir = s.airProgress
        targetVehicleHealth = s.vehicleHealthProgress
        targetExperienceProgress = s.experienceProgress
        targetExperienceLevel = s.experienceLevel
        // easingExperienceLevelはイージングしないのでここで直接更新
        easingExperienceLevel = s.experienceLevel
    }

    /**
     * 設定されたイージング速度に基づいてイージング値を更新します。
     */
    fun ease() {
        val e = easeSpeedSetting.value
        easingHp = (1 - e) * easingHp + e * targetHp
        easingAbsorption = (1 - e) * easingAbsorption + e * targetAbsorption
        easingArmor = (1 - e) * easingArmor + e * targetArmor
        easingToughness = (1 - e) * easingToughness + e * targetToughness
        easingHunger = (1 - e) * easingHunger + e * targetHunger
        easingSaturation = (1 - e) * easingSaturation + e * targetSaturation
        easingAir = (1 - e) * easingAir + e * targetAir
        easingVehicleHealth = (1 - e) * easingVehicleHealth + e * targetVehicleHealth
        easingExperienceProgress = (1 - e) * easingExperienceProgress + e * targetExperienceProgress
    }

    /**
     * イージング値をリセットします（例: リスポーン時）。
     */
    fun reset() {
        easingHp = 0.0
        easingAbsorption = 0.0
        easingArmor = 0.0
        easingToughness = 0.0
        easingHunger = 0.0
        easingSaturation = 0.0
        easingAir = 0.0
        easingVehicleHealth = 0.0
        easingExperienceProgress = 0.0
        easingExperienceLevel = 0
    }
}
