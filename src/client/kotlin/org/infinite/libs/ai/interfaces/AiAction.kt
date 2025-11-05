package org.infinite.libs.ai.interfaces

import org.infinite.libs.client.player.ClientInterface

open class AiAction : ClientInterface() {
    enum class AiActionState {
        Success,
        Progress,
        Failure,
    }

    open fun state(): AiActionState = AiActionState.Success

    open fun tick() {}

    open fun onSuccess() {}

    open fun onFailure() {}
}
