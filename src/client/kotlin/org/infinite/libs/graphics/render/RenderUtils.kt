package org.infinite.libs.graphics.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Box
import net.minecraft.util.math.ColorHelper
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.infinite.utils.rendering.Line
import org.infinite.utils.rendering.Quad
import org.joml.Vector3f

object RenderUtils {
    data class ColorBox(
        val color: Int,
        val box: Box,
    )

    fun renderSolidBox(
        matrix: MatrixStack,
        box: Box,
        color: Int,
        buffer: VertexConsumer,
    ) {
        val entry = matrix.peek()
        val x1 = box.minX.toFloat()
        val y1 = box.minY.toFloat()
        val z1 = box.minZ.toFloat()
        val x2 = box.maxX.toFloat()
        val y2 = box.maxY.toFloat()
        val z2 = box.maxZ.toFloat()

        // Y- face (Bottom) - 法線: (0, -1, 0)
        buffer.quad(entry, 0f, -1f, 0f, color, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2)

        // Y+ face (Top) - 法線: (0, 1, 0)
        buffer.quad(entry, 0f, 1f, 0f, color, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1)

        // Z- face (North) - 法線: (0, 0, -1)
        buffer.quad(entry, 0f, 0f, -1f, color, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1)

        // X+ face (East) - 法線: (1, 0, 0)
        buffer.quad(entry, 1f, 0f, 0f, color, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2)

        // Z+ face (South) - 法線: (0, 0, 1)
        buffer.quad(entry, 0f, 0f, 1f, color, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2)

        // X- face (West) - 法線: (-1, 0, 0)
        buffer.quad(entry, -1f, 0f, 0f, color, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1)
    }

// --------------------------------------------------------------------------------------------------

    // ヘルパー関数 (RenderUtilsオブジェクト内に定義すると便利)
    fun VertexConsumer.quad(
        entry: MatrixStack.Entry,
        nx: Float,
        ny: Float,
        nz: Float, // 法線
        color: Int,
        // 4つの頂点の座標 (x, y, z)
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        x3: Float,
        y3: Float,
        z3: Float,
        x4: Float,
        y4: Float,
        z4: Float,
    ) {
        // 頂点情報
        // 三角形 1 (V1, V2, V3)
        vertex(entry, x1, y1, z1).color(color).normal(entry, nx, ny, nz)
        vertex(entry, x2, y2, z2).color(color).normal(entry, nx, ny, nz)
        vertex(entry, x3, y3, z3).color(color).normal(entry, nx, ny, nz)
        // 三角形 2 (V3, V4, V1)
        vertex(entry, x3, y3, z3).color(color).normal(entry, nx, ny, nz)
        vertex(entry, x4, y4, z4).color(color).normal(entry, nx, ny, nz)
        vertex(entry, x1, y1, z1).color(color).normal(entry, nx, ny, nz)
    }

    /**
     * 単一のBoxのアウトラインをVertexConsumerに書き込みます。
     * この関数はバッファをフラッシュしません。
     */
    fun renderLinedBox(
        matrix: MatrixStack,
        box: Box,
        color: Int,
        buffer: VertexConsumer,
    ) {
        val entry: MatrixStack.Entry = matrix.peek()
        val x1 = box.minX.toFloat()
        val y1 = box.minY.toFloat()
        val z1 = box.minZ.toFloat()
        val x2 = box.maxX.toFloat()
        val y2 = box.maxY.toFloat()
        val z2 = box.maxZ.toFloat()
        buffer.vertex(entry, x1, y1, z1).color(color).normal(entry, 1f, 0f, 0f) //  を追加
        buffer.vertex(entry, x2, y1, z1).color(color).normal(entry, 1f, 0f, 0f)
        buffer.vertex(entry, x1, y1, z1).color(color).normal(entry, 0f, 0f, 1f)
        buffer.vertex(entry, x1, y1, z2).color(color).normal(entry, 0f, 0f, 1f)
        buffer.vertex(entry, x2, y1, z1).color(color).normal(entry, 0f, 0f, 1f)
        buffer.vertex(entry, x2, y1, z2).color(color).normal(entry, 0f, 0f, 1f)
        buffer.vertex(entry, x1, y1, z2).color(color).normal(entry, 1f, 0f, 0f)
        buffer.vertex(entry, x2, y1, z2).color(color).normal(entry, 1f, 0f, 0f)

        // top lines
        buffer.vertex(entry, x1, y2, z1).color(color).normal(entry, 1f, 0f, 0f)
        buffer.vertex(entry, x2, y2, z1).color(color).normal(entry, 1f, 0f, 0f)
        buffer.vertex(entry, x1, y2, z1).color(color).normal(entry, 0f, 0f, 1f)
        buffer.vertex(entry, x1, y2, z2).color(color).normal(entry, 0f, 0f, 1f)
        buffer.vertex(entry, x2, y2, z1).color(color).normal(entry, 0f, 0f, 1f)
        buffer.vertex(entry, x2, y2, z2).color(color).normal(entry, 0f, 0f, 1f)
        buffer.vertex(entry, x1, y2, z2).color(color).normal(entry, 1f, 0f, 0f)
        buffer.vertex(entry, x2, y2, z2).color(color).normal(entry, 1f, 0f, 0f)

        // side lines
        buffer.vertex(entry, x1, y1, z1).color(color).normal(entry, 0f, 1f, 0f)
        buffer.vertex(entry, x1, y2, z1).color(color).normal(entry, 0f, 1f, 0f)
        buffer.vertex(entry, x2, y1, z1).color(color).normal(entry, 0f, 1f, 0f)
        buffer.vertex(entry, x2, y2, z1).color(color).normal(entry, 0f, 1f, 0f)
        buffer.vertex(entry, x1, y1, z2).color(color).normal(entry, 0f, 1f, 0f)
        buffer.vertex(entry, x1, y2, z2).color(color).normal(entry, 0f, 1f, 0f)
        buffer.vertex(entry, x2, y1, z2).color(color).normal(entry, 0f, 1f, 0f)
        buffer.vertex(entry, x2, y2, z2).color(color).normal(entry, 0f, 1f, 0f)
    }

