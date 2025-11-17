package org.infinite.libs.ai.actions.movement

import baritone.api.pathing.goals.Goal
import net.minecraft.entity.Entity
import org.infinite.libs.client.player.ClientInterface
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class EntityGoal(
    val target: Entity,
    val radius: Double? = 2.0,
    val height: Int = 2,
) : ClientInterface(),
    Goal {
    override fun isInGoal(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val targetPos = target.blockPos
        val inX = abs(targetPos.x - x) <= (radius ?: 0.0)
        val inY = abs(targetPos.y - y) <= height
        val inZ = abs(targetPos.z - z) <= (radius ?: 0.0)
        return inX && inY && inZ
    }

    override fun heuristic(
        x: Int,
        y: Int,
        z: Int,
    ): Double {
        val tickProgress = client.renderTickCounter.getTickProgress(false)
        val targetPos = target.getLerpedPos(tickProgress)
        val distX = (targetPos.x - x).pow(2)
        val distY = (targetPos.y - y).pow(2)
        val distZ = (targetPos.z - z).pow(2)
        val euclideanDistance = sqrt(distX + distY + distZ)
        return euclideanDistance
    }
}
