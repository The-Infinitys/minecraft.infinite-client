package org.infinite.features.utils.food

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.component.DataComponentTypes
import net.minecraft.component.type.ConsumableComponent
import net.minecraft.component.type.FoodComponent
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.consume.ApplyEffectsConsumeEffect
import net.minecraft.item.consume.TeleportRandomlyConsumeEffect
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.utils.backpack.BackPackManager
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.settings.FeatureSetting

class FoodManager : ConfigurableFeature() {
    private val targetHunger =
        FeatureSetting.DoubleSetting(
            "TargetHunger",
            10.0,
            0.0,
            10.0,
        )
    private val minHunger =
        FeatureSetting.DoubleSetting(
            "MinHunger",
            6.5,
            0.0,
            10.0,
        )
    private val injuredHunger =
        FeatureSetting.DoubleSetting(
            "InjuredHunger",
            10.0,
            0.0,
            10.0,
        )
    private val injuryThreshold =
        FeatureSetting.DoubleSetting(
            "InjuryThreshold",
            5.0,
            0.5,
            10.0,
        )
    private val allowRottenFlesh =
        FeatureSetting.BooleanSetting(
            "AllowRottenFlesh",
            false,
        )
    private val allowChorusFruit =
        FeatureSetting.BooleanSetting(
            "AllowChorusFruit",
            false,
        )
    private val prioritizeHealth =
        FeatureSetting.BooleanSetting(
            "PrioritizeHealth",
            false,
        )
    private val eatWhileMoving =
        FeatureSetting.BooleanSetting(
            "EatWhileMoving",
            true,
        )
    private val eatWhileAttacking =
        FeatureSetting.BooleanSetting(
            "EatWhileAttacking",
            true,
        )

    // --- 追加: スワップディレイ設定 ---
    private val swapDelay =
        FeatureSetting.IntSetting(
            "SwapDelayTicks",
            5, // デフォルト 5 tick (0.25秒)
            0,
            20,
        )
    // ---------------------------------

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
            swapDelay, // 設定リストに追加
        )

    private var oldSlot = -1
    private var delayTicks = 0 // ディレイカウンター

    // UseKeyがトグル（切り替え）モードかどうか
    private val isUseKeyToggleMode: Boolean
        get() = options.useToggled.value

    override fun onEnabled() {
        oldSlot = -1
        delayTicks = 0
    }

    override fun onDisabled() {
        if (isEating()) {
            stopEating()
        }
        delayTicks = 0
    }

    override fun onTick() {
        val player = player ?: return

        if (player.abilities.creativeMode || !player.canConsume(false)) {
            if (isEating()) stopEating()
            delayTicks = 0 // 無効な状態ではディレイをリセット
            return
        }

        val hungerManager = player.hungerManager
        val foodLevel = hungerManager.foodLevel
        val targetHungerI = (targetHunger.value * 2).toInt()
        val minHungerI = (minHunger.value * 2).toInt()
        val injuredHungerI = (injuredHunger.value * 2).toInt()

        // --- 修正箇所 1: 満腹度MAXチェック ---
        if (foodLevel >= 20) {
            if (isEating()) stopEating()
            delayTicks = 0 // 食事不要ならディレイリセット
            return
        }
        // --- 修正箇所 1 終了 ---

        // --- 修正箇所 2: 食事継続のチェックとトグル/ホールド対応 ---
        if (isEating()) {
            delayTicks = 0 // 食事中はディレイ不要
            if (!isUseKeyToggleMode) {
                options.useKey.isPressed = true
            }

            if (!player.isUsingItem) {
                stopEating()
            }
            return
        }
        // --- 修正箇所 2 終了 ---

        // ------------------ 食事判定ロジック ------------------
        val isPlayerInjured = isInjured(player)
        var shouldEat = false
        var maxPointsToEat = -1

        if (isPlayerInjured && foodLevel < injuredHungerI) {
            shouldEat = true // Eat to full if injured
        } else if (foodLevel < minHungerI) {
            shouldEat = true // Eat to min hunger
        } else if (foodLevel < targetHungerI) {
            shouldEat = true
            maxPointsToEat = targetHungerI - foodLevel
        } else if (prioritizeHealth.value && player.health < player.maxHealth) {
            shouldEat = true // Eat to heal if health is not full
        }

        // 外部要因による食事のキャンセル判定
        val isMoving = (
            options.forwardKey.isPressed ||
                options.backKey.isPressed ||
                options.leftKey.isPressed ||
                options.rightKey.isPressed
        )
        val isAttacking = options.attackKey.isPressed

        if (client.currentScreen != null ||
            (!eatWhileMoving.value && isMoving) ||
            (!eatWhileAttacking.value && isAttacking)
        ) {
            if (isEating()) stopEating()
            delayTicks = 0
            return
        }

        // --- ディレイ処理の実行 ---
        if (shouldEat) {
            if (delayTicks < swapDelay.value) {
                // ディレイカウント中
                delayTicks++
                return
            }
            // ディレイ終了、食事開始
            eat(maxPointsToEat)
        } else {
            // 食事の必要がない場合はディレイをリセット
            delayTicks = 0
        }
    }

    private fun eat(maxPoints: Int) {
        val inventory = inventory ?: return
        val foodSlot = findBestFoodSlot(maxPoints)
        val backPackManager = InfiniteClient.getFeature(BackPackManager::class.java) ?: return

        if (foodSlot == -1) {
            if (isEating()) stopEating()
            return
        }

        // 修正 1: 食事開始時、oldSlotを現在の選択スロットに設定する
        if (!isEating()) {
            oldSlot = inventory.selectedSlot
        }

        // ★ BackPackManagerの一時停止/再開をregisterで置き換え
        backPackManager.register {
            if (foodSlot < 9) { // Hotbar slot
                inventory.selectedSlot = foodSlot
            } else if (foodSlot != 40) { // Off-hand slot
                // 修正 2: 現在選択されているホットバースロットとインベントリ内の食べ物をスワップする
                val currentSelectedSlotIndex = inventory.selectedSlot

                // 剣など（Hotbarのアイテム）をインベントリ内の食べ物のスロットに移動させ、
                // 食べ物をホットバースロットに移動させる
                InventoryManager.swap(
                    InventoryManager.InventoryIndex.Backpack(foodSlot - 9),
                    InventoryManager.InventoryIndex.Hotbar(currentSelectedSlotIndex),
                )
                // スワップにより食べ物がホットバーに移動したので、次のティックで食べるためにreturn
                // 現在の選択スロットはそのまま
                return@register // Wait for next tick to eat
            }
        }

        // --- 修正箇所 3 (食事開始のシグナル): トグル/ホールド対応 ---
        options.useKey.isPressed = true
        // --- 修正箇所 3 終了 ---
    }

    // ... (findBestFoodSlot, slotToInventoryIndex, stopEating, isAllowedFood, isEating, isInjured メソッドは省略) ...
    // ※ 以下のメソッドは元のコードから変更なし

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
        delayTicks = 0 // 停止時もディレイリセット
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
