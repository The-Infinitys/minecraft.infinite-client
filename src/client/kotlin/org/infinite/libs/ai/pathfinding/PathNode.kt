package org.infinite.libs.ai.pathfinding

import net.minecraft.util.math.BlockPos

data class PathNode(
    val position: BlockPos,
    var gCost: Double, // Cost from start to current node
    var hCost: Double, // Heuristic cost from current node to end
    var parent: PathNode?,
) {
    val fCost: Double
        get() = gCost + hCost

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PathNode

        return position == other.position
    }

    override fun hashCode(): Int = position.hashCode()
}
