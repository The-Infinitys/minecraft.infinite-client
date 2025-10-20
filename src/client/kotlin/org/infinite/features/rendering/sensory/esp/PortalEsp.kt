package org.infinite.features.rendering.sensory.esp

import net.minecraft.client.MinecraftClient
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.chunk.Chunk
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.libs.world.WorldManager

object PortalEsp {
    private val portalPositions = mutableListOf<BlockPos>()

    fun handleChunk(chunk: WorldManager.Chunk) {
        when (chunk) {
            is WorldManager.Chunk.Data -> {
                val client = MinecraftClient.getInstance()
                val world = client.world
                if (world != null) {
                    val chunkX = chunk.x
                    val chunkZ = chunk.z
                    val loadedChunk: Chunk? = world.getChunk(chunkX, chunkZ)

                    if (loadedChunk != null) {
                        for (section in loadedChunk.sectionArray) {
                            if (section != null && !section.isEmpty) {
                                for (y in 0 until 16) {
                                    for (z in 0 until 16) {
                                        for (x in 0 until 16) {
                                            val blockState = section.getBlockState(x, y, z)
                                            val block = blockState.block
                                            val blockId = Registries.BLOCK.getId(block).toString()

                                            if (blockId == "minecraft:nether_portal" || blockId == "minecraft:end_portal") {
                                                val x = (chunkX * 16) + x
                                                val z = (chunkZ * 16) + z
                                                val blockPos =
                                                    BlockPos(
                                                        x,
                                                        y,
                                                        z,
                                                    )
                                                portalPositions.add(blockPos)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is WorldManager.Chunk.BlockUpdate -> {
                val pos = chunk.packet.pos
                val blockState = MinecraftClient.getInstance().world?.getBlockState(pos)
                val blockId = blockState?.block?.let { Registries.BLOCK.getId(it).toString() }

                if (blockId == "minecraft:nether_portal" || blockId == "minecraft:end_portal") {
                    if (!portalPositions.contains(pos)) {
                        portalPositions.add(pos)
                    }
                } else {
                    portalPositions.remove(pos)
                }
            }

            is WorldManager.Chunk.DeltaUpdate -> {
                chunk.packet.visitUpdates { pos, state ->
                    val blockId = Registries.BLOCK.getId(state.block).toString()
                    if (blockId == "minecraft:nether_portal" || blockId == "minecraft:end_portal") {
                        if (!portalPositions.contains(pos)) {
                            portalPositions.add(pos)
                        }
                    } else {
                        portalPositions.remove(pos)
                    }
                }
            }
        }
    }

    fun clear() {
        portalPositions.clear()
    }

    fun render(graphics3D: Graphics3D) {
        val portalColor = 0xFF00FF00.toInt() // Green color for portals (ARGB)
        val boxes =
            portalPositions.map { pos ->
                RenderUtils.LinedColorBox(
                    portalColor,
                    Box(
                        pos.x.toDouble(),
                        pos.y.toDouble(),
                        pos.z.toDouble(),
                        pos.x + 1.0,
                        pos.y + 1.0,
                        pos.z + 1.0,
                    ),
                )
            }
        graphics3D.renderLinedColorBoxes(boxes, true)
    }
}
