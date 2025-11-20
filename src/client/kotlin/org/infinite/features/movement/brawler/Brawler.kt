package org.infinite.features.movement.brawler

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.infinite.feature.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class Brawler : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Extend

    val interactCancelBlocks =
        FeatureSetting.BlockListSetting(
            "InteractCancelBlocks",
            mutableListOf(
                "minecraft:crafting_table",
                "minecraft:furnace",
                "minecraft:chest",
                "minecraft:barrel",
                "minecraft:shulker_box",
                "minecraft:ender_chest",
                "minecraft:lever",
                "minecraft:stone_button",
                "minecraft:wooden_button",
                "minecraft:redstone_block",
                "minecraft:dispenser",
                "minecraft:dropper",
                "minecraft:hopper",
                "minecraft:blast_furnace",
                "minecraft:smoker",
                "minecraft:brewing_stand",
                "minecraft:campfire",
                "minecraft:lectern",
                "minecraft:composter",
                "minecraft:grindstone",
                "minecraft:loom",
                "minecraft:cartography_table",
                "minecraft:fletching_table",
                "minecraft:smithing_table",
                "minecraft:stonecutter",
                "minecraft:bell",
                "minecraft:jukebox",
                "minecraft:note_block",
                "minecraft:daylight_detector",
                "minecraft:comparator",
                "minecraft:repeater",
                "minecraft:target",
                "minecraft:cake",
                "minecraft:flower_pot",
                "minecraft:anvil",
                "minecraft:enchanting_table",
                "minecraft:beacon",
                "minecraft:conduit",
                "minecraft:end_portal_frame",
                "minecraft:respawn_anchor",
            ),
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            interactCancelBlocks,
        )

    var currentHitVecOffset: Direction = Direction.UP
    var lastInteractBlockPos: BlockPos? = null
}
