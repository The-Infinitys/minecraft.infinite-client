package org.infinite.features.fighting.totem

import net.minecraft.item.Items
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.libs.client.player.inventory.InventoryManager
import org.infinite.settings.FeatureSetting

class AutoTotem : ConfigurableFeature(initialEnabled = false) {
    private val hpSetting: FeatureSetting.IntSetting =
        FeatureSetting.IntSetting("Hp", "トーテムを持ち始めるHP", 10, 1, 20)
    override val settings: List<FeatureSetting<*>> =
        listOf(
            hpSetting,
        )

    override fun tick() {
        val health: Float = InfiniteClient.playerInterface.player?.health ?: 0f
        val targetHealth = hpSetting.value
        if (health < targetHealth) {
            val manager = InfiniteClient.playerInterface.inventory
            val currentItem = manager.get(InventoryManager.Other.OFF_HAND)
            if (currentItem.item != Items.TOTEM_OF_UNDYING) {
                val targetSlot = manager.findFirst(Items.TOTEM_OF_UNDYING)
                if (targetSlot != null) {
                    manager.swap(InventoryManager.Other.OFF_HAND, targetSlot)
                }
            }
        }
    }
}
