package org.infinite.libs.world

import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ChunkData
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket

class WorldManager {
    sealed class Chunk {
        class Data(
            x: Int,
            z: Int,
            data: ChunkData,
        ) : Chunk()

        class BlockUpdate(
            packet: BlockUpdateS2CPacket,
        ) : Chunk()

        class DeltaUpdate(
            packet: ChunkDeltaUpdateS2CPacket,
        ) : Chunk()
    }

    val queue: ArrayDeque<Chunk> = ArrayDeque(listOf())

    /**
     * ChunkDataをキューの最後に追加します。
     * @param x チャンクのX座標
     * @param z チャンクのZ座標
     * @param chunkData チャンクデータ
     */
    fun handleChunkLoad(
        x: Int,
        z: Int,
        chunkData: ChunkData,
    ) {
        queue.addLast(Chunk.Data(x, z, chunkData))
    }

    /**
     * ChunkDeltaUpdateS2CPacketをキューの最後に追加します。
     * @param packet チャンクデルタ更新パケット
     */
    fun handleDeltaUpdate(packet: ChunkDeltaUpdateS2CPacket) {
        queue.addLast(Chunk.DeltaUpdate(packet))
    }

    /**
     * BlockUpdateS2CPacketをキューの最後に追加します。
     * @param packet ブロック更新パケット
     */
    fun handleBlockUpdate(packet: BlockUpdateS2CPacket) {
        queue.addLast(Chunk.BlockUpdate(packet))
    }
}
