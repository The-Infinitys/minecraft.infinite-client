package org.infinite.features.fighting.armor

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.utils.backpack.BackPackManager
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.libs.client.inventory.InventoryManager.InventoryIndex
import org.infinite.settings.FeatureSetting
import org.infinite.settings.FeatureSetting.BooleanSetting
import org.infinite.settings.FeatureSetting.IntSetting
import org.infinite.utils.item.enchantLevel

private val ARMOR_VALUES =
    mapOf(
        Items.LEATHER_HELMET to 1,
        Items.LEATHER_CHESTPLATE to 3,
        Items.LEATHER_LEGGINGS to 2,
        Items.LEATHER_BOOTS to 1,
        Items.CHAINMAIL_HELMET to 2,
        Items.CHAINMAIL_CHESTPLATE to 5,
        Items.CHAINMAIL_LEGGINGS to 4,
        Items.CHAINMAIL_BOOTS to 1,
        Items.IRON_HELMET to 2,
        Items.IRON_CHESTPLATE to 6,
        Items.IRON_LEGGINGS to 5,
        Items.IRON_BOOTS to 2,
        Items.GOLDEN_HELMET to 2,
        Items.GOLDEN_CHESTPLATE to 5,
        Items.GOLDEN_LEGGINGS to 3,
        Items.GOLDEN_BOOTS to 1,
        Items.DIAMOND_HELMET to 3,
        Items.DIAMOND_CHESTPLATE to 8,
        Items.DIAMOND_LEGGINGS to 6,
        Items.DIAMOND_BOOTS to 3,
        Items.NETHERITE_HELMET to 3,
        Items.NETHERITE_CHESTPLATE to 8,
        Items.NETHERITE_LEGGINGS to 6,
        Items.NETHERITE_BOOTS to 3,
    )

// Armor toughness values
private val ARMOR_TOUGHNESS_VALUES =
    mapOf(
        Items.DIAMOND_HELMET to 2,
        Items.DIAMOND_CHESTPLATE to 2,
        Items.DIAMOND_LEGGINGS to 2,
        Items.DIAMOND_BOOTS to 2,
        Items.NETHERITE_HELMET to 3,
        Items.NETHERITE_CHESTPLATE to 3,
        Items.NETHERITE_LEGGINGS to 3,
        Items.NETHERITE_BOOTS to 3,
    )

class ArmorManager : ConfigurableFeature(initialEnabled = false) {
    private val autoEquip: BooleanSetting =
        BooleanSetting("AutoEquip", true)
    val elytraSwitch: BooleanSetting =
        BooleanSetting("ElytraSwitch", true)
    private val durabilityThreshold: IntSetting =
        IntSetting("DurabilityThreshold", 5, 0, 100)

    override val settings: List<FeatureSetting<*>> =
        listOf(
            autoEquip,
            elytraSwitch,
            durabilityThreshold,
        )

    private var previousChestplate: ItemStack = ItemStack.EMPTY
    private var previousSlot: InventoryIndex? = null
    private var isElytraEquippedByHack: Boolean = false
    private var shouldSendElytraPacket: Boolean = false
    private var floatTick: Int = 0

    // 前のティックでジャンプキーが押されていたかを追跡
    private var wasJumpKeyPressed: Boolean = false
    private var wasTouchingWater: Boolean = false
    override val level: FeatureLevel
        get() = FeatureLevel.Extend

    override fun tick() {
        val player = player ?: return
        val invManager = InventoryManager

        // Skip if a screen is open (e.g., inventory GUI)
        if (client.currentScreen != null) return
        val chestSlotIndex = InventoryIndex.Armor.Chest()
        val currentChestStack = invManager.get(chestSlotIndex)
        if (currentChestStack.item == Items.ELYTRA) {
            isElytraEquippedByHack = true
        }

        if (isElytraEquippedByHack && currentChestStack.item != Items.ELYTRA) {
            resetElytraState()
        }
        if (!previousChestplate.isEmpty && currentChestStack.item != previousChestplate.item && currentChestStack.item != Items.ELYTRA) {
            resetElytraState()
        }

        if (elytraSwitch.value) {
            handleElytraSwitch(player, invManager)
        }

        // Only handle auto-equip if elytra isn't equipped by the hack
        if (autoEquip.value && !isElytraEquippedByHack) {
            handleAutoEquip(invManager)
        }

        // Handle elytra packet sending for immediate gliding
        if (shouldSendElytraPacket && currentChestStack.item == Items.ELYTRA) {
//            sendStartFallFlyingPacket(player)
            shouldSendElytraPacket = false
        }
    }

