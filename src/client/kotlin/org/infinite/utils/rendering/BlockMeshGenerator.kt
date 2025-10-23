package org.infinite.utils.rendering

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import org.joml.Vector3f

// 面を表すデータクラス
data class Quad(
    val vertex1: Vec3d,
    val vertex2: Vec3d,
    val vertex3: Vec3d,
    val vertex4: Vec3d,
    val color: Int,
    val normal: Vector3f,
)

object BlockMeshGenerator {
    /**
     * ブロックの位置と色のマップから、レンダリング可能なメッシュ（ポリゴン）のリストを生成する。
     * 隣接するブロックの間の面はカリングされる。
     *
     * @param blockPositions ブロックの位置と色のマップ
     * @return レンダリング可能なQuadのリスト
     */
    fun generateMesh(blockPositions: Map<BlockPos, Int>): List<Quad> {
        if (blockPositions.isEmpty()) {
            return emptyList()
        }

        val quads = mutableListOf<Quad>()

        blockPositions.forEach { (pos, color) ->
            val x = pos.x.toDouble()
            val y = pos.y.toDouble()
            val z = pos.z.toDouble()

            // 各面をチェックし、隣接するブロックがなければQuadを追加
            // X- (西)
            if (!blockPositions.containsKey(pos.west())) {
                quads.add(
                    Quad(
                        Vec3d(x, y, z),
                        Vec3d(x, y, z + 1),
                        Vec3d(x, y + 1, z + 1),
                        Vec3d(x, y + 1, z),
                        color,
                        Vector3f(-1f, 0f, 0f), // 法線を追加
                    ),
                )
            }
            // X+ (東)
            if (!blockPositions.containsKey(pos.east())) {
                quads.add(
                    Quad(
                        Vec3d(x + 1, y, z),
                        Vec3d(x + 1, y + 1, z),
                        Vec3d(x + 1, y + 1, z + 1),
                        Vec3d(x + 1, y, z + 1),
                        color,
                        Vector3f(1f, 0f, 0f), // 法線を追加
                    ),
                )
            }
            // Y- (下)
            if (!blockPositions.containsKey(pos.down())) {
                quads.add(
                    Quad(
                        Vec3d(x, y, z),
                        Vec3d(x + 1, y, z),
                        Vec3d(x + 1, y, z + 1),
                        Vec3d(x, y, z + 1),
                        color,
                        Vector3f(0f, -1f, 0f), // 法線を追加
                    ),
                )
            }
            // Y+ (上)
            if (!blockPositions.containsKey(pos.up())) {
                quads.add(
                    Quad(
                        Vec3d(x, y + 1, z),
                        Vec3d(x, y + 1, z + 1),
                        Vec3d(x + 1, y + 1, z + 1),
                        Vec3d(x + 1, y + 1, z),
                        color,
                        Vector3f(0f, 1f, 0f), // 法線を追加
                    ),
                )
            }
            // Z- (北)
            if (!blockPositions.containsKey(pos.north())) {
                quads.add(
                    Quad(
                        Vec3d(x, y, z),
                        Vec3d(x, y + 1, z),
                        Vec3d(x + 1, y + 1, z),
                        Vec3d(x + 1, y, z),
                        color,
                        Vector3f(0f, 0f, -1f), // 法線を追加
                    ),
                )
            }
            // Z+ (南)
            if (!blockPositions.containsKey(pos.south())) {
                quads.add(
                    Quad(
                        Vec3d(x, y, z + 1),
                        Vec3d(x + 1, y, z + 1),
                        Vec3d(x + 1, y + 1, z + 1),
                        Vec3d(x + 1, y + 1, z + 1),
                        color,
                        Vector3f(0f, 0f, 1f), // 法線を追加
                    ),
                )
            }
        }
        return quads
    }
}
