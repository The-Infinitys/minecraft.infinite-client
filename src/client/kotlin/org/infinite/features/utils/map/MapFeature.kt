package org.infinite.features.utils.map

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting
import org.lwjgl.glfw.GLFW

class MapFeature : ConfigurableFeature() {
    val zoomLevel = FeatureSetting.IntSetting("ZoomLevel", 1, 1, 10)
    val showPlayerCoordinates =
        FeatureSetting.BooleanSetting(
            "ShowPlayerCoordinates",
            true,
        )
    override val settings: List<FeatureSetting<*>> =
        listOf(
            zoomLevel,
            showPlayerCoordinates,
        )
    override val actionKeybinds: List<ActionKeybind> =
        listOf(
            ActionKeybind("open_map", GLFW.GLFW_KEY_M) {
                client.setScreen(FullScreenMapScreen(this))
            },
        )
}