    private fun handleElytraSwitch(
        player: ClientPlayerEntity,
        invManager: InventoryManager,
    ) {
        val chestSlotIndex = InventoryIndex.Armor.Chest()
        val currentChestStack = invManager.get(chestSlotIndex)
        val isCurrentElytra = currentChestStack.item == Items.ELYTRA
        val options = MinecraftClient.getInstance().options ?: return
        val isReleaseElytraPressed = options.sneakKey.isPressed && options.sprintKey.isPressed
        val backPackManager = InfiniteClient.getFeature(BackPackManager::class.java)

        // Handle elytra unequip logic (自動解除 または 手動解除)
        if (isElytraEquippedByHack) {
            if (!isCurrentElytra) {
                resetElytraState()
                return
            }

            val shouldAutoUnequip = player.isOnGround || player.isTouchingWater

            if (shouldAutoUnequip || isReleaseElytraPressed) {
                // ★ BackPackManagerの一時停止/再開をregisterで置き換え
                backPackManager?.register {
                    var swapped = false
                    // 優先: 以前のアイテムがまだpreviousSlotにあるか確認してからスワップ
                    if (previousSlot != null && !previousChestplate.isEmpty) {
                        val itemInPreviousSlot = invManager.get(previousSlot!!)
                        if (itemInPreviousSlot.item == previousChestplate.item) {
                            swapped = invManager.swap(chestSlotIndex, previousSlot!!)
                        }
                    }
                    if (!swapped) {
                        val originalChestSlot = invManager.findFirst(previousChestplate.item)
                        if (originalChestSlot != null) {
                            swapped = invManager.swap(chestSlotIndex, originalChestSlot)
                        } else {
                            val emptySlot = invManager.findFirstEmptyBackpackSlot()
                            if (emptySlot != null) {
                                swapped = invManager.swap(chestSlotIndex, emptySlot)
                            } else {
                                invManager.drop(chestSlotIndex)
                                swapped = true
                            }
                        }
                    }

                    if (swapped) {
                        resetElytraState() // 成功したら状態をリセット
                    }
                }
                // UnEquip時はwasJumpKeyPressedをリセット
                wasJumpKeyPressed = options.jumpKey.isPressed
                return // Exit to avoid multiple operations in one tick
            }
        }

        // Update floating tick counter
        floatTick = if (player.isOnGround) 0 else floatTick + 1

        // Trigger elytra equip if jumping in mid-air
        val jumpInput = options.jumpKey.isPressed

        // ★追加: 水中脱出の例外条件
        // ピッチ角が-70.0度以下（-90度が真上）の場合を「上を向いている」と判断
        val isLookingUpward = player.pitch <= -70.0f
        // 水中でジャンプキーが押されていて、真上を向いている場合、キー長押しを許可
        val isWaterEscapeAttempt = jumpInput && player.isTouchingWater && isLookingUpward && !wasTouchingWater

        // デフォルトの空中ジャンプ条件: 水中でない かつ 新規ジャンプ入力が必要
        val isAirJumpAttempt = (jumpInput && !wasJumpKeyPressed) && !player.isTouchingWater

        // エリトラ装着を試みるべき最終条件:
        // (新規ジャンプ または 水中脱出) かつ (空中浮遊中) かつ (遅延後) かつ (エリトラ未装着)
        val shouldAttemptElytra =
            (isAirJumpAttempt || isWaterEscapeAttempt) &&
                !player.isOnGround &&
                floatTick > 2 &&
                !isCurrentElytra

        if (shouldAttemptElytra && !isElytraEquippedByHack) {
            val elytraSlot = findBestElytraSlot(invManager)
            if (elytraSlot != null) {
                // 装備前のアイテムを保存
                previousChestplate = currentChestStack.copy()
                previousSlot = elytraSlot

                // ★ BackPackManagerの一時停止/再開をregisterで置き換え
                backPackManager?.register {
                    if (invManager.swap(chestSlotIndex, elytraSlot)) {
                        isElytraEquippedByHack = true
                        shouldSendElytraPacket = true
                        // 装備に成功した場合は、次のティックのために状態を更新
                        wasJumpKeyPressed = true
                    } else {
                        // スワップ失敗時は状態をリセット
                        resetElytraState()
                    }
                }
                return // Exit to ensure swap completes before further actions
            }
        }

        // 次のティックのためにジャンプキーの状態を更新
        wasJumpKeyPressed = jumpInput
        wasTouchingWater = player.isTouchingWater
    }

    private fun resetElytraState() {
        previousSlot = null
        previousChestplate = ItemStack.EMPTY
        isElytraEquippedByHack = false
        shouldSendElytraPacket = false
    }

