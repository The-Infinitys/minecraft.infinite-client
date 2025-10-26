package org.infinite.libs.graphics

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.client.texture.TextureSetup
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
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

    /**
     * テクスチャを指定した位置とサイズで描画します。
     * @param identifier 描画するテクスチャのIdentifier
     * @param x 描画開始X座標 (ピクセル)
     * @param y 描画開始Y座標 (ピクセル)
     * @param width 描画幅 (ピクセル)
     * @param height 描画高さ (ピクセル)
     * @param u テクスチャのU座標 (0.0-1.0)
     * @param v テクスチャのV座標 (0.0-1.0)
     * @param uWidth テクスチャのU方向の幅 (ピクセル)
     * @param vHeight テクスチャのV方向の高さ (ピクセル)
     * @param textureWidth テクスチャの実際の幅 (ピクセル)
     * @param textureHeight テクスチャの実際の高さ (ピクセル)
     * @param rotation 回転角度 (ラジアン)
     */
    fun drawTexture(
        identifier: Identifier,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        u: Float = 0f,
        v: Float = 0f,
        uWidth: Int,
        vHeight: Int,
        textureWidth: Int,
        textureHeight: Int,
        rotation: Float = 0f,
    ) {
        // Check if the texture exists before attempting to d
        if (client.textureManager.getTexture(identifier) == null) {
            return
        }

        pushState()
        if (rotation != 0f) {
            translate(x + width / 2, y + height / 2)
            matrixStack.rotate(rotation)
            translate(-(x + width / 2), -(y + height / 2))
        }

        context.drawTexture(
            RenderPipelines.GUI_TEXTURED,
            identifier,
            x.toInt(),
            y.toInt(),
            width,
            height,
            u.toInt(),
            v.toInt(),
            uWidth,
            vHeight,
            textureWidth,
            textureHeight,
        )
        popState()
    }

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

    // drawLineは、新しい太い線分描画ロジックの簡略化のために残します。
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
     * 2点間に太さを持つ線分を四角形として描画します。
     * このメソッドは、drawTriangleやdrawCircleの辺描画に使用されます。
     *
     * @param x1 始点 X座標
     * @param y1 始点 Y座標
     * @param x2 終点 X座標
     * @param y2 終点 Y座標
     * @param color ARGB形式の色
     * @param size 枠線の太さ (ピクセル)
     */
    private fun drawThickLineSegment(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int,
        size: Int,
    ) {
        val halfSize = size / 2.0f

        // 線分のベクトル
        val dx = x2 - x1
        val dy = y2 - y1
        val length = MathHelper.sqrt(dx * dx + dy * dy)

        if (length < 1e-6) return // 長さが0に近い場合は描画しない

        // 単位法線ベクトル (線分に対して垂直)
        val nx = -dy / length
        val ny = dx / length

        // 太さの半分をかけたオフセットベクトル
        val offsetX = nx * halfSize
        val offsetY = ny * halfSize

        // 太さを持つ線分（四角形）の4つの頂点
        val p1x = x1 + offsetX
        val p1y = y1 + offsetY

        val p2x = x2 + offsetX
        val p2y = y2 + offsetY

        val p3x = x2 - offsetX
        val p3y = y2 - offsetY

        val p4x = x1 - offsetX
        val p4y = y1 - offsetY

        // fillQuad で太さを持った四角形を描画
        fillQuad(
            p1x,
            p1y,
            p2x,
            p2y,
            p3x,
            p3y,
            p4x,
            p4y,
            color,
        )
    }

    /**
     * 三角形の枠を指定した太さで描画します。
     * 各辺を太さを持った線分（四角形）として描画することで、角が重なり合って結合されます。
     *
     * @param x1 頂点1 X座標
     * @param y1 頂点1 Y座標
     * @param x2 頂点2 X座標
     * @param y2 頂点2 Y座標
     * @param x3 頂点3 X座標
     * @param y3 頂点3 Y座標
     * @param color ARGB形式の色
     * @param size 枠線の太さ (ピクセル)
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
        // 各辺を太さを持った線分（四角形）として描画
        drawThickLineSegment(x1, y1, x2, y2, color, size)
        drawThickLineSegment(x2, y2, x3, y3, color, size)
        drawThickLineSegment(x3, y3, x1, y1, color, size)
    }

    /**
     * 半径に応じて、円を近似するために使用するセグメント数（頂点数）を計算します。
     * 最小値は4、最大値は256です。
     */
    private fun calculateSegments(radius: Float): Int {
        val minSegments = 4
        val maxSegments = 256

        val segments = radius.roundToInt()

        return MathHelper.clamp(segments, minSegments, maxSegments)
    }

    fun fillCircle(
        cx: Int,
        cy: Int,
        radius: Int,
        color: Int,
    ) {
        fillCircle(cx.toFloat(), cy.toFloat(), radius.toFloat(), color)
    }

    fun drawCircle(
        cx: Int,
        cy: Int,
        radius: Int,
        color: Int,
    ) {
        drawCircle(cx.toFloat(), cy.toFloat(), radius.toFloat(), color)
    }

    /**
     * 円（真円）を指定した色で塗りつぶし描画します。
     * 半径に応じて動的に決定された多数の三角形（トライアングルファン）で円を近似して描画します。
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
        // 半径に基づいてセグメント数を動的に決定
        val segments = calculateSegments(radius)
        val twoPi = 2.0 * Math.PI

        // 現在の行列を保存
        pushState()

        // 描画の中心を (cx, cy) に移動
        translate(cx, cy)

        // 中心点 (0, 0) と円周上の2点を使って三角形を順次描画
        for (i in 0 until segments) {
            val angle1 = (i.toFloat() / segments.toFloat() * twoPi).toFloat()
            val angle2 = ((i.toFloat() + 1f) / segments.toFloat() * twoPi).toFloat()

            // 頂点1 (中心 - 変換後)
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
     * 半径に応じて動的に決定された多数の太い線分で円を近似して描画します。
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
        // 半径に基づいてセグメント数を動的に決定
        val segments = calculateSegments(radius)
        val twoPi = 2.0 * Math.PI

        var prevX = cx + radius
        var prevY = cy

        // 円周上の点を結んで太い線分を描画
        for (i in 1..segments) {
            val angle = (i.toFloat() / segments.toFloat() * twoPi).toFloat()

            // 円周上の現在の点
            val currentX = cx + MathHelper.cos(angle) * radius
            val currentY = cy + MathHelper.sin(angle) * radius

            // 前の点と現在の点を結んで太い線分を描画
            drawThickLineSegment(prevX, prevY, currentX, currentY, color, size)

            // 現在の点を次の線の始点として保存
            prevX = currentX
            prevY = currentY
        }
    }

    /**
     * 四角形（4つの任意の頂点）を指定した色で塗りつぶし描画します。
     * @param x1 頂点1 X座標
     * @param y1 頂点1 Y座標
     * @param x2 頂点2 X座標
     * @param y2 頂点2 Y座標
     * @param x3 頂点3 X座標
     * @param y3 頂点3 Y座標
     * @param x4 頂点4 X座標
     * @param y4 頂点4 Y座標
     * @param color ARGB形式の色 (0xAARRGGBB)
     */
    fun fillQuad(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        x3: Float,
        y3: Float,
        x4: Float,
        y4: Float,
        color: Int,
    ) {
        val pose = Matrix3x2f(context.matrices)
        val scissor = context.scissorStack.peekLast()
        context.state.addSimpleElement(
            QuadrilateralRenderState(
                RenderPipelines.GUI,
                TextureSetup.empty(),
                pose,
                x1,
                y1,
                x2,
                y2,
                x3,
                y3,
                x4,
                y4,
                color,
                scissor,
            ),
        )
    }

    // --- Rectangle (Rect) ---

    /**
     * 長方形（左上隅と右下隅で定義）を指定した色で塗りつぶし描画します。
     * @param x1 左上隅 X座標
     * @param y1 左上隅 Y座標
     * @param x2 右下隅 X座標
     * @param y2 右下隅 Y座標
     * @param color ARGB形式の色 (0xAARRGGBB)
     */
    fun fillRect(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        color: Int,
    ) {
        val pose = Matrix3x2f(context.matrices)
        val scissor = context.scissorStack.peekLast()
        context.state.addSimpleElement(
            RectangleRenderState(
                RenderPipelines.GUI,
                TextureSetup.empty(),
                pose,
                x1,
                y1,
                x2,
                y2,
                color,
                scissor,
            ),
        )
    }

    data class DisplayPos(
        val x: Double,
        val y: Double,
    )

    fun textWidth(text: String): Int = MinecraftClient.getInstance().textRenderer.getWidth(text)

    fun fontHeight(): Int = MinecraftClient.getInstance().textRenderer.fontHeight
}
