package org.infinite.features.rendering.ui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.component.DataComponentTypes
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.effect.StatusEffects
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.libs.graphics.Graphics2D // Graphics2D クラスのインポート
import org.infinite.settings.FeatureSetting
import org.infinite.utils.item.enchantLevel
import org.infinite.utils.rendering.transparent
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// グローバル変数やクラスプロパティとしてデータを保持する場合 (例として定義)
class HyperUi : ConfigurableFeature() {
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

    private val easeSpeedSetting = FeatureSetting.DoubleSetting("EasingSpeed", 0.25, 0.0, 1.0)
    private val alphaSetting = FeatureSetting.DoubleSetting("Transparency", 0.5, 0.0, 1.0)
    override val settings: List<FeatureSetting<*>> = listOf(easeSpeedSetting, alphaSetting)

    // 取得したデータを保持するためのプロパティ（描画関数からアクセス可能にするため）
    private var stats: PlayerStats? = null

    fun armorProtection(damage: Double): Double {
        val player = player ?: return damage
        val armor = player.armor
        val toughness = player.attributes.getValue(EntityAttributes.ARMOR_TOUGHNESS)
        val constReduction = 0.2
        val armorReduction = armor / 5.0
        val toughnessReduction = (armor - ((4.0 * damage) / (toughness + 8))) / 25.0
        val reduction = min(constReduction, max(armorReduction, toughnessReduction))
        return damage * (1.0 - reduction)
    }

