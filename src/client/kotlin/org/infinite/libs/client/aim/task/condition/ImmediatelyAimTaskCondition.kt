package org.infinite.libs.client.aim.task.condition

class ImmediatelyAimTaskCondition : AimTaskConditionInterface {
    override fun check(): AimTaskConditionReturn = AimTaskConditionReturn.Force
}
