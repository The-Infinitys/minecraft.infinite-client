package org.infinite.features.utils.map

import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ColorHelper
import org.infinite.ConfigurableFeature
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.world.WorldManager
import org.infinite.settings.FeatureSetting
import org.infinite.utils.rendering.transparent
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

// =================================================================================================
// 1. Feature Class: Radar (render2d メソッドを追加)
// =================================================================================================

class HyperMap : ConfigurableFeature(initialEnabled = false) {
    enum class Mode {
        Flat,
        Solid,
    }

    var nearbyMobs: List<LivingEntity> = listOf()
    val mode =
        FeatureSetting.EnumSetting<Mode>("Mode", "feature.utils.hypermapmode.description", Mode.Flat, Mode.entries)
    val radiusSetting = FeatureSetting.IntSetting("Radius", "feature.utils.hypermapradius.description", 32, 5, 256)
    val heightSetting = FeatureSetting.IntSetting("Height", "feature.utils.hypermapheight.description", 8, 1, 32)
    val marginPercent =
        FeatureSetting.IntSetting(
            "Margin",
            "feature.utils.hypermapmargin.description",
            4,
            0,
            40,
        )
    val sizePercent = FeatureSetting.IntSetting("Size", "feature.utils.hypermapsize.description", 40, 5, 100)
    val renderTerrain =
        FeatureSetting.BooleanSetting("Render Terrain", "feature.utils.hypermaprender_terrain.description", true)
    override val settings: List<FeatureSetting<*>> =
        listOf(
            radiusSetting,
            heightSetting,
            marginPercent,
            sizePercent,
            mode,
            renderTerrain,
        )

    override fun tick() {
        nearbyMobs = findTargetMobs()
    }

