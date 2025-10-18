package org.theinfinitys.features.rendering.radar

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.ColorHelper
import net.minecraft.util.math.MathHelper
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.infinite.graphics.Graphics2D
import org.theinfinitys.settings.InfiniteSetting
import org.theinfinitys.utils.rendering.getRainbowColor
import org.theinfinitys.utils.toRadians
import kotlin.collections.iterator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// =================================================================================================
// 1. Feature Class: Radar (render2d メソッドを追加)
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

    /**
     * GUI描画を実行します。Graphics2Dヘルパーを使用します。
     */
    override fun render2d(graphics2D: Graphics2D) {
        if (isEnabled()) {
            RadarRenderer.render(graphics2D, this)
        }
    }
}

// =================================================================================================
// 2. Renderer Object: RadarRenderer (render メソッドのシグネチャと実装を修正)
// =================================================================================================

object RadarRenderer {
    /**
     * エンティティの種類に基づいてドットの色を決定します。（アルファ値は含まない）
     */
    fun getBaseDotColor(entity: LivingEntity): Int =
        when (entity) {
            is PlayerEntity -> 0x00FFFF // プレイヤー: 水色 (ARGBのAなし)
            is HostileEntity -> 0xFF0000 // 敵対モブ: 赤色
            is PassiveEntity -> 0x00FF00 // 友好モブ: 緑色
            else -> 0xFFFF00 // それ以外（中立モブなど）: 黄色
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
        // 255 * (1 - ratio) + 32 * ratio
        val minAlpha = 32 // 最小アルファ値
        val alpha = ((255 * (1.0 - ratio) + minAlpha * ratio)).toInt().coerceIn(minAlpha, 255)

        return alpha
    }

    fun render(
        graphics2d: Graphics2D, // Graphics2Dを受け取るように修正
        radarFeature: Radar,
    ) {
        val client = graphics2d.client
        val player = client.player ?: return
        val font = client.textRenderer

        val screenWidth = graphics2d.width
        val screenHeight = graphics2d.height
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

        val rainbowColor = getRainbowColor()
        val innerColor = ColorHelper.getArgb(128, 0, 0, 0) // レーダー内部の背景色

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
                    0xFFFF0000.toInt()
                } else {
                    0xFFFFFFFF.toInt()
                },
                true, // shadow
            )
        }

        // モブの描画
        val featureRadius = ((radarFeature.getSetting("Radius") as? InfiniteSetting.IntSetting)?.value ?: 10).toDouble()
        val mobDotRadius = 1
        val yawRad = toRadians(playerYaw)
        val featureHeight = (radarFeature.getSetting("Height") as? InfiniteSetting.IntSetting)?.value ?: 5

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

        for (mob in radarFeature.nearbyMobs) {
            val dx = (mob.x - player.x)
            val dz = (mob.z - player.z)

            val distance = sqrt(dx * dx + dz * dz)
            val scaledDistance = (distance / featureRadius * halfSizePx.toDouble()).coerceAtMost(halfSizePx.toDouble())

            val angleToMob = atan2(dz, dx) - yawRad.toDouble() - toRadians(90f)

            val mobX = centerX + (sin(angleToMob) * scaledDistance).toInt()
            val mobY = centerY - (cos(angleToMob) * scaledDistance).toInt()

            val baseColor = (getBaseDotColor(mob))
            val alpha = getAlphaBasedOnHeight(mob, player.y, featureHeight)
            val finalDotColor =
                ColorHelper.getArgb(
                    alpha,
                    ColorHelper.getRed(baseColor),
                    ColorHelper.getGreen(baseColor),
                    ColorHelper.getBlue(baseColor),
                )

            // ドットを描画 (Graphics2D.fill を使用)
            graphics2d.fill(
                mobX - mobDotRadius,
                mobY - mobDotRadius,
                2 * mobDotRadius, // width
                2 * mobDotRadius, // height
                finalDotColor,
            )
        }
    }
}
