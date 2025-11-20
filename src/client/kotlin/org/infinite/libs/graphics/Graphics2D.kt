package org.infinite.libs.graphics

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.client.render.item.KeyedItemRenderState
import net.minecraft.client.texture.TextureSetup
import net.minecraft.item.ItemDisplayContext
import net.minecraft.item.ItemStack
import net.minecraft.text.StringVisitable
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.Language
import net.minecraft.util.crash.CrashException
import net.minecraft.util.crash.CrashReport
import net.minecraft.util.math.ColorHelper
import net.minecraft.util.math.MathHelper
import org.infinite.libs.client.player.ClientInterface
import org.infinite.utils.average
import org.infinite.utils.rendering.drawBorder
import org.infinite.utils.rendering.getRainbowColor
import org.infinite.utils.rendering.transparent
import org.joml.Matrix3x2f
import org.joml.Matrix3x2fStack
import org.joml.Vector2d
import kotlin.math.PI
import kotlin.math.roundToInt

/**
 * MinecraftのGUI描画コンテキストを保持し、カスタム2Dレンダリングを実行するためのクラス。
 * JavaScriptのCanvas 2D APIのようなメソッドを提供します。
 *
 * @property context 描画に必要なGUIコンテキスト
 * @property tickCounter レンダリングティックの情報
 */
