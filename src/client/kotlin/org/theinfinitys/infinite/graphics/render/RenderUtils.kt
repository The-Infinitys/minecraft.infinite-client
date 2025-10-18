package org.theinfinitys.infinite.graphics.render

import net.minecraft.client.MinecraftClient
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.joml.Vector3f

object RenderUtils {
    data class LinedColorBox(
        val color: Int,
        val box: Box,
    )

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
        // ... (renderLinedBox の内容は変更なし) ...
        val entry: MatrixStack.Entry = matrix.peek()
        val x1 = box.minX.toFloat()
        val y1 = box.minY.toFloat()
        val z1 = box.minZ.toFloat()
        val x2 = box.maxX.toFloat()
        val y2 = box.maxY.toFloat()
        val z2 = box.maxZ.toFloat()
        // color() メソッドは ARGB (Int) を受け取るため、そのまま使用します。

        // bottom lines
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

    /**
     * 複数の Box のアウトラインを描画します。バッファのフラッシュはしません。
     *
     * @param buffer 描画先の VertexConsumer (Graphics3Dから取得する)
     */
    fun renderLinedBoxes(
        matrix: MatrixStack,
        boxes: List<Box>,
        color: Int,
        buffer: VertexConsumer, // Graphics3Dから渡される
    ) {
        val camPos = cameraPos().negate()
        boxes.forEach { renderLinedBox(matrix, it.offset(camPos), color, buffer) }
    }

    /**
     * 複数の LinedColorBox のアウトラインを描画します。バッファのフラッシュはしません。
     *
     * @param buffer 描画先の VertexConsumer (Graphics3Dから取得する)
     */
    fun renderLinedColorBoxes(
        matrix: MatrixStack,
        boxes: List<LinedColorBox>,
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

        val r = MathHelper.lerp(f, 1.0f, 0.0f)
        val g = MathHelper.lerp(f, 0.0f, 1.0f)
        val b = MathHelper.lerp(f, 0.0f, 1.0f)

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