    fun renderSolidColorBoxes(
        matrix: MatrixStack,
        boxes: List<ColorBox>,
        buffer: VertexConsumer,
    ) {
        val camPos = cameraPos().negate()
        boxes.forEach {
            renderSolidBox(matrix, it.box.offset(camPos), it.color, buffer)
        }
    }

    fun renderSolidQuads(
        matrix: MatrixStack,
        quads: List<Quad>,
        buffer: VertexConsumer,
    ) {
        val camPos = cameraPos().negate()
        val entry = matrix.peek()
        quads.forEach { quad ->
            // カメラ位置オフセットを適用
            val v1 = quad.vertex1.add(camPos)
            val v2 = quad.vertex2.add(camPos)
            val v3 = quad.vertex3.add(camPos)
            val v4 = quad.vertex4.add(camPos)

            buffer.quad(
                entry,
                quad.normal.x,
                quad.normal.y,
                quad.normal.z,
                quad.color,
                v1.x.toFloat(),
                v1.y.toFloat(),
                v1.z.toFloat(),
                v2.x.toFloat(),
                v2.y.toFloat(),
                v2.z.toFloat(),
                v3.x.toFloat(),
                v3.y.toFloat(),
                v3.z.toFloat(),
                v4.x.toFloat(),
                v4.y.toFloat(),
                v4.z.toFloat(),
            )
        }
    }

    fun renderLinedLines(
        matrix: MatrixStack,
        lines: List<Line>,
        buffer: VertexConsumer,
    ) {
        val camPos = cameraPos().negate()
        val entry = matrix.peek()
        lines.forEach { line ->
            val s = line.start.add(camPos)
            val e = line.end.add(camPos)

            val start3f = Vector3f(s.x.toFloat(), s.y.toFloat(), s.z.toFloat())
            val end3f = Vector3f(e.x.toFloat(), e.y.toFloat(), e.z.toFloat())
            val normal = Vector3f(end3f).sub(start3f).normalize()

            buffer.vertex(entry, start3f).color(line.color).normal(entry, normal.x, normal.y, normal.z)
            buffer.vertex(entry, end3f).color(line.color).normal(entry, normal.x, normal.y, normal.z)
        }
    }

    /**
     * 複数の LinedColorBox のアウトラインを描画します。バッファのフラッシュはしません。
     *
     * @param buffer 描画先の VertexConsumer (Graphics3Dから取得する)
     */
    fun renderLinedColorBoxes(
        matrix: MatrixStack,
        boxes: List<ColorBox>,
        buffer: VertexConsumer, // Graphics3Dから渡される
    ) {
        val camPos = cameraPos().negate()
        boxes.forEach { renderLinedBox(matrix, it.box.offset(camPos), it.color, buffer) }
    }

    // 距離によるグラデーション色の計算 (変更なし)
    private const val MAX_COLOR_DISTANCE = 64.0

    fun distColor(distance: Double): Int {
        val clampDist = MathHelper.clamp(distance, 0.0, MAX_COLOR_DISTANCE)
        val f = (clampDist / MAX_COLOR_DISTANCE).toFloat()

        val startColor =
            org.infinite.InfiniteClient
                .theme()
                .colors.errorColor // Red for close
        val endColor =
            org.infinite.InfiniteClient
                .theme()
                .colors.greenAccentColor // Green for far

        val r =
            MathHelper.lerp(
                f,
                ColorHelper.getRed(startColor).toFloat() / 255f,
                ColorHelper.getRed(endColor).toFloat() / 255f,
            )
        val g =
            MathHelper.lerp(
                f,
                ColorHelper.getGreen(startColor).toFloat() / 255f,
                ColorHelper.getGreen(endColor).toFloat() / 255f,
            )
        val b =
            MathHelper.lerp(
                f,
                ColorHelper.getBlue(startColor).toFloat() / 255f,
                ColorHelper.getBlue(endColor).toFloat() / 255f,
            )

        return (
            0xFF000000.toInt() or
                ((r * 255).toInt() shl 16) or
                ((g * 255).toInt() shl 8) or
                (b * 255).toInt()
        )
    }

    /**
     * 2点間に直線を描画する (ワールド座標基準)。バッファのフラッシュはしません。
     */
    fun renderLine(
        matrix: MatrixStack,
        start: Vec3d,
        end: Vec3d,
        color: Int,
        buffer: VertexConsumer,
    ) {
        val camPos = cameraPos().negate()
        val entry: MatrixStack.Entry = matrix.peek()
        val s = start.add(camPos)
        val e = end.add(camPos)

        // 始点と終点の座標
        val start3f = Vector3f(s.x.toFloat(), s.y.toFloat(), s.z.toFloat())
        val end3f = Vector3f(e.x.toFloat(), e.y.toFloat(), e.z.toFloat())
        // 🚨 修正: 正しい法線ベクトルを計算
        // 法線は線分の方向
        val normal = Vector3f(end3f).sub(start3f).normalize()
        // 頂点情報と法線の書き込み
        buffer.vertex(entry, start3f).color(color).normal(entry, normal.x, normal.y, normal.z)
        buffer.vertex(entry, end3f).color(color).normal(entry, normal.x, normal.y, normal.z)
    }

    fun cameraPos(): Vec3d = MinecraftClient.getInstance().blockEntityRenderDispatcher?.cameraPos ?: Vec3d.ZERO
}
