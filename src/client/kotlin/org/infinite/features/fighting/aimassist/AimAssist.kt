package org.infinite.features.fighting.aimassist

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.player.PlayerEntity
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.libs.client.aim.AimInterface
import org.infinite.libs.client.aim.task.AimTask
import org.infinite.libs.client.aim.task.condition.AimTaskConditionInterface
import org.infinite.libs.client.aim.task.condition.AimTaskConditionReturn
import org.infinite.libs.client.aim.task.config.AimCalculateMethod
import org.infinite.libs.client.aim.task.config.AimPriority
import org.infinite.libs.client.aim.task.config.AimTarget
import org.infinite.settings.FeatureSetting
import kotlin.math.acos

class AimAssist : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Cheat

    enum class Priority {
        Direction, // 角度優先
        Distance, // 距離優先
        Both, // 両方
    }

    private val priority = FeatureSetting.EnumSetting("Priority", Priority.Direction, Priority.entries)
    private val range: FeatureSetting.FloatSetting =
        FeatureSetting.FloatSetting(
            "Range",
            7f,
            3.0f,
            25.0f,
        )
    private val players: FeatureSetting.BooleanSetting =
        FeatureSetting.BooleanSetting(
            "Players",
            true,
        )
    private val mobs: FeatureSetting.BooleanSetting =
        FeatureSetting.BooleanSetting(
            "Mobs",
            true,
        )
    private val fov: FeatureSetting.FloatSetting =
        FeatureSetting.FloatSetting(
            "FOV",
            90.0f,
            10.0f,
            360.0f,
        )
    private val speed: FeatureSetting.FloatSetting =
        FeatureSetting.FloatSetting(
            "Speed",
            1.0f,
            0.5f,
            10f,
        )

    private val method: FeatureSetting.EnumSetting<AimCalculateMethod> =
        FeatureSetting.EnumSetting(
            "Method",
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
            priority,
        )
    var currentTarget: Entity? = null
// AimAssist クラス内に追加

// AimAssist クラス内の checkTarget() 関数を変更

    fun checkTarget(): Entity? {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return null
        val world = client.world ?: return null

        val candidates =
            world.entities
                .filter { it is LivingEntity }
                .filter { it != player && it.isAlive }
                .filter {
                    (players.value && it is PlayerEntity) || (mobs.value && it !is PlayerEntity)
                }.filter { player.distanceTo(it) <= range.value }
                .filter { isWithinFOV(player, it as LivingEntity, fov.value) }

        // Priority 設定に基づいてターゲットを選択
        return when (priority.value) {
            Priority.Direction -> candidates.minByOrNull { calcFov(player, it as LivingEntity) }
            Priority.Distance -> candidates.minByOrNull { player.distanceTo(it) }
            Priority.Both -> candidates.minByOrNull { calculateCombinedScore(player, it as LivingEntity) }
        }
    }
    // AimAssist クラス内に追加

    /**
     * 角度と距離を正規化し、重み付けして総合スコアを計算します (低い方が優先)。
     * 注: この関数は LockOn クラスから流用し、AimAssistの private なヘルパー関数として配置します。
     */
    private fun calculateCombinedScore(
        player: PlayerEntity,
        target: LivingEntity,
    ): Double {
        val distance = player.distanceTo(target).toDouble()
        val angle = calcFov(player, target)

        // 角度を少し優先させる (例: 60% 角度, 40% 距離)
        val angleWeight = 0.6
        val distanceWeight = 0.4

        // 正規化された角度 (0 から 1)
        val maxFovAngle = (fov.value / 2.0).coerceAtLeast(0.001)
        val normalizedAngle = (angle / maxFovAngle).coerceIn(0.0, 1.0)

        // 正規化された距離 (0 から 1)
        val maxRange = range.value.toDouble().coerceAtLeast(0.001)
        val normalizedDistance = (distance / maxRange).coerceIn(0.0, 1.0)

        // 総合スコア (低い方が優先)
        return (angleWeight * normalizedAngle) + (distanceWeight * normalizedDistance)
    }

    fun summonTask() {
        if (AimInterface.taskLength() == 0) {
            currentTarget = checkTarget()
            if (currentTarget != null) {
                AimInterface.addTask(
                    AimTask(
                        AimPriority.Normally,
                        AimTarget.EntityTarget(currentTarget!!),
                        AimAssistTaskCondition(),
                        method.value,
                        speed.value.toDouble(),
                        onSuccess = { summonTask() },
                        onFailure = { summonTask() },
                    ),
                )
            }
        }
    }

    override fun onTick() {
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

    class AimAssistTaskCondition : AimTaskConditionInterface {
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
}