    enum class ArmorDamageType {
        Normal,
        Fire,
        Blast,
        Projectile,
        Other,
    }

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
            val protectionLevel = enchantLevel(armor, Enchantments.PROTECTION)
            val fireLevel = enchantLevel(armor, Enchantments.FIRE_PROTECTION)
            val projectileLevel = enchantLevel(armor, Enchantments.PROJECTILE_PROTECTION)
            val blastLevel = enchantLevel(armor, Enchantments.BLAST_PROTECTION)
            protectionValue += protectionLevel
            fireValue += fireLevel
            projectileValue += projectileLevel
            blastValue += blastLevel
        }
        val reduction =
            (
                when (type) {
                    ArmorDamageType.Normal -> protectionValue * 4
                    ArmorDamageType.Fire -> protectionValue * 4 + fireValue * 8
                    ArmorDamageType.Projectile -> protectionValue * 4 + projectileValue * 8
                    ArmorDamageType.Blast -> protectionValue * 4 + blastValue * 8
                    ArmorDamageType.Other -> 0
                } / 100.0
            ).coerceIn(0.0, 0.8)
        return damage * (1.0 - reduction)
    }

    /**
     * ステータス効果（主に耐性/Resistance）によるダメージ軽減を計算します。
     * 軽減は最大100%に制限されます。
     * * @param damage 現在のダメージ量 (Double)
     * @return 軽減後のダメージ量 (Double)
     */
    fun playerEffectProtection(damage: Double): Double {
        val player = player ?: return damage
        // 1. 耐性（Resistance）効果のレベルを取得
        val resistanceLevel = player.getStatusEffect(StatusEffects.RESISTANCE)?.amplifier ?: return damage
        // 2. 軽減率を計算 (レベル * 20%)
        // Minecraftの耐性効果: 20% * Level
        val reductionFactor = min(1.0, resistanceLevel * 0.2) // 最大100% (1.0) に制限
        // 3. 軽減後のダメージを返す (耐性効果による軽減は通常最後のステップで適用される)
        return damage * (1.0 - reductionFactor)
    }

    // tick()関数をオーバーライドし、ゲームティックごとにデータを更新する
    override fun tick() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        if (fireTicks > 0) fireTicks--
        // データの取得ロジック（省略されていたgetAttributeValueの修正、吸収量の取得を追加）
        val hp = player.health
        val maxHp = player.maxHealth
        val hpProgress: Double = (hp / maxHp).toDouble()
        val armorValue = player.armor.toDouble()
        val maxArmor = 20.0
        val armorProgress: Double = armorValue / maxArmor
        val toughnessValue = player.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS)
        val maxToughness = 3.0 * 4
        val toughnessProgress = toughnessValue / maxToughness
        // 吸収量を取得。最大吸収量は20 (ハート10個分)
        val absorptionProgress = (player.absorptionAmount / maxHp).coerceAtMost(1f).toDouble()

        // 満腹度 (Hunger) と 隠し満腹度 (Saturation)
        val hungerManager = player.hungerManager
        val hungerLevel = hungerManager.foodLevel
        val saturationLevel = hungerManager.saturationLevel
        val maxHunger = 20.0
        val hungerProgress: Double = hungerLevel / maxHunger
        val saturationProgress = saturationLevel / maxHunger
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
        // 取得したデータをプロパティに保存
        stats =
            PlayerStats(
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

    private var fireTicks = 0

    private fun estimations(player: ClientPlayerEntity): Double {
        val poisonEffect = player.getStatusEffect(StatusEffects.POISON)
        val poisonDurationTicks: Int = poisonEffect?.duration ?: 0
        val witherEffect = player.getStatusEffect(StatusEffects.WITHER)
        val witherDurationTicks: Int = witherEffect?.duration ?: 0
        fireTicks = max(fireTicks, player.fireTicks)
        val estimatedTotalPoisonDamage =
            if (poisonDurationTicks > 0 && poisonEffect != null) {
                val level = poisonEffect.amplifier
                val damage =
                    when {
                        level == 0 -> 0.0
                        level == 1 -> 0.8
                        level == 2 -> 1.66
                        level == 3 -> 3.32
                        level == 4 -> 6.66
                        level >= 5 -> 20.0
                        else -> 0.0
                    }
                // ダメージを与える回数 = (持続ティック数 / ダメージ間隔ティック数)
                val intervals =
                    floor(
                        poisonDurationTicks.toDouble() /
                            when {
                                level == 0 -> 40
                                level == 1 -> 25
                                level == 2 -> 12
                                level == 3 -> 12
                                level == 4 -> 12
                                level >= 5 -> 10
                                else -> 40
                            },
                    )
                // 合計ダメージ = 回数 * ダメージ量
                intervals * playerEffectProtection(armorEnchantmentProtection(damage, ArmorDamageType.Normal))
            } else {
                0.0
            }
        val estimatedTotalFireDamage =
            if (fireTicks > 0) {
                val intervals = floor(fireTicks / 20.0)
                val damage = 1.0
                intervals *
                    playerEffectProtection(
                        armorEnchantmentProtection(
                            armorProtection(damage),
                            ArmorDamageType.Fire,
                        ),
                    )
            } else {
                0.0
            }
        val estimatedTotalWitherDamage =
            if (witherDurationTicks > 0 && witherEffect != null) {
                val level = witherEffect.amplifier
                val damage = 1.0 * (level + 1)
                val intervalTicks = 40.0 // 常に40T間隔でダメージ
                // ダメージを与える回数
                val intervals = floor(witherDurationTicks / intervalTicks)
                intervals * playerEffectProtection(armorEnchantmentProtection(damage, ArmorDamageType.Normal))
            } else {
                0.0
            }
        return estimatedTotalFireDamage + estimatedTotalPoisonDamage + estimatedTotalWitherDamage
    }

    private var easingHp = 0.0
    private var easingAbsorption = 0.0
    private var easingArmor = 0.0
    private var easingToughness = 0.0
    private var easingHunger = 0.0
    private var easingSaturation = 0.0
    private var easingAir = 0.0
    private var easingVehicleHealth = 0.0

    /**
     * 画面の左下にプレイヤーの簡易的な統計情報をテキストで描画します。
     * @param graphics2D 描画コンテキスト
     */
    override fun render2d(graphics2D: Graphics2D) {
        val s = stats ?: return
        easing(s)
        val p = 4.0
        val h = 24.0
        val a = alphaSetting.value
        val colors = InfiniteClient.theme().colors
        renderBar(graphics2D, 1.0, h + p * 2.0, 0.0, colors.backgroundColor.transparent(255 * a), BarSide.Left)
        renderBar(graphics2D, 1.0, h + p * 2.0, 0.0, colors.backgroundColor.transparent(255 * a), BarSide.Right)
        renderBar(
            graphics2D,
            max(s.armorProgress, easingArmor),
            h + p,
            p / 2.0,
            colors.aquaAccentColor.transparent(200 * a),
            BarSide.Left,
        )
        renderBar(
            graphics2D,
            min(s.armorProgress, easingArmor),
            h + p,
            p / 2.0,
            colors.aquaAccentColor.transparent(255 * a),
            BarSide.Left,
        )
        renderBar(
            graphics2D,
            max(s.toughnessProgress, easingToughness),
            h + p,
            p / 2.0,
            colors.blueAccentColor.transparent(200 * a),
            BarSide.Left,
        )
        renderBar(
            graphics2D,
            min(s.toughnessProgress, easingToughness),
            h + p,
            p / 2.0,
            colors.blueAccentColor.transparent(255 * a),
            BarSide.Left,
        )
        renderBar(graphics2D, 1.0, h, p, colors.backgroundColor.transparent(500 * a), BarSide.Left)
        val drawnHpProgress =
            ((s.hpProgress * s.maxHp - (estimations(player!!) - s.absorptionProgress * s.maxHp).coerceAtLeast(0.0)) / s.maxHp).coerceIn(
                0.0,
                1.0,
            )

        renderBar(
            graphics2D,
            max(drawnHpProgress, easingHp),
            h,
            p,
            colors.redAccentColor.transparent(200 * a),
            BarSide.Left,
        )
        renderBar(
            graphics2D,
            min(drawnHpProgress, easingHp),
            h,
            p,
            colors.redAccentColor.transparent(255 * a),
            BarSide.Left,
        )
        renderBar(
            graphics2D,
            max(s.absorptionProgress, easingAbsorption),
            h,
            p,
            colors.yellowAccentColor.transparent(100 * a),
            BarSide.Left,
        )
        renderBar(
            graphics2D,
            min(s.absorptionProgress, easingAbsorption),
            h,
            p,
            colors.yellowAccentColor.transparent(100 * a),
            BarSide.Left,
        )
        renderBar(
            graphics2D,
            max(s.vehicleHealthProgress, easingVehicleHealth),
            h + p,
            p / 2.0,
            colors.greenAccentColor.transparent(200 * a),
            BarSide.Right,
        )
        renderBar(
            graphics2D,
            min(s.vehicleHealthProgress, easingVehicleHealth),
            h + p,
            p / 2.0,
            colors.greenAccentColor.transparent(255 * a),
            BarSide.Right,
        )
        renderBar(graphics2D, 1.0, h, p, colors.backgroundColor.transparent(500 * a), BarSide.Right)
        val foodInfo = foodSaturation()
        val hungerProgress = (s.hungerProgress + foodInfo.first).coerceIn(0.0, 1.0)
        val saturationProgress = (s.saturationProgress + foodInfo.second).coerceIn(0.0, s.hungerProgress)
        renderBar(
            graphics2D,
            max(hungerProgress, easingHunger),
            h,
            p,
            colors.yellowAccentColor.transparent(200 * a),
            BarSide.Right,
        )
        renderBar(
            graphics2D,
            min(hungerProgress, easingHunger),
            h,
            p,
            colors.yellowAccentColor.transparent(255 * a),
            BarSide.Right,
        )
        renderBar(
            graphics2D,
            max(saturationProgress, easingSaturation),
            h,
            p,
            colors.orangeAccentColor.transparent(200 * a),
            BarSide.Right,
        )
        renderBar(
            graphics2D,
            min(saturationProgress, easingSaturation),
            h,
            p,
            colors.orangeAccentColor.transparent(255 * a),
            BarSide.Right,
        )
        val transparentOfAirProgress = min(1.0, (1.0 - s.airProgress) * 10)
        val isSubmerged = if (player?.isSubmergedInWater ?: false) 1.0 else 0.0
        renderBar(
            graphics2D,
            max(s.airProgress, easingAir),
            h / 2.0,
            p,
            colors.oceanAccentColor.transparent(200 * transparentOfAirProgress * a),
            BarSide.Right,
        )
        renderBar(
            graphics2D,
            min(s.airProgress, easingAir),
            h / 2.0,
            p,
            colors.oceanAccentColor.transparent(255 * transparentOfAirProgress * a),
            BarSide.Right,
        )
        val diveTime = diveSeconds()
        if (diveTime > 0) {
            val diveTimeString = diveTime.toString() + "s"
            graphics2D.drawText(
                diveTimeString,
                graphics2D.width - graphics2D.textWidth(diveTimeString),
                graphics2D.height - graphics2D.fontHeight(),
                colors.foregroundColor.transparent(255 * isSubmerged * (1 + transparentOfAirProgress) / 2.0),
            )
        }
    }

    private fun diveSeconds(): Int {
        val remainAirTick = player?.air ?: 0
        val airMultiply =
            1 +
                run {
                    val helmetItem = InventoryManager.get(InventoryManager.InventoryIndex.Armor.Head())
                    val level = enchantLevel(helmetItem, Enchantments.RESPIRATION)
                    return@run level
                }
        val remainBreath = player?.getStatusEffect(StatusEffects.WATER_BREATHING)?.duration ?: 0
        return (remainBreath + remainAirTick * airMultiply) / 20
    }

    private fun easing(s: PlayerStats) {
        val e = easeSpeedSetting.value
        easingHp = (1 - e) * easingHp + e * s.hpProgress
        easingAbsorption = (1 - e) * easingAbsorption + e * s.absorptionProgress
        easingArmor = (1 - e) * easingArmor + e * s.armorProgress
        easingToughness = (1 - e) * easingToughness + e * s.toughnessProgress
        easingHunger = (1 - e) * easingHunger + e * s.hungerProgress
        easingSaturation = (1 - e) * easingSaturation + e * s.saturationProgress
        easingAir = (1 - e) * easingAir + e * s.airProgress
        easingVehicleHealth = (1 - e) * easingVehicleHealth + e * s.vehicleHealthProgress
    }

    enum class BarSide {
        Left,
        Right,
    }

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
        val cornerX =
            when (side) {
                BarSide.Right -> screenWidth - padding
                BarSide.Left -> padding
            }
        val cornerPos = cornerX to (screenHeight - padding)
        val topPos = cornerX to screenHeight - height * 1.5 - padding
        val hotBarWidth = 180
        val offHandSlotSize = 30
        val barMaxWidth =
            (screenWidth - hotBarWidth) / 2.0 - 2 * padding -
                if (player?.offHandStack?.isEmpty == false && side == BarSide.Left) offHandSlotSize else 0
        val turnPos =
            cornerX + when (side) {
                BarSide.Left -> barMaxWidth
                BarSide.Right -> -barMaxWidth
            } * 0.9 to screenHeight - height - padding
        val endPos =
            cornerX +
                when (side) {
                    BarSide.Left -> barMaxWidth
                    BarSide.Right -> -barMaxWidth
                } to screenHeight - padding
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

    override fun respawn() {
        fireTicks = 0
    }

    private fun foodSaturation(): Pair<Double, Double> {
        val mainHandItem = InventoryManager.get(InventoryManager.InventoryIndex.MainHand())
        val offHandItem = InventoryManager.get(InventoryManager.InventoryIndex.OffHand())
        val food =
            mainHandItem.get(DataComponentTypes.FOOD) ?: offHandItem.get(DataComponentTypes.FOOD) ?: return 0.0 to 0.0
        val maxHunger = 20.0
        val nutrition = food.nutrition / maxHunger
        val saturation = food.saturation / maxHunger
        return nutrition to saturation
    }
}
