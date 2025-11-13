package org.infinite.features.rendering.ui

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.utils.item.enchantLevel
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// ダメージ計算ロジックをカプセル化
class DamageCalculator {
    enum class ArmorDamageType { Normal, Fire, Blast, Projectile, Other }

    /**
     * 装甲によるダメージ軽減を計算します。
     */
    fun armorProtection(
        player: ClientPlayerEntity,
        damage: Double,
    ): Double {
        val armor = player.armor.toDouble()
        val toughness = player.attributes.getValue(EntityAttributes.ARMOR_TOUGHNESS)
        val constReduction = 0.2 // 固定最大軽減率
        // ダメージ軽減計算式の適用
        val armorReduction = armor / 5.0
        // ダメージとToughnessに応じた動的な軽減 (複雑な計算のため簡略化せずに残す)
        val toughnessReduction = (armor - ((4.0 * damage) / (toughness + 8))) / 25.0
        val reduction = min(constReduction, max(armorReduction, toughnessReduction))
        return damage * (1.0 - reduction)
    }

    /**
     * エンチャントによるダメージ軽減を計算します。
     */
    fun armorEnchantmentProtection(
        damage: Double,
        type: ArmorDamageType,
    ): Double {
        val armors =
            listOf(
                InventoryManager.get(InventoryManager.InventoryIndex.Armor.Head()),
                InventoryManager.get(InventoryManager.InventoryIndex.Armor.Chest()),
                InventoryManager.get(InventoryManager.InventoryIndex.Armor.Legs()),
                InventoryManager.get(InventoryManager.InventoryIndex.Armor.Foots()),
            )
        var protectionValue = 0
        var fireValue = 0
        var projectileValue = 0
        var blastValue = 0

        for (armor in armors) {
            if (armor.isEmpty) continue
            protectionValue += enchantLevel(armor, Enchantments.PROTECTION)
            fireValue += enchantLevel(armor, Enchantments.FIRE_PROTECTION)
            projectileValue += enchantLevel(armor, Enchantments.PROJECTILE_PROTECTION)
            blastValue += enchantLevel(armor, Enchantments.BLAST_PROTECTION)
        }

        // 軽減率の計算 (Minecraftのロジックを再現)
        val protectionSum =
            when (type) {
                ArmorDamageType.Normal -> protectionValue * 4
                ArmorDamageType.Fire -> protectionValue * 4 + fireValue * 8
                ArmorDamageType.Projectile -> protectionValue * 4 + projectileValue * 8
                ArmorDamageType.Blast -> protectionValue * 4 + blastValue * 8
                ArmorDamageType.Other -> 0
            }
        val reduction = (protectionSum / 100.0).coerceIn(0.0, 0.8) // 最大80%に制限
        return damage * (1.0 - reduction)
    }

    /**
     * ステータス効果（主に耐性/Resistance）によるダメージ軽減を計算します。
     */
    fun playerEffectProtection(
        player: ClientPlayerEntity,
        damage: Double,
    ): Double {
        // 1. 耐性（Resistance）効果のレベルを取得
        val resistanceEffect = player.getStatusEffect(StatusEffects.RESISTANCE) ?: return damage
        val resistanceLevel = resistanceEffect.amplifier + 1 // レベルは0から始まるため+1
        // 2. 軽減率を計算 (レベル * 20%)
        val reductionFactor = min(1.0, resistanceLevel * 0.2) // 最大100% (1.0) に制限
        // 3. 軽減後のダメージを返す
        return damage * (1.0 - reductionFactor)
    }

    /**
     * ポイズン、火災、ウィザーによる推定合計ダメージを計算します。
     * @param fireTicks ティック関数で管理されている火災ティック数
     */
    fun estimations(
        player: ClientPlayerEntity,
        fireTicks: Int,
    ): Double {
        val estimatedPoisonDamage = calculatePoisonDamage(player)
        val estimatedFireDamage = calculateFireDamage(player, fireTicks)
        val estimatedWitherDamage = calculateWitherDamage(player)
        return estimatedFireDamage + estimatedPoisonDamage + estimatedWitherDamage
    }

    private fun calculatePoisonDamage(player: ClientPlayerEntity): Double {
        val poisonEffect = player.getStatusEffect(StatusEffects.POISON) ?: return 0.0
        val poisonDurationTicks: Int = poisonEffect.duration
        val level = poisonEffect.amplifier + 1 // レベルは0から始まるため+1

        val damage: Double
        val intervalTicks: Int
        when (level) {
            1 -> {
                damage = 0.8
                intervalTicks = 25
            }
            2 -> {
                damage = 1.66
                intervalTicks = 12
            }
            3 -> {
                damage = 3.32
                intervalTicks = 12
            }
            4 -> {
                damage = 6.66
                intervalTicks = 12
            }
            in 5..Int.MAX_VALUE -> {
                damage = 20.0
                intervalTicks = 10
            }
            else -> return 0.0
        }

        // ダメージを与える回数 = floor(持続ティック数 / ダメージ間隔ティック数)
        val intervals = floor(poisonDurationTicks.toDouble() / intervalTicks)

        // 軽減適用
        val finalDamagePerInterval = playerEffectProtection(player, armorEnchantmentProtection(damage, ArmorDamageType.Normal))

        return intervals * finalDamagePerInterval
    }

    private fun calculateFireDamage(
        player: ClientPlayerEntity,
        fireTicks: Int,
    ): Double {
        if (fireTicks <= 0) return 0.0
        val damage = 1.0 // 基本火災ダメージ
        val intervalTicks = 20.0 // 20ティックごと (1秒)
        val intervals = floor(fireTicks / intervalTicks)

        // 軽減適用 (装甲も考慮)
        val reducedByArmor = armorProtection(player, damage)
        val reducedByEnchant = armorEnchantmentProtection(reducedByArmor, ArmorDamageType.Fire)
        val finalDamagePerInterval = playerEffectProtection(player, reducedByEnchant)

        return intervals * finalDamagePerInterval
    }

    private fun calculateWitherDamage(player: ClientPlayerEntity): Double {
        val witherEffect = player.getStatusEffect(StatusEffects.WITHER) ?: return 0.0
        val witherDurationTicks: Int = witherEffect.duration
        val level = witherEffect.amplifier + 1 // レベルは0から始まるため+1

        val damage = 1.0 * level // 1.0 * Level
        val intervalTicks = 40.0 // 常に40T間隔でダメージ
        val intervals = floor(witherDurationTicks / intervalTicks)

        // 軽減適用
        val finalDamagePerInterval = playerEffectProtection(player, armorEnchantmentProtection(damage, ArmorDamageType.Normal))

        return intervals * finalDamagePerInterval
    }
}
