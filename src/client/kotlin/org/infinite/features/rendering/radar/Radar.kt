package org.infinite.features.rendering.radar

import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.BlockPos
import org.infinite.ConfigurableFeature
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.settings.FeatureSetting

// =================================================================================================
// 1. Feature Class: Radar (render2d メソッドを追加)
// =================================================================================================

class Radar : ConfigurableFeature(initialEnabled = false) {
    enum class Mode {
        Flat,
        Solid,
    }

    val mode =
        FeatureSetting.EnumSetting<Mode>("Mode", "feature.rendering.radar.mode.description", Mode.Flat, Mode.entries)
    val radiusSetting = FeatureSetting.IntSetting("Radius", "feature.rendering.radar.radius.description", 32, 5, 256)
    val heightSetting = FeatureSetting.IntSetting("Height", "feature.rendering.radar.height.description", 8, 1, 32)
    val marginPercent =
        FeatureSetting.IntSetting(
            "Margin",
            "feature.rendering.radar.margin.description",
            4,
            0,
            40,
        )
    val sizePercent = FeatureSetting.IntSetting("Size", "feature.rendering.radar.size.description", 40, 5, 100)
    val renderTerrain = FeatureSetting.BooleanSetting("Render Terrain", "feature.rendering.radar.render_terrain.description", true)
    override val settings: List<FeatureSetting<*>> =
        listOf(
            radiusSetting,
            heightSetting,
            marginPercent,
            sizePercent,
            mode,
            renderTerrain,
        )

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

    @Volatile
    var nearbyMobs: List<LivingEntity> = listOf()

    @Volatile
    var nearbyBlocks: MutableMap<BlockPos, BlockState> = java.util.concurrent.ConcurrentHashMap()

    private var tickCounter: Int = 0
    private val updateInterval = 10 // Update every 10 ticks (0.5 seconds)

    override fun tick() {
        nearbyMobs =
            if (isEnabled()) {
                findTargetMobs()
            } else {
                listOf()
            }

        if (isEnabled() && renderTerrain.value) {
            tickCounter++
            if (tickCounter % updateInterval == 0) {
                val world = client.world ?: return
                val player = client.player ?: return
                val radius = radiusSetting.value
                val height = heightSetting.value

                nearbyBlocks.clear()

                val playerBlockX = player.blockX
                val playerBlockY = player.blockY
                val playerBlockZ = player.blockZ

                for (x in -radius..radius) {
                    for (z in -radius..radius) {
                        // Search from slightly above player down to playerBlockY - height
                        for (yOffset in 0..height) { // Iterate downwards from player's Y level
                            val currentY = playerBlockY - yOffset
                            val blockPos = BlockPos(playerBlockX + x, currentY, playerBlockZ + z)
                            val blockState = world.getBlockState(blockPos)

                            // Check if the block itself is not air
                            if (!blockState.isAir) {
                                // Check if the two blocks above are air
                                val blockAbove1 = world.getBlockState(blockPos.up(1))
                                val blockAbove2 = world.getBlockState(blockPos.up(2))

                                if (blockAbove1.isAir && blockAbove2.isAir) {
                                    // This is a passable surface block
                                    nearbyBlocks[blockPos] = blockState
                                    break // Found a block, move to the next (x, z) column
                                }
                            }
                        }
                    }
                }
            }
        } else {
            nearbyBlocks.clear()
            tickCounter = 0 // Reset counter when disabled
        }
    }

    /**
     * GUI描画を実行します。Graphics2Dヘルパーを使用します。
     */
    override fun render2d(graphics2D: Graphics2D) {
        if (mode.value == Mode.Flat) {
            RadarRenderer.render(graphics2D, this)
        }
    }

    override fun render3d(graphics3D: Graphics3D) {
//        if (mode.value == Mode.Solid) {
//            RadarRenderer.render(graphics3D, this)
//        }
    }
}
