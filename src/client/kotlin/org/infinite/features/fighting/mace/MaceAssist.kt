package org.infinite.features.fighting.mace

import net.minecraft.enchantment.Enchantments
import net.minecraft.entity.LivingEntity
import net.minecraft.item.Items
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import org.infinite.ConfigurableFeature
import org.infinite.libs.client.inventory.InventoryManager
import org.infinite.settings.FeatureSetting
import org.infinite.utils.item.enchantLevel

class MaceAssist : ConfigurableFeature() {
    private val reactTicks = FeatureSetting.IntSetting("ReactTicks", 4, 0, 10)
    override val settings =
        listOf(
            reactTicks,
        )
    private var fallDistance: Double = 0.0

    override fun tick() {
        val player = player ?: return
        val world = world ?: return
        fallDistance()
        if (player.fallDistance > 0 && player.mainHandStack.item == Items.MACE) {
            val (predictedPos, hasCollision) = predictFallLocation(reactTicks.value)
            if (hasCollision) {
                val boxSize = 1.0
                val halfBoxSize = boxSize / 2.0
                val searchBox =
                    Box(-halfBoxSize, -halfBoxSize, -halfBoxSize, halfBoxSize, halfBoxSize, halfBoxSize).offset(
                        predictedPos,
                    )
                val targetEntities =
                    world.getOtherEntities(player, searchBox).filter { it is LivingEntity && it.isAlive }
            }
        }
    }

    private fun fallDistance() {
        val velocity = velocity ?: return
        val y = velocity.y
        if (y > 0) {
            fallDistance = 0.0
        } else {
            fallDistance -= y
        }
    }

    private fun predictFallLocation(ticks: Int): Pair<Vec3d, Boolean> {
        val player = player!!
        val world = world!!
        var motion = player.velocity
        var pos = playerPos!!
        val gravity = 0.08 // Approximate value for player gravity
        val friction = 0.98

        for (i in 0 until ticks) {
            val nextPos = pos.add(motion)

            // Create a bounding box at the predicted position
            val playerBox = player.boundingBox
            val predictedBox = playerBox.offset(nextPos)
            if (world.getBlockCollisions(null, predictedBox).toList().isNotEmpty()) {
                return pos to true
            }
            pos = nextPos
            motion = motion.multiply(friction, 1.0, friction)
            motion = motion.add(0.0, -gravity, 0.0)
        }

        // No collision within the given ticks
        return pos to false
    }

    /**
     * メイスで与えられるダメージを計算する
     * @param fallDistance 落下距離
     * @return ダメージ量
     */
    fun maceDamage(): Double? {
        val stack = InventoryManager.get(InventoryManager.InventoryIndex.MainHand())
        if (stack.item != Items.MACE) return null
        val densityLevel = enchantLevel(stack, Enchantments.DENSITY)
        val normalDamage = 6.0
        val criticalBaseDamage = 9.0
        val criticalPoint = 1.0
        val smashPoint = 1.5

        fun smashDamage(distance: Double): Double {
            val damageCalcInfo =
                listOf(
                    4 + densityLevel / 2.0 to 1.5..3.0,
                    2 + densityLevel / 2.0 to 3.0..8.0,
                    2 + densityLevel / 2.0 to 8.0..Double.POSITIVE_INFINITY,
                )
            var damage = 0.0
            for ((multiply, range) in damageCalcInfo) {
                if (distance in range) {
                    damage += multiply * (distance - range.start)
                    break
                } else if (distance > range.endInclusive) {
                    damage += multiply * (range.endInclusive - range.start)
                }
            }
            return damage
        }
        return when {
            fallDistance < criticalPoint -> normalDamage
            fallDistance > criticalPoint && fallDistance < smashPoint -> criticalBaseDamage
            fallDistance > smashPoint -> criticalBaseDamage + smashDamage(fallDistance)
            else -> null
        }
    }
}
