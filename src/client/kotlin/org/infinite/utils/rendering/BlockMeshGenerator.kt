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

// 線を表すデータクラス
data class Line(
    val start: Vec3d,
    val end: Vec3d,
    val color: Int,
)

// メッシュ全体を表すデータクラス
data class BlockMesh(
    val quads: List<Quad>,
    val lines: List<Line>,
)

object BlockMeshGenerator {
    /**
     * ブロックの位置と色のマップから、レンダリング可能なメッシュ（ポリゴン）のリストを生成する。
     * 隣接するブロックの間の面はカリングされる。
     *
     * @param blockPositions ブロックの位置と色のマップ
     * @return レンダリング可能なQuadのリスト
     */
    fun generateMesh(blockPositions: Map<BlockPos, Int>): BlockMesh {
        if (blockPositions.isEmpty()) {
            return BlockMesh(emptyList(), emptyList())
        }

        val quads = mutableListOf<Quad>()
        val lines = mutableListOf<Line>()

        // 重複する線を避けるためのセット (正規化されたVec3dペア)
        val uniqueLineSegments = mutableSetOf<Pair<Vec3d, Vec3d>>()

        blockPositions.forEach { (pos, color) ->
            val x = pos.x.toDouble()
            val y = pos.y.toDouble()
            val z = pos.z.toDouble()

            // --- Quads (Faces) --- - 隣接ブロックが存在しないか、色が異なる場合にその面を描画
            val neighborWestColor = blockPositions[pos.west()]
            val drawXMinusFace = neighborWestColor == null || neighborWestColor != color
            val neighborEastColor = blockPositions[pos.east()]
            val drawXPlusFace = neighborEastColor == null || neighborEastColor != color
            val neighborDownColor = blockPositions[pos.down()]
            val drawYMinusFace = neighborDownColor == null || neighborDownColor != color
            val neighborUpColor = blockPositions[pos.up()]
            val drawYPlusFace = neighborUpColor == null || neighborUpColor != color
            val neighborNorthColor = blockPositions[pos.north()]
            val drawZMinusFace = neighborNorthColor == null || neighborNorthColor != color
            val neighborSouthColor = blockPositions[pos.south()]
            val drawZPlusFace = neighborSouthColor == null || neighborSouthColor != color

            if (drawXMinusFace) {
                quads.add(
                    Quad(
                        Vec3d(x, y, z),
                        Vec3d(x, y, z + 1),
                        Vec3d(x, y + 1, z + 1),
                        Vec3d(x, y + 1, z),
                        color,
                        Vector3f(-1f, 0f, 0f),
                    ),
                )
            }
            if (drawXPlusFace) {
                quads.add(
                    Quad(
                        Vec3d(x + 1, y, z),
                        Vec3d(x + 1, y + 1, z),
                        Vec3d(x + 1, y + 1, z + 1),
                        Vec3d(x + 1, y, z + 1),
                        color,
                        Vector3f(1f, 0f, 0f),
                    ),
                )
            }
            if (drawYMinusFace) {
                quads.add(
                    Quad(
                        Vec3d(x, y, z),
                        Vec3d(x + 1, y, z),
                        Vec3d(x + 1, y, z + 1),
                        Vec3d(x, y, z + 1),
                        color,
                        Vector3f(0f, -1f, 0f),
                    ),
                )
            }
            if (drawYPlusFace) {
                quads.add(
                    Quad(
                        Vec3d(x, y + 1, z),
                        Vec3d(x, y + 1, z + 1),
                        Vec3d(x + 1, y + 1, z + 1),
                        Vec3d(x + 1, y + 1, z),
                        color,
                        Vector3f(0f, 1f, 0f),
                    ),
                )
            }
            if (drawZMinusFace) {
                quads.add(
                    Quad(
                        Vec3d(x, y, z),
                        Vec3d(x, y + 1, z),
                        Vec3d(x + 1, y + 1, z),
                        Vec3d(x + 1, y, z),
                        color,
                        Vector3f(0f, 0f, -1f),
                    ),
                )
            }
            // Z+ face (South) - 修正後
            if (drawZPlusFace) {
                quads.add(
                    Quad(
                        Vec3d(x, y, z + 1), // (x, y, z+1)
                        Vec3d(x, y + 1, z + 1), // (x, y+1, z+1) ← 2番目の頂点を修正
                        Vec3d(x + 1, y + 1, z + 1), // (x+1, y+1, z+1)
                        Vec3d(x + 1, y, z + 1), // (x+1, y, z+1) ← 4番目の頂点を修正
                        color,
                        Vector3f(0f, 0f, 1f),
                    ),
                )
            }

            // --- Lines (Edges) --- - 辺に共有される**いずれかの**面が描画される場合に線を描画
            val p000 = Vec3d(x, y, z)
            val p100 = Vec3d(x + 1, y, z)
            val p010 = Vec3d(x, y + 1, z)
            val p001 = Vec3d(x, y, z + 1)
            val p110 = Vec3d(x + 1, y + 1, z)
            val p101 = Vec3d(x + 1, y, z + 1)
            val p011 = Vec3d(x, y + 1, z + 1)
            val p111 = Vec3d(x + 1, y + 1, z + 1)

            // X軸に平行な辺
            // (x,y,z) - (x+1,y,z) : Y-面とZ-面の間
            val colorP000P100 = getEdgeColorForLine(color, blockPositions, pos.down(), pos.north())
            colorP000P100?.let { addUniqueLine(lines, uniqueLineSegments, p000, p100, it) }

            // (x,y+1,z) - (x+1,y+1,z) : Y+面とZ-面の間
            val colorP010P110 = getEdgeColorForLine(color, blockPositions, pos.up(), pos.north())
            colorP010P110?.let { addUniqueLine(lines, uniqueLineSegments, p010, p110, it) }

            // (x,y,z+1) - (x+1,y,z+1) : Y-面とZ+面の間
            val colorP001P101 = getEdgeColorForLine(color, blockPositions, pos.down(), pos.south())
            colorP001P101?.let { addUniqueLine(lines, uniqueLineSegments, p001, p101, it) }

            // (x,y+1,z+1) - (x+1,y+1,z+1) : Y+面とZ+面の間
            val colorP011P111 = getEdgeColorForLine(color, blockPositions, pos.up(), pos.south())
            colorP011P111?.let { addUniqueLine(lines, uniqueLineSegments, p011, p111, it) }

            // Y軸に平行な辺
            // (x,y,z) - (x,y+1,z) : X-面とZ-面の間
            val colorP000P010 = getEdgeColorForLine(color, blockPositions, pos.west(), pos.north())
            colorP000P010?.let { addUniqueLine(lines, uniqueLineSegments, p000, p010, it) }

            // (x+1,y,z) - (x+1,y+1,z) : X+面とZ-面の間
            val colorP100P110 = getEdgeColorForLine(color, blockPositions, pos.east(), pos.north())
            colorP100P110?.let { addUniqueLine(lines, uniqueLineSegments, p100, p110, it) }

            // (x,y,z+1) - (x,y+1,z+1) : X-面とZ+面の間
            val colorP001P011 = getEdgeColorForLine(color, blockPositions, pos.west(), pos.south())
            colorP001P011?.let { addUniqueLine(lines, uniqueLineSegments, p001, p011, it) }

            // (x+1,y,z+1) - (x+1,y+1,z+1) : X+面とZ+面の間
            val colorP101P111 = getEdgeColorForLine(color, blockPositions, pos.east(), pos.south())
            colorP101P111?.let { addUniqueLine(lines, uniqueLineSegments, p101, p111, it) }

            // Z軸に平行な辺
            // (x,y,z) - (x,y,z+1) : X-面とY-面の間
            val colorP000P001 = getEdgeColorForLine(color, blockPositions, pos.west(), pos.down())
            colorP000P001?.let { addUniqueLine(lines, uniqueLineSegments, p000, p001, it) }

            // (x+1,y,z) - (x+1,y,z+1) : X+面とY-面の間
            val colorP100P101 = getEdgeColorForLine(color, blockPositions, pos.east(), pos.down())
            colorP100P101?.let { addUniqueLine(lines, uniqueLineSegments, p100, p101, it) }

            // (x,y+1,z) - (x,y+1,z+1) : X-面とY+面の間
            val colorP010P011 = getEdgeColorForLine(color, blockPositions, pos.west(), pos.up())
            colorP010P011?.let { addUniqueLine(lines, uniqueLineSegments, p010, p011, it) }

            // (x+1,y+1,z) - (x+1,y+1,z+1) : X+面とY+面の間
            val colorP110P111 = getEdgeColorForLine(color, blockPositions, pos.east(), pos.up())
            colorP110P111?.let { addUniqueLine(lines, uniqueLineSegments, p110, p111, it) }
        }

        // 線の結合
        val combinedLines = mutableListOf<Line>()

        // X軸に平行な線を結合
        lines
            .filter { it.start.y == it.end.y && it.start.z == it.end.z && it.start.x != it.end.x }
            .groupBy { Pair(it.start.y, it.start.z) } // Y, Z座標でグループ化
            .forEach { (_, xLines) ->
                val sortedXLines = xLines.sortedBy { it.start.x }
                var currentStart: Vec3d? = null
                var currentEnd: Vec3d? = null
                var currentColor: Int? = null

                sortedXLines.forEach { line ->
                    if (currentStart == null) {
                        currentStart = line.start
                        currentEnd = line.end
                        currentColor = line.color
                    } else if (line.start == currentEnd && line.color == currentColor) {
                        currentEnd = line.end // 結合
                    } else {
                        // 結合が途切れたので、現在の結合された線を追加
                        combinedLines.add(Line(currentStart, currentEnd!!, currentColor!!))
                        currentStart = line.start
                        currentEnd = line.end
                        currentColor = line.color
                    }
                }
                if (currentStart != null) {
                    combinedLines.add(Line(currentStart, currentEnd!!, currentColor!!))
                }
            }

        // Y軸に平行な線を結合
        lines
            .filter { it.start.x == it.end.x && it.start.z == it.end.z && it.start.y != it.end.y }
            .groupBy { Pair(it.start.x, it.start.z) } // X, Z座標でグループ化
            .forEach { (_, yLines) ->
                val sortedYLines = yLines.sortedBy { it.start.y }
                var currentStart: Vec3d? = null
                var currentEnd: Vec3d? = null
                var currentColor: Int? = null

                sortedYLines.forEach { line ->
                    if (currentStart == null) {
                        currentStart = line.start
                        currentEnd = line.end
                        currentColor = line.color
                    } else if (line.start == currentEnd && line.color == currentColor) {
                        currentEnd = line.end // 結合
                    } else {
                        combinedLines.add(Line(currentStart, currentEnd!!, currentColor!!))
                        currentStart = line.start
                        currentEnd = line.end
                        currentColor = line.color
                    }
                }
                if (currentStart != null) {
                    combinedLines.add(Line(currentStart, currentEnd!!, currentColor!!))
                }
            }

        // Z軸に平行な線を結合
        lines
            .filter { it.start.x == it.end.x && it.start.y == it.end.y && it.start.z != it.end.z }
            .groupBy { Pair(it.start.x, it.start.y) } // X, Y座標でグループ化
            .forEach { (_, zLines) ->
                val sortedZLines = zLines.sortedBy { it.start.z }
                var currentStart: Vec3d? = null
                var currentEnd: Vec3d? = null
                var currentColor: Int? = null

                sortedZLines.forEach { line ->
                    if (currentStart == null) {
                        currentStart = line.start
                        currentEnd = line.end
                        currentColor = line.color
                    } else if (line.start == currentEnd && line.color == currentColor) {
                        currentEnd = line.end // 結合
                    } else {
                        combinedLines.add(Line(currentStart, currentEnd!!, currentColor!!))
                        currentStart = line.start
                        currentEnd = line.end
                        currentColor = line.color
                    }
                }
                if (currentStart != null) {
                    combinedLines.add(Line(currentStart, currentEnd!!, currentColor!!))
                }
            }

        return BlockMesh(quads, combinedLines)
    }

