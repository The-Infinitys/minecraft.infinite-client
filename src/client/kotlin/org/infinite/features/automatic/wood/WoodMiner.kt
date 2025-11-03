package org.infinite.features.automatic.wood

import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import org.infinite.ConfigurableFeature
import org.infinite.libs.ai.AiInterface
import org.infinite.libs.ai.actions.block.MineBlockAction
import org.infinite.libs.ai.actions.movement.AbsoluteMoveToAction
import org.infinite.libs.ai.pathfinding.Pathfinder
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

    override fun tick() {
        if (AiInterface.actions.isNotEmpty()) {
            return // An action is already in progress
        }
        val searchRadius = searchRadius.value

        val mineSpecificWood = mineSpecificWood.value
        val woodTypes = woodTypes.value
        val playerBlockPos = player?.blockPos ?: return
        val worldInstance = world ?: return

        var targetTreeBlock: BlockPos? = null
        var minDistance = Double.MAX_VALUE

        // Search for a tree
        for (x in -searchRadius.toInt()..searchRadius.toInt()) {
            for (y in -searchRadius.toInt()..searchRadius.toInt()) {
                for (z in -searchRadius.toInt()..searchRadius.toInt()) {
                    val currentPos = playerBlockPos.add(x, y, z)
                    val blockState = worldInstance.getBlockState(currentPos)
                    val block = blockState.block

                    if (isWoodBlock(block, mineSpecificWood, woodTypes)) {
                        val distance = playerBlockPos.getManhattanDistance(currentPos).toDouble()
                        if (distance < minDistance) {
                            minDistance = distance
                            targetTreeBlock = currentPos
                        }
                    }
                }
            }
        }

        if (targetTreeBlock != null) {
            // Found a tree, now pathfind to it and mine it
            val pathfinder = Pathfinder(worldInstance, playerBlockPos, targetTreeBlock)
            val path = pathfinder.findPath()

            if (!path.isEmpty) {
                // Add actions in reverse order so the first action is at the front of the deque
                for (i in path.waypoints.indices.reversed()) {
                    val waypoint = path.waypoints[i]
                    if (i == path.waypoints.lastIndex) {
                        // This is the tree block itself, mine it
                        AiInterface.actions.addFirst(MineBlockAction(waypoint))
                    } else {
                        // Move to the waypoint
                        AiInterface.actions.addFirst(AbsoluteMoveToAction(waypoint.x, waypoint.z, waypoint.y))
                    }
                }
            }
        }
    }

    private fun isWoodBlock(
        block: Block,
        mineSpecificWood: Boolean,
        woodTypes: List<String>,
    ): Boolean {
        val blockId = Registries.BLOCK.getId(block).toString() // Get block ID
        return if (mineSpecificWood) {
            woodTypes.contains(blockId)
        } else {
            block == Blocks.OAK_LOG || block == Blocks.SPRUCE_LOG || block == Blocks.BIRCH_LOG ||
                block == Blocks.JUNGLE_LOG || block == Blocks.ACACIA_LOG || block == Blocks.DARK_OAK_LOG ||
                block == Blocks.MANGROVE_LOG || block == Blocks.CHERRY_LOG || block == Blocks.CRIMSON_STEM ||
                block == Blocks.WARPED_STEM
        }
    }
}
