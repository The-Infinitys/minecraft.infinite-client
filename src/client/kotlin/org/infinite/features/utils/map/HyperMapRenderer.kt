package org.infinite.features.utils.map

import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.ColorHelper
import net.minecraft.util.math.MathHelper
import org.infinite.InfiniteClient
import org.infinite.libs.client.player.ClientInterface
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.rendering.transparent
import org.infinite.utils.toRadians
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object HyperMapRenderer : ClientInterface() {
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
    fun getAlphaBasedOnHeight(
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

    /**
     * メインレンダリング関数: モードを受け取るように変更
     */
    fun render(
        graphics2d: Graphics2D,
        hyperMapFeature: HyperMap,
        mode: HyperMap.Mode, // Mode を受け取るように変更
    ) {
        val player = player ?: return
        val font = client.textRenderer

        val screenWidth = graphics2d.width
        val screenHeight = graphics2d.height
        val shortSide = screenWidth.coerceAtMost(screenHeight)
        val marginPercent = hyperMapFeature.marginPercent.value
        val sizePercent = hyperMapFeature.sizePercent.value
        val marginPx = (shortSide * marginPercent / 100.0).toInt()
        val sizePx = (shortSide * sizePercent / 100.0).toInt()
        val halfSizePx = sizePx / 2

        // レーダーの中心座標を右上に設定
        val centerX = screenWidth - marginPx - halfSizePx
        val centerY = marginPx + halfSizePx
        val primaryColor = InfiniteClient.theme().colors.primaryColor
        val innerColor =
            InfiniteClient
                .theme()
                .colors.backgroundColor
                .transparent(128)
        val playerYaw = player.headYaw
        // 背景と外枠の描画
        graphics2d.fill(centerX - halfSizePx, centerY - halfSizePx, sizePx, sizePx, innerColor)
        graphics2d.enableScissor(
            centerX - halfSizePx,
            centerY - halfSizePx,
            centerX + halfSizePx,
            centerY + halfSizePx,
        )
        if (hyperMapFeature.renderTerrain.value) {
            renderTerrain(graphics2d, hyperMapFeature, mode) // mode を渡す
        }
        renderMobs(graphics2d, hyperMapFeature, centerX, centerY, halfSizePx, player, playerYaw)
        graphics2d.disableScissor()
        graphics2d.drawBorder(centerX - halfSizePx, centerY - halfSizePx, sizePx, sizePx, primaryColor, 4)
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
            val textX = centerX + (sin(relativeRad) * textOffset).toInt().coerceIn(-halfSizePx, halfSizePx)
            val textY = centerY - (cos(relativeRad) * textOffset).toInt().coerceIn(-halfSizePx, halfSizePx)
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
        // 座標情報の描画
        val textWidth = graphics2d.textWidth("x: -300000000.00")
        val textX = centerX - halfSizePx - marginPx + (halfSizePx - textWidth) / 2
        val textY = centerY + halfSizePx + marginPx
        val fontSize = graphics2d.fontHeight()
        val colors = InfiniteClient.theme().colors
        val xString = "%12.1f".format(player.x)
        val yString = "%12.1f".format(player.y)
        val zString = "%12.1f".format(player.z)
        graphics2d.fill(textX, textY, textWidth, fontSize * 3, colors.backgroundColor)
        graphics2d.drawText("x: $xString", textX, textY, colors.blueAccentColor, true)
        graphics2d.drawText("y: $yString", textX, textY + fontSize, colors.greenAccentColor, true)
        graphics2d.drawText("z: $zString", textX, textY + 2 * fontSize, colors.redAccentColor, true)
        graphics2d.drawText("fps: ${client.currentFps}", textX, textY + 3 * fontSize, colors.foregroundColor, true)
    }

    fun renderMobs(
        graphics2d: Graphics2D,
        hyperMapFeature: HyperMap,
        centerX: Int,
        centerY: Int,
        halfSizePx: Int,
        player: ClientPlayerEntity,
        playerYaw: Float,
    ) {
        // モブの描画
        val featureRadius = hyperMapFeature.radiusSetting.value.toDouble()
        val mobDotRadius = 1
        val yawRad = toRadians(playerYaw)
        val featureHeight = hyperMapFeature.heightSetting.value

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
            val scaledDistance = (distance / featureRadius * halfSizePx.toDouble())
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
            graphics2d.rect(
                x - mobDotRadius,
                y - mobDotRadius,
                x + mobDotRadius,
                y + mobDotRadius,
                finalDotColor,
            )
        }
    }

    /**
     * 地形レンダリング関数: モードを受け取るように変更し、テクスチャファイル名を動的に決定
     */
    fun renderTerrain(
        graphics2d: Graphics2D,
        hyperMapFeature: HyperMap,
        mode: HyperMap.Mode, // Mode を受け取るように変更
    ) {
        val player = player ?: return
        val camera = client.cameraEntity ?: return
        val screenWidth = graphics2d.width
        val screenHeight = graphics2d.height
        val shortSide = screenWidth.coerceAtMost(screenHeight)
        val marginPercent = hyperMapFeature.marginPercent.value
        val sizePercent = hyperMapFeature.sizePercent.value
        val marginPx = (shortSide * marginPercent / 100.0).toInt()
        val sizePx = (shortSide * sizePercent / 100.0).toInt()
        val halfSizePx = sizePx / 2
        val centerX = screenWidth - marginPx - halfSizePx
        val centerY = marginPx + halfSizePx
        val featureRadius = hyperMapFeature.radiusSetting.value.toDouble()
        val yaw = camera.yaw
        val yawRad = toRadians(yaw)

        val playerBlockX = player.blockX
        val playerBlockZ = player.blockZ
        val playerChunkX = playerBlockX shr 4
        val playerChunkZ = playerBlockZ shr 4
        val renderDistanceChunks = (featureRadius / 16.0).toInt() + 1
        val minChunkX = playerChunkX - renderDistanceChunks
        val maxChunkX = playerChunkX + renderDistanceChunks
        val minChunkZ = playerChunkZ - renderDistanceChunks
        val maxChunkZ = playerChunkZ + renderDistanceChunks
        val dimensionKey = MapTextureManager.dimensionKey
        // ------------------------------------------------
        // 1. テクスチャファイル名の決定
        // ------------------------------------------------
        val textureFileName =
            when (mode) {
                HyperMap.Mode.Flat -> "surface.png"
                HyperMap.Mode.Solid -> {
                    // プレイヤーのY座標からセクションの開始Y座標を計算 (例: Y=64 -> 64)
                    val sectionY = (player.blockY / 16) * 16
                    "section_$sectionY.png"
                }
            }
        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                val chunkWorldCenterX = chunkX * 16 + 8.0
                val chunkWorldCenterZ = chunkZ * 16 + 8.0

                val dx = (chunkWorldCenterX - player.x)
                val dz = (chunkWorldCenterZ - player.z)

                val distanceToChunkCenter2 = dx * dx + dz * dz
                val featureRange2 = 2 * (featureRadius + 12) * (featureRadius + 12)
                // 2. 適切なファイル名を使ってテクスチャIdentifierを取得
                val chunkIdentifier =
                    MapTextureManager.getChunkTextureIdentifier(
                        chunkX,
                        chunkZ,
                        dimensionKey,
                        textureFileName,
                    )

                if (chunkIdentifier != null) {
                    if (distanceToChunkCenter2 < featureRange2) {
                        val distanceToChunkCenter = sqrt(distanceToChunkCenter2)
                        val scaledDistance =
                            (distanceToChunkCenter / featureRadius * halfSizePx.toDouble())
                        val angleToChunk =
                            atan2(dz, dx) - yawRad.toDouble() - toRadians(90f) // Adjust for radar orientation
                        val renderX = centerX + (sin(angleToChunk) * scaledDistance)
                        val renderY = centerY - (cos(angleToChunk) * scaledDistance)
                        val chunkRenderSize = (16.0 * halfSizePx / featureRadius).coerceAtMost(sizePx.toDouble())
                        graphics2d.drawRotatedTexture(
                            chunkIdentifier,
                            renderX - chunkRenderSize / 2,
                            renderY - chunkRenderSize / 2,
                            chunkRenderSize,
                            chunkRenderSize,
                            -yawRad + toRadians(180f),
                        )
                    }
                }
            }
        }
    }
}
