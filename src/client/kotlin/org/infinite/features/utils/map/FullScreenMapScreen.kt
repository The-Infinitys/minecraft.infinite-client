package org.infinite.features.utils.map

import net.minecraft.client.gui.Click
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import net.minecraft.util.math.ColorHelper
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.rendering.transparent
import kotlin.math.max
import kotlin.math.min

class FullScreenMapScreen(
    val mapFeature: MapFeature,
) : Screen(Text.of("Full Screen Map")) {
    private var zoom: Double = 1.0
    private var panX: Double = 0.0
    private var panY: Double = 0.0
    private val hyperMap: HyperMap
        get() = InfiniteClient.getFeature(HyperMap::class.java)!!

    override fun render(
        context: DrawContext,
        mouseX: Int,
        mouseY: Int,
        delta: Float,
    ) {
        this.renderBackground(context, mouseX, mouseY, delta)
        val graphics2D = Graphics2D(context, client!!.renderTickCounter)

        val screenWidth = width
        val screenHeight = height

        // Calculate map size based on zoom
        val mapSize = min(screenWidth, screenHeight).toFloat() * zoom
        val halfMapSize = mapSize / 2.0f

        // Center the map initially, then apply pan
        val renderX = (screenWidth / 2.0f - halfMapSize + panX).toInt()
        val renderY = (screenHeight / 2.0f - halfMapSize + panY).toInt()

        context.enableScissor(renderX, renderY, (renderX + mapSize).toInt(), (renderY + mapSize).toInt())

        // Render terrain
        renderTerrainFullScreen(graphics2D, renderX, renderY, mapSize.toInt())

        // Render entities
        renderEntitiesFullScreen(graphics2D, renderX, renderY, mapSize.toInt())

        context.disableScissor()

        // Draw border
        graphics2D.drawBorder(
            renderX,
            renderY,
            mapSize.toInt(),
            mapSize.toInt(),
            InfiniteClient.theme().colors.primaryColor,
            4,
        )

        // Draw player dot in the center of the map area
        val playerDotRadius = 2
        val playerDotColor =
            InfiniteClient
                .theme()
                .colors.infoColor
                .transparent(255)
        graphics2D.fill(
            (renderX + halfMapSize - playerDotRadius).toInt(),
            (renderY + halfMapSize - playerDotRadius).toInt(),
            playerDotRadius * 2, // width
            playerDotRadius * 2, // height
            playerDotColor,
        )

        // Draw compass (North always up)
        val compassRadius = halfMapSize * 0.8f
        val compassCenterX = renderX + halfMapSize
        val compassCenterY = renderY + halfMapSize
        val northColor = InfiniteClient.theme().colors.errorColor
        val otherColor = InfiniteClient.theme().colors.foregroundColor

        val font = textRenderer
        val textOffset = font.fontHeight / 2

        // North
        graphics2D.drawText(
            "N",
            (compassCenterX - font.getWidth("N") / 2).toInt(),
            (compassCenterY - compassRadius - textOffset).toInt(),
            northColor,
            true,
        )
        // East
        graphics2D.drawText(
            "E",
            (compassCenterX + compassRadius - font.getWidth("E") / 2).toInt(),
            (compassCenterY - textOffset).toInt(),
            otherColor,
            true,
        )
        // South
        graphics2D.drawText(
            "S",
            (compassCenterX - font.getWidth("S") / 2).toInt(),
            (compassCenterY + compassRadius - textOffset).toInt(),
            otherColor,
            true,
        )
        // West
        graphics2D.drawText(
            "W",
            (compassCenterX - compassRadius - font.getWidth("W") / 2).toInt(),
            (compassCenterY - textOffset).toInt(),
            otherColor,
            true,
        )

        // Player coordinates
        if (mapFeature.showPlayerCoordinates.value) {
            val player = client!!.player
            if (player != null) {
                val x = player.x
                val y = player.y
                val z = player.z

                val coordsText = Text.of("X: %.1f Y: %.1f Z: %.1f".format(x, y, z))
                val coordsWidth = font.getWidth(coordsText)
                val coordsX = (screenWidth - coordsWidth) / 2
                val coordsY = screenHeight - font.fontHeight - 10

                graphics2D.drawText(
                    coordsText.string,
                    coordsX,
                    coordsY,
                    InfiniteClient.theme().colors.foregroundColor,
                    true,
                )
            }
        }

        super.render(context, mouseX, mouseY, delta)
    }

    private fun renderTerrainFullScreen(
        graphics2D: Graphics2D,
        renderX: Int,
        renderY: Int,
        mapSize: Int,
    ) {
        val client = graphics2D.client
        val player = client.player ?: return

        val playerBlockX = player.blockX
        val playerBlockZ = player.blockZ
        val playerChunkX = playerBlockX shr 4
        val playerChunkZ = playerBlockZ shr 4

        val featureRadius = hyperMap.radiusSetting.value.toDouble() * zoom // Adjust radius by zoom
        val renderDistanceChunks = (featureRadius / 16.0).toInt() + 1

        val minChunkX = playerChunkX - renderDistanceChunks
        val maxChunkX = playerChunkX + renderDistanceChunks
        val minChunkZ = playerChunkZ - renderDistanceChunks
        val maxChunkZ = playerChunkZ + renderDistanceChunks
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

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                val chunkWorldCenterX = chunkX * 16 + 8.0
                val chunkWorldCenterZ = chunkZ * 16 + 8.0

                val dx = (chunkWorldCenterX - player.x)
                val dz = (chunkWorldCenterZ - player.z)

                // Calculate position relative to the center of the full-screen map
                val scaledDx = dx / featureRadius * (mapSize / 2.0)
                val scaledDz = dz / featureRadius * (mapSize / 2.0)

                val chunkRenderSize = (16.0 / featureRadius * (mapSize / 2.0)).toFloat()

                val drawX = (renderX + mapSize / 2.0 + scaledDx - chunkRenderSize / 2.0).toFloat()
                val drawY = (renderY + mapSize / 2.0 + scaledDz - chunkRenderSize / 2.0).toFloat()

                val chunkIdentifier =
                    MapTextureManager.getChunkTextureIdentifier(chunkX, chunkZ, dimensionKey, textureFileName)

                if (chunkIdentifier != null) {
                    graphics2D.drawRotatedTexture(
                        chunkIdentifier,
                        drawX,
                        drawY,
                        chunkRenderSize,
                        chunkRenderSize,
                        0f,
                    ) // North always up
                }
            }
        }
    }

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

        val centerX = renderX + mapSize / 2
        val centerY = renderY + mapSize / 2

        for (mob in hyperMap.nearbyMobs) {
            val dx = (mob.x - player.x)
            val dz = (mob.z - player.z)

            // Calculate position relative to the center of the full-screen map
            val scaledDx = dx / featureRadius * (mapSize / 2.0)
            val scaledDz = dz / featureRadius * (mapSize / 2.0)

            val mobRenderX = (centerX + scaledDx).toInt()
            val mobRenderY = (centerY + scaledDz).toInt()

            val baseColor = (HyperMapRenderer.getBaseDotColor(mob))
            val relativeHeight = mob.y - player.y
            val maxBlendFactor = 0.5 // Maximum 50% black or white
            val blendFactor =
                (kotlin.math.abs(relativeHeight) / hyperMap.heightSetting.value).coerceIn(0.0, maxBlendFactor).toFloat()
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

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double,
    ): Boolean {
        if (verticalAmount > 0) {
            zoom = min(5.0, zoom + 0.1)
        } else if (verticalAmount < 0) {
            zoom = max(0.5, zoom - 0.1)
        }
        return true
    }

    override fun mouseDragged(
        click: Click,
        offsetX: Double,
        offsetY: Double,
    ): Boolean {
        if (click.button() == 0) { // Left mouse button
            panX += offsetX
            panY += offsetY
            return true
        }
        return super.mouseDragged(click, offsetX, offsetY)
    }
}
