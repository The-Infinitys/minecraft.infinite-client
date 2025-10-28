package org.infinite.features.utils.map

import com.google.gson.Gson
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import java.awt.image.BufferedImage
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories

object MapTextureManager {
    private const val CHUNK_SIZE = 16

    // キャッシュキーは dimension_chunkX_chunkZ_fileName になります
    private val textureCache: ConcurrentHashMap<String, NativeImageBackedTexture> = ConcurrentHashMap()
    private val gson = Gson()

    /**
     * チャンクのブロックデータからテクスチャを生成し、ファイルに保存します。
     * また、生成されたテクスチャを管理し、Identifierを返します。
     * @param fileName surface.png または section_Y.png (Yはセクション開始Y)
     */
    fun saveAndRegisterTexture(
        chunkX: Int,
        chunkZ: Int,
        dimensionKey: String,
        fileName: String,
        blockData: List<ChunkBlockData>,
    ): Identifier? {
        val image = BufferedImage(CHUNK_SIZE, CHUNK_SIZE, BufferedImage.TYPE_INT_ARGB)
        // すべてのピクセルを透明で初期化
        for (y in 0 until CHUNK_SIZE) {
            for (x in 0 until CHUNK_SIZE) {
                image.setRGB(x, y, 0x00000000)
            }
        }

        for (data in blockData) {
            val relativeX = (data.x % CHUNK_SIZE + CHUNK_SIZE) % CHUNK_SIZE
            val relativeZ = (data.z % CHUNK_SIZE + CHUNK_SIZE) % CHUNK_SIZE
            image.setRGB(relativeX, relativeZ, data.color)
        }
        val chunkDir = getChunkDirectory(chunkX, chunkZ, dimensionKey)
        chunkDir.createDirectories() // ディレクトリが存在しない場合は作成
        val outputFile = chunkDir.resolve(fileName).toFile()

        try {
            ImageIO.write(image, "PNG", outputFile)
        } catch (e: Exception) {
            System.err.println("Failed to save chunk texture ($fileName) for chunk ($chunkX, $chunkZ): ${e.message}")
        }

        // 3. NativeImageBackedTextureの管理と更新
        val cacheKey = "${dimensionKey}_${chunkX}_${chunkZ}_$fileName"
        val identifier = Identifier.of("infinite", "map_chunk_$cacheKey")
        val client = MinecraftClient.getInstance()
        val textureManager = client.textureManager

        val nativeImage = NativeImage(image.width, image.height, true) // true = ARGB
        for (imgY in 0 until image.height) {
            for (imgX in 0 until image.width) {
                nativeImage.setColorArgb(imgX, imgY, image.getRGB(imgX, imgY))
            }
        }

        val existingTexture = textureCache[cacheKey]
        if (existingTexture != null) {
            // Update existing texture (テクスチャを置き換える)
            existingTexture.image?.close() // 古い NativeImage を解放
            existingTexture.image = nativeImage
            existingTexture.upload() // GPUにアップロード
        } else {
            // Register new texture
            val newTexture = NativeImageBackedTexture({ "map_chunk_$cacheKey" }, nativeImage)
            textureManager.registerTexture(identifier, newTexture)
            textureCache[cacheKey] = newTexture
        }
        return identifier
    }

    /**
     * ファイルシステムからテクスチャをロードし、NativeImageBackedTextureとして登録・キャッシュします。
     */
    fun loadAndRegisterTextureFromFile(
        chunkX: Int,
        chunkZ: Int,
        dimensionKey: String,
        fileName: String,
    ): Identifier? {
        val cacheKey = "${dimensionKey}_${chunkX}_${chunkZ}_$fileName"
        // 既にキャッシュされている場合は再ロードしない
        if (textureCache.containsKey(cacheKey)) {
            return Identifier.of("infinite", "map_chunk_$cacheKey")
        }
        val chunkDir = getChunkDirectory(chunkX, chunkZ, dimensionKey)
        val inputFile = chunkDir.resolve(fileName).toFile()
        if (!inputFile.exists()) {
            return null // ファイルが存在しない場合はスキップ
        }
        val identifier = Identifier.of("infinite", "map_chunk_$cacheKey")
        val client = MinecraftClient.getInstance()
        val textureManager = client.textureManager
        try {
            val image = ImageIO.read(inputFile) ?: throw IllegalStateException("ImageIO failed to read file: $fileName")
            val nativeImage = NativeImage(image.width, image.height, true)
            for (imgY in 0 until image.height) {
                for (imgX in 0 until image.width) {
                    nativeImage.setColorArgb(imgX, imgY, image.getRGB(imgX, imgY))
                }
            }
            // テクスチャを登録・キャッシュ
            val newTexture = NativeImageBackedTexture({ "map_chunk_$cacheKey" }, nativeImage)
            textureManager.registerTexture(identifier, newTexture)
            textureCache[cacheKey] = newTexture
            return identifier
        } catch (e: Exception) {
            System.err.println("Failed to load and register chunk texture ($fileName) from file for chunk ($chunkX, $chunkZ): ${e.message}")
            return null
        }
    }

