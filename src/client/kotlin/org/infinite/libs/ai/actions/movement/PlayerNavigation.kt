package org.infinite.libs.ai.actions.movement

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i
import org.infinite.libs.client.player.ClientInterface

object PlayerNavigation : ClientInterface() {
    class Node(
        val from: Vec3i,
        val to: Vec3i,
        val kind: NodeKind,
    ) {
        enum class NodeKind {
            Walk,
            Jump,
            Swim,
        }
    }

    fun find(
        currentPos: BlockPos,
        targetPos: Vec3i,
        safeFallHeight: Int,
        jumpHeight: Float,
    ): List<Node> = emptyList()
}
