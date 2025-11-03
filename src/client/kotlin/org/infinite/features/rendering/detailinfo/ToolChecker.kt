package org.infinite.features.rendering.detailinfo

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.client.MinecraftClient
import net.minecraft.enchantment.Enchantments
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.Identifier

object ToolChecker {
    enum class ToolKind {
        Sword,
        Axe,
        PickAxe,
        Shovel,
        Hoe,
    }

    class CorrectTool(
        val toolKind: ToolKind?,
        val toolLevel: Int,
        val isSilkTouchRequired: Boolean = false,
    ) {
        fun checkPlayerToolStatus(): Int {
            val client = MinecraftClient.getInstance()
            val player = client.player ?: return 2
            val heldItem: ItemStack = player.mainHandStack

            if (toolKind == null) return 0

            val toolId = Registries.ITEM.getId(heldItem.item).toString()
            val materialStr = toolId.substringAfter("minecraft:").substringBeforeLast("_")
            val isCorrectToolKind =
                when (toolKind) {
                    ToolKind.PickAxe -> toolId.endsWith("_pickaxe")
                    ToolKind.Axe -> toolId.endsWith("_axe")
                    ToolKind.Shovel -> toolId.endsWith("_shovel")
                    ToolKind.Sword -> toolId.endsWith("_sword")
                    ToolKind.Hoe -> toolId.endsWith("_hoe")
                }

            if (!isCorrectToolKind) return 2

            val actualLevel =
                when (materialStr) {
                    "wooden", "golden" -> 0
                    "stone" -> 1
                    "iron" -> 2
                    "diamond" -> 3
                    "netherite" -> 4
                    else -> -1
                }

            if (actualLevel < 0 || actualLevel < toolLevel) return 2

            if (isSilkTouchRequired) {
                val hasSilkTouch = heldItem.enchantments.enchantments.any { it == Enchantments.SILK_TOUCH }
                if (!hasSilkTouch) return 1
            }

            return 0
        }

        fun getId(): String? {
            if (toolKind == null) return null
            val material =
                when {
                    toolLevel >= 4 -> "netherite"
                    toolLevel == 3 -> "diamond"
                    toolLevel == 2 -> "iron"
                    toolLevel == 1 -> "stone"
                    else -> "wooden"
                }
            val toolSuffix =
                when (toolKind) {
                    ToolKind.PickAxe -> "pickaxe"
                    ToolKind.Shovel -> "shovel"
                    ToolKind.Axe -> "axe"
                    ToolKind.Hoe -> "hoe"
                    ToolKind.Sword -> "sword"
                }
            return "minecraft:${material}_$toolSuffix"
        }
    }

    fun isSilkTouchRequiredClient(block: Block): Boolean {
        val state = block.defaultState
        val id = Registries.BLOCK.getId(block).path

        if (id.endsWith("_ore") || id == "ancient_debris") return true
        if (block == Blocks.STONE || block == Blocks.DEEPSLATE) return true
        if (block == Blocks.GILDED_BLACKSTONE) return true
        if (id.contains("glass") ||
            id.contains("ice") ||
            block == Blocks.BLUE_ICE ||
            block == Blocks.PACKED_ICE ||
            block == Blocks.FROSTED_ICE
        ) {
            return block != Blocks.FROSTED_ICE
        }
        if (block == Blocks.GLOWSTONE) return true
        if (block == Blocks.COBWEB) return true
        if (block == Blocks.SEA_LANTERN) return true
        if (block == Blocks.GRASS_BLOCK || block == Blocks.MYCELIUM || block == Blocks.PODZOL || block == Blocks.DIRT_PATH) return true
        if (block == Blocks.ENDER_CHEST) return true
        if (block == Blocks.BEEHIVE || block == Blocks.BEE_NEST) return true
        if (state.isIn(BlockTags.LEAVES)) return true
        val amethystId = Registries.BLOCK.getId(block).path
        if (amethystId.startsWith("small_amethyst_bud") ||
            amethystId.startsWith("medium_amethyst_bud") ||
            amethystId.startsWith("large_amethyst_bud") ||
            amethystId == "amethyst_cluster"
        ) {
            return true
        }
        if (state.isIn(BlockTags.CORAL_BLOCKS) || state.isIn(BlockTags.CORAL_PLANTS)) return true
        return false
    }

    fun getCorrectTool(block: Block): CorrectTool {
        val state = block.defaultState
        val toolLevel =
            when {
                state.isIn(BlockTags.NEEDS_STONE_TOOL) -> 1
                state.isIn(BlockTags.NEEDS_IRON_TOOL) -> 2
                state.isIn(BlockTags.NEEDS_DIAMOND_TOOL) -> 3
                else -> 0
            }
        val toolKind =
            when {
                state.isIn(BlockTags.AXE_MINEABLE) -> ToolKind.Axe
                state.isIn(BlockTags.PICKAXE_MINEABLE) -> ToolKind.PickAxe
                state.isIn(BlockTags.SHOVEL_MINEABLE) -> ToolKind.Shovel
                state.isIn(BlockTags.HOE_MINEABLE) -> ToolKind.Hoe
                state.isIn(BlockTags.LEAVES) || Registries.BLOCK
                    .getId(block)
                    .toString() == "minecraft:cobweb" -> ToolKind.Sword

                else -> null
            }
        val isSilkTouchRequired = isSilkTouchRequiredClient(block)
        return if (toolKind == null) {
            CorrectTool(null, -1, false)
        } else {
            CorrectTool(toolKind, toolLevel, isSilkTouchRequired)
        }
    }

    fun getItemStackFromId(id: String): ItemStack =
        try {
            val identifier = Identifier.of(id)
            val item = Registries.ITEM.get(identifier)
            if (item != Items.AIR) ItemStack(item) else ItemStack(Items.BARRIER)
        } catch (_: Exception) {
            ItemStack(Items.BARRIER)
        }
}
