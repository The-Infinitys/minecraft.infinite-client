package org.infinite.features.automatic.wood

import net.minecraft.block.Block
import net.minecraft.util.math.Vec3d
import org.infinite.ConfigurableFeature
import org.infinite.libs.ai.AiInterface
import org.infinite.libs.ai.actions.movement.MovementAction
import org.infinite.settings.FeatureSetting

class WoodMiner : ConfigurableFeature() {
    open class State {
        class Idle : State()

        class Goto(
            target: Tree,
        ) : State()
    }

    data class Tree(
        val block: Block,
        val count: Int,
        val pos: Vec3d,
    )

    override val togglable: Boolean = false
    val searchRadius =
        FeatureSetting.DoubleSetting(
            name = "SearchRadius",
            defaultValue = 10.0,
            min = 1.0,
            max = 64.0,
        )

    val woodTypes =
        FeatureSetting.BlockListSetting(
            name = "WoodTypes",
            defaultValue = mutableListOf("minecraft:oak_log", "minecraft:spruce_log"),
        )
    override val settings: List<FeatureSetting<*>> =
        listOf(
            searchRadius,
            woodTypes,
        )

    override fun start() = disable()

    override fun tick() {
        val pos = playerPos ?: return
        val action = MovementAction(pos.add(4.0, 4.0, 4.0))
        AiInterface.add(action)
        disable()
    }
}
