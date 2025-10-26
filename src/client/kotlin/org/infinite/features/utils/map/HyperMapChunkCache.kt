package org.infinite.features.utils.map

import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

data class CachedChunkImage(
    val image: NativeImage,
    val identifier: Identifier,
)

class HyperMapChunkCache {
    private val client: MinecraftClient = MinecraftClient.getInstance()
    private val chunkImages: ConcurrentHashMap<Long, CachedChunkImage> = ConcurrentHashMap() // Key: chunkPos.toLong()

    private fun getChunkKey(
        chunkX: Int,
        chunkZ: Int,
    ): Long {
        return BlockPos(chunkX, 0, chunkZ).asLong() // Using BlockPos.asLong() for a unique chunk key
    }

    private fun getDataDirectory(dimension: String): Path {
        val gameDir = FabricLoader.getInstance().gameDir
        val isSinglePlayer = client.isIntegratedServerRunning
        val serverName =
            if (isSinglePlayer) {
                client.server
                    ?.getSavePath(WorldSavePath.ROOT)
                    ?.parent
                    ?.fileName
                    ?.toString() ?: "single_player_world"
            } else {
                client.currentServerEntry?.address ?: "multi_player_server"
            }
        val dataName = "hypermap_chunks"
        return gameDir
            .resolve("infinite")
            .resolve("data")
            .resolve(if (isSinglePlayer) "single_player" else "multi_player")
            .resolve(serverName)
            .resolve(dimension)
            .resolve(dataName)
    }

    private fun getDimensionKey(): String {
        val world = client.world ?: return "minecraft_overworld"
        val dimensionId = world.registryKey.value
        return dimensionId?.toString()?.replace(":", "_") ?: "minecraft_overworld"
    }

    fun getCachedChunkImage(
        chunkX: Int,
        chunkZ: Int,
    ): CachedChunkImage? {
        val key = getChunkKey(chunkX, chunkZ)
        return chunkImages[key]
    }

    fun putChunkImage(
        chunkX: Int,
        chunkZ: Int,
        image: NativeImage,
    ) {
        val key = getChunkKey(chunkX, chunkZ)
        val identifier = Identifier.of("infinite", "hypermap/chunk_${chunkX}_$chunkZ")

        // Close existing image and unregister texture if any
        chunkImages.remove(key)?.let { cached ->
            cached.image.close()
            client.textureManager.destroyTexture(cached.identifier)
        }

        client.textureManager.registerTexture(identifier)
        chunkImages[key] = CachedChunkImage(image, identifier)
    }

    fun invalidateChunk(
        chunkX: Int,
        chunkZ: Int,
    ) {
        val key = getChunkKey(chunkX, chunkZ)
        chunkImages.remove(key)?.let { cached ->
            cached.image.close()
            client.textureManager.destroyTexture(cached.identifier)
        }
    }

    fun clearCache() {
        chunkImages.values.forEach { cached ->
            cached.image.close()
            client.textureManager.destroyTexture(cached.identifier)
        }
        chunkImages.clear()
    }

    fun saveChunkImage(
        chunkX: Int,
        chunkZ: Int,
        image: NativeImage,
    ) {
        val dimension = getDimensionKey()
        val dataDir = getDataDirectory(dimension).toFile()
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
        val file = File(dataDir, "chunk_${chunkX}_$chunkZ.png")
        image.writeTo(file)
    }

    fun loadChunkImage(
        chunkX: Int,
        chunkZ: Int,
    ): NativeImage? {
        val dimension = getDimensionKey()
        val dataDir = getDataDirectory(dimension).toFile()
        val file = File(dataDir, "chunk_${chunkX}_$chunkZ.png")
        if (file.exists()) {
            return try {
                NativeImage.read(file.inputStream())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        return null
    }
}
