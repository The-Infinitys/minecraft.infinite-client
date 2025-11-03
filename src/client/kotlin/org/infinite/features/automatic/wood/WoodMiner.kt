package org.infinite.features.automatic.wood

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.registry.Registries
import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class WoodMiner : ConfigurableFeature() {
    val searchRadius =
        FeatureSetting.DoubleSetting(
            name = "SearchRadius",
            descriptionKey = "feature.automatic.woodminer.search_radius.description",
            defaultValue = 10.0,
            min = 1.0,
            max = 64.0,
        )

    val mineSpecificWood =

        FeatureSetting.BooleanSetting(
            name = "MineSpecificWood",
            descriptionKey = "feature.automatic.woodminer.mine_specific_wood.description",
            defaultValue = false,
        )
    val woodTypes =
        FeatureSetting.BlockListSetting(
            name = "WoodTypes",
            descriptionKey = "feature.automatic.woodminer.wood_types.description",
            defaultValue = mutableListOf("minecraft:oak_log", "minecraft:spruce_log"),
        )
    override val settings: List<FeatureSetting<*>> =
        listOf(
            searchRadius,
            mineSpecificWood,
            woodTypes,
        )

    private fun isWoodBlock(
        block: Block,
        woodTypes: List<String>,
    ): Boolean {
        val blockId = Registries.BLOCK.getId(block).toString() // Get block ID
        return woodTypes.contains(blockId) ||
            listOf(
                Blocks.OAK_LOG,
                Blocks.SPRUCE_LOG,
                Blocks.BIRCH_LOG,
                Blocks.JUNGLE_LOG,
                Blocks.ACACIA_LOG,
                Blocks.DARK_OAK_LOG,
                Blocks.PALE_OAK_LOG,
                Blocks.MANGROVE_LOG,
                Blocks.CHERRY_LOG,
                Blocks.CRIMSON_STEM,
                Blocks.WARPED_STEM,
            ).contains(block)
    }
}
