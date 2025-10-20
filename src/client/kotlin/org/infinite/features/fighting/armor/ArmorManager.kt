package org.infinite.features.fighting.armor

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.EquipmentSlot
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.registry.Registries
import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.InfiniteClient
import org.infinite.libs.client.player.inventory.InventoryManager
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
    override val settings: List<FeatureSetting<*>> =
        listOf(
            BooleanSetting("Auto Equip", "Automatically equips the best available armor.", true),
            BooleanSetting("Elytra Switch", "Automatically switches to elytra when airborne.", true),
            IntSetting("Min Health", "Disables elytra when health drops below this value.", 10, 1, 20),
            IntSetting("Durability Threshold", "Ignores armor with durability below this percentage (%).", 5, 0, 100),
        )

    private val autoEquip: BooleanSetting by lazy { settings.find { it.name == "Auto Equip" } as BooleanSetting }
    private val elytraSwitch: BooleanSetting by lazy { settings.find { it.name == "Elytra Switch" } as BooleanSetting }
    private val minHealth: IntSetting by lazy { settings.find { it.name == "Min Health" } as IntSetting }
    private val durabilityThreshold: IntSetting by lazy { settings.find { it.name == "Durability Threshold" } as IntSetting }

    private var previousChestplate: ItemStack = ItemStack.EMPTY
    private var previousSlot: InventoryManager.InventoryIndex? = null
    private var isElytraEquippedByHack: Boolean = false
    private var shouldSendElytraPacket: Boolean = false
    private var floatTick: Int = 0

    override val level: FeatureLevel
        get() = FeatureLevel.EXTEND

    override fun tick() {
        val player = InfiniteClient.playerInterface.player ?: return
        val invManager = InfiniteClient.playerInterface.inventory
        val client = InfiniteClient.playerInterface.client

        // Skip if a screen is open (e.g., inventory GUI)
        if (client.currentScreen != null) return

        val chestSlotIndex = InventoryManager.Armor.CHEST
        val currentChestStack = invManager.get(chestSlotIndex)

        // 🛡️ マニュアル操作による状態不正を検出・修正するロジック
        // 1. ハックがエリトラ装備中と信じているが、実際はエリトラが装備されていない場合（ユーザーが手動でエリトラを外した場合）
        if (isElytraEquippedByHack && currentChestStack.item != Items.ELYTRA) {
            resetElytraState()
        }

        // 2. ハックが以前のチェストプレートを記憶しているが、ユーザーが手動で現在の装備を別のアイテムに変更した場合
        //    (この場合、previousChestplateに戻す処理が邪魔になる可能性があるため、状態をクリアする)
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
            sendStartFallFlyingPacket(player)
            shouldSendElytraPacket = false
        }
    }

    private fun handleElytraSwitch(
        player: ClientPlayerEntity,
        invManager: InventoryManager,
    ) {
        val chestSlotIndex = InventoryManager.Armor.CHEST
        val currentChestStack = invManager.get(chestSlotIndex)
        val isCurrentElytra = currentChestStack.item == Items.ELYTRA
        val options = MinecraftClient.getInstance().options ?: return
        // ★追加: Zキーによる手動解除のチェック
        val isReleaseElytraPressed = options.sneakKey.isPressed && options.sprintKey.isPressed
        val shouldManualUnequip = isReleaseElytraPressed && isElytraEquippedByHack

        // Handle elytra unequip logic (自動解除 または 手動解除)
        if (isElytraEquippedByHack) {
            if (!isCurrentElytra) {
                resetElytraState()
                return
            }
            val shouldAutoUnequip = player.isOnGround || player.health <= minHealth.value || player.isTouchingWater
            if (shouldAutoUnequip || shouldManualUnequip) {
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
                return // Exit to avoid multiple operations in one tick
            }
        }

        // Update floating tick counter
        floatTick = if (player.isOnGround) 0 else floatTick + 1

        // Trigger elytra equip if jumping in mid-air
        val jumpInput = InfiniteClient.playerInterface.client.options.jumpKey.isPressed
        val shouldAttemptElytra = jumpInput && !player.isOnGround && floatTick > 2 && !isCurrentElytra

        if (shouldAttemptElytra && !isElytraEquippedByHack) {
            val elytraSlot = findBestElytraSlot(invManager)
            if (elytraSlot != null) {
                // 装備前のアイテムを保存
                previousChestplate = currentChestStack.copy()
                previousSlot = elytraSlot

                if (invManager.swap(chestSlotIndex, elytraSlot)) {
                    isElytraEquippedByHack = true
                    shouldSendElytraPacket = true
                    return // Exit to ensure swap completes before further actions
                } else {
                    // スワップ失敗時は状態をリセット
                    resetElytraState()
                }
            }
        }
    }

    private fun resetElytraState() {
        previousSlot = null
        previousChestplate = ItemStack.EMPTY
        isElytraEquippedByHack = false
        shouldSendElytraPacket = false
    }

    private fun sendStartFallFlyingPacket(player: ClientPlayerEntity) {
        val packet = ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING)
        player.networkHandler.sendPacket(packet)
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
                    EquipmentSlot.HEAD -> InventoryManager.Armor.HEAD
                    EquipmentSlot.CHEST -> InventoryManager.Armor.CHEST
                    EquipmentSlot.LEGS -> InventoryManager.Armor.LEGS
                    EquipmentSlot.FEET -> InventoryManager.Armor.FEET
                    else -> continue
                }

            val currentStack = invManager.get(armorIndex)
            var bestData = ArmorData(-1, getArmorValue(currentStack))
            bestArmorData[slot] = bestData

            for (i in 0 until 36) {
                val inventoryIndex = if (i < 9) InventoryManager.Hotbar(i) else InventoryManager.Backpack(i - 9)
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

        for (slot in slots.shuffled()) {
            val data = bestArmorData[slot] ?: continue
            if (data.invSlot == -1) continue

            val inventoryIndex =
                when {
                    data.invSlot < 9 -> InventoryManager.Hotbar(data.invSlot)
                    else -> InventoryManager.Backpack(data.invSlot - 9)
                }
            val armorSlot =
                when (slot) {
                    EquipmentSlot.HEAD -> InventoryManager.Armor.HEAD
                    EquipmentSlot.CHEST -> InventoryManager.Armor.CHEST
                    EquipmentSlot.LEGS -> InventoryManager.Armor.LEGS
                    EquipmentSlot.FEET -> InventoryManager.Armor.FEET
                    else -> continue
                }

            if (invManager.swap(armorSlot, inventoryIndex)) {
                return // Process one swap per tick
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

    private fun findBestElytraSlot(invManager: InventoryManager): InventoryManager.InventoryIndex? {
        var bestSlot: InventoryManager.InventoryIndex? = null
        var maxDurability = -1

        for (i in 0 until 36) {
            val index = if (i < 9) InventoryManager.Hotbar(i) else InventoryManager.Backpack(i - 9)
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
