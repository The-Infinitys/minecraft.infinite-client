package org.infinite.libs.graphics

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.client.texture.TextureSetup
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.math.MathHelper
import org.infinite.utils.rendering.drawBorder
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fStack
import kotlin.math.roundToInt

/**
 * MinecraftのGUI描画コンテキストを保持し、カスタム2Dレンダリングを実行するためのクラス。
 * JavaScriptのCanvas 2D APIのようなメソッドを提供します。
 *
 * @property context 描画に必要なGUIコンテキスト
 * @property tickCounter レンダリングティックの情報
 */
class Graphics2D(
    val context: DrawContext,
    val tickCounter: RenderTickCounter,
) {
    val client: MinecraftClient = MinecraftClient.getInstance()

    val matrixStack: Matrix3x2fStack = context.matrices

    /** 部分ティックの進行度 */
    val tickProgress: Float = tickCounter.getTickProgress(false)

    /** 画面の幅 */
    val width: Int = context.scaledWindowWidth

    /** 画面の高さ */
    val height: Int = context.scaledWindowHeight

    // ----------------------------------------------------------------------
    // MatrixState/Transform 状態管理 (Canvasの save/restore に相当)
    // ----------------------------------------------------------------------

    /**
     * 現在の変換行列を保存します (Canvasの save() に相当)。
     * 描画位置やスケールの変更を一時的に行う際に使用します。
     */
    fun pushState() {
        matrixStack.pushMatrix()
    }

    /**
     * 以前に保存された変換行列を復元します (Canvasの restore() に相当)。
     */
    fun popState() {
        matrixStack.popMatrix()
    }

    /**
     * 描画原点を移動させます (Canvasの translate() に相当)。
     * @param x X軸方向の移動量 (ピクセル)
     * @param y Y軸方向の移動量 (ピクセル)
     */
    fun translate(
        x: Float,
        y: Float,
    ) {
        matrixStack.translate(x, y)
    }

    /**
     * 描画のスケールを変更します (Canvasの scale() に相当)。
     */
    fun scale(
        scaleX: Float,
        scaleY: Float,
    ) {
        // Zスケールは1.0として、2D描画用の3D行列を操作
        matrixStack.scale(scaleX, scaleY)
    }

    // ----------------------------------------------------------------------
    // Draw Primitives プリミティブ描画
    // ----------------------------------------------------------------------

    /**
     * 矩形（四角形）を指定した色で塗りつぶし描画します。
     * @param x 描画開始X座標 (ピクセル)
     * @param y 描画開始Y座標 (ピクセル)
     * @param width 矩形の幅 (ピクセル)
     * @param height 矩形の高さ (ピクセル)
     * @param color ARGB形式の色 (0xAARRGGBB)
     */
    fun fill(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
    ) {
        // DrawContext.fill の引数は (x1, y1, x2, y2, color) の形式であるため、
        // widthとheightを使用して x2 = x + width, y2 = y + height を計算します。
        context.fill(x, y, x + width, y + height, color)
    }

    /**
     * 矩形（四角形）を指定した太さの枠線（縁取り）で描画します。
     *
     * @param x 描画開始X座標 (ピクセル)
     * @param y 描画開始Y座標 (ピクセル)
     * @param width 矩形の幅 (ピクセル)
     * @param height 矩形の高さ (ピクセル)
     * @param color ARGB形式の色 (0xAARRGGBB)
     * @param size ボーダの太さ
     * */
    fun drawBorder(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int,
        size: Int = 1,
    ) {
        // 上の線
        context.drawBorder(x, y, width, height, color, size)
    }

    /**
     * 文字列を指定した位置に描画します。
     */
    fun drawText(
        text: String,
        x: Int,
        y: Int,
        color: Int,
        shadow: Boolean = true,
    ) {
        if (shadow) {
            context.drawTextWithShadow(client.textRenderer, text, x, y, color)
        } else {
            context.drawText(client.textRenderer, text, x, y, color, false)
        }
    }

    fun drawText(
        text: Text,
        x: Int,
        y: Int,
        color: Int,
        shadow: Boolean = true,
    ) {
        if (shadow) {
            context.drawTextWithShadow(client.textRenderer, text, x, y, color)
        } else {
            context.drawText(client.textRenderer, text, x, y, color, false)
        }
    }

    /**
     * アイテムスタックを指定した位置に描画します。
     */
    fun drawItem(
        itemStack: ItemStack,
        x: Int,
        y: Int,
    ) {
        context.drawItem(itemStack, x, y)
    }

    fun fillTriangle(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
        color: Int,
    ) {
        val pose = Matrix3x2f(context.matrices)
        val scissor = context.scissorStack.peekLast()
        context.state.addSimpleElement(
            TriangleRenderState(
                RenderPipelines.GUI,
                TextureSetup.empty(),
                pose,
                x1,
                y1,
                x2,
                y2,
                x3,
                y3,
                color,
                scissor,
            ),
        )
    }

    fun drawLine(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int,
        size: Int = 1,
    ) {
        val scale: Int = MinecraftClient.getInstance().window.scaleFactor
        val x = x1 * scale
        val y = y1 * scale
        val w = (x2 - x1) * scale
        val h = (y2 - y1) * scale
        val angle = MathHelper.atan2(h.toDouble(), w.toDouble()).toFloat()
        val length = MathHelper.sqrt(w * w + h * h).roundToInt()

        context.matrices.pushMatrix()
        context.matrices.scale(1f / scale)
        context.matrices.translate(x, y)
        context.matrices.rotate(angle)
        context.matrices.translate(-0.5f, -0.5f)
        context.fill(0, -size / 2, length - 1, size / 2, color)
        context.matrices.popMatrix()
    }

    /**
     * 三角形の枠をベベル結合で描画します。
     * fillTriangleを利用して、頂点での交点に尖ったベベル形状を作成します。
     *
     * @param x1 頂点1 X座標
     * @param y1 頂点1 Y座標
     * @param x2 頂点2 X座標
     * @param y2 頂点2 Y座標
     * @param x3 頂点3 X座標
     * @param y3 頂点3 Y座標
     * @param color ARGB形式の色
     * @param size 枠線の太さ (ピクセル)
     * @param miterLimit ベベル結合の制限角度（ラジアン）。これより鋭い角ではベベルを切り詰める
     */
    fun drawTriangle(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
        color: Int,
        size: Int = 1,
    ) {
        val halfSize = size / 2.0f
        drawLine(x1, y1, x2, y2, color, size)
        drawLine(x2, y2, x3, y3, color, size)
        drawLine(x3, y3, x1, y1, color, size)
        // 各頂点でのベベル結合を計算して描画
        drawBevelJointAtVertex(x1, y1, x2, y2, x3, y3, halfSize, color)
        drawBevelJointAtVertex(x2, y2, x3, y3, x1, y1, halfSize, color)
        drawBevelJointAtVertex(x3, y3, x1, y1, x2, y2, halfSize, color)
    }

    /**
     * 指定された頂点でのベベル結合を描画します。
     * 2本の一定の太さを持つ線分が交わる頂点で、外部の点２つと頂点を結んだ三角形を描画します。
     */
    private fun drawBevelJointAtVertex(
        vertexX: Float,
        vertexY: Float,
        prevX: Float,
        prevY: Float,
        nextX: Float,
        nextY: Float,
        halfSize: Float,
        color: Int,
    ) {
        // 頂点と前の点 (prev) を結ぶ線分のベクトル (V_prev)
        val dx1 = vertexX - prevX
        val dy1 = vertexY - prevY
        // 頂点と次の点 (next) を結ぶ線分のベクトル (V_next)
        val dx2 = nextX - vertexX
        val dy2 = nextY - vertexY

        // ----------------------------------------------------------------------
        // V_prev の外側への法線ベクトルを計算
        // 法線 N = (-dy, dx) または (dy, -dx)
        // 進行方向 V_prev = (dx1, dy1)
        //
        // 線分の太さの半分 (halfSize) の長さを持つ法線ベクトルを求める
        val len1 = MathHelper.sqrt(dx1 * dx1 + dy1 * dy1)
        val nx1: Float
        val ny1: Float
        if (len1 > 1e-6) { // ゼロ割を避ける
            // 単位法線ベクトル: N1_unit = (-dy1/len1, dx1/len1)
            // 外側のオフセット点 p1 を求めるためのベクトル: N1 = N1_unit * halfSize
            // 線分の「外側」は、線分 (prev->vertex) の左側、つまり V_prev の左側を仮定
            nx1 = -dy1 / len1 * halfSize
            ny1 = dx1 / len1 * halfSize
        } else {
            nx1 = 0f
            ny1 = 0f
        }

        // p1 = vertex + N1
        val p1X = vertexX + nx1
        val p1Y = vertexY + ny1

        // ----------------------------------------------------------------------
        // V_next の外側への法線ベクトルを計算
        // 進行方向 V_next = (dx2, dy2)
        // 線分の「外側」は、線分 (vertex->next) の左側、つまり V_next の左側を仮定
        val len2 = MathHelper.sqrt(dx2 * dx2 + dy2 * dy2)
        val nx2: Float
        val ny2: Float
        if (len2 > 1e-6) { // ゼロ割を避ける
            // 単位法線ベクトル: N2_unit = (-dy2/len2, dx2/len2)
            // 外側のオフセット点 p2 を求めるためのベクトル: N2 = N2_unit * halfSize
            nx2 = -dy2 / len2 * halfSize
            ny2 = dx2 / len2 * halfSize
        } else {
            nx2 = 0f
            ny2 = 0f
        }

        // p2 = vertex + N2
        val p2X = vertexX + nx2
        val p2Y = vertexY + ny2

        // ----------------------------------------------------------------------
        // ベベル結合は、頂点 V と、それぞれの線分の外側のオフセット点 p1, p2 を結ぶ三角形
        fillTriangle(
            vertexX,
            vertexY, // 頂点 V
            p1X,
            p1Y, // 線分 (prev-V) の外側点 p1
            p2X,
            p2Y, // 線分 (V-next) の外側点 p2
            color,
        )
    }

    /**
     * 円（真円）を指定した色で塗りつぶし描画します。
     * 多数の三角形で円を近似して描画します。
     *
     * @param cx 円の中心X座標 (ピクセル)
     * @param cy 円の中心Y座標 (ピクセル)
     * @param radius 円の半径 (ピクセル)
     * @param color ARGB形式の色 (0xAARRGGBB)
     */
    fun fillCircle(
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
    ) {
        // 円を近似するための三角形の数 (多いほど滑らかになるが、描画負荷が増す)
        val segments = 32
        val twoPi = 2.0 * Math.PI

        // 現在の行列を保存
        pushState()

        // 描画の中心を (cx, cy) に移動
        translate(cx, cy)

        // 中心点と円周上の2点を使って三角形を順次描画
        for (i in 0 until segments) {
            val angle1 = (i.toFloat() / segments.toFloat() * twoPi).toFloat()
            val angle2 = ((i.toFloat() + 1f) / segments.toFloat() * twoPi).toFloat()

            // 頂点1 (中心)
            val x1 = 0f
            val y1 = 0f
            // 頂点2 (円周上)
            val x2 = MathHelper.cos(angle1) * radius
            val y2 = MathHelper.sin(angle1) * radius
            // 頂点3 (円周上)
            val x3 = MathHelper.cos(angle2) * radius
            val y3 = MathHelper.sin(angle2) * radius

            // 頂点座標は既に中心 (0, 0) を基準に計算されている
            fillTriangle(x1, y1, x2, y2, x3, y3, color)
        }

        // 行列を復元
        popState()
    }

    /**
     * 円（真円）を指定した太さの枠線で描画します。
     * 多数の線分で円を近似して描画します。
     *
     * @param cx 円の中心X座標 (ピクセル)
     * @param cy 円の中心Y座標 (ピクセル)
     * @param radius 円の半径 (ピクセル)
     * @param color ARGB形式の色 (0xAARRGGBB)
     * @param size 枠線の太さ (ピクセル)
     */
    fun drawCircle(
        cx: Float,
        cy: Float,
        radius: Float,
        color: Int,
        size: Int = 1,
    ) {
        // 円周を近似するための線分の数
        val segments = 32
        val twoPi = 2.0 * Math.PI

        var prevX = cx + radius
        var prevY = cy

        // 円周上の点を結んで線分を描画
        for (i in 1..segments) {
            val angle = (i.toFloat() / segments.toFloat() * twoPi).toFloat()

            // 円周上の現在の点
            val currentX = cx + MathHelper.cos(angle) * radius
            val currentY = cy + MathHelper.sin(angle) * radius

            // 前の点と現在の点を結んで線を描画
            drawLine(prevX, prevY, currentX, currentY, color, size)

            // 現在の点を次の線の始点として保存
            prevX = currentX
            prevY = currentY
        }
    }

    data class DisplayPos(
        val x: Double,
        val y: Double,
    )

    fun textWidth(text: String): Int = MinecraftClient.getInstance().textRenderer.getWidth(text)

    fun fontHeight(): Int = MinecraftClient.getInstance().textRenderer.fontHeight
}
