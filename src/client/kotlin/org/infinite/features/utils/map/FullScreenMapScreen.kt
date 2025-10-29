package org.infinite.features.utils.map

import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import net.minecraft.util.math.ColorHelper
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.rendering.transparent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.roundToInt

class FullScreenMapScreen(
    val mapFeature: MapFeature,
) : Screen(Text.of("Full Screen Map")) {
    private var zoom: Double = 1.0

    // マップの中心点となるワールド座標 (X, Z)
    private var centerX: Double = 0.0
    private var centerZ: Double = 0.0
    private val hyperMap: HyperMap
        get() = InfiniteClient.getFeature(HyperMap::class.java)!!

    private var dragStartX: Double = 0.0
    private var dragStartZ: Double = 0.0
    private var dragStartMouseX: Double = 0.0
    private var dragStartMouseY: Double = 0.0

    // --- 定数 ---
    private val ZOOM_STEP = 0.1
    private val MIN_ZOOM = 0.5
    private val MAX_ZOOM = 5.0
    private val BUTTON_WIDTH = 40
    private val BUTTON_HEIGHT = 20

    // 💡 プレイヤーの現在地をマップの中心として初期化 (コンストラクタ init ブロック)
    init {
        val player = client?.player
        if (player != null) {
            centerX = player.x
            centerZ = player.z
        }
    }

    // --- ウィジェットの追加 ---
    override fun init() {
        super.init()

        val buttonSpacing = 5
        var currentX = width - (BUTTON_WIDTH * 3 + buttonSpacing * 3)

        // 1. ズームアウト (-) ボタン
        val zoomOutButton =
            ButtonWidget.builder(Text.of("-")) {
                zoom = max(MIN_ZOOM, zoom - ZOOM_STEP)
            }.dimensions(currentX, buttonSpacing, BUTTON_WIDTH, BUTTON_HEIGHT).build()
        addDrawableChild(zoomOutButton)
        currentX += BUTTON_WIDTH + buttonSpacing

        // 2. ズームイン (+) ボタン
        val zoomInButton =
            ButtonWidget.builder(Text.of("+")) {
                zoom = min(MAX_ZOOM, zoom + ZOOM_STEP)
            }.dimensions(currentX, buttonSpacing, BUTTON_WIDTH, BUTTON_HEIGHT).build()
        addDrawableChild(zoomInButton)
        currentX += BUTTON_WIDTH + buttonSpacing

        // 3. 現在地へリセット (⌖) ボタン
        val centerButton =
            ButtonWidget.builder(Text.of("⌖")) {
                val p = client?.player
                if (p != null) {
                    // マップの中心をプレイヤーの現在地に戻す
                    centerX = p.x
                    centerZ = p.z
                    zoom = 1.0
                }
            }.dimensions(currentX, buttonSpacing, BUTTON_WIDTH, BUTTON_HEIGHT).build()
        addDrawableChild(centerButton)
    }

    // --- mouseClicked / mouseDragged / mouseScrolled のロジックは変更なし ---
    override fun mouseClicked(click: Click, doubled: Boolean): Boolean {
        if (click.button() == 0) { // 左クリック
            dragStartX = centerX
            dragStartZ = centerZ
            dragStartMouseX = click.x
            dragStartMouseY = click.y
        }
        return super.mouseClicked(click, doubled)
    }

    override fun mouseDragged(
        click: Click,
        offsetX: Double,
        offsetY: Double,
    ): Boolean {
        if (click.button() == 0) { // Left mouse button
            val screenWidth = width
            val screenHeight = height

            val totalMoveX = click.x() - dragStartMouseX
            val totalMoveY = click.y() - dragStartMouseY

            val mapBaseSize = max(screenWidth, screenHeight).toDouble()
            val mapSize = mapBaseSize * zoom
            val halfMapSize = mapSize / 2.0

            val featureRadius = hyperMap.radiusSetting.value.toDouble() * zoom

            val worldMoveX = (totalMoveX / halfMapSize) * featureRadius
            val worldMoveZ = (totalMoveY / halfMapSize) * featureRadius

            centerX = dragStartX - worldMoveX
            centerZ = dragStartZ - worldMoveZ

            return true
        }
        return super.mouseDragged(click, offsetX, offsetY)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        if (verticalAmount > 0) {
            zoom = min(MAX_ZOOM, zoom + ZOOM_STEP)
        } else if (verticalAmount < 0) {
            zoom = max(MIN_ZOOM, zoom - ZOOM_STEP)
        }
        return true
    }

    // --- render 関数の修正: コンパスをプレイヤーの向きに合わせて表示 ---
    override fun render(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        val graphics2D = Graphics2D(context, client!!.renderTickCounter)
        val player = client!!.player ?: return

        val screenWidth = width
        val screenHeight = height

        val mapBaseSize = max(screenWidth, screenHeight).toFloat()
        val mapSize = mapBaseSize * zoom
        val halfMapSize = mapSize / 2.0f

        val renderX = (screenWidth / 2.0f - halfMapSize).toInt()
        val renderY = (screenHeight / 2.0f - halfMapSize).toInt()

        // Render terrain
        renderTerrainFullScreen(graphics2D, renderX, renderY, mapSize.toInt())

        // Render entities
        renderEntitiesFullScreen(graphics2D, renderX, renderY, mapSize.toInt())

        // Draw player dot
        val featureRadius = hyperMap.radiusSetting.value.toDouble() * zoom
        val playerDx = player.x - centerX
        val playerDz = player.z - centerZ

        val scaledPlayerDx = playerDx / featureRadius * halfMapSize
        val scaledPlayerDz = playerDz / featureRadius * halfMapSize

        val playerDotRadius = 2
        val playerDotColor =
            InfiniteClient
                .theme()
                .colors.infoColor
                .transparent(255)

        graphics2D.fill(
            (screenWidth / 2.0 + scaledPlayerDx - playerDotRadius).toInt(),
            (screenHeight / 2.0 + scaledPlayerDz - playerDotRadius).toInt(),
            playerDotRadius * 2,
            playerDotRadius * 2,
            playerDotColor,
        )


        // 💡 方角表示 (左上にコンパクトに表示)
        val font = textRenderer
        val margin = 10
        val compassRadius = 20f // コンパスのサイズを縮小
        val arrowLength = compassRadius * 0.7f
        val arrowHeadSize = 4

        val northColor = InfiniteClient.theme().colors.errorColor
        val otherColor = InfiniteClient.theme().colors.foregroundColor

        // 1. プレイヤー座標の表示 (左上)
        var currentY = margin
        if (mapFeature.showPlayerCoordinates.value) {
            val p = client!!.player?:return
            val coordsText = Text.of("X: %.1f Y: %.1f Z: %.1f".format(p.x, p.y, p.z))
            graphics2D.drawText(
                coordsText.string,
                margin,
                currentY,
                otherColor,
                true,
            )
            currentY += font.fontHeight + 5
        }

        // 2. コンパスの描画
        val compassX = margin + compassRadius.toInt()
        val compassY = currentY + compassRadius.toInt()
        val compassSize = compassRadius.toInt() * 2

        // 背景円
        graphics2D.fill(
            compassX - compassRadius.toInt(),
            compassY - compassRadius.toInt(),
            compassSize,
            compassSize,
            0x55000000
        )

        // 中心点
        graphics2D.fill(
            compassX - 1,
            compassY - 1,
            2,
            2,
            otherColor
        )

        // 💡 プレイヤーの向きを取得し、コンパスを回転させて描画
        // Y軸の回転をラジアンに変換 (マイナスはMinecraftの座標系に合わせるため)
        val yawRad = -(player.yaw + 90) * (PI / 180.0)

        // 北 (N) の矢印の終点計算 (Z軸負方向)
        val endX = (compassX + sin(yawRad) * arrowLength).roundToInt()
        val endY = (compassY - cos(yawRad) * arrowLength).roundToInt()

        // 北 (N) の矢印
        graphics2D.drawLine(
            compassX,
            compassY,
            endX,
            endY,
            northColor,
            arrowHeadSize
        )
        // 文字の描画 (Nは常に上側)
        graphics2D.drawText(
            "N",
            (compassX - font.getWidth("N") / 2),
            compassY - compassRadius.toInt() - font.fontHeight - 2,
            northColor,
            true,
        )

        super.render(context, mouseX, mouseY, delta)
    }

    // --- renderTerrainFullScreen のロジック (テクスチャロード含む) ---
    private fun renderTerrainFullScreen(
        graphics2D: Graphics2D,
        renderX: Int,
        renderY: Int,
        mapSize: Int,
    ) {
        val client = graphics2D.client
        val player = client.player ?: return

        val centerBlockX = centerX.toInt()
        val centerBlockZ = centerZ.toInt()
        val centerChunkX = centerBlockX shr 4
        val centerChunkZ = centerBlockZ shr 4

        val featureRadius = hyperMap.radiusSetting.value.toDouble() * zoom
        val renderDistanceChunks = (featureRadius / 16.0).toInt() + 1

        val minChunkX = centerChunkX - renderDistanceChunks
        val maxChunkX = centerChunkX + renderDistanceChunks
        val minChunkZ = centerChunkZ - renderDistanceChunks
        val maxChunkZ = centerChunkZ + renderDistanceChunks
        val dimensionKey = MapTextureManager.dimensionKey

        val isUnderground = player.let { hyperMap.isUnderground(it.blockY) }
        val actualMode = if (isUnderground) HyperMap.Mode.Solid else hyperMap.mode.value

        val textureFileName =
            when (actualMode) {
                HyperMap.Mode.Flat -> "surface.png"
                HyperMap.Mode.Solid -> {
                    val sectionY = (player.blockY / 16) * 16
                    "section_$sectionY.png"
                }
            }

        val halfMapSize = mapSize / 2.0

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                val chunkWorldCenterX = chunkX * 16 + 8.0
                val chunkWorldCenterZ = chunkZ * 16 + 8.0

                val dx = (chunkWorldCenterX - centerX)
                val dz = (chunkWorldCenterZ - centerZ)

                val scaledDx = dx / featureRadius * halfMapSize
                val scaledDz = dz / featureRadius * halfMapSize

                val chunkRenderSize = (16.0 / featureRadius * halfMapSize).toFloat()

                val drawX = (renderX + halfMapSize + scaledDx - chunkRenderSize / 2.0).toFloat()
                val drawY = (renderY + halfMapSize + scaledDz - chunkRenderSize / 2.0).toFloat()

                var chunkIdentifier =
                    MapTextureManager.getChunkTextureIdentifier(chunkX, chunkZ, dimensionKey, textureFileName)

                // テクスチャが存在しない場合はディスクからロードを試みる
                if (chunkIdentifier == null) {
                    // ⚠️ 注意: I/O (ロード) をメインスレッドで実行しています。
                    // チャンクが大量にある場合、一時的にゲームがフリーズする可能性があります。
                    chunkIdentifier = MapTextureManager.loadAndRegisterTextureFromFile(
                        chunkX,
                        chunkZ,
                        dimensionKey,
                        textureFileName
                    )
                }

                if (chunkIdentifier != null) {
                    graphics2D.drawRotatedTexture(
                        chunkIdentifier,
                        drawX,
                        drawY,
                        chunkRenderSize,
                        chunkRenderSize,
                        0f,
                    )
                } else {
                    // テクスチャがファイルにも存在しない場合、未描画エリアとして仮の四角を描画
                    graphics2D.fill(
                        drawX.toInt(),
                        drawY.toInt(),
                        chunkRenderSize.toInt(),
                        chunkRenderSize.toInt(),
                        0xAA333333.toInt() // 濃い灰色
                    )
                }
            }
        }
    }

    // ... (renderEntitiesFullScreenのロジックは変更なし) ...

    private fun renderEntitiesFullScreen(
        graphics2D: Graphics2D,
        renderX: Int,
        renderY: Int,
        mapSize: Int,
    ) {
        val client = graphics2D.client
        val player = client.player ?: return

        val featureRadius = hyperMap.radiusSetting.value.toDouble() * zoom
        val mobDotRadius = 2

        val screenCenterX = renderX + mapSize / 2
        val screenCenterY = renderY + mapSize / 2

        for (mob in hyperMap.nearbyMobs) {
            val dx = (mob.x - centerX)
            val dz = (mob.z - centerZ)

            val scaledDx = dx / featureRadius * (mapSize / 2.0)
            val scaledDz = dz / featureRadius * (mapSize / 2.0)

            val mobRenderX = (screenCenterX + scaledDx).toInt()
            val mobRenderY = (screenCenterY + scaledDz).toInt()

            val baseColor = (HyperMapRenderer.getBaseDotColor(mob))
            val relativeHeight = mob.y - player.y
            val maxBlendFactor = 0.5
            val blendFactor =
                (kotlin.math.abs(relativeHeight) / hyperMap.heightSetting.value).coerceIn(0.0, maxBlendFactor).toFloat()
            val blendedColor =
                when {
                    relativeHeight > 0 ->
                        ColorHelper.lerp(
                            blendFactor,
                            baseColor,
                            0xFFFFFFFF.toInt(),
                        )

                    relativeHeight < 0 ->
                        ColorHelper.lerp(
                            blendFactor,
                            baseColor,
                            0xFF000000.toInt(),
                        )

                    else -> baseColor
                }

            val alpha = HyperMapRenderer.getAlphaBasedOnHeight(mob, player.y, hyperMap.heightSetting.value)
            val finalDotColor = blendedColor.transparent(alpha)

            graphics2D.fill(
                mobRenderX - mobDotRadius,
                mobRenderY - mobDotRadius,
                mobDotRadius * 2,
                mobDotRadius * 2,
                finalDotColor,
            )
        }
    }
}