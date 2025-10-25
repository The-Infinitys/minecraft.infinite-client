package org.infinite.features.rendering.radar

import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.ColorHelper
import net.minecraft.util.math.MathHelper
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.settings.FeatureSetting
import org.infinite.utils.rendering.transparent
import org.infinite.utils.toRadians
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.util.math.RotationAxis as Axis

object RadarRenderer {
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
        radarFeature: Radar,
    ) {
        val client = graphics2d.client
        val player = client.player ?: return
        val font = client.textRenderer

        val screenWidth = graphics2d.width
        val screenHeight = graphics2d.height
        val shortSide = screenWidth.coerceAtMost(screenHeight)
        val marginPercent = InfiniteClient.getSettingInt(Radar::class.java, "Margin", 4)
        val sizePercent = InfiniteClient.getSettingInt(Radar::class.java, "Size", 40)
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
        val featureRadius = ((radarFeature.getSetting("Radius") as? FeatureSetting.IntSetting)?.value ?: 10).toDouble()
        val mobDotRadius = 1
        val yawRad = toRadians(playerYaw)
        val featureHeight = (radarFeature.getSetting("Height") as? FeatureSetting.IntSetting)?.value ?: 5

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

            val mobX = centerX + (sin(angleToMob) * scaledDistance)
            val mobY = centerY - (cos(angleToMob) * scaledDistance)

            val baseColor = (getBaseDotColor(mob))
            val alpha = getAlphaBasedOnHeight(mob, player.y, featureHeight)
            val finalDotColor = baseColor.transparent(alpha)
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

    fun render(
        graphics3D: Graphics3D,
        radarFeature: Radar,
    ) {
        val client = graphics3D.client
        val player = client.player ?: return
        val world = client.world ?: return

        val featureRadius = (radarFeature.getSetting("Radius") as? FeatureSetting.IntSetting)?.value ?: 10
        val featureHeight = (radarFeature.getSetting("Height") as? FeatureSetting.IntSetting)?.value ?: 5

        // Get 2D radar position and size for 3D minimap placement
        val screenWidth = client.window.scaledWidth
        val screenHeight = client.window.scaledHeight
        val shortSide = screenWidth.coerceAtMost(screenHeight)
        val marginPercent = InfiniteClient.getSettingInt(Radar::class.java, "Margin", 4)
        val sizePercent = InfiniteClient.getSettingInt(Radar::class.java, "Size", 40)
        val marginPx = (shortSide * marginPercent / 100.0).toInt()
        val sizePx = (shortSide * sizePercent / 100.0).toInt()

        graphics3D.pushMatrix()

        // --- Step 1: Apply screen-space positioning and scaling ---

        // Calculate the desired screen position for the minimap's center in normalized device coordinates (NDC).

        // NDC ranges from -1 to 1 for X and Y, where (-1,-1) is bottom-left and (1,1) is top-right.

        // The 2D radar's centerX and centerY are in screen pixels (0,0 top-left).

        val ndcX = (marginPx + sizePx / 2.0) / screenWidth * 2.0 - 1.0

        val ndcY = 1.0 - (marginPx + sizePx / 2.0) / screenHeight * 2.0

        val ndcZ = -2.0 // Fixed Z to place it in front of the camera

        // Translate the entire scene to this screen-space position.

        // This is applied to the model-view matrix, effectively moving the rendered content.

        graphics3D.translate(ndcX, ndcY, ndcZ)

        // Scale the minimap scene to the desired size.

        // The scale factor needs to be adjusted based on the screen size and desired minimap size.

        // We want the minimap to occupy 'sizePx' pixels on screen.

        // A simple way to approximate this is to scale relative to the screen height.

        val fixedMinimapScale = sizePx / 200.0 // Adjust this factor as needed for visual size

        graphics3D.matrixStack.scale(
            fixedMinimapScale.toFloat(),
            fixedMinimapScale.toFloat(),
            fixedMinimapScale.toFloat(),
        )

        // --- Step 2: Rotate the minimap scene ---

        // Rotate to look down from above (X-axis rotation)

        graphics3D.matrixStack.multiply(Axis.POSITIVE_X.rotationDegrees(90f))

        // Rotate around the Y-axis to align with the player's yaw

        graphics3D.matrixStack.multiply(Axis.POSITIVE_Y.rotationDegrees(-player.headYaw))

        // --- Step 3: Translate to player's relative position ---

        // This makes the terrain/mobs/compass render relative to the player's current position,

        // effectively centering the minimap on the player.

        graphics3D.translate(-player.x, -player.y, -player.z)

        // Render terrain

        render3DTerrain(graphics3D, player, world, featureRadius, featureHeight)

        // Render mobs

        render3DMobs(graphics3D, radarFeature, player)

        // Render compass

        render3DCompass(graphics3D, player, featureRadius)

        graphics3D.popMatrix()
    }

    private fun render3DTerrain(
        graphics3D: Graphics3D,
        player: PlayerEntity,
        world: net.minecraft.client.world.ClientWorld,
        radius: Int,
        height: Int,
    ) {
        val playerBlockX = player.blockX
        val playerBlockY = player.blockY
        val playerBlockZ = player.blockZ

        val terrainBoxes = mutableListOf<RenderUtils.ColorBox>()

        for (x in -radius..radius) {
            for (y in -height..height) {
                for (z in -radius..radius) {
                    val blockPos = BlockPos(playerBlockX + x, playerBlockY + y, playerBlockZ + z)
                    val blockState = world.getBlockState(blockPos)

                    if (!blockState.isAir && blockState.isOpaqueFullCube) {
                        val blockColor = getBlockColor(blockState)
                        // Render blocks relative to the player's current position
                        val box =
                            Box(
                                blockPos.x.toDouble() - player.x,
                                blockPos.y.toDouble() - player.y,
                                blockPos.z.toDouble() - player.z,
                                blockPos.x + 1.0 - player.x,
                                blockPos.y + 1.0 - player.y,
                                blockPos.z + 1.0 - player.z,
                            )
                        terrainBoxes.add(RenderUtils.ColorBox(blockColor.transparent(128), box))
                    }
                }
            }
        }
        graphics3D.renderSolidColorBoxes(terrainBoxes, false) // Render with depth test
    }

    private fun getBlockColor(blockState: BlockState): Int {
        // Simple color mapping for common blocks, can be expanded
        return when (blockState.block) {
            Blocks.GRASS_BLOCK -> 0xFF00AA00.toInt() // Dark Green
            Blocks.DIRT -> 0xFF8B4513.toInt() // Saddle Brown
            Blocks.STONE -> 0xFF808080.toInt() // Gray
            Blocks.WATER -> 0xFF0000FF.toInt() // Blue
            Blocks.LAVA -> 0xFFFF0000.toInt() // Red
            Blocks.OAK_LOG -> 0xFF8B4513.toInt() // Brown
            Blocks.OAK_LEAVES -> 0xFF00FF00.toInt() // Green
            else -> 0xFFCCCCCC.toInt() // Light Gray for others
        }
    }

    private fun render3DMobs(
        graphics3D: Graphics3D,
        radarFeature: Radar,
        player: PlayerEntity,
    ) {
        val mobBoxes = mutableListOf<RenderUtils.ColorBox>()
        val mobSize = 0.3 // Size of mob cube

        for (mob in radarFeature.nearbyMobs) {
            val mobPos = mob.getLerpedPos(graphics3D.tickProgress)
            val baseColor = getBaseDotColor(mob)
            val alpha =
                getAlphaBasedOnHeight(
                    mob,
                    player.y,
                    (radarFeature.getSetting("Height") as? FeatureSetting.IntSetting)?.value ?: 5,
                )
            val mobColor = baseColor.transparent(alpha)

            // Render mobs relative to the player's current position
            val box =
                Box(
                    mobPos.x - mobSize / 2 - player.x,
                    mobPos.y - player.y,
                    mobPos.z - mobSize / 2 - player.z,
                    mobPos.x + mobSize / 2 - player.x,
                    mobPos.y + mob.height - player.y,
                    mobPos.z + mob.height - player.z, // Fix: mob.height for Z-axis extent
                )
            mobBoxes.add(RenderUtils.ColorBox(mobColor, box))
        }
        graphics3D.renderSolidColorBoxes(mobBoxes, true) // Render mobs always on top (no depth test)
    }

    private fun render3DCompass(
        graphics3D: Graphics3D,
        player: PlayerEntity,
        radius: Int,
    ) {
        val compassColor =
            InfiniteClient
                .theme()
                .colors.foregroundColor
                .transparent(200)

        // Render lines for cardinal directions relative to player's position
        val lineLength = radius.toDouble() * 0.8 // Shorter lines for compass
        val tick = graphics3D.tickCounter.getTickProgress(false)
        val pos = player.getLerpedPos(tick) // This is player's interpolated world position

        // North (Z-)
        graphics3D.renderLine(
            pos.add(0.0, 0.0, -lineLength).subtract(player.x, player.y, player.z),
            pos.add(0.0, 0.0, -lineLength - 1.0).subtract(player.x, player.y, player.z),
            compassColor,
            true,
        )
        // South (Z+)
        graphics3D.renderLine(
            pos.add(0.0, 0.0, lineLength).subtract(player.x, player.y, player.z),
            pos.add(0.0, 0.0, lineLength + 1.0).subtract(player.x, player.y, player.z),
            compassColor,
            true,
        )
        // East (X+)
        graphics3D.renderLine(
            pos.add(lineLength, 0.0, 0.0).subtract(player.x, player.y, player.z),
            pos.add(lineLength + 1.0, 0.0, 0.0).subtract(player.x, player.y, player.z),
            compassColor,
            true,
        )
        // West (X-)
        graphics3D.renderLine(
            pos.add(-lineLength, 0.0, 0.0).subtract(player.x, player.y, player.z),
            pos.add(-lineLength - 1.0, 0.0, 0.0).subtract(player.x, player.y, player.z),
            compassColor,
            true,
        )

        // Render player's current position marker at the center of the minimap (0,0,0 relative)
        val playerMarkerColor =
            InfiniteClient
                .theme()
                .colors.primaryColor
                .transparent(255)
        val markerSize = 0.2
        val playerBox =
            Box(
                -markerSize,
                -markerSize,
                -markerSize,
                markerSize,
                markerSize,
                markerSize,
            )
        graphics3D.renderSolidColorBoxes(listOf(RenderUtils.ColorBox(playerMarkerColor, playerBox)), true)
    }
}
