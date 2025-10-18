package org.infinite.features.fighting.aimassist

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.InfiniteClient
import org.infinite.libs.client.player.fighting.AimInterface
import org.infinite.libs.client.player.fighting.aim.AimCalculateMethod
import org.infinite.libs.client.player.fighting.aim.AimPriority
import org.infinite.libs.client.player.fighting.aim.AimTarget
import org.infinite.libs.client.player.fighting.aim.AimTask
import org.infinite.libs.client.player.fighting.aim.AimTaskCondition
import org.infinite.libs.client.player.fighting.aim.AimTaskConditionReturn
import org.infinite.settings.FeatureSetting
import kotlin.math.acos

class AimAssist : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT

    private val range: FeatureSetting.FloatSetting =
        FeatureSetting.FloatSetting(
            "Range",
            "Aim assist range.",
            7f,
            3.0f,
            25.0f,
        )
    private val players: FeatureSetting.BooleanSetting =
        FeatureSetting.BooleanSetting(
            "Players",
            "Target players.",
            true,
        )
    private val mobs: FeatureSetting.BooleanSetting =
        FeatureSetting.BooleanSetting(
            "Mobs",
            "Target mobs.",
            true,
        )
    private val fov: FeatureSetting.FloatSetting =
        FeatureSetting.FloatSetting(
            "FOV",
            "Field of View to limit targeting (degrees).",
            90.0f,
            10.0f,
            180.0f,
        )
    private val speed: FeatureSetting.FloatSetting =
        FeatureSetting.FloatSetting(
            "Speed",
            "The Rotation Speed",
            1.0f,
            0.5f,
            10f,
        )

    private val method: FeatureSetting.EnumSetting<AimCalculateMethod> =
        FeatureSetting.EnumSetting(
            "Method",
            "Rotation Method",
            AimCalculateMethod.Linear,
            AimCalculateMethod.entries,
        )
    override val settings: List<FeatureSetting<*>> =
        listOf(
            range,
            players,
            mobs,
            fov,
            speed,
            method,
        )
    var currentTarget: Entity? = null

    fun checkTarget(): Entity? {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return null
        val world = client.world ?: return null
        return world.entities
            .filter { it is LivingEntity }
            .filter { it != player && it.isAlive }
            .filter {
                (players.value && it is PlayerEntity) || (mobs.value && it !is PlayerEntity)
            }.filter { player.distanceTo(it) <= range.value }
            .filter { isWithinFOV(player, it as LivingEntity, fov.value) }
            .minByOrNull { calcFov(player, it as LivingEntity) }
    }

    fun summonTask() {
        if (AimInterface.taskLength() == 0) {
            currentTarget = checkTarget()
            if (currentTarget != null) {
                AimInterface.addTask(
                    AimAssistAimTask(
                        AimPriority.Normally,
                        AimTarget.EntityTarget(currentTarget!!),
                        AimAssistTaskCondition(),
                        method.value,
                        speed.value.toDouble(),
                    ),
                )
            }
        }
    }

    override fun tick() {
        summonTask()
    }

    fun calcFov(
        player: PlayerEntity,
        target: LivingEntity,
    ): Double {
        val playerLookVec = player.rotationVector.normalize()
        val targetCenterVec = target.boundingBox.center
        val targetVec = targetCenterVec.subtract(player.eyePos)
        val targetLookVec = targetVec.normalize()
        val dotProduct = playerLookVec.dotProduct(targetLookVec)
        val angleRadians = acos(dotProduct.coerceIn(-1.0, 1.0))
        return Math.toDegrees(angleRadians)
    }

    private fun isWithinFOV(
        player: PlayerEntity,
        target: LivingEntity,
        fovDegrees: Float,
    ): Boolean {
        val angleDegrees = calcFov(player, target)
        // 7. ターゲットがFOVの半分以内かどうかをチェック
        return angleDegrees <= fovDegrees / 2.0f
    }
}

class AimAssistTaskCondition : AimTaskCondition {
    override fun check(): AimTaskConditionReturn {
        val aimAssist = InfiniteClient.getFeature(AimAssist::class.java) ?: return AimTaskConditionReturn.Failure
        if (aimAssist.isDisabled()) {
            return AimTaskConditionReturn.Success
        }
        if (aimAssist.checkTarget() != aimAssist.currentTarget) {
            return AimTaskConditionReturn.Failure
        }
        return AimTaskConditionReturn.Exec
    }
}

class AimAssistAimTask(
    override val priority: AimPriority,
    override val target: AimTarget,
    override val condition: AimAssistTaskCondition,
    override val calcMethod: AimCalculateMethod,
    override val multiply: Double,
) : AimTask(priority, target, condition, calcMethod, multiply) {
    override fun atSuccess() {
        InfiniteClient.getFeature(AimAssist::class.java)?.summonTask()
    }

    override fun atFailure() {
        InfiniteClient.getFeature(AimAssist::class.java)?.summonTask()
    }
}
