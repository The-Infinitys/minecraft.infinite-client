package org.theinfinitys.utils.block

import net.minecraft.block.entity.AbstractFurnaceBlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries

fun AbstractFurnaceBlockEntity.createFuelTimeMap(): Map<Item, Int> {
    val fuelRegistry = MinecraftClient.getInstance().world?.fuelRegistry ?: return mapOf()
    val fuelMap = mutableMapOf<Item, Int>()

    // Iterate over all registered items
    for (item in Registries.ITEM) {
        val fuelTicks = fuelRegistry.getFuelTicks(ItemStack(item))
        if (fuelTicks > 0) {
            fuelMap[item] = fuelTicks
        }
    }

    // Add special case for bucket (if not already included)
    if (!fuelMap.containsKey(Items.BUCKET)) {
        val bucketTicks = fuelRegistry.getFuelTicks(ItemStack(Items.BUCKET))
        if (bucketTicks > 0) {
            fuelMap[Items.BUCKET] = bucketTicks
        }
    }

    return fuelMap
}
