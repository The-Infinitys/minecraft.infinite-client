package org.infinite.features.rendering.sensory

import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.InfiniteClient
import org.infinite.features.rendering.sensory.esp.ItemEsp
import org.infinite.features.rendering.sensory.esp.MobEsp
import org.infinite.features.rendering.sensory.esp.PlayerEsp
import org.infinite.features.rendering.sensory.esp.PortalEsp
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.world.WorldManager
import org.infinite.settings.FeatureSetting

class ExtraSensory : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BooleanSetting("PlayerEsp", "feature.rendering.extrasensory.playeresp.description", true),
            FeatureSetting.BooleanSetting("MobEsp", "feature.rendering.extrasensory.mobesp.description", true),
            FeatureSetting.BooleanSetting("ItemEsp", "feature.rendering.extrasensory.itemesp.description", true),
            FeatureSetting.BooleanSetting("PortalEsp", "feature.rendering.extrasensory.portalesp.description", true),
        )

    private fun isEnabled(type: String): Boolean = InfiniteClient.isSettingEnabled(ExtraSensory::class.java, type + "Esp")

    override fun render3d(graphics3D: Graphics3D) {
        if (isEnabled("Portal")) {
            PortalEsp.render(graphics3D)
        }
        if (isEnabled("Player")) {
            PlayerEsp.render(graphics3D)
        }
        if (isEnabled("Mob")) {
            MobEsp.render(graphics3D)
        }
        if (isEnabled("Item")) {
            ItemEsp.render(graphics3D)
        }
    }

    override fun handleChunk(worldChunk: WorldManager.Chunk) {
        PortalEsp.handleChunk(worldChunk)
    }

    override fun disabled() {
        super.disabled()
        PortalEsp.clear()
    }

    override fun tick() {
        PortalEsp.tick()
    }
}