class Graphics2D(
    private val context: DrawContext,
    val tickCounter: RenderTickCounter,
) : ClientInterface() {
    val matrixStack: Matrix3x2fStack = context.matrices

    /** 部分ティックの進行度 */
    val tickProgress: Float = tickCounter.getTickProgress(false)

    /** 画面の幅 */
    val width: Int
        get() = context.scaledWindowWidth
    val height: Int
        get() = context.scaledWindowHeight

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

    fun rotate(ang: Float) {
        matrixStack.rotate(ang)
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

    fun drawBorder(
        x: Double,
        y: Double,
        width: Double,
        height: Double,
        color: Int,
        size: Double = 1.0,
    ) {
        rect(x, y, x + width, y + size, color)
        rect(x + width - size, y + size, x + width, y + height - size, color)
        rect(x, y + height - size, x + width, y + height, color)
        rect(x, y + size, x + size, y + height - size, color)
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
        text: String,
        x: Double,
        y: Double,
        color: Int,
        shadow: Boolean = true,
    ): Unit = drawText(text, x.toFloat(), y.toFloat(), color, shadow)

    fun drawText(
        text: String,
        x: Float,
        y: Float,
        color: Int,
        shadow: Boolean = true,
    ) {
        val orderedText = Language.getInstance().reorder(StringVisitable.plain(text))
        val backgroundColor = 0
        val clipBounds = context.scissorStack.peekLast()
        val state =
            TextRenderState(
                client.textRenderer,
                orderedText,
                matrixStack,
                x,
                y,
                color,
                backgroundColor,
                shadow,
                clipBounds,
            )
        context.state.addText(state)
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
        quad(outerX1, outerY1, innerX1, innerY1, innerX2, innerY2, outerX2, outerY2, color)
        quad(outerX2, outerY2, innerX2, innerY2, innerX3, innerY3, outerX3, outerY3, color)
        quad(outerX3, outerY3, innerX3, innerY3, innerX1, innerY1, outerX1, outerY1, color)
    }

    fun quad(
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
        quad(
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
     * 半径に応じて動的に決定された多数の四角形（Quad）で円を近似して描画します。
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
        var segments = calculateSegments(radius)
        // fillQuadを使用するため、セグメント数を偶数に調整 (常に2セグメントを1つの四角形として処理)
        if (segments % 2 != 0) {
            segments++
        }
        val twoPi = 2.0 * Math.PI

        // 現在の行列を保存
        pushState()

        // 描画の中心を (cx, cy) に移動
        translate(cx, cy)

        // 中心点 (0, 0) を基準に計算
        val center = DisplayPos(0.0, 0.0)

        // 2セグメントごとに1つの四角形を順次描画
        for (i in 0 until segments step 2) {
            val angle1 = (i.toFloat() / segments.toFloat() * twoPi).toFloat()
            val angle2 = ((i.toFloat() + 1f) / segments.toFloat() * twoPi).toFloat()
            val angle3 = ((i.toFloat() + 2f) / segments.toFloat() * twoPi).toFloat()

            // 頂点1 (円周上)
            val p1X = MathHelper.cos(angle1) * radius
            val p1Y = MathHelper.sin(angle1) * radius
            // 頂点2 (円周上)
            val p2X = MathHelper.cos(angle2) * radius
            val p2Y = MathHelper.sin(angle2) * radius
            // 頂点3 (円周上)
            val p3X = MathHelper.cos(angle3) * radius
            val p3Y = MathHelper.sin(angle3) * radius

            quad(
                p1X,
                p1Y,
                p2X,
                p2Y,
                p3X,
                p3Y,
                center.x.toFloat(),
                center.y.toFloat(),
                color,
            )
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
            quad(
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
    fun quad(
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
    fun rect(
        x1: Double,
        y1: Double,
        x2: Double,
        y2: Double,
        color: Int,
    ): Unit = rect(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), color)

    /**
     * 長方形（左上隅と右下隅で定義）を指定した色で塗りつぶし描画します。
     * @param x1 左上隅 X座標
     * @param y1 左上隅 Y座標
     * @param x2 右下隅 X座標
     * @param y2 右下隅 Y座標
     * @param color ARGB形式の色 (0xAARRGGBB)
     */
    fun rect(
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

    /**
     * 頂点のリストに基づいて、太さのある多角線（ポリライン）を描画します。
     * 線分同士の接続点（ジョイント）は、線分の四角形が重ならないようベベルジョイントで結合されます。
     * 終端は丸く処理されます。
     *
     * @param lines 頂点のリスト。Pair<Float, Float> は (X座標, Y座標) を表します。
     * @param color ARGB形式の色 (0xAARRGGBB)
     * @param size 線の太さ (ピクセル)。線分幅全体を表します。
     */
    fun renderLines(
        lines: List<Pair<Float, Float>>,
        color: Int,
        size: Float = 1f,
    ) {
        if (lines.size < 2) return

        val halfSize = size / 2f

        // 各頂点における線分に垂直な単位ベクトル（外側向き）を計算
        val normals = mutableListOf<Pair<Float, Float>>()
        for (i in lines.indices) {
            val (_, _) = lines[i]
            val prevPoint = if (i > 0) lines[i - 1] else null
            val nextPoint = if (i < lines.size - 1) lines[i + 1] else null

            var normalX: Float
            var normalY: Float

            // 始点
            if (prevPoint == null) {
                // 最初の線分に垂直なベクトルを計算
                val (x1, y1) = lines[i]
                val (x2, y2) = lines[i + 1]
                val dx = x2 - x1
                val dy = y2 - y1
                val length = MathHelper.sqrt(dx * dx + dy * dy)
                normalX = -(dy / length) // 垂直ベクトルのX (-dy)
                normalY = dx / length // 垂直ベクトルのY (dx)
            } else if (nextPoint == null) {
                // 最後の線分に垂直なベクトルを計算
                val (x1, y1) = lines[i - 1]
                val (x2, y2) = lines[i]
                val dx = x2 - x1
                val dy = y2 - y1
                val length = MathHelper.sqrt(dx * dx + dy * dy)
                normalX = -(dy / length) // 垂直ベクトルのX (-dy)
                normalY = dx / length // 垂直ベクトルのY (dx)
            } else {
                // 前後の線分の方向ベクトル
                val (px, py) = prevPoint
                val (cx, cy) = lines[i]
                val (nx, ny) = nextPoint

                val v1x = cx - px // 前の線分の方向
                val v1y = cy - py
                val v2x = nx - cx // 次の線分の方向
                val v2y = ny - cy

                val length1 = MathHelper.sqrt(v1x * v1x + v1y * v1y)
                val length2 = MathHelper.sqrt(v2x * v2x + v2y * v2y)

                // 単位方向ベクトル
                val u1x = v1x / length1
                val u1y = v1y / length1
                val u2x = v2x / length2
                val u2y = v2y / length2

                // 2つの線分の単位方向ベクトルの和
                val sumX = u1x + u2x
                val sumY = u1y + u2y
                val sumLength = MathHelper.sqrt(sumX * sumX + sumY * sumY)

                // 角度の二等分線に沿った単位ベクトル
                val bisectorX = if (sumLength != 0f) sumX / sumLength else u1x
                val bisectorY = if (sumLength != 0f) sumY / sumLength else u1y

                // 垂直ベクトルの計算 (時計回りに90度回転: (y, -x) または (-y, x))
                // 外側の単位法線ベクトル (ジョイントを正確に計算するためのベクトル)
                val outerNormalX = bisectorY
                val outerNormalY = -bisectorX

                // マイターのスケールを計算 (角度が急なほど大きくなる)
                // 角度の二等分線と線分の法線との間のコサイン角: cos(theta/2) = dot(outerNormal, u1_perp)
                val perp1X = -u1y // u1に垂直なベクトル
                val perp1Y = u1x
                val cosThetaHalf = (outerNormalX * perp1X + outerNormalY * perp1Y)

                // 非常に小さな値で割るのを防ぐ
                val scale =
                    if (MathHelper.abs(cosThetaHalf) < 0.0001f) {
                        1.0f // マイターが適用されない直線の場合
                    } else {
                        1.0f / cosThetaHalf
                    }

                // クロス積 (外積) を計算し、ジョイントが凸（外側）か凹（内側）かを判定
                // u1 から u2 への回転方向
                val cross = u1x * u2y - u1y * u2x

                // 外側 (凸) のジョイントの場合、法線ベクトルとスケールを適用
                if (cross < 0) { // 凸の場合 (外側ジョイント)
                    normalX = outerNormalX * scale
                    normalY = outerNormalY * scale
                } else { // 凹の場合 (内側ジョイント) -> ベベルジョイントで処理
                    // 凹ジョイントでは、正確なマイター計算は不要で、ベベル（元の法線）を使用
                    // ここでは、外側と内側で異なる頂点座標を計算する必要がある
                    // 簡単のため、凹の場合は前後の線分の法線の中間を使用するか、単純なベベルを採用します。
                    // ベベルジョイントの頂点座標は、線分ごとに独立して計算し、共通の2つの内側頂点と、
                    // 4つの外側頂点（前後の線分から2つずつ）を使って2つの三角形（Quadの半分）で埋めます。

                    // 複雑になるため、ここではマイタージョイントのスケールを使用し、凹ジョイントは内側に描画することで簡略化します。
                    // このコードは凸ジョイントの計算を簡略化したものです。凹ジョイントは別途処理が必要です。
                    // 簡単化のため、凹ジョイントでは単純な平均法線を使用
                    val perpSumX = -(u1y + u2y)
                    val perpSumY = u1x + u2x
                    val perpSumLength = MathHelper.sqrt(perpSumX * perpSumX + perpSumY * perpSumY)
                    normalX = if (perpSumLength != 0f) perpSumX / perpSumLength else perp1X
                    normalY = if (perpSumLength != 0f) perpSumY / perpSumLength else perp1Y
                }
            }
            normals.add(normalX to normalY)
        }

        // 2. 線分（四角形）とジョイントの描画
        for (i in 0 until lines.size - 1) {
            val (x1, y1) = lines[i]
            val (x2, y2) = lines[i + 1]

            val (n1x, n1y) = normals[i]
            val (n2x, n2y) = normals[i + 1]

            // 頂点iにおける外側と内側の座標
            val inner1X = x1 - n1x * halfSize
            val inner1Y = y1 - n1y * halfSize
            val outer1X = x1 + n1x * halfSize
            val outer1Y = y1 + n1y * halfSize

            // 頂点i+1における外側と内側の座標
            val inner2X = x2 - n2x * halfSize
            val inner2Y = y2 - n2y * halfSize
            val outer2X = x2 + n2x * halfSize
            val outer2Y = y2 + n2y * halfSize

            // 線分 (i から i+1) を四角形として描画
            // 頂点の順序は、GPUでの描画順序（巻き順）に合わせて調整します。
            quad(outer1X, outer1Y, inner1X, inner1Y, inner2X, inner2Y, outer2X, outer2Y, color)
        }
    }

    /**
     * 指定された中心、半径、太さで円弧を描画します。
     * 円弧は下部 (Y軸正方向, 270度/1.5*PI) から時計回りに始まります。
     */
    fun drawArc(
        cx: Float,
        cy: Float,
        radius: Float,
        startAngle: Float, // 0 から 2*PI までの開始角度
        endAngle: Float, // 0 から 2*PI までの完了角度
        thickness: Int, // 線の太さ
        color: Int,
    ) {
        // 円弧の中心線となる頂点リスト
        val arcPoints = mutableListOf<Pair<Float, Float>>()
        val segments = calculateSegments(radius)

        // 描画するセグメント数 (endAngle に比例)
        val segmentsToDraw = (segments * (endAngle / (2 * PI))).toInt()

        // セグメントごとの角度変化量
        // endAngle までに segmentsToDraw 分のステップで到達させる
        val angleStep = endAngle / segmentsToDraw.toFloat()

        // 開始点 (クールダウンバーの始点)
        var currentAngle = startAngle

        // 最初の点を追加
        val startX = cx + MathHelper.cos(currentAngle) * radius
        val startY = cy + MathHelper.sin(currentAngle) * radius
        arcPoints.add(startX to startY)

        // 中間点を計算し、リストに追加
        for (i in 1..segmentsToDraw) {
            currentAngle = startAngle + i * angleStep

            // 描画する円弧上の頂点の座標を計算
            val x = cx + MathHelper.cos(currentAngle) * radius
            val y = cy + MathHelper.sin(currentAngle) * radius

            arcPoints.add(x to y)
        }
        renderLines(arcPoints, color, thickness.toFloat())
    }

    fun drawItem(
        stack: ItemStack,
        x: Int,
        y: Int,
        alpha: Float = 1.0f,
    ) {
        drawItem(stack, x.toFloat(), y.toFloat(), alpha)
    }

    fun drawItem(
        stack: ItemStack,
        x: Float,
        y: Float,
        alpha: Float = 1.0f,
    ) {
        if (stack.isEmpty) return
        val size = 16f
        val keyedItemRenderState = KeyedItemRenderState()
        this.client.itemModelManager.clearAndUpdate(
            keyedItemRenderState,
            stack,
            ItemDisplayContext.GUI,
            world,
            player,
            0,
        )
        try {
            context.state.addItem(
                ItemRenderState(
                    stack.item.name.toString(),
                    Matrix3x2f(this.matrixStack),
                    keyedItemRenderState,
                    x,
                    y,
                    context.scissorStack.peekLast(),
                ),
            )
        } catch (throwable: Throwable) {
            val crashReport = CrashReport.create(throwable, "Rendering item")
            val crashReportSection = crashReport.addElement("Item being rendered")
            crashReportSection.add("Item Type") { stack.item.toString() }
            crashReportSection.add("Item Components") { stack.getComponents().toString() }
            crashReportSection.add("Item Foil") { stack.hasGlint().toString() }
            throw CrashException(crashReport)
        }

        // 個数の描画
        if (stack.count > 1) {
            val text = stack.count.toString()
            val textColor = ColorHelper.getArgb((alpha * 255).toInt(), 255, 255, 255)
            drawText(
                text,
                x + size - textWidth(text),
                y + size - fontHeight(),
                textColor,
                true,
            )
        }

        // 耐久値の描画
        if (stack.isDamageable && stack.damage > 0) {
            val progress = (stack.maxDamage - stack.damage).toFloat() / stack.maxDamage.toFloat()
            val barHeight = 2f
            val barY = y + size - barHeight
            val alphaInt = (alpha * 255).toInt()

            rect(x, barY, x + size, barY + barHeight, ColorHelper.getArgb(alphaInt, 0, 0, 0))
            // 耐久値の進捗バー
            val fillWidth = (size * progress).toInt()
            if (fillWidth > 0) {
                val color = getRainbowColor(progress * 0.3f).transparent(alphaInt)
                rect(x, barY, x + fillWidth, barY + barHeight, color)
            }
        }
    }

    fun drawItem(
        stack: ItemStack,
        x: Double,
        y: Double,
        alpha: Float = 1.0f,
    ) {
        drawItem(stack, x.toFloat(), y.toFloat(), alpha)
    }

    fun enableScissor(
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
    ) {
        context.enableScissor(x1, y1, x2, y2)
    }

    fun disableScissor() {
        context.disableScissor()
    }
}
