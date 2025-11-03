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
                mutableMapOf(
                    "minecraft:ancient_debris" to 0x808B4513.toInt(),
                    "minecraft:anvil" to 0x80FF00FF.toInt(),
                    "minecraft:beacon" to 0x80FF00FF.toInt(),
                    "minecraft:bone_block" to 0x80FF00FF.toInt(),
                    "minecraft:bookshelf" to 0x80FF00FF.toInt(),
                    "minecraft:brewing_stand" to 0x80FF00FF.toInt(),
                    "minecraft:chain_command_block" to 0x80FF00FF.toInt(),
                    "minecraft:chest" to 0x80FF00FF.toInt(),
                    "minecraft:clay" to 0x80FF00FF.toInt(),
                    "minecraft:coal_block" to 0x80333333.toInt(),
                    "minecraft:coal_ore" to 0x80333333.toInt(),
                    "minecraft:command_block" to 0x80FF00FF.toInt(),
                    "minecraft:copper_ore" to 0x80B87333.toInt(),
                    "minecraft:crafting_table" to 0x80FF00FF.toInt(),
                    "minecraft:deepslate_coal_ore" to 0x80333333.toInt(),
                    "minecraft:deepslate_copper_ore" to 0x80B87333.toInt(),
                    "minecraft:deepslate_diamond_ore" to 0x8000FFFF.toInt(),
                    "minecraft:deepslate_emerald_ore" to 0x8000FF00.toInt(),
                    "minecraft:deepslate_gold_ore" to 0x80DAA520.toInt(),
                    "minecraft:deepslate_iron_ore" to 0x80C0C0C0.toInt(),
                    "minecraft:deepslate_lapis_ore" to 0x801560BD.toInt(),
                    "minecraft:deepslate_redstone_ore" to 0x80FF0000.toInt(),
                    "minecraft:diamond_block" to 0x8000FFFF.toInt(),
                    "minecraft:diamond_ore" to 0x8000FFFF.toInt(),
                    "minecraft:dispenser" to 0x80FF00FF.toInt(),
                    "minecraft:dropper" to 0x80FF00FF.toInt(),
                    "minecraft:emerald_block" to 0x8000FF00.toInt(),
                    "minecraft:emerald_ore" to 0x8000FF00.toInt(),
                    "minecraft:enchanting_table" to 0x80FF00FF.toInt(),
                    "minecraft:end_portal" to 0x80FF00FF.toInt(),
                    "minecraft:end_portal_frame" to 0x80FF00FF.toInt(),
                    "minecraft:ender_chest" to 0x80FF00FF.toInt(),
                    "minecraft:furnace" to 0x80FF00FF.toInt(),
                    "minecraft:glowstone" to 0x80FF00FF.toInt(),
                    "minecraft:gold_block" to 0x80DAA520.toInt(),
                    "minecraft:gold_ore" to 0x80DAA520.toInt(),
                    "minecraft:hopper" to 0x80FF00FF.toInt(),
                    "minecraft:iron_block" to 0x80C0C0C0.toInt(),
                    "minecraft:iron_ore" to 0x80C0C0C0.toInt(),
                    "minecraft:ladder" to 0x80FF00FF.toInt(),
                    "minecraft:lapis_block" to 0x801560BD.toInt(),
                    "minecraft:lapis_ore" to 0x801560BD.toInt(),
                    "minecraft:lava" to 0x80FF00FF.toInt(),
                    "minecraft:lodestone" to 0x80FF00FF.toInt(),
                    "minecraft:mossy_cobblestone" to 0x80FF00FF.toInt(),
                    "minecraft:nether_gold_ore" to 0x80DAA520.toInt(),
                    "minecraft:nether_portal" to 0x80FF00FF.toInt(),
                    "minecraft:nether_quartz_ore" to 0x80F0F0F0.toInt(),
                    "minecraft:raw_copper_block" to 0x80B87333.toInt(),
                    "minecraft:raw_gold_block" to 0x80DAA520.toInt(),
                    "minecraft:raw_iron_block" to 0x80C0C0C0.toInt(),
                    "minecraft:redstone_block" to 0x80FF0000.toInt(),
                    "minecraft:redstone_ore" to 0x80FF0000.toInt(),
                    "minecraft:repeating_command_block" to 0x80FF00FF.toInt(),
                    "minecraft:spawner" to 0x80FF00FF.toInt(),
                    "minecraft:suspicous_sand" to 0x80FF00FF.toInt(),
                    "minecraft:tnt" to 0x80FF00FF.toInt(),
                    "minecraft:torch" to 0x80FF00FF.toInt(),
                    "minecraft:trapped_chest" to 0x80FF00FF.toInt(),
                    "minecraft:water" to 0x80FF00FF.toInt(),
                ),
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
