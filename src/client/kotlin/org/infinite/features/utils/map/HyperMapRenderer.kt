package org.infinite.features.utils.map

import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.ColorHelper
import net.minecraft.util.math.MathHelper
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics2D
import org.infinite.settings.FeatureSetting
import org.infinite.utils.rendering.transparent
import org.infinite.utils.toRadians
import kotlin.collections.iterator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object HyperMapRenderer {
    /**
     * エンティティの種類に基づいてドットの色を決定します。（アルファ値は含まない）
     */
    fun getBaseDotColor(entity: LivingEntity): Int =
        when (entity) {
            is PlayerEntity -> InfiniteClient.theme().colors.infoColor // プレイヤー: 水色 (ARGBのAなし)
            is HostileEntity -> InfiniteClient.theme().colors.errorColor // 敵対モブ: 赤色
            is PassiveEntity -> InfiniteClient.theme().colors.greenAccentColor // 友好モブ: 緑色
            else -> InfiniteClient.theme().colors.warnColor // それ以外（中立モブなど）: 黄色
        }

    /**
     * y軸方向のズレに基づいてアルファ値（透明度）を決定します。
     */
    private fun getAlphaBasedOnHeight(
        entity: LivingEntity,
        playerY: Double,
        maxRelHeight: Int,
    ): Int {
        // y軸方向の絶対ズレ
        val dy = abs(entity.y - playerY)

        // dyが0の場合は完全不透明 (alpha = 255)
        if (dy <= 0.5) return 255

        // dyがmaxRelHeight（設定された探知高さ）以上の場合は完全透明に近い (alpha = 32を最小とする)
        if (dy >= maxRelHeight) return 32

        // ズレの比率 (0.0 から 1.0)
        val ratio = dy / maxRelHeight.toDouble()

        // アルファ値を線形補間 (ratio=0で255, ratio=1で32)
        // 255 * (1.0 - ratio) + 32 * ratio
        val minAlpha = 32 // 最小アルファ値
        val alpha = ((255 * (1.0 - ratio) + minAlpha * ratio)).toInt().coerceIn(minAlpha, 255)

        return alpha
    }

    fun render(
        graphics2d: Graphics2D, // Graphics2Dを受け取るように修正
        hyperMapFeature: HyperMap,
    ) {
        val client = graphics2d.client
        client.textureManager ?: return
        val player = client.player ?: return

        val font = client.textRenderer

        val screenWidth = graphics2d.width

        val screenHeight = graphics2d.height

        val shortSide = screenWidth.coerceAtMost(screenHeight)

        val marginPercent = InfiniteClient.getSettingInt(HyperMap::class.java, "Margin", 4)

        val sizePercent = InfiniteClient.getSettingInt(HyperMap::class.java, "Size", 40)

        val marginPx = (shortSide * marginPercent / 100.0).toInt()

        val sizePx = (shortSide * sizePercent / 100.0).toInt()

        val halfSizePx = sizePx / 2

        // レーダーの中心座標を右上に設定 **変更点**

        val centerX = screenWidth - marginPx - halfSizePx // 画面右端からマージンと半分のサイズを引く

        val centerY = marginPx + halfSizePx // 画面上端からマージンと半分のサイズを足す

        val rainbowColor = InfiniteClient.theme().colors.primaryColor

        val innerColor =

            InfiniteClient
                .theme()
                .colors.backgroundColor
                .transparent(128)

        // レーダー内部の背景を塗りつぶし (Graphics2D.fill を使用: x, y, width, height)

        graphics2d.fillCircle(centerX, centerY, halfSizePx, innerColor)

        graphics2d.drawCircle(centerX, centerY, halfSizePx, rainbowColor)

        val playerYaw = player.headYaw

        // 方位描画 (Graphics2D.drawText を使用)

        val compassPoints =

            mapOf(
                0f to "S",
                90f to "W",
                180f to "N",
                270f to "E",
            )

        val clipOffset = (halfSizePx - (font.fontHeight / 2))

        val textOffset = sqrt(2.0) * clipOffset

        for ((degree, char) in compassPoints) {
            val relativeYaw = MathHelper.wrapDegrees(degree - playerYaw)

            val relativeRad = toRadians(relativeYaw)

            val textX = centerX + (sin(relativeRad) * textOffset).toInt().coerceIn(-clipOffset, clipOffset)

            val textY = centerY - (cos(relativeRad) * textOffset).toInt().coerceIn(-clipOffset, clipOffset)

            val textWidth = font.getWidth(char)

            graphics2d.drawText(
                char, // text
                textX - textWidth / 2, // x
                textY - font.fontHeight / 2, // y
                if (char == "N") {
                    InfiniteClient.theme().colors.errorColor
                } else {
                    InfiniteClient.theme().colors.foregroundColor
                },
                true, // shadow
            )
        }

        // Render terrain using cached chunk images

        if (hyperMapFeature.renderTerrain.value) {
            val featureRadius = hyperMapFeature.radiusSetting.value

            val playerChunkX = player.chunkPos.x

            val playerChunkZ = player.chunkPos.z

            val chunkRenderRadius = (featureRadius / 16) + 1 // Render chunks slightly beyond the feature radius

            val yawRad = toRadians(playerYaw)

            for (chunkX in playerChunkX - chunkRenderRadius..playerChunkX + chunkRenderRadius) {
                for (chunkZ in playerChunkZ - chunkRenderRadius..playerChunkZ + chunkRenderRadius) {
                    val cachedChunkImage = hyperMapFeature.hyperMapChunkCache.getCachedChunkImage(chunkX, chunkZ)

                    if (cachedChunkImage != null) {
                        val chunkImage = cachedChunkImage.image
                        val identifier = cachedChunkImage.identifier

                        // Calculate position and rotation for the chunk image

                        val chunkWorldX = chunkX * 16 + 8 // Center of the chunk

                        val chunkWorldZ = chunkZ * 16 + 8

                        val dx = (chunkWorldX - player.x)

                        val dz = (chunkWorldZ - player.z)

                        val distance = sqrt(dx * dx + dz * dz)

                        val scaledDistance =
                            (distance / featureRadius * halfSizePx.toDouble()).coerceAtMost(halfSizePx.toDouble())

                        val angleToChunk = atan2(dz, dx) - yawRad.toDouble() - toRadians(90f)

                        val chunkScreenX = centerX + (sin(angleToChunk) * scaledDistance)

                        val chunkScreenY = centerY - (cos(angleToChunk) * scaledDistance)

                        // Scale the chunk image to fit the map

                        val chunkSizeOnMap = (16.0 / featureRadius * halfSizePx.toDouble()).toFloat()

                        graphics2d.drawTexture(
                            identifier,
                            chunkScreenX.toFloat() - chunkSizeOnMap / 2,
                            chunkScreenY.toFloat() - chunkSizeOnMap / 2,
                            chunkSizeOnMap,
                            chunkSizeOnMap,
                            0f, // U
                            0f, // V
                            chunkImage.width, // U width
                            chunkImage.height, // V height
                            chunkImage.width, // Texture width
                            chunkImage.height, // Texture height
                            yawRad, // Rotation
                        )
                    }
                }
            }
        }

        // モブの描画

        val featureRadius =

            ((hyperMapFeature.getSetting("Radius") as? FeatureSetting.IntSetting)?.value ?: 10).toDouble()

        val mobDotRadius = 1

        val yawRad = toRadians(playerYaw)

        val featureHeight = (hyperMapFeature.getSetting("Height") as? FeatureSetting.IntSetting)?.value ?: 5

        // プレイヤーのドットを中央に描画

        val playerDotColor =

            ColorHelper.getArgb(
                255,
                ColorHelper.getRed(getBaseDotColor(player)),
                ColorHelper.getGreen(getBaseDotColor(player)),
                ColorHelper.getBlue(getBaseDotColor(player)),
            )

        // Graphics2D.fill(x, y, width, height, color) を使用

        graphics2d.fill(
            centerX - mobDotRadius,
            centerY - mobDotRadius,
            2 * mobDotRadius, // width
            2 * mobDotRadius, // height
            playerDotColor,
        )

        for (mob in hyperMapFeature.nearbyMobs) {
            val dx = (mob.x - player.x)

            val dz = (mob.z - player.z)

            val distance = sqrt(dx * dx + dz * dz)

            val scaledDistance = (distance / featureRadius * halfSizePx.toDouble()).coerceAtMost(halfSizePx.toDouble())

            val angleToMob = atan2(dz, dx) - yawRad.toDouble() - toRadians(90f)

            val mobX = centerX + (sin(angleToMob) * scaledDistance)

            val mobY = centerY - (cos(angleToMob) * scaledDistance)

            val baseColor = (getBaseDotColor(mob))

            val relativeHeight = mob.y - player.y

            val maxBlendFactor = 0.5 // Maximum 50% black or white

            val blendFactor = (abs(relativeHeight) / featureHeight).coerceIn(0.0, maxBlendFactor).toFloat()

            val blendedColor =

                when {
                    relativeHeight > 0 ->

                        ColorHelper.lerp(
                            blendFactor,
                            baseColor,
                            0xFFFFFFFF.toInt(),
                        ) // Blend with white

                    relativeHeight < 0 ->

                        ColorHelper.lerp(
                            blendFactor,
                            baseColor,
                            0xFF000000.toInt(),
                        ) // Blend with black

                    else -> baseColor
                }

            val alpha = getAlphaBasedOnHeight(mob, player.y, featureHeight)

            val finalDotColor = blendedColor.transparent(alpha)

            val x = mobX.toFloat()

            val y = mobY.toFloat()

            graphics2d.fillRect(
                x - mobDotRadius,
                y - mobDotRadius,
                x + mobDotRadius,
                y + mobDotRadius,
                finalDotColor,
            )
        }
    }
}

