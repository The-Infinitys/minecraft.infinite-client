package org.infinite.libs.graphics

import com.mojang.blaze3d.buffers.GpuBufferSlice
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.Perspective
import net.minecraft.client.render.Camera
import net.minecraft.client.render.RenderTickCounter
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.ObjectAllocator
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import org.infinite.libs.client.aim.camera.CameraRoll
import org.infinite.libs.graphics.render.RenderResources
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.utils.rendering.Line
import org.infinite.utils.rendering.Quad
import org.joml.Matrix4f
import org.joml.Vector4f

// ... (コンストラクタとプロパティは変更なし) ...
class Graphics3D(
    val allocator: ObjectAllocator,
    val tickCounter: RenderTickCounter,
    val renderBlockOutline: Boolean,
    val camera: Camera,
    positionMatrix: Matrix4f,
    val projectionMatrix: Matrix4f,
    val matrix4f2: Matrix4f,
    val gpuBufferSlice: GpuBufferSlice,
    val vector4f: Vector4f,
    val bl: Boolean,
) {
    val client: MinecraftClient = MinecraftClient.getInstance()

    val immediate: VertexConsumerProvider.Immediate =
        client.bufferBuilders.entityVertexConsumers

    val matrixStack = MatrixStack()

    val tickProgress: Float = tickCounter.getTickProgress(false)

    init {
        matrixStack.multiplyPositionMatrix(positionMatrix)
    }

    // ----------------------------------------------------------------------
    // MatrixStack操作メソッド (変更なし)
    // ----------------------------------------------------------------------
    fun translate(
        x: Double,
        y: Double,
        z: Double,
    ) {
        matrixStack.translate(x, y, z)
    }

    fun pushMatrix() {
        matrixStack.push()
    }

    fun popMatrix() {
        matrixStack.pop()
    }

    // ----------------------------------------------------------------------
    // 描画ヘルパーメソッド (RenderUtilsのコア機能を呼び出すラッパー)
    // ----------------------------------------------------------------------

    /**
     * 単一の Box を線で描画します。
     * Graphics3Dが自動で VertexConsumer を取得し、RenderUtilsのコア関数に渡します。
     */
    fun renderLinedBox(
        box: Box,
        color: Int,
        isOverDraw: Boolean = false,
    ) {
        val layer = RenderResources.renderLinedLayer(isOverDraw)
        val buffer = immediate.getBuffer(layer)

        RenderUtils.renderLinedBox(matrixStack, box, color, buffer)
    }

    /**
     * 複数の Box をそれぞれ異なる色で線描画します。
     */
    fun renderLinedColorBoxes(
        boxes: List<RenderUtils.ColorBox>,
        isOverDraw: Boolean = false,
    ) {
        val layer = RenderResources.renderLinedLayer(isOverDraw)
        val buffer = immediate.getBuffer(layer)
        RenderUtils.renderLinedColorBoxes(matrixStack, boxes, buffer)
    }

    /**
     * 複数の Box をそれぞれ異なる色で塗りつぶし描画します。
     */
    fun renderSolidColorBoxes(
        boxes: List<RenderUtils.ColorBox>, // 仮定: 塗りつぶし用の色付きBoxは RenderUtils.ColorBox 型とします。
        isOverDraw: Boolean = false,
    ) {
        val layer = RenderResources.renderSolidLayer(isOverDraw)
        val buffer = immediate.getBuffer(layer)
        RenderUtils.renderSolidColorBoxes(matrixStack, boxes, buffer)
    }

    fun renderSolidQuads(
        quads: List<Quad>,
        isOverDraw: Boolean = false,
    ) {
        val layer = RenderResources.renderSolidLayer(isOverDraw)
        val buffer = immediate.getBuffer(layer)
        RenderUtils.renderSolidQuads(matrixStack, quads, buffer)
    }

    fun renderLinedLines(
        lines: List<Line>,
        isOverDraw: Boolean = false,
    ) {
        val layer = RenderResources.renderLinedLayer(isOverDraw)
        val buffer = immediate.getBuffer(layer)
        RenderUtils.renderLinedLines(matrixStack, lines, buffer)
    }

    /**
     * 2点間に直線を描画します (ワールド座標基準)。
     */
    fun renderLine(
        start: Vec3d,
        end: Vec3d,
        color: Int,
        isOverDraw: Boolean = false,
    ) {
        val layer = RenderResources.renderLinedLayer(isOverDraw)
        val buffer = immediate.getBuffer(layer)
        RenderUtils.renderLine(matrixStack, start, end, color, buffer)
    }

    /**
     * ワールド座標 (Vec3d) を画面座標 (DisplayPos) に変換します。
     * ターゲットがカメラの後ろにある場合や、画面外にある場合は null を返します。
     */
    fun toDisplayPos(targetPos: Vec3d): Graphics2D.DisplayPos? {
        val camera = this.camera
        val window = client.window
        // Graphics2Dが使用するのと同じスケーリングされた幅/高さを取得
        val scaledWidth = window.scaledWidth.toDouble()
        val scaledHeight = window.scaledHeight.toDouble()

        // 1. ワールド座標から相対座標 (View Space) へ
        val relX = (targetPos.x - camera.pos.x).toFloat()
        val relY = (targetPos.y - camera.pos.y).toFloat()
        val relZ = (targetPos.z - camera.pos.z).toFloat()

        // 4Dベクトル (x, y, z, w=1.0)
        val targetVector = Vector4f(relX, relY, relZ, 1.0f)

        // 2. ビュープロジェクション行列を合成し、ベクトルを変換
        val modelViewMatrix = matrixStack.peek().positionMatrix
        val viewProjectionMatrix = Matrix4f(projectionMatrix).mul(modelViewMatrix)
        targetVector.mul(viewProjectionMatrix)

        // 3. W値による遠近補正 (Perspective Divide)
        val w = targetVector.w

        // w <= 0 は、カメラの後ろにあることを意味します
        if (w <= 0.05f) {
            return null
        }

        // NDC (Normalized Device Coordinates) への変換: [-1.0, 1.0]
        val ndcX = targetVector.x / w
        val ndcY = targetVector.y / w

        // NDC範囲 [-1.0, 1.0] の外側にある場合は非表示
        if (ndcX < -1.0f || ndcX > 1.0f || ndcY < -1.0f || ndcY > 1.0f) {
            return null
        }

        // 4. NDCから画面座標 (ピクセル) への変換
        val x = (ndcX + 1.0) * 0.5 * scaledWidth
        val y = (1.0 - ndcY) * 0.5 * scaledHeight // Y軸を反転

        return Graphics2D.DisplayPos(x, y)
    }

    private fun tracerOrigin(partialTicks: Float): Vec3d? {
        val yaw: Double = client.player?.getYaw(partialTicks)?.toDouble() ?: return null
        val pitch: Double = client.player?.getPitch(partialTicks)?.toDouble() ?: return null
        var start: Vec3d =
            CameraRoll(yaw, pitch).vec().multiply(5.0)
        if (client.options
                .perspective == Perspective.THIRD_PERSON_FRONT
        ) {
            start = start.negate()
        }

        return start
    }

    fun renderTracer(
        end: Vec3d,
        color: Int,
        isOverDraw: Boolean,
    ) {
        val layer = RenderResources.renderLinedLayer(isOverDraw)
        val buffer = immediate.getBuffer(layer)
        val start = tracerOrigin(tickProgress) ?: return
        val offset: Vec3d = RenderUtils.cameraPos().negate()
        RenderUtils.renderLine(matrixStack, start, end.add(offset), color, buffer)
    }

    fun render() {
        immediate.draw()
    }
}
