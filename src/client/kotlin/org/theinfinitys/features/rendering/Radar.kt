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
    override val settings: List<InfiniteSetting<*>> =
        listOf(
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
        nearbyMobs =
            if (isEnabled()) {
                findTargetMobs()
            } else {
                listOf()
            }
    }
}
// =================================================================================================
// 2. Renderer Object: RadarRenderer (位置反転修正とパーセント計算導入)
// =================================================================================================

object RadarRenderer {
    private fun toRadians(direction: Float) = direction / 180f * MathHelper.PI

    private fun getRainbowColor(): Int {
        val rainbowDuration = 6000L
        val colors =
            intArrayOf(
                0xFFFF0000.toInt(),
                0xFFFFFF00.toInt(),
                0xFF00FF00.toInt(),
                0xFF00FFFF.toInt(),
                0xFF0000FF.toInt(),
                0xFFFF00FF.toInt(),
                0xFFFF0000.toInt(),
            )
        // ... (省略: 色の補間ロジック) ...
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

    fun render(
        context: DrawContext,
        client: MinecraftClient,
        radarFeature: Radar,
    ) {
        val player = client.player ?: return
        val font = client.textRenderer

        // 1. パーセント設定をピクセルに変換
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        val shortSide = screenWidth.coerceAtMost(screenHeight)

        val marginPercent = (radarFeature.getSetting("Margin") as? InfiniteSetting.IntSetting)?.value ?: 4
        val sizePercent = (radarFeature.getSetting("Size") as? InfiniteSetting.IntSetting)?.value ?: 10

        // 短い辺に対するパーセントでピクセル数を計算
        val marginPx = (shortSide * marginPercent / 100.0).toInt()
        val sizePx = (shortSide * sizePercent / 100.0).toInt()
        val radius = sizePx / 2

        // レーダーの中心座標を左下に設定
        val centerX = marginPx + radius
        val centerY = screenHeight - marginPx - radius

        val rainbowColor = getRainbowColor()
        val innerColor = ColorHelper.getArgb(128, 0, 0, 0)
        val whiteColor = 0xFFFFFFFF.toInt()
        val mobs = radarFeature.nearbyMobs

        val featureRadius = ((radarFeature.getSetting("Radius") as? InfiniteSetting.IntSetting)?.value ?: 10).toDouble()

        // 2-5. 円、縁、十字線、方位の描画 (座標計算はcenterX/centerYの変更により対応済み)
        context.fill(centerX - radius, centerY - radius, centerX + radius, centerY + radius, innerColor)

        // 縁の描画 (既存: レインボーアニメーション)
        drawCircleOutline(context, centerX, centerY, radius, rainbowColor)

        val playerYaw = player.headYaw
        val yawRad = toRadians(playerYaw)

        // 十字線の終点計算
        val forwardX = centerX + (sin(yawRad) * radius).toInt()
        val forwardY = centerY - (cos(yawRad) * radius).toInt()
        val backwardX = centerX - (sin(yawRad) * radius).toInt()
        val backwardY = centerY + (cos(yawRad) * radius).toInt()

        val rightX = centerX + (cos(yawRad) * radius).toInt()
        val rightY = centerY + (sin(yawRad) * radius).toInt()
        val leftX = centerX - (cos(yawRad) * radius).toInt()
        val leftY = centerY - (cos(yawRad) * radius).toInt()

        // 十字線の描画: drawThickLineに修正し、レインボーカラーを使用
        drawThickLine(context, centerX, centerY, forwardX, forwardY, rainbowColor)
        drawThickLine(context, centerX, centerY, backwardX, backwardY, rainbowColor)
        drawThickLine(context, centerX, centerY, rightX, rightY, rainbowColor)
        drawThickLine(context, centerX, centerY, leftX, leftY, rainbowColor)

        // 方位描画
        val compassPoints =
            mapOf(
                0f to "S",
                90f to "W",
                180f to "N",
                270f to "E",
            )
        val textRadius = radius + 5

        for ((degree, char) in compassPoints) {
            val relativeYaw = MathHelper.wrapDegrees(degree - playerYaw)
            val relativeRad = toRadians(relativeYaw)

            val textX = centerX + (sin(relativeRad) * textRadius).toInt()
            val textY = centerY - (cos(relativeRad) * textRadius).toInt()

            val textWidth = font.getWidth(char)
            context.drawText(
                font,
                Text.literal(char),
                textX - textWidth / 2,
                textY - font.fontHeight / 2,
                whiteColor, // 方位文字は白のまま
                true,
            )
        }

        // 6. モブの描画 (省略)
        val mobDotRadius = 1

        for (mob in mobs) {
            val dx = (mob.x - player.x)
            val dz = (mob.z - player.z)

            // 距離と角度の計算
            val distance = sqrt(dx * dx + dz * dz)
            val scaledDistance = (distance / featureRadius * radius.toDouble()).coerceAtMost(radius.toDouble())

            // 角度修正
            val angleToMob = kotlin.math.atan2(dx, dz) - yawRad.toDouble()

            // レーダー上の描画位置
            val mobX = centerX + (sin(angleToMob) * scaledDistance).toInt()
            val mobY = centerY - (cos(angleToMob) * scaledDistance).toInt()

            val mobColor = 0xFFFF0000.toInt()

            context.fill(
                mobX - mobDotRadius,
                mobY - mobDotRadius,
                mobX + mobDotRadius,
                mobY + mobDotRadius,
                mobColor,
            )
        }
    }

    // 輪郭線（円の縁）を描画する簡易的な関数
    private fun drawCircleOutline(
        context: DrawContext,
        centerX: Int,
        centerY: Int,
        radius: Int,
        color: Int,
    ) {
        val detail = 36 // 分割数
        val step = 2 * Math.PI / detail
        var prevX: Int? = null
        var prevY: Int? = null

        for (i in 0..detail) {
            val angle = i * step
            val currentX = centerX + (radius * sin(angle)).toInt()
            val currentY = centerY + (radius * cos(angle)).toInt()

            if (prevX != null && prevY != null) {
                // drawLineからdrawThickLineに変更
                drawThickLine(context, prevX, prevY, currentX, currentY, color)
            }
            prevX = currentX
            prevY = currentY
        }
    }

    // 斜め線も描画できるように修正した近似描画関数
    private fun drawThickLine(
        context: DrawContext,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        color: Int,
        thickness: Int = 1,
    ) {
        val dx = (x2 - x1).toFloat()
        val dy = (y2 - y1).toFloat()
        val distance = sqrt(dx * dx + dy * dy)

        if (distance < 1) {
            // 点を描画
            context.fill(x1, y1, x1 + thickness, y1 + thickness, color)
            return
        }

        // 線を構成するセグメントの数
        val numSegments = distance.toInt().coerceAtLeast(1)

        for (i in 0..numSegments) {
            val progress = i.toFloat() / numSegments.toFloat()
            val currentX = x1 + (dx * progress).toInt()
            val currentY = y1 + (dy * progress).toInt()

            // thickness=1 の場合、1x1ピクセルを描画
            context.fill(
                currentX,
                currentY,
                currentX + thickness,
                currentY + thickness,
                color,
            )
        }
    }
}
