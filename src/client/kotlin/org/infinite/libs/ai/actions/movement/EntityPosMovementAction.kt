package org.infinite.libs.ai.actions.movement

import baritone.api.BaritoneAPI
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import org.infinite.InfiniteClient
import org.infinite.libs.ai.interfaces.AiAction

class EntityPosMovementAction(
    val target: Entity,
    val radius: Double? = 2.0,
    val height: Int = 2,
    val stateRegister: () -> AiActionState? = { null },
    val onFailureAction: () -> Unit = {},
    val onSuccessAction: () -> Unit = {},
) : AiAction() {
    private val api
        get() = BaritoneAPI.getProvider()
    private val baritone
        get() = api.getBaritoneForMinecraft(client)

    var registered = false

    override fun tick() {
        if (!baritoneCheck()) return
        if (!registered) {
            goal =
                EntityGoal(target, radius, height)
            baritone.customGoalProcess.setGoalAndPath(
                goal as EntityGoal,
            )
            registered = true
        }
    }

    val pos: BlockPos
        get() = player!!.blockPos

    fun baritoneCheck(): Boolean =
        try {
            Class.forName("baritone.api.BaritoneAPI")
            true
        } catch (_: ClassNotFoundException) {
            false
        }

    var goal: Any? = null

    override fun state(): AiActionState =
        if (!baritoneCheck()) {
            InfiniteClient.error("You have to import Baritone for this Feature!")
            AiActionState.Failure
        } else {
            stateRegister() ?: when {
                !registered || baritone.pathingBehavior.goal == goal -> AiActionState.Progress

                // 既に何かしらのアクションが行われている
                registered && (
                    if (radius == null) {
                        baritone.pathingBehavior.goal != goal
                    } else {
                        (goal as EntityGoal).isInGoal(
                            pos.x,
                            pos.y,
                            pos.z,
                        )
                    }
                ) -> AiActionState.Success

                else -> AiActionState.Failure
            }
        }

    private fun cancelTask() {
        if (baritoneCheck()) {
            baritone.pathingBehavior.cancelEverything()
        }
    }

    override fun onFailure() {
        cancelTask()
        onFailureAction()
    }

    override fun onSuccess() {
        cancelTask()
        onSuccessAction()
    }
}
