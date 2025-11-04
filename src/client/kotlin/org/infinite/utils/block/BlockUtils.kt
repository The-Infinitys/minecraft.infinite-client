package org.infinite.utils.block

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.inventory.Inventory
import net.minecraft.item.BlockItem
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import org.infinite.libs.client.inventory.InventoryManager
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
        // println("RotationManager: Faked rotation set to Yaw=$serverYaw, Pitch=$serverPitch")
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

    // --- ブロック設置ユーティリティ ---

    /**
     * ブロックを設置するためのパケットを送信し、設置操作をシミュレートします。
     *
     * @param neighbor 設置したい場所の隣接ブロックの座標 (このブロックの面に設置する)
     * @param side 設置先のブロック面 (neighborのどの面に設置するか)
     * @param hitVec ブロックの当たり判定ボックス内の正確なクリック位置
     * @param hotbarSlot 使用するホットバーのスロットインデックス (0-8)
     * @return 設置パケット送信が試行された場合 true
     */
    fun placeBlock(
        neighbor: BlockPos,
        side: Direction,
        hitVec: Vec3d,
        hotbarSlot: Int,
    ): Boolean {
        val player = player ?: return false
        val interactionManager = client.interactionManager ?: return false

        // 1. ホットバーのスロット切り替え
        val previousSlot = player.inventory.selectedSlot
        player.inventory.selectedSlot = hotbarSlot

        // 2. 設置に使用するアイテムが BlockItem であることを再確認 (念のため)
        val stack = InventoryManager.get(InventoryManager.InventoryIndex.MainHand())
        if (stack.isEmpty || stack.item !is BlockItem) {
            // スロットを元に戻す
            player.inventory.selectedSlot = previousSlot
            return false
        }

        // 3. 視線合わせ
        // 接触点 (hitVec) に合わせて正確に視線を合わせる
        faceVectorPacket(hitVec)

        // 4. ブロック設置パケットの作成
        // hitVec はワールド絶対座標なので、neighbor の相対座標に変換する必要がある
        // Vec3d hitRel = hitVec.subtract(neighbor.toCenterPos().subtract(0.5, 0.5, 0.5));
        val hitRelX = (hitVec.x - neighbor.x).toFloat()
        val hitRelY = (hitVec.y - neighbor.y).toFloat()
        val hitRelZ = (hitVec.z - neighbor.z).toFloat()

        // 補正: Minecraftのブロック設置パケットは、隣接ブロックの角から0.0〜1.0の範囲でヒット位置を示す
        val hitRel =
            Vec3d(
                hitRelX.coerceIn(0f, 1f).toDouble(),
                hitRelY.coerceIn(0f, 1f).toDouble(),
                hitRelZ.coerceIn(0f, 1f).toDouble(),
            )

        // 5. BlockHitResult の作成
        val hitResult =
            BlockHitResult(
                hitRel, // 💡 修正点: 相対座標を使用
                side, // 設置先のブロック面
                neighbor,
                false, // 内部ヒットのフラグ (通常false)
            )
//        val world=world?:return
        // 6. スニークパケットの処理 (サーバーがブロックと対話するのを防ぐため)
        // ブロックに Interact (右クリック) がある場合、プレイヤーはスニークする必要がある
//        val originalSneaking = player.isSneaking
//        val shouldSneak = world.getBlockState(neighbor).hasBlockEntity() // 例としてエンティティを持つブロックの場合

        // 7. interactBlock を使用して設置パケットを送信
        val result = interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult)

        // 8. クライアント側の手振りアニメーション
        player.swingHand(Hand.MAIN_HAND)
        // 9. ホットバーのスロットを元に戻す
        player.inventory.selectedSlot = previousSlot

        // 設置パケットの送信自体は true を返す (結果が成功したかどうかはワールドの状態を確認する必要がある)
        return result.isAccepted
    }
}
