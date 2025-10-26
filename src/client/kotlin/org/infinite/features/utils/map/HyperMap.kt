package org.infinite.features.utils.map

import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.BlockPos
import org.infinite.ConfigurableFeature
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.settings.FeatureSetting
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
    var nearbyBlocks: MutableMap<BlockPos, BlockState> = ConcurrentHashMap()

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
                val client = MinecraftClient.getInstance()
                val world = client.world ?: return
                val player = client.player ?: return
                val radius = radiusSetting.value
                val playerY = player.blockY // プレイヤーのY座標（ブロック単位）を取得

                nearbyBlocks.clear()

                val playerBlockX = player.blockX
                val playerBlockZ = player.blockZ

                // ----------------------------------------------------
                // 修正された地形スキャンロジック
                // ----------------------------------------------------
                for (x in -radius..radius) {
                    for (z in -radius..radius) {
                        var closestBlockPos: BlockPos? = null
                        var minAbsDiffY: Int = Int.MAX_VALUE // 最小のY座標の差の絶対値

                        // プレイヤーのY座標の上下を中心にスキャン範囲を調整
                        // 例：プレイヤーY - 高さ設定 から プレイヤーY + 高さ設定 の範囲
                        val scanMinY = (playerY - radius).coerceAtLeast(world.bottomY)
                        val scanMaxY = (playerY + radius).coerceAtMost(world.bottomY + world.height - 1)

                        for (y in scanMaxY downTo scanMinY) { // 上から下にスキャン
                            val blockPos = BlockPos(playerBlockX + x, y, playerBlockZ + z)
                            val blockState = world.getBlockState(blockPos)

                            // 1. ブロック自体が空気ブロックでない、または液体ブロックである
                            if (!blockState.isAir || !blockState.fluidState.isEmpty) {
                                val blockAbove1 = world.getBlockState(blockPos.up(1))
                                val blockAbove2 = world.getBlockState(blockPos.up(2))

                                val isAboveAir = blockAbove1.isAir && blockAbove2.isAir
                                val isAboveLiquid = !blockAbove1.fluidState.isEmpty && !blockAbove2.fluidState.isEmpty
                                val isLiquidAndAboveSolid =
                                    !blockState.fluidState.isEmpty && !blockAbove1.isAir && !blockAbove2.isAir

                                if (isAboveAir || isAboveLiquid || isLiquidAndAboveSolid) {
                                    // 候補ブロックが見つかった
                                    val absDiffY = abs(y - playerY)

                                    if (absDiffY < minAbsDiffY) {
                                        // プレイヤーのY座標に最も近いブロックを更新
                                        minAbsDiffY = absDiffY
                                        closestBlockPos = blockPos
                                    }
                                }
                            }
                        }

                        // (x, z)列で最もプレイヤーYに近いブロックが特定された場合、それを保存
                        if (closestBlockPos != null) {
                            nearbyBlocks[closestBlockPos] = world.getBlockState(closestBlockPos)
                        }
                    }
                }
                // ----------------------------------------------------
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
            HyperMapRenderer.render(graphics2D, this)
        }
    }

    override fun render3d(graphics3D: Graphics3D) {
//        if (mode.value == Mode.Solid) {
//            RadarRenderer.render(graphics3D, this)
//        }
    }
}
