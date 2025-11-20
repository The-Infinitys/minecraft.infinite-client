package org.infinite.features.fighting.mace

import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround
import net.minecraft.util.hit.HitResult
import org.infinite.feature.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class HyperMace : ConfigurableFeature(initialEnabled = false) {
    fun execute(
        player: PlayerEntity,
        target: Entity,
    ) {
        if (client.crosshairTarget == null || client.crosshairTarget!!.type !== HitResult.Type.ENTITY) {
            return
        }
        if (player.attackCooldownProgressPerTick < 0.9f) {
            return
        }
        if (!player.mainHandStack.isOf(Items.MACE)) {
            return
        }
        if (target.isInvulnerable) {
            return
        }

        // Get damageBoost setting from HyperMace feature
        val damageBoost: Int = damageBoost.value

        // Get fallDistance setting from HyperMace feature
        val fallDistanceToSend = getFallDistanceToSend(player)
        if (player.fallDistance >= damageBoost) {
            sendFakeY(player, fallDistanceToSend)
            sendFakeY(player, 0.0)
        }
    }

    override val level: FeatureLevel = FeatureLevel.Cheat

    val damageBoost: FeatureSetting.IntSetting =
        FeatureSetting.IntSetting(
            "DamageBoost",
            4,
            0,
            10,
        )
    val fallMultiply = FeatureSetting.DoubleSetting("FallMultiply", 1.5, 1.0, 5.0)
    val fallDistance =
        FeatureSetting.DoubleSetting(
            "FallDistance",
            500.0, // 既存のsqrt(500.0)に合わせて初期値を設定
            1.0,
            1000.0,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            damageBoost,
            fallMultiply,
            fallDistance,
        )

    private fun getFallDistanceToSend(player: PlayerEntity): Double {
        val fallDistance = fallDistance.value
        val fallMultiply = fallMultiply.value
        val actualFall = player.fallDistance
        val fallDistanceToSend: Double
        if (actualFall <= fallDistance) {
            fallDistanceToSend = fallDistance
        } else {
            // 上回った分
            val excessFall = actualFall - fallDistance
            // 上回った分に倍率を適用した追加ブースト
            val multipliedBoost = excessFall * fallMultiply
            // 擬似落下距離 = 設定値（保証ブースト） + 倍率による追加ブースト
            fallDistanceToSend = fallDistance + multipliedBoost
        }
        return fallDistanceToSend
    }

    private fun sendFakeY(
        player: PlayerEntity,
        offset: Double,
    ) {
        networkHandler?.sendPacket(
            PositionAndOnGround(
                player.x,
                player.y + offset,
                player.z,
                false,
                player.horizontalCollision,
            ),
        )
    }
}
