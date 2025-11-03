package org.infinite.features.utils.food

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ConsumableComponent
import net.minecraft.component.type.FoodComponent
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.consume.ApplyEffectsConsumeEffect
import net.minecraft.item.consume.TeleportRandomlyConsumeEffect
import org.infinite.ConfigurableFeature
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.settings.FeatureSetting

class FoodManager : ConfigurableFeature() {
    private val targetHunger =
        FeatureSetting.DoubleSetting(
            "TargetHunger",
            "feature.utils.foodmanager.target_hunger.description",
            10.0,
            0.0,
            10.0,
        )
    private val minHunger =
        FeatureSetting.DoubleSetting(
            "MinHunger",
            "feature.utils.foodmanager.min_hunger.description",
            6.5,
            0.0,
            10.0,
        )
    private val injuredHunger =
        FeatureSetting.DoubleSetting(
            "InjuredHunger",
            "feature.utils.foodmanager.injured_hunger.description",
            10.0,
            0.0,
            10.0,
        )
    private val injuryThreshold =
        FeatureSetting.DoubleSetting(
            "InjuryThreshold",
            "feature.utils.foodmanager.injury_threshold.description",
            5.0,
            0.5,
            10.0,
        )
    private val allowRottenFlesh =
        FeatureSetting.BooleanSetting(
            "AllowRottenFlesh",
            "feature.utils.foodmanager.allow_rotten_flesh.description",
            false,
        )
    private val allowChorusFruit =
        FeatureSetting.BooleanSetting(
            "AllowChorusFruit",
            "feature.utils.foodmanager.allow_chorus_fruit.description",
            false,
        )
    private val prioritizeHealth =
        FeatureSetting.BooleanSetting(
            "PrioritizeHealth",
            "feature.utils.foodmanager.prioritize_health.description",
            false,
        )
    private val eatWhileMoving =
        FeatureSetting.BooleanSetting(
            "EatWhileMoving",
            "feature.utils.foodmanager.eat_while_moving.description",
            true, // デフォルトは移動中も食べる(true)にしておくと便利かもしれません
        )
    private val eatWhileAttacking =
        FeatureSetting.BooleanSetting(
            "EatWhileAttacking",
            "feature.utils.foodmanager.eat_while_attacking.description",
            true,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            targetHunger,
            minHunger,
            injuredHunger,
            injuryThreshold,
            allowRottenFlesh,
            allowChorusFruit,
            prioritizeHealth,
            eatWhileMoving,
            eatWhileAttacking,
        )

    private var oldSlot = -1

    // UseKeyがトグル（切り替え）モードかどうか
    private val isUseKeyToggleMode: Boolean
        get() = options.useToggled.value

    override fun enabled() {
        oldSlot = -1
    }

    override fun disabled() {
        if (isEating()) {
            stopEating()
        }
    }

    override fun tick() {
        val player = player ?: return

        if (player.abilities.creativeMode || !player.canConsume(false)) {
            if (isEating()) stopEating()
            return
        }

        val hungerManager = player.hungerManager
        val foodLevel = hungerManager.foodLevel
        val targetHungerI = (targetHunger.value * 2).toInt()
        val minHungerI = (minHunger.value * 2).toInt()
        val injuredHungerI = (injuredHunger.value * 2).toInt()

        // --- 修正箇所 1: 満腹度MAXチェック ---
        // 満腹度が最大値(20)の場合、処理を停止
        if (foodLevel >= 20) {
            if (isEating()) stopEating()
            return
        }
        // --- 修正箇所 1 終了 ---

        // --- 修正箇所 2: 食事継続のチェックとトグル/ホールド対応 ---
        if (isEating()) {
            // ホールドモードの場合、キーを押し続ける必要がある
            if (!isUseKeyToggleMode) {
                options.useKey.isPressed = true
            }

            // プレイヤーがアイテムの使用を終えたら（アニメーションが終了したら）、
            // またはトグルモードの場合は一度押したら食事は続いているので、
            // isUsingItemがfalseになったら完了と判断
            if (!player.isUsingItem) {
                stopEating()
            }
            return
        }
        // --- 修正箇所 2 終了 ---

        val isPlayerInjured = isInjured(player)

        // 食事が必要な条件をチェック
        if (isPlayerInjured && foodLevel < injuredHungerI) {
            eat(-1) // Eat to full if injured
            return
        }

        if (foodLevel < minHungerI) {
            eat(-1) // Eat to min hunger
            return
        }
        // 移動中に食べるか
        if (!eatWhileMoving.value && (
                options.forwardKey.isPressed ||
                    options.backKey.isPressed ||
                    options.leftKey.isPressed ||
                    options.rightKey.isPressed
            )
        ) {
            if (isEating()) {
                stopEating()
            }
            return
        }
        if (client.currentScreen != null) return
        // 攻撃中に食べるか
        if (!eatWhileAttacking.value && options.attackKey.isPressed) {
            if (isEating()) {
                stopEating()
            }
            return
        }
        if (foodLevel < targetHungerI) {
            val maxPoints = targetHungerI - foodLevel
            eat(maxPoints)
        } else if (prioritizeHealth.value && player.health < player.maxHealth) {
            eat(-1) // Eat to heal if health is not full and prioritizeHealth is true
        } else {
            // 食事の必要がない場合は何もしない
        }
    }