    fun findTargetMobs(): List<LivingEntity> {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return emptyList()
        val player = client.player ?: return emptyList()

        val radius = radiusSetting.value
        val height = heightSetting.value

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

    internal val hyperMapChunkCache = HyperMapChunkCache()

    override fun handleChunk(worldChunk: WorldManager.Chunk) {
        if (!isEnabled() || !renderTerrain.value) {
            return
        }
        when (worldChunk) {
            is WorldManager.Chunk.Data -> {
                val chunkX = worldChunk.x
                val chunkZ = worldChunk.z
                // Trigger re-render for this chunk
                renderAndCacheChunk(chunkX, chunkZ)
            }

            is WorldManager.Chunk.BlockUpdate -> {
                val blockPos = worldChunk.packet.pos
                val chunkX = blockPos.x shr 4
                val chunkZ = blockPos.z shr 4
                // Invalidate and trigger re-render for the affected chunk
                renderAndCacheChunk(chunkX, chunkZ)
            }

            is WorldManager.Chunk.DeltaUpdate -> {
                // For delta updates, we need to iterate through all affected blocks
                // and re-render the chunks they belong to.
                val affectedChunks = mutableSetOf<Pair<Int, Int>>()
                worldChunk.packet.visitUpdates { blockPos, _ ->
                    val chunkX = blockPos.x shr 4
                    val chunkZ = blockPos.z shr 4
                    affectedChunks.add(Pair(chunkX, chunkZ))
                }
                affectedChunks.forEach { (chunkX, chunkZ) ->
                    renderAndCacheChunk(chunkX, chunkZ)
                }
            }
        }
    }

    private fun renderAndCacheChunk(
        chunkX: Int,
        chunkZ: Int,
    ) {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        val player = client.player ?: return
        val radius = radiusSetting.value
        val playerY = player.blockY

        val chunkMinX = chunkX * 16
        val chunkMinZ = chunkZ * 16
        val chunkMaxX = chunkMinX + 15
        val chunkMaxZ = chunkMinZ + 15

        // Create a temporary map to store blocks for this chunk's rendering
        val chunkBlocks: MutableMap<BlockPos, BlockState> = ConcurrentHashMap()

        for (x in chunkMinX..chunkMaxX) {
            for (z in chunkMinZ..chunkMaxZ) {
                var closestBlockPos: BlockPos? = null
                var minAbsDiffY: Int = Int.MAX_VALUE

                val scanMinY = (playerY - radius).coerceAtLeast(world.bottomY)
                val scanMaxY = (playerY + radius).coerceAtMost(world.bottomY + world.height - 1)

                for (y in scanMaxY downTo scanMinY) {
                    val blockPos = BlockPos(x, y, z)
                    val blockState = world.getBlockState(blockPos)

                    val blockAbove1 = world.getBlockState(blockPos.up(1))
                    val blockAbove2 = world.getBlockState(blockPos.up(2))

                    val isLiquidWithNoLiquidAbove =
                        !blockState.fluidState.isEmpty && blockAbove1.fluidState.isEmpty

                    val isSolidWithTwoAirOrLiquidAbove =
                        !blockState.isAir &&
                            blockState.fluidState.isEmpty &&
                            // Is solid
                            (blockAbove1.isAir || !blockAbove1.fluidState.isEmpty) &&
                            // Above 1 is air OR liquid
                            (blockAbove2.isAir || !blockAbove2.fluidState.isEmpty) // Above 2 is air OR liquid

                    if (isLiquidWithNoLiquidAbove || isSolidWithTwoAirOrLiquidAbove) {
                        val absDiffY = abs(y - playerY)

                        if (absDiffY < minAbsDiffY) {
                            minAbsDiffY = absDiffY
                            closestBlockPos = blockPos
                        }
                    }
                }

                if (closestBlockPos != null) {
                    chunkBlocks[closestBlockPos] = world.getBlockState(closestBlockPos)
                }
            }
        }

        // Now render this chunkBlocks into a NativeImage and cache it
        // For now, let's assume a fixed size for the chunk image (e.g., 16x16 pixels for a 16x16 block chunk)
        val imageSize = 16 // 1 pixel per block
        val image = NativeImage(imageSize, imageSize, false)

        for (x in 0 until imageSize) {
            for (z in 0 until imageSize) {
                val blockX = chunkMinX + x
                val blockZ = chunkMinZ + z
                // Find the block in chunkBlocks that corresponds to this (blockX, blockZ)
                // This is a simplified approach. A more accurate approach would involve
                // iterating through chunkBlocks and mapping them to image pixels.
                val blockPos = chunkBlocks.keys.find { it.x == blockX && it.z == blockZ }
                if (blockPos != null) {
                    val blockState = chunkBlocks[blockPos]!!
                    val baseBlockColor = blockState.mapColor.color
                    val blockAlpha = if (!blockState.fluidState.isEmpty) 128 else 255
                    val blockColor = baseBlockColor.transparent(blockAlpha)

                    // Simplified height blending for the chunk image
                    val relativeHeight = blockPos.y - playerY
                    val featureHeight = (getSetting("Height") as? FeatureSetting.IntSetting)?.value ?: 5
                    val maxBlendFactor = 0.5f
                    val blendFactor =
                        (abs(relativeHeight) / featureHeight)
                            .toDouble()
                            .coerceIn(0.0, maxBlendFactor.toDouble())
                            .toFloat()

                    val finalBlockColor =
                        when {
                            relativeHeight > 0 -> ColorHelper.lerp(blendFactor, blockColor, 0xFFFFFFFF.toInt())
                            relativeHeight < 0 -> ColorHelper.lerp(blendFactor, blockColor, 0xFF000000.toInt())
                            else -> blockColor
                        }
                    image.setColor(x, z, finalBlockColor)
                } else {
                    image.setColor(x, z, 0) // Transparent or black for empty space
                }
            }
        }

        hyperMapChunkCache.putChunkImage(chunkX, chunkZ, image)
        hyperMapChunkCache.saveChunkImage(chunkX, chunkZ, image)
    }

    override fun enabled() {
        hyperMapChunkCache.clearCache()
    }

    override fun disabled() {
        hyperMapChunkCache.clearCache()
    }

    /**
     * GUI描画を実行します。Graphics2Dヘルパーを使用します。
     */
    override fun render2d(graphics2D: Graphics2D) {
        if (mode.value == Mode.Flat) {
            HyperMapRenderer.render(graphics2D, this)
        }
    }

    override fun render3d(graphics3D: Graphics3D) {
//        if (mode.value == Mode.Solid) {
//            RadarRenderer.render(graphics3D, this)
//        }
    }
}
