package org.infinite.features.rendering.radar

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import org.infinite.ConfigurableFeature
import org.infinite.libs.graphics.Graphics2D
import org.infinite.settings.FeatureSetting

// =================================================================================================
// 1. Feature Class: Radar (render2d メソッドを追加)
// =================================================================================================

class Radar : ConfigurableFeature(initialEnabled = false) {
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
    override val settings: List<FeatureSetting<*>> =
        listOf(
            radiusSetting,
            heightSetting,
            marginPercent,
            sizePercent,
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
