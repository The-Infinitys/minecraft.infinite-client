package org.theinfinitys.features.fighting.totem

import net.minecraft.item.Items
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.InfiniteClient
import org.theinfinitys.infinite.client.player.inventory.InventoryManager
import org.theinfinitys.settings.InfiniteSetting

class AutoTotem : ConfigurableFeature(initialEnabled = false) {
    private val hpSetting: InfiniteSetting.IntSetting =
        InfiniteSetting.IntSetting("Hp", "トーテムを持ち始めるHP", 10, 1, 20)
    override val settings: List<InfiniteSetting<*>> =
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
