package org.infinite.features.movement.builder

import net.minecraft.util.math.Direction
import org.infinite.InfiniteClient
import org.infinite.feature.ConfigurableFeature
import org.infinite.settings.FeatureSetting
import org.lwjgl.glfw.GLFW

class Builder : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Extend
    override val settings: List<FeatureSetting<*>> = emptyList()

    // Keybindings for modifying block placement coordinates
    override val actionKeybinds: List<ActionKeybind> =
        listOf(
            ActionKeybind("move_up", GLFW.GLFW_KEY_U) { modifyPlacementOffset(Direction.UP) },
            ActionKeybind("move_down", GLFW.GLFW_KEY_O) { modifyPlacementOffset(Direction.DOWN) },
            ActionKeybind("move_forward", GLFW.GLFW_KEY_8) { modifyPlacementOffset(Direction.NORTH) }, // Assuming 8 is for forward (Z-)
            ActionKeybind("move_backward", GLFW.GLFW_KEY_I) { modifyPlacementOffset(Direction.SOUTH) }, // Assuming I is for backward (Z+)
            ActionKeybind("move_left", GLFW.GLFW_KEY_7) { modifyPlacementOffset(Direction.WEST) }, // Assuming 7 is for left (X-)
            ActionKeybind("move_right", GLFW.GLFW_KEY_9) { modifyPlacementOffset(Direction.EAST) }, // Assuming 9 is for right (X+)
        )

    var currentPlacementOffset = Direction.UP // Default to placing on top of the targeted block

    private fun modifyPlacementOffset(direction: Direction) {
        currentPlacementOffset = direction
        // Optionally, provide feedback to the user via chat
        InfiniteClient.log("Builder: Placement offset set to ${direction.asString()}")
    }
}
