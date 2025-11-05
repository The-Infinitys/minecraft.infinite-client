package org.infinite.libs.client.aim.task.condition

class AimTaskConditionByFrame(
    val reactFrame: Int = 6,
    val totalFrame: Int = 9,
    val force: Boolean,
) : AimTaskConditionInterface {
    private var frame = -1

    override fun check(): AimTaskConditionReturn {
        frame++
        return if (frame < reactFrame) {
            AimTaskConditionReturn.Suspend
        } else if (frame >= totalFrame) {
            if (force) {
                AimTaskConditionReturn.Force
            } else {
                AimTaskConditionReturn.Failure
            }
        } else {
            AimTaskConditionReturn.Exec
        }
    }
}
