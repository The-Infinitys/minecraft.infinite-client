package org.infinite.libs.client.aim.task.config

import net.minecraft.block.entity.BlockEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.command.argument.EntityAnchorArgumentType
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.infinite.libs.client.aim.camera.CameraRoll

/**
 * ブロックの狙う面を定義する
 */

sealed class AimTarget {
    enum class BlockFace {
        Top,
        Bottom,
        North,
        East,
        South,
        West,
        Center,
    }

    open class EntityTarget(
        e: Entity,
    ) : AimTarget() {
        open val entity = e
    }

    // ブロックを狙う面（face）を追加し、デフォルトをCENTERに設定
    open class BlockTarget(
        val blockPos: BlockPos,
        val face: BlockFace = BlockFace.Center, // デフォルトを中央 (CENTER) に設定
    ) : AimTarget() {
        constructor(b: BlockEntity, face: BlockFace = BlockFace.Center) : this(b.pos, face)

        fun pos(offset: Double = 0.5): Vec3d {
            val center = blockPos.toCenterPos()
            return when (this.face) {
                BlockFace.Center -> center

                BlockFace.Top -> center.add(0.0, 0.5 * (2 * offset - 1), 0.0)

                // Y+
                BlockFace.Bottom -> center.add(0.0, -0.5 * (2 * offset - 1), 0.0)

                // Y-
                BlockFace.North -> center.add(0.0, 0.0, -0.5 * (2 * offset - 1))

                // Z-
                BlockFace.East -> center.add(0.5 * (2 * offset - 1), 0.0, 0.0)

                // X+
                BlockFace.South -> center.add(0.0, 0.0, 0.5 * (2 * offset - 1))

                // Z+
                BlockFace.West -> center.add(-0.5 * (2 * offset - 1), 0.0, 0.0) // X-
            }
        }
    }

    open class WaypointTarget(
        p: Vec3d,
    ) : AimTarget() {
        open val pos = p
    }

    open class RollTarget(
        r: CameraRoll,
    ) : AimTarget() {
        open val roll = r
    }

    fun lookAt() {
        val pos = pos() ?: return
        MinecraftClient.getInstance().player?.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, pos)
    }

    /**
     * AimTargetのワールド内位置を計算して返します。
     * RollTargetなど、位置を持たない場合は null を返します。
     */
    fun pos(): Vec3d? =
        when (this) { // when式の対象を 'this' に変更し、スマートキャストを有効化
            is EntityTarget -> {
                // MinecraftClient.getInstance().renderTickCounter.getTickProgress(false) を使用
                // Entityの位置に目線の高さを加算
                this.entity
                    .getLerpedPos(MinecraftClient.getInstance().renderTickCounter.getTickProgress(false))
                    .add(0.0, this.entity.getEyeHeight(this.entity.pose).toDouble(), 0.0)
            }

            is BlockTarget -> {
                this.pos()
            }

            is WaypointTarget -> {
                this.pos()
            }

            is RollTarget -> {
                null
            }
        }
}