private fun Graphics2D.renderBlockDot(
    blockCenterX: Double,
    blockCenterY: Double,
    blockDotSize: Float,
    direction: Float,
    finalBlockColor: Int,
) {
    // 中心から角までの距離（半対角線の長さ）

    val r = 1.5 * blockDotSize

    // 正方形の4つの角の初期角度 (角度0を右 (X+) としたときの反時計回り)

    // 頂点順序: 右上(45), 左上(135), 左下(225), 右下(315)

    val baseAngles =
        listOf(
            toRadians(45f),
            toRadians(135f),
            toRadians(225f),
            toRadians(315f),
        )

    // 【修正2: ドットの回転角】

    // 進行方向が上になる座標系での描画に対応するため、マイクラのヨー角に90度のオフセットを加える

    val rotation = direction

    val corners =
        baseAngles.map { baseAngle ->

            // 頂点の角度 = (基準角度) + (回転角度)

            val angle = baseAngle + rotation

            // 回転された頂点座標を計算 (X: sin, Y: -cos)

            // sin(angle) が X 座標の増減、-cos(angle) が Y 座標の増減に対応

            val x = blockCenterX + r * sin(angle)

            val y = blockCenterY - r * cos(angle)

            x.toFloat() to y.toFloat()
        }

    this.fillQuad(
        corners[0].first,
        corners[0].second,
        corners[1].first,
        corners[1].second,
        corners[2].first,
        corners[2].second,
        corners[3].first,
        corners[3].second,
        finalBlockColor,
    )
}
