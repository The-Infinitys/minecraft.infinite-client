package org.infinite.libs.ai.interfaces

interface AiAction {
    enum class AiActionState {
        Success,
        Progress,
        Failure,
    }

    fun state(): AiActionState

    fun tick()

    fun onSuccess()

    fun onFailure()
}
