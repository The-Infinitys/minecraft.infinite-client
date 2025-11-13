package org.infinite.features.rendering.ui

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.utils.item.enchantLevel

// プレイヤーの生の統計情報を保持するデータクラス
data class PlayerStats(
    val hpProgress: Double,
    val maxHp: Float,
    val absorptionProgress: Double,
    val armorProgress: Double,
    val toughnessProgress: Double,
    val hungerProgress: Double,
    val saturationProgress: Double,
    val airProgress: Double,
    val vehicleHealthProgress: Double,
)

// 統計情報の取得ロジックをカプセル化
class PlayerStatsManager {
    fun getStats(player: ClientPlayerEntity): PlayerStats {
        // 体力と吸収
        val hp = player.health
        val maxHp = player.maxHealth
        val hpProgress: Double = (hp / maxHp).toDouble()
        // 吸収量を取得。最大吸収量は20 (ハート10個分)
        val absorptionProgress = (player.absorptionAmount / maxHp).coerceAtMost(1f).toDouble()

        // 装甲 (Armor) と装甲強度 (Toughness)
        val armorValue = player.armor.toDouble()
        val maxArmor = 20.0
        val armorProgress: Double = armorValue / maxArmor
        val toughnessValue = player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS)
        val maxToughness = 3.0 * 4 // 一般的な最大値
        val toughnessProgress = toughnessValue / maxToughness

        // 満腹度 (Hunger) と 隠し満腹度 (Saturation)
        val hungerManager = player.hungerManager
        val hungerLevel = hungerManager.foodLevel.toDouble()
        val saturationLevel = hungerManager.saturationLevel.toDouble()
        val maxHunger = 20.0
        val hungerProgress: Double = hungerLevel / maxHunger
        val saturationProgress = saturationLevel / maxHunger

        // 空気 (Air)
        val air = player.air.toDouble()
        val maxAir = player.maxAir.toDouble()
        val airProgress = air / maxAir

        // 乗り物の体力 (Vehicle Health)
        val vehicleHealthProgress =
            player.vehicle?.let { vehicle ->
                if (vehicle is LivingEntity) {
                    (vehicle.health / vehicle.maxHealth).toDouble()
                } else {
                    0.0
                }
            } ?: 0.0

        return PlayerStats(
            hpProgress = hpProgress,
            maxHp = maxHp,
            absorptionProgress = absorptionProgress,
            armorProgress = armorProgress,
            toughnessProgress = toughnessProgress,
            hungerProgress = hungerProgress,
            saturationProgress = saturationProgress,
            airProgress = airProgress,
            vehicleHealthProgress = vehicleHealthProgress,
        )
    }

    /**
     * 装備している食物アイテムから得られる栄養と飽和度の情報を取得します。
     * @return Pair<栄養度の進捗, 飽和度の進捗>
     */
    fun foodSaturation(player: ClientPlayerEntity): Pair<Double, Double> {
        val mainHandItem = player.mainHandStack
        val offHandItem = player.offHandStack
        val food =
            mainHandItem.get(DataComponentTypes.FOOD) ?: offHandItem.get(DataComponentTypes.FOOD) ?: return 0.0 to 0.0
        val maxHunger = 20.0
        val nutrition = food.nutrition / maxHunger
        val saturation = food.saturation / maxHunger
        return nutrition to saturation
    }

    /**
     * 残り潜水時間を秒単位で計算します。
     */
    fun diveSeconds(player: ClientPlayerEntity): Int {
        val remainAirTick = player.air
        val airMultiply =
            1 +
                run {
                    val helmetItem = InventoryManager.get(InventoryManager.InventoryIndex.Armor.Head())
                    val level = enchantLevel(helmetItem, Enchantments.RESPIRATION)
                    return@run level
                }
        val remainBreath = player.getStatusEffect(StatusEffects.WATER_BREATHING)?.duration ?: 0
        return (remainBreath + remainAirTick * airMultiply) / 20
    }
}
