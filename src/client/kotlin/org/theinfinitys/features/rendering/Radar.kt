package org.theinfinitys.features.rendering

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.LivingEntity
import net.minecraft.text.Text
import net.minecraft.util.math.ColorHelper
import net.minecraft.util.math.MathHelper
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// =================================================================================================
// 1. Feature Class: Radar (設定値の範囲をパーセント単位に変更)
// =================================================================================================

class Radar : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<InfiniteSetting<*>> = listOf(
        // RadiusとHeightは探知距離なのでピクセルやパーセントとは関係ありません
        InfiniteSetting.IntSetting("Radius", "探知半径を設定します。", 10, 5, 64),
        InfiniteSetting.IntSetting("Height", "探知する高さを設定します。", 5, 1, 32),
        // MarginとSizeをパーセント単位（画面の短い辺に対する）として扱う
        InfiniteSetting.IntSetting("Margin", "レーダーUIのマージン(% of screen short side)。", 10, 0, 40),
        InfiniteSetting.IntSetting("Size", "レーダーUIの大きさ(% of screen short side)。", 25, 5, 100),
    )

    // ... (findTargetMobs() は変更なし) ...
    fun findTargetMobs(): List<LivingEntity> {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return emptyList()
        val player = client.player ?: return emptyList()

        val radiusSetting = getSetting("Radius") as? InfiniteSetting.IntSetting
        val heightSetting = getSetting("Height") as? InfiniteSetting.IntSetting

        val radius = radiusSetting?.value ?: 10
        val height = heightSetting?.value ?: 5

        val playerX = player.x
        val playerY = player.y
        val playerZ = player.z

        val targets = mutableListOf<LivingEntity>()

        for (entity in world.entities) {
            if (entity == player) continue

            if (entity is LivingEntity) {
                val dx = entity.x - playerX
                val dz = entity.z - playerZ
                val distanceSq = dx * dx + dz * dz

                if (distanceSq <= radius * radius) {
                    val dy = entity.y - playerY

                    if (dy >= -height && dy <= height) {
                        targets.add(entity)
                    }
                }
            }
        }
        return targets
    }

    @Volatile
    var nearbyMobs: List<LivingEntity> = listOf()

    override fun tick() {
        nearbyMobs = if (isEnabled()) {
            findTargetMobs()
        } else {
            listOf()
        }
    }
}

// =================================================================================================
// 2. Renderer Object: RadarRenderer (十字線削除)
// =================================================================================================

object RadarRenderer {

    private fun toRadians(direction: Float) = direction / 180f * MathHelper.PI

    private fun getRainbowColor(): Int {
        val rainbowDuration = 6000L
        val colors = intArrayOf(
            0xFFFF0000.toInt(), 0xFFFFFF00.toInt(), 0xFF00FF00.toInt(),
            0xFF00FFFF.toInt(), 0xFF0000FF.toInt(), 0xFFFF00FF.toInt(), 0xFFFF0000.toInt(),
        )
        val currentTime = System.currentTimeMillis()
        val elapsedTime = currentTime % rainbowDuration
        val progress = elapsedTime.toFloat() / rainbowDuration.toFloat()
        val numSegments = colors.size - 1
        val segmentLength = 1.0f / numSegments
        val currentSegmentIndex = (progress / segmentLength).toInt().coerceAtMost(numSegments - 1)
        val segmentProgress = (progress % segmentLength) / segmentLength
        val startColor = colors[currentSegmentIndex]
        val endColor = colors[currentSegmentIndex + 1]

        return ColorHelper.getArgb(
            255,
            (ColorHelper.getRed(startColor) * (1 - segmentProgress) + ColorHelper.getRed(endColor) * segmentProgress).toInt(),
            (ColorHelper.getGreen(startColor) * (1 - segmentProgress) + ColorHelper.getGreen(endColor) * segmentProgress).toInt(),
            (ColorHelper.getBlue(startColor) * (1 - segmentProgress) + ColorHelper.getBlue(endColor) * segmentProgress).toInt(),
        )
    }

    fun render(context: DrawContext, client: MinecraftClient, radarFeature: Radar) {
        val player = client.player ?: return
        val font = client.textRenderer

        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        val shortSide = screenWidth.coerceAtMost(screenHeight)

        val marginPercent = (radarFeature.getSetting("Margin") as? InfiniteSetting.IntSetting)?.value ?: 4
        val sizePercent = (radarFeature.getSetting("Size") as? InfiniteSetting.IntSetting)?.value ?: 10

        val marginPx = (shortSide * marginPercent / 100.0).toInt()
        val sizePx = (shortSide * sizePercent / 100.0).toInt()
        val halfSizePx = sizePx / 2

        // レーダーの中心座標を左下に設定
        val centerX = marginPx + halfSizePx
        val centerY = screenHeight - marginPx - halfSizePx

        // レーダーの左上と右下の座標
        val startX = centerX - halfSizePx
        val startY = centerY - halfSizePx
        val endX = centerX + halfSizePx
        val endY = centerY + halfSizePx

        val rainbowColor = getRainbowColor()
        val innerColor = ColorHelper.getArgb(128, 0, 0, 0) // レーダー内部の背景色

        // レーダー内部の背景を塗りつぶし
        context.fill(startX, startY, endX, endY, innerColor)
        context.drawBorder(startX, startY, 2 * halfSizePx, 2 * halfSizePx, rainbowColor)
        val playerYaw = player.headYaw

        // 方位描画 (正方形の枠の内側に収まるように)
        val compassPoints = mapOf(
            0f to "S", 90f to "W", 180f to "N", 270f to "E",
        )
        val clipOffset = (halfSizePx - (font.fontHeight / 2))
        val textOffset = sqrt(2.0) * clipOffset
        for ((degree, char) in compassPoints) {
            val relativeYaw = MathHelper.wrapDegrees(degree - playerYaw)
            val relativeRad = toRadians(relativeYaw)

            // 正方形の辺に沿って文字を配置
            val textX = centerX + (sin(relativeRad) * textOffset).toInt().coerceIn(-clipOffset, clipOffset)
            val textY = centerY - (cos(relativeRad) * textOffset).toInt().coerceIn(-clipOffset, clipOffset)

            val textWidth = font.getWidth(char)

            context.drawText(
                font,
                Text.literal(char),
                textX - textWidth / 2,
                textY - font.fontHeight / 2,
                0xFFFFFFFF.toInt(), // 方位文字は白のまま
                true
            )
        }

        // モブの描画
        val featureRadius = ((radarFeature.getSetting("Radius") as? InfiniteSetting.IntSetting)?.value ?: 10).toDouble()
        val mobDotRadius = 1 // モブの点サイズ
        val yawRad = toRadians(playerYaw) // モブの相対位置計算には必要

        for (mob in radarFeature.nearbyMobs) {
            val dx = (player.x - mob.x)
            val dz = (player.z - mob.z)

            val distance = sqrt(dx * dx + dz * dz)
            // レーダーの正方形の半分サイズ（halfSizePx）にスケール
            val scaledDistance = (distance / featureRadius * halfSizePx.toDouble()).coerceAtMost(halfSizePx.toDouble())

            val angleToMob = kotlin.math.atan2(dx, dz) - yawRad.toDouble()

            val mobX = centerX + (sin(angleToMob) * scaledDistance).toInt()
            val mobY = centerY - (cos(angleToMob) * scaledDistance).toInt()

            val mobColor = 0xFFFF0000.toInt() // モブの色は赤

            context.fill(
                mobX - mobDotRadius, mobY - mobDotRadius, mobX + mobDotRadius, mobY + mobDotRadius, mobColor
            )
        }
    }

}