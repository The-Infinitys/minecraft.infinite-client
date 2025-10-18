package org.theinfinitys.features.rendering.sensory

import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.InfiniteClient
import org.theinfinitys.features.rendering.sensory.esp.ItemEsp
import org.theinfinitys.features.rendering.sensory.esp.MobEsp
import org.theinfinitys.features.rendering.sensory.esp.PlayerEsp
import org.theinfinitys.infinite.graphics.Graphics3D
import org.theinfinitys.settings.InfiniteSetting

class ExtraSensory : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.CHEAT
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.BooleanSetting("PlayerEsp", "Show Player", true),
            InfiniteSetting.BooleanSetting("MobEsp", "Show Mob", true),
            InfiniteSetting.BooleanSetting("ItemEsp", "Show Item", true),
            InfiniteSetting.BooleanSetting("PortalEsp", "Show Portal", true),
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
