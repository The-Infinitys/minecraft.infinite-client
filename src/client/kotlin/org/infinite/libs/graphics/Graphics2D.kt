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
import org.infinite.utils.average
import org.infinite.utils.rendering.drawBorder
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fStack
import org.joml.Vector2d
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
        val gx = average(x1, x2, x3)
        val gy = average(y1, y2, y3)
        val vec1 = Vector2d(x1 - gx, y1 - gy).normalize()
        val vec2 = Vector2d(x2 - gx, y2 - gy).normalize()
        val vec3 = Vector2d(x3 - gx, y3 - gy).normalize()
        val outerX1 = x1 + vec1.x * size / 2
        val outerY1 = y1 + vec1.y * size / 2
        val outerX2 = x2 + vec2.x * size / 2
        val outerY2 = y2 + vec2.y * size / 2
        val outerX3 = x3 + vec3.x * size / 2
        val outerY3 = y3 + vec3.y * size / 2
        val innerX1 = x1 - vec1.x * size / 2
        val innerY1 = y1 - vec1.y * size / 2
        val innerX2 = x2 - vec2.x * size / 2
        val innerY2 = y2 - vec2.y * size / 2
        val innerX3 = x3 - vec3.x * size / 2
        val innerY3 = y3 - vec3.y * size / 2
        fillQuad(outerX1, outerY1, innerX1, innerY1, innerX2, innerY2, outerX2, outerY2, color)
        fillQuad(outerX2, outerY2, innerX2, innerY2, innerX3, innerY3, outerX3, outerY3, color)
        fillQuad(outerX3, outerY3, innerX3, innerY3, innerX1, innerY1, outerX1, outerY1, color)
    }

    private fun fillQuad(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        x3: Double,
        y3: Double,
        x4: Double,
        y4: Double,
        color: Int,
    ) {
        fillQuad(
            x1.toFloat(),
            y1.toFloat(),
            x2.toFloat(),
            y2.toFloat(),
            x3.toFloat(),
            y3.toFloat(),
            x4.toFloat(),
            y4.toFloat(),
            color,
        )
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
        size: Int = 1,
    ) {
        drawCircle(cx.toFloat(), cy.toFloat(), radius.toFloat(), color, size)
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
        val segments = calculateSegments(radius)
        val twoPi = 2.0 * Math.PI
        val halfSize = size / 2.0f
        val outerRadius = radius + halfSize
        val innerRadius = radius - halfSize
        var prevOuterX = cx + outerRadius
        var prevOuterY = cy
        var prevInnerX = cx + innerRadius
        var prevInnerY = cy
        for (i in 1..segments) {
            val angle = (i.toFloat() / segments.toFloat() * twoPi).toFloat()
            val outerX = cx + MathHelper.cos(angle) * outerRadius
            val outerY = cy + MathHelper.sin(angle) * outerRadius
            val innerX = cx + MathHelper.cos(angle) * innerRadius
            val innerY = cy + MathHelper.sin(angle) * innerRadius
            fillQuad(
                prevOuterX,
                prevOuterY,
                prevInnerX,
                prevInnerY,
                innerX,
                innerY,
                outerX,
                outerY,
                color,
            )
            prevOuterX = outerX
            prevOuterY = outerY
            prevInnerX = innerX
            prevInnerY = innerY
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
    // ... (imports remain the same)

// ... (class definition and properties remain the same)

// ----------------------------------------------------------------------
// MatrixState/Transform 状態管理 (Canvasの save/restore に相当)
// ----------------------------------------------------------------------

// ... (pushState, popState, translate, scale メソッドはそのまま)

    /**
     * テクスチャを指定した位置とサイズで描画します。
     * * @param identifier 描画するテクスチャのIdentifier
     * @param x 描画開始X座標 (ピクセル)
     * @param y 描画開始Y座標 (ピクセル)
     * @param width 描画幅 (ピクセル)
     * @param height 描画高さ (ピクセル)
     * @param u テクスチャのU座標（テクスチャピクセル単位） - **注: 0.0-1.0 ではなく、ピクセル単位での開始U座標に変更**
     * @param v テクスチャのV座標（テクスチャピクセル単位） - **注: 0.0-1.0 ではなく、ピクセル単位での開始V座標に変更**
     * @param uWidth テクスチャのU方向の幅 (ピクセル)
     * @param vHeight テクスチャのV方向の高さ (ピクセル)
     * @param textureWidth テクスチャの実際の幅 (ピクセル)
     * @param textureHeight テクスチャの実際の高さ (ピクセル)
     * @param rotation 回転角度 (ラジアン)
     * @param color 適用する色 (0xAARRGGBB)。デフォルトは白 (0xFFFFFFFF)。
     */
    fun drawRotatedTexture(
        identifier: Identifier,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rotation: Float = 0f,
        color: Int = -1,
        u: Float = 0f,
        v: Float = 0f,
        uWidth: Float = 1f,
        vHeight: Float = 1f,
        textureWidth: Float = 1f,
        textureHeight: Float = 1f,
    ) {
        // テクスチャが存在するかどうかを確認
        if (client.textureManager.getTexture(identifier) == null) {
            return
        }

        // UV座標を0.0-1.0の範囲に正規化
        val uNormalized1 = u / textureWidth
        val vNormalized1 = v / textureHeight
        val uNormalized2 = (u + uWidth) / textureWidth
        val vNormalized2 = (v + vHeight) / textureHeight

        pushState()
        // 回転が必要な場合、中心に移動して回転し、元に戻す
        if (rotation != 0f) {
            // 回転の中心を計算
            val centerX = x + width / 2f
            val centerY = y + height / 2f

            // 1. 中心に移動
            translate(centerX, centerY)
            // 2. 回転
            matrixStack.rotate(rotation)
            // 3. 元の座標系に戻す
            translate(-centerX, -centerY)
        }

        // 現在の変換行列を取得し、描画状態として登録
        val pose = Matrix3x2f(context.matrices)
        val scissor = context.scissorStack.peekLast()
        val gpuTextureView =
            this.client.textureManager
                .getTexture(identifier)
                .getGlTextureView()

        // TexturedQuadRenderState を使用して描画要素を追加
        context.state.addSimpleElement(
            TexturedQuadRenderState(
                RenderPipelines.GUI_TEXTURED,
                TextureSetup.of(gpuTextureView),
                pose,
                x,
                y,
                x + width,
                y + height,
                uNormalized1,
                uNormalized2,
                vNormalized1,
                vNormalized2,
                color,
                scissor,
            ),
        )

        popState()
    }

    fun textWidth(text: String): Int = MinecraftClient.getInstance().textRenderer.getWidth(text)

    fun fontHeight(): Int = MinecraftClient.getInstance().textRenderer.fontHeight

    fun drawRotatedTexture(
        identifier: Identifier,
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        rotation: Float,
    ) {
        drawRotatedTexture(identifier, x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat(), rotation)
    }

    fun drawLine(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Int,
        size: Int = 1,
    ): Unit = drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), color, size)

    fun drawLine(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: Int,
        size: Int = 1,
    ): Unit = drawLine(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), color, size)
}
