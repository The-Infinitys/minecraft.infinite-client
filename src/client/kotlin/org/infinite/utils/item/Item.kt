package org.infinite.utils.item
import net.minecraft.client.MinecraftClient
import net.minecraft.enchantment.Enchantment
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.item.ItemStack
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.entry.RegistryEntry
import java.util.Optional
import java.util.function.Function

fun enchantLevel(
    items: ItemStack,
    enchantment: RegistryKey<Enchantment>,
): Int {
    val registryManager = MinecraftClient.getInstance().world?.registryManager
    val enchantRegistry = registryManager?.getOrThrow(RegistryKeys.ENCHANTMENT)
    if (enchantRegistry != null) {
        val protection: Optional<RegistryEntry.Reference<Enchantment>> =
            enchantRegistry.getOptional(enchantment)
        val protectionLevel =
            protection
                .map(
                    Function { entry: RegistryEntry.Reference<Enchantment> ->
                        EnchantmentHelper.getLevel(
                            entry,
                            items,
                        )
                    },
                ).orElse(0)
        return protectionLevel
    } else {
        return 0
    }
}
