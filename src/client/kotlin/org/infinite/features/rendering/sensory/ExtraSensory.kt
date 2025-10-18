package org.infinite.features.rendering.sensory

import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.InfiniteClient
import org.infinite.features.rendering.sensory.esp.ItemEsp
import org.infinite.features.rendering.sensory.esp.MobEsp
import org.infinite.features.rendering.sensory.esp.PlayerEsp
import org.infinite.libs.graphics.Graphics3D
import org.infinite.settings.FeatureSetting

class ExtraSensory : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BooleanSetting("PlayerEsp", "Show Player", true),
            FeatureSetting.BooleanSetting("MobEsp", "Show Mob", true),
            FeatureSetting.BooleanSetting("ItemEsp", "Show Item", true),
            FeatureSetting.BooleanSetting("PortalEsp", "Show Portal", true),
        )

    private fun isEnabled(type: String): Boolean = InfiniteClient.isSettingEnabled(ExtraSensory::class.java, type + "Esp")

    override fun render3d(graphics3D: Graphics3D) {
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
}
