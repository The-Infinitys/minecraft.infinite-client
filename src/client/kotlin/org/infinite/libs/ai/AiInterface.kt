package org.infinite.libs.ai

import org.infinite.libs.ai.interfaces.AiAction
import org.infinite.libs.client.player.ClientInterface

object AiInterface : ClientInterface() {
    var actions: ArrayDeque<AiAction> = ArrayDeque()

    fun tick() {
        val currentAction = actions.firstOrNull() ?: return
        val state = currentAction.state()
        when (state) {
            AiAction.AiActionState.Progress -> currentAction.tick()
            AiAction.AiActionState.Success -> currentAction.onSuccess()
            AiAction.AiActionState.Failure -> currentAction.onFailure()
        }
        if (state != AiAction.AiActionState.Progress) actions.remove(currentAction)
    }
}
