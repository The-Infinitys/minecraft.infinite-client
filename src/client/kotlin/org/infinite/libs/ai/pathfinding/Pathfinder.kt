package org.infinite.libs.ai.pathfinding

import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import java.util.PriorityQueue
import kotlin.math.abs

class Pathfinder(
    private val world: World,
    private val startPos: BlockPos,
    private val endPos: BlockPos,
) {
    fun findPath(): Path {
        val openSet = PriorityQueue<PathNode>(compareBy { it.fCost })
        val closedSet = HashSet<PathNode>()
        val startNode = PathNode(startPos, 0.0, calculateHeuristic(startPos, endPos), null)
        openSet.add(startNode)

        val cameFrom = mutableMapOf<BlockPos, PathNode>()
        val gScore = mutableMapOf<BlockPos, Double>()
        gScore[startPos] = 0.0

        while (openSet.isNotEmpty()) {
            val currentNode = openSet.poll()

            if (currentNode.position == endPos) {
                return reconstructPath(cameFrom, currentNode)
            }

            closedSet.add(currentNode)

            for (neighborPos in getNeighbors(currentNode.position)) {
                if (closedSet.any { it.position == neighborPos }) {
                    continue
                }

                val tentativeGCost =
                    gScore.getOrDefault(currentNode.position, Double.MAX_VALUE) + getMovementCost(currentNode.position, neighborPos)

                if (tentativeGCost < gScore.getOrDefault(neighborPos, Double.MAX_VALUE)) {
                    cameFrom[neighborPos] = currentNode
                    gScore[neighborPos] = tentativeGCost
                    val neighborNode = PathNode(neighborPos, tentativeGCost, calculateHeuristic(neighborPos, endPos), currentNode)

                    if (!openSet.any { it.position == neighborPos }) {
                        openSet.add(neighborNode)
                    }
                }
            }
        }

        return Path(emptyList()) // No path found
    }

    private fun reconstructPath(
        cameFrom: Map<BlockPos, PathNode>,
        currentNode: PathNode,
    ): Path {
        val path = mutableListOf<BlockPos>()
        var current: PathNode? = currentNode
        while (current != null) {
            path.add(current.position)
            current = cameFrom[current.position]
        }
        path.reverse()
        return Path(path)
    }

    private fun calculateHeuristic(
        pos1: BlockPos,
        pos2: BlockPos,
    ): Double {
        // Manhattan distance heuristic
        return (abs(pos1.x - pos2.x) + abs(pos1.y - pos2.y) + abs(pos1.z - pos2.z)).toDouble()
    }

    private fun getMovementCost(
        from: BlockPos,
        to: BlockPos,
    ): Double {
        // Simple cost for now, can be expanded to consider different block types
        return from.getManhattanDistance(to).toDouble()
    }

    private fun getNeighbors(pos: BlockPos): List<BlockPos> {
        val neighbors = mutableListOf<BlockPos>()
        // Check 26 surrounding blocks (3x3x3 cube excluding center)
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    if (dx == 0 && dy == 0 && dz == 0) continue // Skip current block

                    val neighborPos = pos.add(dx, dy, dz)
                    if (isWalkable(neighborPos)) {
                        neighbors.add(neighborPos)
                    }
                }
            }
        }
        return neighbors
    }

    private fun isWalkable(pos: BlockPos): Boolean {
        // Check if the block itself is not solid
        val blockState = world.getBlockState(pos)
        if (blockState.isSolidBlock(world, pos)) {
            return false
        }

        // Check if the block above is not solid (for player height)
        val blockAboveState = world.getBlockState(pos.up())
        if (blockAboveState.isSolidBlock(world, pos.up())) {
            return false
        }

        // Check if the block below is solid (to stand on)
        val blockBelowState = world.getBlockState(pos.down())
        return blockBelowState.isSolidBlock(world, pos.down())
        // If the block below is not solid, check if it's a climbable block (e.g., ladder, vine)
        // For simplicity, let's assume we can only walk on solid blocks for now.
    }
}
