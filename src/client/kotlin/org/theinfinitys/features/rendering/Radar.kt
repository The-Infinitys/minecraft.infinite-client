package org.theinfinitys.features.rendering

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.passive.AnimalEntity
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.math.ColorHelper
import net.minecraft.util.math.MathHelper
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// =================================================================================================
// 1. Feature Class: Radar (設定値の範囲をパーセント単位に変更)
// =================================================================================================

class Radar : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            // RadiusとHeightは探知距離
            InfiniteSetting.IntSetting("Radius", "探知半径を設定します。", 32, 5, 256),
            InfiniteSetting.IntSetting("Height", "探知する高さを設定します。", 8, 1, 32),
            // MarginとSizeをパーセント単位（画面の短い辺に対する）として扱う
            InfiniteSetting.IntSetting("Margin", "レーダーUIのマージン(% of screen short side)。", 4, 0, 40),
            InfiniteSetting.IntSetting("Size", "レーダーUIの大きさ(% of screen short side)。", 40, 5, 100),
        )

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
// 2. Renderer Object: RadarRenderer (ドット描画)
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

    /**
     * エンティティの種類に基づいてドットの色を決定します。（アルファ値は含まない）
     */
    private fun getBaseDotColor(entity: LivingEntity): Int =
        when (entity) {
            is PlayerEntity -> 0x00FFFF // プレイヤー: 水色 (ARGBのAなし)
            is HostileEntity -> 0xFF0000 // 敵対モブ: 赤色
            is AnimalEntity, is VillagerEntity -> 0x00FF00 // 友好モブ: 緑色
            else -> 0xFFFF00 // それ以外（中立モブなど）: 黄色
        }

    /**
     * y軸方向のズレに基づいてアルファ値（透明度）を決定します。
     * ズレが小さいほど不透明（アルファ値255）に、探知高さの限界に近いほど透明（アルファ値の最小値）になります。
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
        // 255 * (1 - ratio) + 32 * ratio
        val minAlpha = 32 // 最小アルファ値
        val alpha = ((255 * (1.0 - ratio) + minAlpha * ratio)).toInt().coerceIn(minAlpha, 255)

        return alpha
    }

    fun render(
        context: DrawContext,
        client: MinecraftClient,
        radarFeature: Radar,
    ) {
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

        // 方位描画 (omitted for brevity, remains unchanged)
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

            context.drawText(
                font,
                Text.literal(char),
                textX - textWidth / 2,
                textY - font.fontHeight / 2,
                if (char == "N") {
                    0xFFFF0000
                } else {
                    0xFFFFFFFF
                }.toInt(),
                true,
            )
        }

        // モブの描画
        val featureRadius = ((radarFeature.getSetting("Radius") as? InfiniteSetting.IntSetting)?.value ?: 10).toDouble()
        // ドットのサイズを小さくする (例: 2x2 から 1x1 へ。 fillメソッドの引数を変更することで実質的なサイズを調整)
        val mobDotRadius = 1 // ドットのサイズ (変更: 2 -> 1)
        val yawRad = toRadians(playerYaw)

        // 探知高さを取得
        val featureHeight = (radarFeature.getSetting("Height") as? InfiniteSetting.IntSetting)?.value ?: 5

        // プレイヤーのドットを中央に描画 (プレイヤーは常に不透明)
        val playerDotColor =
            ColorHelper.getArgb(
                255,
                ColorHelper.getRed(getBaseDotColor(player)),
                ColorHelper.getGreen(getBaseDotColor(player)),
                ColorHelper.getBlue(getBaseDotColor(player)),
            )
        context.fill(
            centerX - mobDotRadius,
            centerY - mobDotRadius,
            centerX + mobDotRadius,
            centerY + mobDotRadius,
            playerDotColor,
        )

        for (mob in radarFeature.nearbyMobs) {
            val dx = (mob.x - player.x)
            val dz = (mob.z - player.z)

            val distance = sqrt(dx * dx + dz * dz)
            val scaledDistance = (distance / featureRadius * halfSizePx.toDouble()).coerceAtMost(halfSizePx.toDouble())

            // atan2の引数の順序を修正: atan2(y, x) -> atan2(dz, dx) (Minecraftの座標系に合わせる)
            val angleToMob = kotlin.math.atan2(dz, dx) - yawRad.toDouble() - toRadians(90f)

            val mobX = centerX + (sin(angleToMob) * scaledDistance).toInt()
            val mobY = centerY - (cos(angleToMob) * scaledDistance).toInt()

            // 基本の色とy軸のズレに基づくアルファ値を取得
            val baseColor = (getBaseDotColor(mob))
            val alpha = getAlphaBasedOnHeight(mob, player.y, featureHeight)
            val finalDotColor =
                ColorHelper.getArgb(
                    alpha,
                    ColorHelper.getRed(baseColor),
                    ColorHelper.getGreen(baseColor),
                    ColorHelper.getBlue(baseColor),
                )

            // ドットを描画 (色分けされた四角形、サイズ変更、透明度適用)
            context.fill(
                mobX - mobDotRadius,
                mobY - mobDotRadius,
                mobX + mobDotRadius,
                mobY + mobDotRadius,
                finalDotColor,
            )
        }
    }
}
