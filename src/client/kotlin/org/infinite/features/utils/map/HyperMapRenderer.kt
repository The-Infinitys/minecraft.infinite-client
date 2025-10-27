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
        if (hyperMapFeature.renderTerrain.value) {
            renderTerrain(graphics2d, hyperMapFeature)
        }

        val client = graphics2d.client
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

        // レーダーの中心座標を左下に設定
        val centerX = marginPx + halfSizePx
        val centerY = screenHeight - marginPx - halfSizePx

        // レーダーの左上と右下の座標
        val startX = centerX - halfSizePx
        val startY = centerY - halfSizePx

        val rainbowColor = InfiniteClient.theme().colors.primaryColor
        val innerColor =
            InfiniteClient
                .theme()
                .colors.backgroundColor
                .transparent(128)
        // レーダー内部の背景を塗りつぶし (Graphics2D.fill を使用: x, y, width, height)
        graphics2d.fill(startX, startY, sizePx, sizePx, innerColor)
        graphics2d.drawBorder(startX, startY, sizePx, sizePx, rainbowColor)
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

        // モブの描画
        val featureRadius = ((hyperMapFeature.getSetting("Radius") as? FeatureSetting.IntSetting)?.value ?: 10).toDouble()
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

    fun renderTerrain(
        graphics2d: Graphics2D,
        hyperMapFeature: HyperMap,
    ) {
        val client = graphics2d.client
        val player = client.player ?: return

        val screenWidth = graphics2d.width
        val screenHeight = graphics2d.height
        val shortSide = screenWidth.coerceAtMost(screenHeight)
        val marginPercent = InfiniteClient.getSettingInt(HyperMap::class.java, "Margin", 4)
        val sizePercent = InfiniteClient.getSettingInt(HyperMap::class.java, "Size", 40)
        val marginPx = (shortSide * marginPercent / 100.0).toInt()
        val sizePx = (shortSide * sizePercent / 100.0).toInt()
        val halfSizePx = sizePx / 2

        val centerX = marginPx + halfSizePx
        val centerY = screenHeight - marginPx - halfSizePx

        val featureRadius = ((hyperMapFeature.getSetting("Radius") as? FeatureSetting.IntSetting)?.value ?: 10).toDouble()
        val playerYaw = player.headYaw
        val yawRad = toRadians(-playerYaw)

        val blockDotSize = 1f // Half-size of each block square on the radar

        for ((blockPos, blockState) in hyperMapFeature.nearbyBlocks) {
            val dx = (blockPos.x - player.x)
            val dz = (blockPos.z - player.z)

            val distance = sqrt(dx * dx + dz * dz)
            val scaledDistance = (distance / featureRadius * halfSizePx.toDouble()).coerceAtMost(halfSizePx.toDouble())

            val angleToBlock = atan2(dz, dx) + yawRad.toDouble()

            val blockCenterX = centerX + (sin(angleToBlock) * scaledDistance)
            val blockCenterY = centerY - (cos(angleToBlock) * scaledDistance)

            // Define corners of a square centered at (0,0) with side length 2 * blockDotSize
            val corners =
                arrayOf(
                    -blockDotSize to -blockDotSize,
                    blockDotSize to -blockDotSize,
                    blockDotSize to blockDotSize,
                    -blockDotSize to blockDotSize,
                )

            val rotatedCorners =
                corners.map { (x, y) ->
                    val rotatedX = x * cos(yawRad + toRadians(90f)) - y * sin(yawRad + toRadians(90f))
                    val rotatedY = x * sin(yawRad + toRadians(90f)) + y * cos(yawRad + toRadians(90f))
                    rotatedX + blockCenterX to rotatedY + blockCenterY
                }

            val baseBlockColor = blockState.mapColor.color
            val blockAlpha = if (!blockState.fluidState.isEmpty) 128 else 255 // 液体なら半透明 (128), それ以外は不透明 (255)
            val blockColor = baseBlockColor.transparent(blockAlpha)

            val relativeHeight = blockPos.y - player.y
            val featureHeight = (hyperMapFeature.getSetting("Height") as? FeatureSetting.IntSetting)?.value ?: 5
            val maxBlendFactor = 0.5 // Maximum 50% black or white

            val blendFactor = (abs(relativeHeight) / featureHeight).coerceIn(0.0, maxBlendFactor).toFloat()

            val finalBlockColor =
                when {
                    relativeHeight > 0 ->
                        ColorHelper.lerp(
                            blendFactor,
                            blockColor,
                            0xFFFFFFFF.toInt(),
                        ) // Blend with white
                    relativeHeight < 0 ->
                        ColorHelper.lerp(
                            blendFactor,
                            blockColor,
                            0xFF000000.toInt(),
                        ) // Blend with black
                    else -> blockColor
                }

            graphics2d.fillQuad(
                rotatedCorners[0].first.toFloat(),
                rotatedCorners[0].second.toFloat(),
                rotatedCorners[1].first.toFloat(),
                rotatedCorners[1].second.toFloat(),
                rotatedCorners[2].first.toFloat(),
                rotatedCorners[2].second.toFloat(),
                rotatedCorners[3].first.toFloat(),
                rotatedCorners[3].second.toFloat(),
                finalBlockColor,
            )
        }
    }
}
