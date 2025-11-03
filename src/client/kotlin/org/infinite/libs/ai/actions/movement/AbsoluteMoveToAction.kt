package org.infinite.libs.ai.actions.movement

import net.minecraft.util.math.Vec3d
import org.infinite.libs.ai.interfaces.AiAction
import org.infinite.libs.client.aim.task.config.AimTarget
import org.infinite.libs.client.control.ControllerInterface

class AbsoluteMoveToAction(
    val x: Int,
    val z: Int,
    val y: Int? = null,
    val tolerance: Double = 0.5,
) : AiAction() {
    private val targetPos: Vec3d = Vec3d(x + 0.5, y?.toDouble() ?: player?.y ?: 0.0, z + 0.5) // Center of the block

    override fun state(): AiActionState =
        if ((playerPos?.distanceTo(targetPos) ?: Double.MAX_VALUE) < tolerance) {
            AiActionState.Success
        } else {
            AiActionState.Progress
        }

    override fun tick() {
        val currentPos = playerPos ?: return

        // Look at the target waypoint
        AimTarget.WaypointTarget(targetPos).lookAt()

        // Simple movement: move directly towards the target
        if (currentPos.distanceTo(targetPos) > tolerance) {
            ControllerInterface.press(client.options.forwardKey) { (playerPos?.distanceTo(targetPos) ?: Double.MAX_VALUE) > tolerance }
        } else {
            ControllerInterface.release(client.options.forwardKey)
        }
    }

    override fun onSuccess() {
        println("Successfully moved to $x, $z")
        ControllerInterface.release(client.options.forwardKey)
        ControllerInterface.release(client.options.backKey)
        ControllerInterface.release(client.options.leftKey)
        ControllerInterface.release(client.options.rightKey)
    }

    override fun onFailure() {
        println("Failed to move to $x, $z")
        ControllerInterface.release(client.options.forwardKey)
        ControllerInterface.release(client.options.backKey)
        ControllerInterface.release(client.options.leftKey)
        ControllerInterface.release(client.options.rightKey)
    }
}
