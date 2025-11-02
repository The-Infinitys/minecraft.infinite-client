package org.infinite.features.utils.map

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting
import org.lwjgl.glfw.GLFW

class MapFeature : ConfigurableFeature(initialEnabled = true) {
    val zoomLevel = FeatureSetting.IntSetting("ZoomLevel", "feature.utils.map.zoom_level.description", 1, 1, 10)
    val showPlayerCoordinates =
        FeatureSetting.BooleanSetting(
            "ShowPlayerCoordinates",
            "feature.utils.map.show_player_coordinates.description",
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
