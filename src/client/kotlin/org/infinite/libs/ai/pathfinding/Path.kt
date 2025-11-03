package org.infinite.libs.ai.pathfinding

import net.minecraft.util.math.BlockPos

data class Path(
    val waypoints: List<BlockPos>,
) {
    val isEmpty: Boolean
        get() = waypoints.isEmpty()
    val start: BlockPos?
        get() = waypoints.firstOrNull()
    val end: BlockPos?
        get() = waypoints.lastOrNull()
}
