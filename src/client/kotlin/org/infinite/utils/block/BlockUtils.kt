package org.infinite.utils.block

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import org.infinite.libs.client.player.ClientInterface
import kotlin.math.atan2

data class Rotation(
    val yaw: Float,
    val pitch: Float,
) {
    companion object {
        fun wrapped(
            yaw: Float,
            pitch: Float,
        ): Rotation = Rotation(MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90.0f, 90.0f))
    }
}

// 視点変更を処理するシングルトン/クラスを仮定 (RotationFakerに相当)
object RotationManager {
    private var isFakingRotation = false
    private var serverYaw = 0f
    private var serverPitch = 0f

    // プレイヤーの現在の視点と、必要な視点から、サーバーに送信する回転を設定するメソッド
    fun faceVectorPacket(
        player: ClientPlayerEntity,
        vec: Vec3d,
    ) {
        val needed = BlockUtils.getNeededRotations(vec)
        // Wurstの RotationFaker.faceVectorPacket(Vec3d vec) ロジックを再現
        isFakingRotation = true
        serverYaw = BlockUtils.limitAngleChange(player.yaw, needed.yaw)
        serverPitch = needed.pitch

        // ここでサーバーに回転パケットを送信するロジックが必要
        // (WurstClientの RotationFaker の onPreMotion/onPostMotion で行われる)
        // 今回は実装を省略
        println("RotationManager: Faked rotation set to Yaw=$serverYaw, Pitch=$serverPitch")
    }
}

object BlockUtils : ClientInterface() {
    // --- プレイヤーと視点関連のユーティリティ (RotationUtilsに相当) ---

    private fun getEyesPos(): Vec3d {
        val player = player ?: return Vec3d.ZERO
        val eyeHeight = player.getEyeHeight(player.pose)
        return playerPos!!.add(0.0, eyeHeight.toDouble(), 0.0)
    }

    /**
     * Wurstの RotationFaker.faceVectorPacket(...) ロジックを呼び出す。
     * 実際に回転パケットを送信する処理は RotationManager に委譲される。
     */
    fun faceVectorPacket(vec: Vec3d) {
        val player = player ?: return
        RotationManager.faceVectorPacket(player, vec)
    }

    /**
     * Wurstの RotationUtils.getNeededRotations(Vec3d vec) に相当
     */
    fun getNeededRotations(vec: Vec3d): Rotation {
        val eyes = getEyesPos()

        val diffX = vec.x - eyes.x
        val diffZ = vec.z - eyes.z
        val yaw = Math.toDegrees(atan2(diffZ, diffX)) - 90F

        val diffY = vec.y - eyes.y
        val diffXZ = kotlin.math.sqrt(diffX * diffX + diffZ * diffZ)
        val pitch = -Math.toDegrees(atan2(diffY, diffXZ))

        return Rotation.wrapped(yaw.toFloat(), pitch.toFloat())
    }

    /**
     * Wurstの RotationUtils.limitAngleChange(float current, float intended, float maxChange) に相当
     * ※ RotationFaker.faceVectorPacket で使用されている引数なしバージョンを実装
     */
    fun limitAngleChange(
        current: Float,
        intended: Float,
    ): Float {
        val currentWrapped = MathHelper.wrapDegrees(current)
        val intendedWrapped = MathHelper.wrapDegrees(intended)

        val change = MathHelper.wrapDegrees(intendedWrapped - currentWrapped)

        return current + change
    }

    private fun rayCast(
        from: Vec3d,
        to: Vec3d,
    ): BlockHitResult {
        val context =
            RaycastContext(
                from,
                to,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                player,
            )

        return client.world?.raycast(context) ?: BlockHitResult.createMissed(to, Direction.DOWN, BlockPos.ORIGIN)
    }

    private fun hasLineOfSight(
        from: Vec3d,
        to: Vec3d,
    ): Boolean = rayCast(from, to).type == net.minecraft.util.hit.HitResult.Type.MISS

    data class BlockBreakingParams(
        val pos: BlockPos,
        val side: Direction,
        val hitVec: Vec3d,
        val distanceSq: Double,
        val lineOfSight: Boolean,
    )

    fun getBlockBreakingParams(pos: BlockPos): BlockBreakingParams? {
        val eyes = getEyesPos()
        val sides = Direction.entries.toTypedArray()
        val world = client.world ?: return null

        val state = world.getBlockState(pos)
        val shape = state.getOutlineShape(world, pos)
        if (shape.isEmpty) return null

        val box = shape.boundingBox
        val halfSize =
            Vec3d(
                box.maxX - box.minX,
                box.maxY - box.minY,
                box.maxZ - box.minZ,
            ).multiply(0.5)
        val center = Vec3d.of(pos).add(box.center)

        val hitVecs =
            sides.map { side ->
                val dirVec = side.vector
                val relHitVec = Vec3d(halfSize.x * dirVec.x, halfSize.y * dirVec.y, halfSize.z * dirVec.z)
                center.add(relHitVec)
            }

        val distanceSqToCenter = eyes.squaredDistanceTo(center)
        val distancesSq = hitVecs.map { eyes.squaredDistanceTo(it) }
        val linesOfSight = BooleanArray(sides.size) { false }

        for (i in sides.indices) {
            if (distancesSq[i] >= distanceSqToCenter) continue
            linesOfSight[i] = hasLineOfSight(eyes, hitVecs[i])
        }

        var bestSide = sides[0]
        for (i in 1 until sides.size) {
            val currentBestIndex = bestSide.ordinal

            if (!linesOfSight[currentBestIndex] && linesOfSight[i]) {
                bestSide = sides[i]
                continue
            }
            if (linesOfSight[currentBestIndex] && !linesOfSight[i]) continue

            if (distancesSq[i] < distancesSq[currentBestIndex]) {
                bestSide = sides[i]
            }
        }

        val bestIndex = bestSide.ordinal
        return BlockBreakingParams(
            pos = pos,
            side = bestSide,
            hitVec = hitVecs[bestIndex],
            distanceSq = distancesSq[bestIndex],
            lineOfSight = linesOfSight[bestIndex],
        )
    }
}