    private fun handleAutoEquip(invManager: InventoryManager) {
        val slots =
            listOf(
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET,
            )

        // Find the best armor for each slot
        val bestArmorData = mutableMapOf<EquipmentSlot, ArmorData>()
        for (slot in slots) {
            val armorIndex =
                when (slot) {
                    EquipmentSlot.HEAD -> InventoryIndex.Armor.Head()
                    EquipmentSlot.CHEST -> InventoryIndex.Armor.Chest()
                    EquipmentSlot.LEGS -> InventoryIndex.Armor.Legs()
                    EquipmentSlot.FEET -> InventoryIndex.Armor.Foots()
                    else -> continue
                }

            val currentStack = invManager.get(armorIndex)
            var bestData = ArmorData(-1, getArmorValue(currentStack))
            bestArmorData[slot] = bestData

            for (i in 0 until 36) {
                val inventoryIndex = if (i < 9) InventoryIndex.Hotbar(i) else InventoryIndex.Backpack(i - 9)
                val stack = invManager.get(inventoryIndex)

                if (slot == EquipmentSlot.CHEST && stack.item == Items.ELYTRA) continue
                if (getItemEquipmentSlot(stack) != slot) continue

                if (stack.isDamaged) {
                    val durabilityPercent = (stack.maxDamage - stack.damage).toFloat() / stack.maxDamage * 100
                    if (durabilityPercent < durabilityThreshold.value) continue
                }

                val armorValue = getArmorValue(stack)
                if (armorValue > bestData.armorValue) {
                    bestData = ArmorData(i, armorValue)
                    bestArmorData[slot] = bestData
                }
            }
        }

        val backPackManager = InfiniteClient.getFeature(BackPackManager::class.java)
        for (slot in slots.shuffled()) {
            val data = bestArmorData[slot] ?: continue
            if (data.invSlot == -1) continue

            val inventoryIndex =
                when {
                    data.invSlot < 9 -> InventoryIndex.Hotbar(data.invSlot)
                    else -> InventoryIndex.Backpack(data.invSlot - 9)
                }
            val armorSlot =
                when (slot) {
                    EquipmentSlot.HEAD -> InventoryIndex.Armor.Head()
                    EquipmentSlot.CHEST -> InventoryIndex.Armor.Chest()
                    EquipmentSlot.LEGS -> InventoryIndex.Armor.Legs()
                    EquipmentSlot.FEET -> InventoryIndex.Armor.Foots()
                    else -> continue
                }

            // ★ BackPackManagerの一時停止/再開をregisterで置き換え
            backPackManager?.register {
                if (invManager.swap(armorSlot, inventoryIndex)) {
                    return@register // Process one swap per tick
                }
            }
        }
    }

    private fun getArmorValue(stack: ItemStack): Int {
        if (stack.isEmpty) return 0

        val item = stack.item
        if (item == Items.ELYTRA) return 0

        val armorPoints = ARMOR_VALUES[item] ?: 0
        val toughness = ARMOR_TOUGHNESS_VALUES[item] ?: 0
        val protection = enchantLevel(stack, Enchantments.PROTECTION)
        val fireProtection = enchantLevel(stack, Enchantments.FIRE_PROTECTION)
        val blastProtection = enchantLevel(stack, Enchantments.BLAST_PROTECTION)
        val projectileProtection = enchantLevel(stack, Enchantments.PROJECTILE_PROTECTION)
        // 評価式: 防御ポイント * 5 + 強靭さ * 2 + Protectionレベル * 3
        return armorPoints * 5 + (toughness + fireProtection + blastProtection + projectileProtection) * 2 + protection * 3
    }

    private fun findBestElytraSlot(invManager: InventoryManager): InventoryIndex? {
        var bestSlot: InventoryIndex? = null
        var maxDurability = -1

        for (i in 0 until 36) {
            val index = if (i < 9) InventoryIndex.Hotbar(i) else InventoryIndex.Backpack(i - 9)
            val stack = invManager.get(index)
            if (stack.item != Items.ELYTRA) continue

            if (stack.isDamaged) {
                val durabilityPercent = (stack.maxDamage - stack.damage).toFloat() / stack.maxDamage * 100
                if (durabilityPercent < durabilityThreshold.value) continue
            }

            val durability = stack.maxDamage - stack.damage
            if (durability > maxDurability) {
                maxDurability = durability
                bestSlot = index
            }
        }
        return bestSlot
    }

    private fun getItemEquipmentSlot(stack: ItemStack): EquipmentSlot? {
        if (stack.isEmpty) return null
        val id = Registries.ITEM.getId(stack.item).path
        return when {
            id.endsWith("helmet") || id.endsWith("head") -> EquipmentSlot.HEAD
            id.endsWith("chestplate") || id.endsWith("elytra") -> EquipmentSlot.CHEST
            id.endsWith("leggings") -> EquipmentSlot.LEGS
            id.endsWith("boots") -> EquipmentSlot.FEET
            else -> null
        }
    }

    private data class ArmorData(
        val invSlot: Int,
        val armorValue: Int,
    )
}
