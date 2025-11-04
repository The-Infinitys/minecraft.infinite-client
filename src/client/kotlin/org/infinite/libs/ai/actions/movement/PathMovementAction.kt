package org.infinite.libs.ai.actions.movement

import baritone.api.BaritoneAPI
import org.infinite.libs.ai.interfaces.AiAction

class PathMovementAction(
    val x: Int,
    val z: Int,
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
        if (!registered) {
            baritone.exploreProcess.explore(x, z)
            registered = true
        }
    }

    override fun state(): AiActionState =
        stateRegister() ?: when {
            !registered || baritone.exploreProcess.isActive -> AiActionState.Progress // 既に何かしらのアクションが行われている
            registered && !baritone.exploreProcess.isActive -> AiActionState.Success
            else -> AiActionState.Failure
        }

    private fun cancelTask() {
        baritone.pathingBehavior.cancelEverything()
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
