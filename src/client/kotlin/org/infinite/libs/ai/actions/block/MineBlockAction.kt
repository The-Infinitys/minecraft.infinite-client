package org.infinite.libs.ai.actions.block

import net.minecraft.client.option.KeyBinding
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import org.infinite.libs.ai.AiInterface
import org.infinite.libs.ai.actions.movement.AbsoluteMoveToAction
import org.infinite.libs.ai.interfaces.AiAction
import org.infinite.libs.client.aim.task.config.AimTarget
import org.infinite.libs.client.control.ControllerInterface

class MineBlockAction(
    val targetBlockPos: BlockPos,
) : AiAction() {
    private var currentMiningFace: Direction? = null // Store the face being mined
    private val playerReach: Double
        get() = player?.blockInteractionRange ?: 4.5
    private val attackKey: KeyBinding = client.options.attackKey

    override fun state(): AiActionState {
        if (world?.getBlockState(targetBlockPos)?.isAir == true) {
            return AiActionState.Success
        }

        // Check if player is within mining reach
        if ((playerPos?.distanceTo(targetBlockPos.toCenterPos()) ?: Double.MAX_VALUE) > playerReach) {
            return AiActionState.Progress // Need to move closer
        }

        return AiActionState.Progress // Ready to mine or mining in progress
    }

    override fun tick() {
        if ((playerPos?.distanceTo(targetBlockPos.toCenterPos()) ?: Double.MAX_VALUE) > playerReach) {
            // Move closer to the block
            AiInterface.actions.addFirst(AbsoluteMoveToAction(targetBlockPos.x, targetBlockPos.z, targetBlockPos.y))
            return
        }

        // Player is close enough, aim and mine
        if (currentMiningFace == null) {
            currentMiningFace = findAccessibleFace()
            if (currentMiningFace == null) {
                // No accessible face found, consider this a failure
                println("MineBlockAction: No accessible face found for block at $targetBlockPos")
                // We need to signal failure somehow, perhaps by setting a flag and letting state() handle it.
                // For now, let's just stop trying to mine.
                ControllerInterface.release(attackKey)
                return
            }
        }

        // Aim at the chosen face
        val aimTargetFace =
            when (currentMiningFace) {
                Direction.UP -> AimTarget.BlockFace.Top
                Direction.DOWN -> AimTarget.BlockFace.Bottom
                Direction.NORTH -> AimTarget.BlockFace.North
                Direction.EAST -> AimTarget.BlockFace.East
                Direction.SOUTH -> AimTarget.BlockFace.South
                Direction.WEST -> AimTarget.BlockFace.West
                else -> AimTarget.BlockFace.Center // Should not happen if currentMiningFace is not null
            }
        AimTarget.BlockTarget(targetBlockPos, aimTargetFace).lookAt()

        // Start/continue mining
        ControllerInterface.press(attackKey) { world?.getBlockState(targetBlockPos)?.isAir != true }
    }

    private fun findAccessibleFace(): Direction? {
        targetBlockPos.toCenterPos()

        // Iterate through all directions to find an accessible face
        for (direction in Direction.entries) {
            val neighborPos = targetBlockPos.offset(direction)
            if (world?.getBlockState(neighborPos)?.isAir == true) {
                // Simple check: if the adjacent block is air, this face is considered accessible
                return direction
            }
        }
        return null // No accessible face found
    }

    override fun onSuccess() {
        println("Successfully mined block at $targetBlockPos")
        ControllerInterface.release(attackKey) // Ensure attack key is released
    }

    override fun onFailure() {
        println("Failed to mine block at $targetBlockPos")
        ControllerInterface.release(attackKey) // Ensure attack key is released
    }
}
