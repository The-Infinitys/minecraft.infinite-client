package org.infinite.features.rendering.search

import org.infinite.ConfigurableFeature
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.world.WorldManager
import org.infinite.settings.FeatureSetting

class BlockSearch : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BlockColorListSetting(
                "blockSearchColors",
                "feature.rendering.search.blocksearch.colors.description",
                mutableMapOf(),
            ),
        )

    fun getBlockSearchColors(): MutableMap<String, Int> = (getSetting("blockSearchColors") as FeatureSetting.BlockColorListSetting).value

    override fun tick() {
        BlockSearchRenderer.tick()
    }

    override fun render3d(graphics3D: Graphics3D) {
        BlockSearchRenderer.render(graphics3D)
    }

    override fun handleChunk(worldChunk: WorldManager.Chunk) {
        BlockSearchRenderer.handleChunk(worldChunk)
    }

    override fun enabled() {
        BlockSearchRenderer.clear()
    }

    override fun disabled() {
        BlockSearchRenderer.clear()
    }
}