    private fun addUniqueLine(
        lines: MutableList<Line>,
        uniqueLineSegments: MutableSet<Pair<Vec3d, Vec3d>>,
        start: Vec3d,
        end: Vec3d,
        color: Int,
    ) {
        val pair =
            if (start.x < end.x || (start.x == end.x && (start.y < end.y || (start.y == end.y && start.z < end.z)))) {
                Pair(start, end)
            } else {
                Pair(end, start)
            }
        if (uniqueLineSegments.add(pair)) {
            lines.add(Line(start, end, color))
        }
    }

    private fun interpolateColor(
        color1: Int,
        color2: Int,
        ratio: Float = 0.5f,
    ): Int {
        val a1 = (color1 shr 24) and 0xFF
        val r1 = (color1 shr 16) and 0xFF
        val g1 = (color1 shr 8) and 0xFF
        val b1 = color1 and 0xFF

        val a2 = (color2 shr 24) and 0xFF
        val r2 = (color2 shr 16) and 0xFF
        val g2 = (color2 shr 8) and 0xFF
        val b2 = color2 and 0xFF

        val a = (a1 * (1 - ratio) + a2 * ratio).toInt()
        val r = (r1 * (1 - ratio) + r2 * ratio).toInt()
        val g = (g1 * (1 - ratio) + g2 * ratio).toInt()
        val b = (b1 * (1 - ratio) + b2 * ratio).toInt()

        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun getEdgeColorForLine(
        currentBlockColor: Int,
        blockPositions: Map<BlockPos, Int>,
        neighborPos1: BlockPos?, // 辺を共有する面1の隣接ブロックの座標
        neighborPos2: BlockPos?, // 辺を共有する面2の隣接ブロックの座標
    ): Int? { // nullを返すと描画しない
        val color1 = neighborPos1?.let { blockPositions[it] }
        val color2 = neighborPos2?.let { blockPositions[it] }

        val isFace1External = color1 == null || color1 != currentBlockColor
        val isFace2External = color2 == null || color2 != currentBlockColor

        // 両方の面が内部（同じ色のブロックに面している）なら描画しない
        if (!isFace1External && !isFace2External) {
            return null
        }

        // 少なくとも片方の面が外部に面している場合
        if (isFace1External && isFace2External) {
            // 両方の面が外部に面している
            return if (color1 == null && color2 == null) {
                // 両側が空間 -> CurrentBlockColor
                currentBlockColor
            } else if (color1 == null && color2 != null) {
                // 片側が空間、片側が異なるブロック -> 中間色 (currentBlockColor と color2)
                interpolateColor(currentBlockColor, color2)
            } else if (color1 != null && color2 == null) {
                // 逆パターン -> 中間色 (currentBlockColor と color1)
                interpolateColor(currentBlockColor, color1)
            } else { // color1 != null && color2 != null && color1 != currentBlockColor && color2 != currentBlockColor
                // 両側が異なるブロック -> 中間色 (color1 と color2)
                interpolateColor(color1!!, color2!!)
            }
        } else if (isFace1External) {
            // Face1のみが外部に面している (Face2は内部)
            return if (color1 == null) {
                currentBlockColor // 空間に面している
            } else { // color1 != null && color1 != currentBlockColor
                interpolateColor(currentBlockColor, color1) // 異なるブロックに面している
            }
        } else { // isFace2External (Face2のみが外部に面している)
            return if (color2 == null) {
                currentBlockColor // 空間に面している
            } else { // color2 != null && color2 != currentBlockColor
                interpolateColor(currentBlockColor, color2) // 異なるブロックに面している
            }
        }
    }
}