    private fun eat(maxPoints: Int) {
        val inventory = inventory ?: return
        val foodSlot = findBestFoodSlot(maxPoints)

        if (foodSlot == -1) {
            if (isEating()) stopEating()
            return
        }

        if (!isEating()) {
            oldSlot = inventory.selectedSlot
        }

        if (foodSlot < 9) { // Hotbar slot
            inventory.selectedSlot = foodSlot
        } else if (foodSlot == 40) { // Off-hand slot
            // No need to select anything, it's already in off-hand
        } else { // Inventory slot, move to hotbar
            // Find an empty hotbar slot or swap with current selected slot
            val currentHotbarSlot = inventory.selectedSlot
            InventoryManager.swap(
                InventoryManager.InventoryIndex.Backpack(foodSlot - 9),
                InventoryManager.InventoryIndex.Hotbar(currentHotbarSlot),
            )
            return // Wait for next tick to eat
        }

        // --- 修正箇所 3: トグル/ホールド対応 ---
        // Eat food: トグル/ホールドモードに応じてキー操作を変更
        if (isUseKeyToggleMode) {
            // トグルモード: キーを一度押す (trueにしてすぐにfalseに戻す)
            options.useKey.isPressed = true
            // 仮想的な入力として、次のティックでfalseに戻すことが理想だが、
            // tick()の同じサイクルでfalseにすると認識されない可能性があるため、
            // isPressed=true の状態を維持し、stopEating()でfalseに戻す戦略を採用
            // ただし、トグルモードの場合、isPressed=true は食事開始のシグナル
        } else {
            // ホールドモード: キーを押し続ける
            options.useKey.isPressed = true
        }
        // --- 修正箇所 3 終了 ---
    }

    // ... (findBestFoodSlot, slotToInventoryIndex, isAllowedFood, isInjured メソッドは省略) ...

    private fun findBestFoodSlot(maxPoints: Int): Int {
        var bestFood: FoodComponent? = null
        var bestSlot = -1
        var bestHealingPotential = 0f

        val slotsToSearch = (0..35).toMutableList()
        slotsToSearch.add(40)

        for (slot in slotsToSearch) {
            val stack = InventoryManager.get(slotToInventoryIndex(slot) ?: continue)

            if (!stack.contains(DataComponentTypes.FOOD)) continue

            val food = stack.get(DataComponentTypes.FOOD) ?: continue
            val consumable = stack.get(DataComponentTypes.CONSUMABLE)

            if (!isAllowedFood(consumable)) continue

            if (maxPoints >= 0 && food.nutrition() > maxPoints) continue

            var currentHealingPotential = 0f
            if (consumable != null) {
                for (effect in consumable.onConsumeEffects()) {
                    if (effect is ApplyEffectsConsumeEffect) {
                        for (statusEffectInstance in effect.effects()) {
                            if (statusEffectInstance.effectType.value() == StatusEffects.SATURATION) {
                                currentHealingPotential += statusEffectInstance.amplifier + 1
                            }
                        }
                    }
                }
            }

            if (prioritizeHealth.value && currentHealingPotential > 0f) {
                if (bestFood == null || currentHealingPotential > bestHealingPotential) {
                    bestFood = food
                    bestSlot = slot
                    bestHealingPotential = currentHealingPotential
                }
            } else {
                if (bestFood == null || food.saturation() > bestFood.saturation()) {
                    bestFood = food
                    bestSlot = slot
                }
            }
        }
        return bestSlot
    }

    private fun slotToInventoryIndex(slot: Int): InventoryManager.InventoryIndex? =
        when (slot) {
            in 0..8 -> InventoryManager.InventoryIndex.Hotbar(slot)
            in 9..35 -> InventoryManager.InventoryIndex.Backpack(slot - 9)
            40 -> InventoryManager.InventoryIndex.OffHand()
            else -> null
        }

    private fun stopEating() {
        options.useKey.isPressed = false
        inventory?.selectedSlot = oldSlot
        oldSlot = -1
    }

    private fun isAllowedFood(consumable: ConsumableComponent?): Boolean {
        if (consumable == null) return false

        for (consumeEffect in consumable.onConsumeEffects()) {
            if (!allowChorusFruit.value && consumeEffect is TeleportRandomlyConsumeEffect) {
                return false
            }

            if (consumeEffect is ApplyEffectsConsumeEffect) {
                for (effect in consumeEffect.effects()) {
                    val entry = effect.effectType
                    if (!allowRottenFlesh.value && entry.value() == StatusEffects.HUNGER) {
                        return false
                    }
                }
            }
        }
        return true
    }

    private fun isEating(): Boolean = oldSlot != -1

    private fun isInjured(player: ClientPlayerEntity): Boolean {
        val injuryThresholdI = (injuryThreshold.value * 2).toInt()
        return player.health < player.maxHealth - injuryThresholdI
    }
}