    /**
     * 指定されたIdentifierに関連付けられたテクスチャを解放し、キャッシュから削除します。
     * @param fileName surface.png または section_Y.png
     */
    fun unloadTexture(
        chunkX: Int,
        chunkZ: Int,
        dimensionKey: String,
        fileName: String,
    ) {
        val cacheKey = "${dimensionKey}_${chunkX}_${chunkZ}_$fileName"
        val identifier = Identifier.of("infinite", "map_chunk_$cacheKey")
        val client = MinecraftClient.getInstance()
        val textureManager = client.textureManager
        val texture = textureCache.remove(cacheKey)
        if (texture != null) {
            // 1. Minecraftのテクスチャマネージャーから登録を解除（GPU/GLメモリの解放）
            textureManager.destroyTexture(identifier)
            // 2. NativeImageを閉じる（Javaメモリの解放）
            texture.close()
        }
    }

    /**
     * チャンクのメタデータ (info.json) を保存します。
     */
    fun saveChunkInfo(
        chunkX: Int,
        chunkZ: Int,
        dimensionKey: String,
        info: ChunkInfo,
    ) {
        val chunkDir = getChunkDirectory(chunkX, chunkZ, dimensionKey)
        chunkDir.createDirectories()
        val outputFile = chunkDir.resolve("info.json").toFile()
        try {
            outputFile.writeText(gson.toJson(info))
        } catch (e: Exception) {
            System.err.println("Failed to save chunk info for chunk ($chunkX, $chunkZ): ${e.message}")
        }
    }

    /**
     * キャッシュから指定されたチャンクのテクスチャIdentifierを返します。（存在チェック用）
     */
    fun getChunkTextureIdentifier(
        chunkX: Int,
        chunkZ: Int,
        dimensionKey: String,
        fileName: String, // surface.png または section_Y.png
    ): Identifier? {
        val cacheKey = "${dimensionKey}_${chunkX}_${chunkZ}_$fileName"
        return if (textureCache.containsKey(cacheKey)) {
            Identifier.of("infinite", "map_chunk_$cacheKey")
        } else {
            null
        }
    }

    /**
     * キャッシュされているすべてのテクスチャをクリアし、Minecraftのテクスチャマネージャーから登録解除します。
     */
    fun clearCache() {
        val client = MinecraftClient.getInstance()
        val textureManager = client.textureManager
        textureCache.forEach { (key, texture) ->
            val identifier = Identifier.of("infinite", "map_chunk_$key")
            textureManager.destroyTexture(identifier)
            texture.close()
        }
        textureCache.clear()
    }

    // ----------------------------------------------------------------------
    // Path Helpers
    // ----------------------------------------------------------------------

    /**
     * マップデータを保存するベースディレクトリのパスを返します。
     */
    private fun getMapDataDirectory(dimension: String): Path {
        val client = MinecraftClient.getInstance()
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

        val dataName = "maps"
        return gameDir
            .resolve("infinite")
            .resolve("data")
            .resolve(if (isSinglePlayer) "single_player" else "multi_player")
            .resolve(serverName)
            .resolve(dimension)
            .resolve(dataName)
    }

    /**
     * チャンクのディレクトリパスを返します。
     */
    private fun getChunkDirectory(
        chunkX: Int,
        chunkZ: Int,
        dimensionKey: String,
    ): Path = getMapDataDirectory(dimensionKey).resolve("chunk_${chunkX}_$chunkZ")

    val dimensionKey: String
        get() {
            val world = MinecraftClient.getInstance().world ?: return "minecraft_overworld"
            val dimensionId = world.registryKey.value
            return dimensionId?.toString()?.replace("_", "-")?.replace(":", "_") ?: "minecraft_overworld"
        }
}
