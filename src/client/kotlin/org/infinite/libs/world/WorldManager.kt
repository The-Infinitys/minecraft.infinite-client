package org.infinite.libs.world

import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ChunkData
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket

class WorldManager {
    fun handleChunkLoad(
        x: Int,
        z: Int,
        chunkData: ChunkData,
    ) {}

    fun handleDeltaUpdate(packet: ChunkDeltaUpdateS2CPacket) {}

    fun handleBlockUpdate(packet: BlockUpdateS2CPacket) {}
}
