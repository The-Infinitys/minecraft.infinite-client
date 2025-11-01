package org.infinite.features.fighting.totem

import net.minecraft.item.Items
import org.infinite.ConfigurableFeature
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.libs.client.inventory.InventoryManager.InventoryIndex
import org.infinite.settings.FeatureSetting

class AutoTotem : ConfigurableFeature(initialEnabled = false) {
    private val hpSetting: FeatureSetting.IntSetting =
        FeatureSetting.IntSetting("Hp", "feature.fighting.autototem.hp.description", 10, 1, 20)
    override val settings: List<FeatureSetting<*>> =
        listOf(
            hpSetting,
        )

    override fun tick() {
        val health: Float = player?.health ?: 0f
        val targetHealth = hpSetting.value
        if (health < targetHealth) {
            val manager = InventoryManager
            val currentItem = manager.get(InventoryIndex.OffHand())
            if (currentItem.item != Items.TOTEM_OF_UNDYING) {
                val targetSlot = manager.findFirst(Items.TOTEM_OF_UNDYING)
                if (targetSlot != null) {
                    manager.swap(InventoryIndex.OffHand(), targetSlot)
                }
            }
        }
    }
}
