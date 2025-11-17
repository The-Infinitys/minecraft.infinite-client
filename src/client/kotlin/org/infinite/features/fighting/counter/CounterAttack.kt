package org.infinite.features.fighting.counter

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket
import org.infinite.ConfigurableFeature
import org.infinite.libs.client.aim.AimInterface
import org.infinite.libs.client.aim.task.AimTask
import org.infinite.libs.client.aim.task.condition.AimTaskConditionByFrame
import org.infinite.libs.client.aim.task.config.AimCalculateMethod
import org.infinite.libs.client.aim.task.config.AimPriority
import org.infinite.libs.client.aim.task.config.AimTarget
import org.infinite.settings.FeatureSetting

class CounterAttack : ConfigurableFeature(initialEnabled = false) {
    private val reactionTickSetting = FeatureSetting.IntSetting("ReactionTick", 4, 0, 10)
    private val processTickSetting = FeatureSetting.IntSetting("ProcessTick", 4, 0, 10)
    private val randomizerSetting = FeatureSetting.IntSetting("Randomizer", 2, 0, 10)
    private val methodSetting =
        FeatureSetting.EnumSetting("Method", AimCalculateMethod.Linear, AimCalculateMethod.entries)
    private val aimSpeed =
        FeatureSetting.DoubleSetting("AimSpeed", 5.0, 1.0, 10.0)
    override val settings: List<FeatureSetting<*>> =
        listOf(
            reactionTickSetting,
            processTickSetting,
            randomizerSetting,
            methodSetting,
            aimSpeed,
        )

    /**
     * 1. ダメージパケット受信時の処理 (Mixinで呼び出される想定)
     * サーバーからの正確な攻撃者情報に基づいて反撃対象をセットします。
     */
    fun receive(packet: EntityDamageS2CPacket) {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val world = client.world ?: return
        // パケットの対象エンティティがプレイヤー自身か確認
        if (packet.entityId() != player.id) {
            return
        }
        // 攻撃者（間接的な原因）のIDを取得
        // DamageSource#getAttacker() に相当する sourceCauseId を使用
        val attackerId = packet.sourceCauseId()
        // IDからエンティティを取得
        val attackerEntity = world.getEntityById(attackerId)
        if (attackerEntity is LivingEntity && attackerEntity != player) {
            executeCounterAttack(attackerEntity)
        }
    }

    val rand: Int
        get() = (0..randomizerSetting.value).random()

    private fun executeCounterAttack(target: LivingEntity) {
        val react = reactionTickSetting.value + rand
        val progress = processTickSetting.value + rand
        val total = react + progress
        AimInterface.addTask(
            AimTask(
                AimPriority.Preferentially,
                AimTarget.EntityTarget(target),
                condition = AimTaskConditionByFrame(react, total, true),
                calcMethod = methodSetting.value,
                multiply = aimSpeed.value,
                onSuccess = {
                    val player = player ?: return@AimTask
                    val interactionManager = interactionManager ?: return@AimTask
                    interactionManager.attackEntity(player, target)
                },
            ),
        )
    }
}
