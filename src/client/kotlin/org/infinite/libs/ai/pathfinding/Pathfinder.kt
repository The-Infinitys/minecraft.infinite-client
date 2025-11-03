package org.infinite.libs.ai.pathfinding

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.World
import java.util.Collections
import java.util.HashSet
import java.util.PriorityQueue

class Pathfinder(
    private val world: World,
    private val startPos: BlockPos,
    private val endPos: BlockPos,
) {
    fun findPath(): Path {
        val openSet = PriorityQueue<PathNode>(compareBy { it.fCost })
        val closedSet = HashSet<BlockPos>() // Store BlockPos directly for O(1) lookup
        val openSetNodes = HashMap<BlockPos, PathNode>() // Store nodes for quick access

        val startNode = PathNode(startPos, 0.0, calculateHeuristic(startPos, endPos), null)
        openSet.add(startNode)
        openSetNodes[startPos] = startNode

        val cameFrom = mutableMapOf<BlockPos, PathNode>()
        val gScore = mutableMapOf<BlockPos, Double>()
        gScore[startPos] = 0.0

        while (openSet.isNotEmpty()) {
            val currentNode = openSet.poll()
            openSetNodes.remove(currentNode.position)

            if (currentNode.position == endPos) {
                return reconstructPath(cameFrom, currentNode)
            }

            closedSet.add(currentNode.position)

            for (direction in Direction.values()) { // Check 6 direct neighbors
                val neighborPos = currentNode.position.offset(direction)

                if (closedSet.contains(neighborPos)) { // O(1) lookup
                    continue
                }

                if (!isWalkable(neighborPos)) {
                    continue
                }

                val tentativeGCost =
                    gScore.getOrDefault(currentNode.position, Double.MAX_VALUE) + getMovementCost(currentNode.position, neighborPos)

                if (tentativeGCost < gScore.getOrDefault(neighborPos, Double.MAX_VALUE)) {
                    cameFrom[neighborPos] = currentNode
                    gScore[neighborPos] = tentativeGCost
                    val neighborNode = PathNode(neighborPos, tentativeGCost, calculateHeuristic(neighborPos, endPos), currentNode)

                    if (!openSetNodes.containsKey(neighborPos)) { // O(1) lookup
                        openSet.add(neighborNode)
                        openSetNodes[neighborPos] = neighborNode
                    } else if (tentativeGCost < openSetNodes[neighborPos]!!.gCost) { // Update if a better path is found
                        openSet.remove(openSetNodes[neighborPos]) // Remove old node
                        openSetNodes[neighborPos] = neighborNode // Add new node
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
        Collections.reverse(path)
        return Path(path)
    }

    private fun calculateHeuristic(
        pos1: BlockPos,
        pos2: BlockPos,
    ): Double {
        // Manhattan distance heuristic
        return (Math.abs(pos1.x - pos2.x) + Math.abs(pos1.y - pos2.y) + Math.abs(pos1.z - pos2.z)).toDouble()
    }

    private fun getMovementCost(
        from: BlockPos,
        to: BlockPos,
    ): Double {
        // Simple cost for now, can be expanded to consider different block types
        return from.getManhattanDistance(to).toDouble()
    }

    private fun isWalkable(pos: BlockPos): Boolean {
        // Check if the block at 'pos' is traversable (not a full solid block)
        val blockState = world.getBlockState(pos)
        if (!blockState.getCollisionShape(world, pos).isEmpty) { // If it has a collision shape, it's not traversable
            return false
        }

        // Check if the block above 'pos' is traversable (for player's head)
        val blockAboveState = world.getBlockState(pos.up())
        if (!blockAboveState.getCollisionShape(world, pos.up()).isEmpty) {
            return false
        }

        // Check if the block below 'pos' is solid enough to stand on
        val blockBelowState = world.getBlockState(pos.down())
        // Corrected logic: player needs a non-empty collision shape below to stand on
        if (blockBelowState.getCollisionShape(world, pos.down()).isEmpty) { // If the block below has no collision, player would fall
            return false
        }

        return true
    }
}
