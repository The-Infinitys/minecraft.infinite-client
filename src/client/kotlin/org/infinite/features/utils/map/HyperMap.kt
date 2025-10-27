package org.infinite.features.utils.map

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import org.infinite.ConfigurableFeature
import org.infinite.libs.graphics.Graphics2D
import org.infinite.settings.FeatureSetting
import org.infinite.utils.rendering.transparent
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

// =================================================================================================
// 1. Feature Class: HyperMap
// =================================================================================================

class HyperMap : ConfigurableFeature(initialEnabled = false) {
    enum class Mode {
        Flat, // 平面図 (地表ビュー)
        Solid, // 断面図 (スライスビュー)
    }

    // 設定項目
    val mode =
        FeatureSetting.EnumSetting<Mode>("Mode", "feature.utils.hypermapmode.description", Mode.Flat, Mode.entries)
    val radiusSetting = FeatureSetting.IntSetting("Radius", "feature.utils.hypermapradius.description", 32, 5, 256)
    val heightSetting = FeatureSetting.IntSetting("Height", "feature.utils.hypermapheight.description", 8, 1, 32)
    val marginPercent =
        FeatureSetting.IntSetting(
            "Margin",
            "feature.utils.hypermapmargin.description",
            4,
            0,
            40,
        )
    val sizePercent = FeatureSetting.IntSetting("Size", "feature.utils.hypermapsize.description", 40, 5, 100)
    val renderTerrain =
        FeatureSetting.BooleanSetting("Render Terrain", "feature.utils.hypermaprender_terrain.description", true)

    val useShading = FeatureSetting.BooleanSetting("Use Shading", "feature.utils.hypermapuseshading.description", true)

    override val settings: List<FeatureSetting<*>> =
        listOf(
            radiusSetting,
            heightSetting,
            marginPercent,
            sizePercent,
            mode,
            renderTerrain,
            useShading,
        )

    fun findTargetMobs(): List<LivingEntity> {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return emptyList()
        val player = client.player ?: return emptyList()

        val radius = radiusSetting.value
        val height = heightSetting.value

        val playerX = player.x
        val playerY = player.y
        val playerZ = player.z

        val targets = mutableListOf<LivingEntity>()

        for (entity in world.entities) {
            if (entity == player) continue

            if (entity is LivingEntity) {
                val dx = entity.x - playerX
                val dz = entity.z - playerZ
                val distanceSq = dx * dx + dz * dz

                if (distanceSq <= radius * radius) {
                    val dy = entity.y - playerY

                    if (dy >= -height && dy <= height) {
                        targets.add(entity)
                    }
                }
            }
        }
        return targets
    }

    @Volatile
    var nearbyMobs: List<LivingEntity> = listOf()

    private var tickCounter: Int = 0
    private val updateInterval = 10

    private val chunkUpdateIntervalMs = 5000L
    private val updatedChunks: ConcurrentHashMap<String, Long> = ConcurrentHashMap()
    private val loadedChunkKeys: ConcurrentHashMap<String, Boolean> = ConcurrentHashMap()
    private val textureUnloadInterval = 200

    private val undergroundYThreshold = 60

    /**
     * RGBカラー値を明るさレベルに基づいて調整します。
     * 最大: 1.25倍 (元の色 + 25%)、最小: 0.75倍 (元の色 - 25%黒を混ぜる)
     */
    private fun applyLighting(
        color: Int,
        brightnessFactor: Float,
    ): Int {
        val alpha = color ushr 24 and 0xFF
        var red = color ushr 16 and 0xFF
        var green = color ushr 8 and 0xFF
        var blue = color and 0xFF

        // 調整範囲: [0.75, 1.25]
        val minFactor = 0.75f
        val maxFactor = 1.25f

        // brightnessFactor (0.0 to 1.0) を [minFactor, maxFactor] に線形マッピング
        val finalFactor = minFactor + (maxFactor - minFactor) * brightnessFactor

        red = (red * finalFactor).toInt().coerceIn(0, 255)
        green = (green * finalFactor).toInt().coerceIn(0, 255)
        blue = (blue * finalFactor).toInt().coerceIn(0, 255)

        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    /**
     * RGBカラー値を彩度調整します (最大50%増加)。
     */
    private fun adjustSaturation(
        color: Int,
        saturationFactor: Float,
    ): Int {
        val alpha = color ushr 24 and 0xFF
        val javaColor = Color(color, true)

        // HSBモデルに変換
        val hsb = Color.RGBtoHSB(javaColor.red, javaColor.green, javaColor.blue, null)
        val hue = hsb[0]
        var saturation = hsb[1]
        val brightness = hsb[2]

        // 彩度を最大50%増加 (saturation * (1 + 0.5 * factor))
        val maxIncrease = 0.5f // 最大50%増
        saturation *= (1.0f + maxIncrease * saturationFactor)
        saturation = saturation.coerceIn(0.0f, 1.0f) // 1.0fにクランプ

        // HSBからRGBに戻す
        val newRgb = Color.HSBtoRGB(hue, saturation, brightness)
        val newColor = Color(newRgb)

        return (alpha shl 24) or (newColor.red shl 16) or (newColor.green shl 8) or newColor.blue
    }

    /**
     * Solidモード専用: Y座標に基づきシェーディング（高さ）補正します。
     */
    private fun applyShadingByHeight(
        color: Int,
        y: Int,
        yMin: Int,
        yMax: Int,
    ): Int {
        if (yMax <= yMin) return color

        val alpha = color ushr 24 and 0xFF
        val javaColor = Color(color, true)

        val hsb = Color.RGBtoHSB(javaColor.red, javaColor.green, javaColor.blue, null)
        val hue = hsb[0]
        val saturation = hsb[1]
        var brightness = hsb[2]

        val normalizedY = (y - yMin).toFloat() / (yMax - yMin).toFloat()

        // Solidモード: 地形高低を強調 (0.75倍〜1.25倍)
        val minFactor = 0.75f
        val maxFactor = 1.25f
        val factor = minFactor + (maxFactor - minFactor) * normalizedY

        brightness *= factor
        brightness = brightness.coerceIn(0.0f, 1.0f)

        val newRgb = Color.HSBtoRGB(hue, saturation, brightness)
        val newColor = Color(newRgb)

        return (alpha shl 24) or (newColor.red shl 16) or (newColor.green shl 8) or newColor.blue
    }

    /**
     * 周囲との高低差（勾配）に基づき明るさを調整します。
     * 光源は北西 (-X, -Z) から当たると仮定します。
     */
    private fun applySlopeShading(
        x: Int,
        z: Int,
        chunkX: Int,
        chunkZ: Int,
        heightMap: Array<IntArray>,
        color: Int,
        world: net.minecraft.world.World,
    ): Int {
        val currentY = heightMap[x][z]
        if (currentY <= world.bottomY) return color

        val alpha = color ushr 24 and 0xFF
        val javaColor = Color(color, true)
        val hsb = Color.RGBtoHSB(javaColor.red, javaColor.green, javaColor.blue, null)
        val hue = hsb[0]
        val saturation = hsb[1]
        var brightness = hsb[2]

        // 周囲との高低差を取得 (チャンク境界では隣接チャンクのデータを使用)

        // 1. 北側 (-Z方向)
        val northY =
            if (z > 0) {
                heightMap[x][z - 1]
            } else {
                world.getChunk(chunkX, chunkZ - 1)?.getHeightmap(Heightmap.Type.MOTION_BLOCKING)?.get(x, 15) ?: currentY
            }
        val diffNorth = northY - currentY // 正の値: 北側が高い (影になる)

        // 2. 東側 (+X方向)
        val eastY =
            if (x < 15) {
                heightMap[x + 1][z]
            } else {
                world.getChunk(chunkX + 1, chunkZ)?.getHeightmap(Heightmap.Type.MOTION_BLOCKING)?.get(0, z) ?: currentY
            }
        val diffEast = eastY - currentY // 正の値: 東側が高い (光を遮る)

        // ------------------------------------------------
        // 明るさ調整のルール: 光源は北西から
        // ------------------------------------------------

        var brightnessAdjustment = 0.0f
        val maxShade = 0.15f // 最大15%の明るさ調整の変動幅
        val slopeScale = 5.0f // 5ブロック差で最大の調整がかかるようにする

        // 影の処理 (北側、西側が高い場合)
        if (diffNorth > 0) {
            // 北側が高い（影になる） -> 暗くする
            brightnessAdjustment -= min(maxShade, diffNorth / slopeScale * maxShade)
        }
        // 西側の確認
        if (x > 0 && heightMap[x - 1][z] > currentY) {
            // 西側が高い（影になる） -> 暗くする
            brightnessAdjustment -= min(maxShade, (heightMap[x - 1][z] - currentY) / slopeScale * maxShade)
        }

        // 光の処理 (東側、南側が低い場合)
        if (diffEast < 0) {
            // 東側が低い（光が当たる） -> 明るくする
            brightnessAdjustment += min(maxShade, (currentY - eastY) / slopeScale * maxShade)
        }
        // 南側の確認
        if (z < 15 && heightMap[x][z + 1] < currentY) {
            // 南側が低い（光が当たる） -> 明るくする
            brightnessAdjustment += min(maxShade, (currentY - heightMap[x][z + 1]) / slopeScale * maxShade)
        }

        // 最終的な明るさを適用
        brightness += brightnessAdjustment
        brightness = brightness.coerceIn(0.0f, 1.0f)

        val newRgb = Color.HSBtoRGB(hue, saturation, brightness)
        val newColor = Color(newRgb)

        return (alpha shl 24) or (newColor.red shl 16) or (newColor.green shl 8) or newColor.blue
    }

    // 地下判定ヘルパー
    private fun isUnderground(playerY: Int): Boolean {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return false
        val player = client.player ?: return false

        val isForceSolid = mode.value == Mode.Solid
        val isBelowThreshold = playerY < undergroundYThreshold
        val isSkyObscured = !world.isSkyVisible(player.blockPos)

        return isForceSolid || (isBelowThreshold && isSkyObscured)
    }

    // ------------------------------------------------------------------------------------------------
    // tick()
    // ------------------------------------------------------------------------------------------------

    override fun tick() {
        nearbyMobs =
            if (isEnabled()) {
                findTargetMobs()
            } else {
                listOf()
            }

        if (isEnabled() && renderTerrain.value) {
            tickCounter++

            val client = MinecraftClient.getInstance()
            val world = client.world ?: return
            val player = client.player ?: return
            val dimensionKey = getDimensionKey()
            val radius = radiusSetting.value
            val playerY = player.blockY
            val playerBlockX = player.blockX
            val playerBlockZ = player.blockZ
            val playerChunkX = playerBlockX shr 4
            val playerChunkZ = playerBlockZ shr 4
            val currentSectionY = (playerY / 16) * 16

            val currentMode = if (isUnderground(playerY)) Mode.Solid else mode.value

            val texturesInRenderRange = mutableSetOf<String>()

            val globalScanMinY = world.bottomY
            val globalScanMaxY = world.bottomY + world.height

            // プレイヤーを中心とした半径内のチャンクをスキャン
            for (chunkXOffset in -radius / 16..radius / 16) {
                for (chunkZOffset in -radius / 16..radius / 16) {
                    val currentChunkX = playerChunkX + chunkXOffset
                    val currentChunkZ = playerChunkZ + chunkZOffset
                    val chunkKey = "${currentChunkX}_$currentChunkZ"
                    val chunkBaseKey = "${dimensionKey}_${currentChunkX}_$currentChunkZ"

                    val surfaceFileName = "surface.png"
                    val sectionFileName = "section_$currentSectionY.png"

                    val surfaceCacheKey = "${chunkBaseKey}_$surfaceFileName"
                    val sectionCacheKey = "${chunkBaseKey}_$sectionFileName"

                    // ------------------------------------------------
                    // A. テクスチャの読み込みチェック (ファイルからメモリへ)
                    // ------------------------------------------------
                    if (MapTextureManager.getChunkTextureIdentifier(currentChunkX, currentChunkZ, dimensionKey, surfaceFileName) == null) {
                        MapTextureManager.loadAndRegisterTextureFromFile(currentChunkX, currentChunkZ, dimensionKey, surfaceFileName)
                    }
                    texturesInRenderRange.add(surfaceCacheKey)

                    if (MapTextureManager.getChunkTextureIdentifier(currentChunkX, currentChunkZ, dimensionKey, sectionFileName) == null) {
                        MapTextureManager.loadAndRegisterTextureFromFile(currentChunkX, currentChunkZ, dimensionKey, sectionFileName)
                    }
                    texturesInRenderRange.add(sectionCacheKey)

                    // ------------------------------------------------
                    // B. データ生成と保存 (更新が必要な場合のみ)
                    // ------------------------------------------------
                    if (tickCounter % updateInterval == 0) {
                        val lastUpdateTime = updatedChunks[chunkKey] ?: 0L
                        if (System.currentTimeMillis() - lastUpdateTime > chunkUpdateIntervalMs) {
                            val scanMinY = world.bottomY
                            val scanMaxY = world.bottomY + world.height

                            // チャンク内の地表Y座標と元の色を保持する一時マップ
                            val heightMap = Array(16) { IntArray(16) { scanMinY - 1 } } // Y座標を保存
                            val rawBlockDataMap = mutableMapOf<Pair<Int, Int>, Pair<BlockPos, Int>>() // BlockPosとベースカラーを保存
                            var maxBlockY = world.bottomY

                            // 1. チャンクデータ収集 (パス1: Y座標とベースカラーの取得)
                            for (x in 0 until 16) {
                                for (z in 0 until 16) {
                                    var closestBlockPos: BlockPos? = null

                                    // a) Flatデータ収集 (空気ブロックではない最上部のブロック)
                                    for (y in scanMaxY downTo scanMinY) {
                                        val blockPos = BlockPos(currentChunkX * 16 + x, y, currentChunkZ * 16 + z)
                                        val blockState = world.getBlockState(blockPos)

                                        if (!blockState.isAir) {
                                            closestBlockPos = blockPos
                                            heightMap[x][z] = y // Y座標を保存
                                            maxBlockY = maxBlockY.coerceAtLeast(y)
                                            break // 空気ブロックではないブロックを見つけたらスキャン終了
                                        }
                                    }

                                    if (closestBlockPos != null) {
                                        val blockState = world.getBlockState(closestBlockPos)
                                        val baseBlockColor = blockState.mapColor.color
                                        rawBlockDataMap[x to z] = Pair(closestBlockPos, baseBlockColor)
                                    }
                                }
                            }

                            // 2. Surfaceテクスチャの最終生成 (パス2: シェーディング適用)
                            val chunkBlockDataMap = mutableMapOf<Pair<Int, Int>, ChunkBlockData>()
                            val surfaceDataList = mutableListOf<ChunkBlockData>()

                            for (x in 0 until 16) {
                                for (z in 0 until 16) {
                                    val rawData = rawBlockDataMap[x to z]
                                    if (rawData != null) {
                                        val (closestBlockPos, initialColor) = rawData
                                        val blockState = world.getBlockState(closestBlockPos)
                                        var finalColor = initialColor
                                        val blockAlpha = if (!blockState.fluidState.isEmpty) 128 else 255

                                        // 【シェーディング/ライティングの適用】
                                        if (useShading.value) {
                                            if (currentMode == Mode.Flat) {
                                                // 1. 勾配シェーディングを適用
                                                finalColor =
                                                    applySlopeShading(
                                                        x,
                                                        z,
                                                        currentChunkX,
                                                        currentChunkZ,
                                                        heightMap,
                                                        finalColor,
                                                        world,
                                                    )

                                                // 2. ライティング補正 (-25%から+25%の範囲)
                                                val lightLevel = world.getLightLevel(closestBlockPos) // 0 to 15
                                                val brightnessFactor = lightLevel / 15.0f
                                                finalColor = applyLighting(finalColor, brightnessFactor)

                                                // 3. 彩度補正 (最大50%増加)
                                                finalColor = adjustSaturation(finalColor, brightnessFactor)
                                            } else { // Mode.Solid
                                                // SolidモードではY座標シェーディングで高低差を強調
                                                finalColor =
                                                    applyShadingByHeight(
                                                        finalColor,
                                                        closestBlockPos.y,
                                                        globalScanMinY,
                                                        globalScanMaxY,
                                                    )
                                            }
                                        }

                                        val blockColor = finalColor.transparent(blockAlpha)

                                        val data =
                                            ChunkBlockData(
                                                closestBlockPos.x,
                                                closestBlockPos.y,
                                                closestBlockPos.z,
                                                blockColor,
                                            )
                                        chunkBlockDataMap[x to z] = data
                                        surfaceDataList.add(data)
                                    }
                                }
                            }

                            // 3. Surfaceテクスチャの生成・保存
                            MapTextureManager.saveAndRegisterTexture(
                                currentChunkX,
                                currentChunkZ,
                                dimensionKey,
                                surfaceFileName,
                                surfaceDataList,
                            )

                            // 4. Sectionテクスチャの生成・保存 (Solidモード用)
                            val sectionStartY = currentSectionY.coerceIn(scanMinY, scanMaxY - 16)
                            val sectionDataList = mutableListOf<ChunkBlockData>()

                            for (x in 0 until 16) {
                                for (z in 0 until 16) {
                                    for (y in (sectionStartY + 15) downTo sectionStartY) {
                                        val blockPos = BlockPos(currentChunkX * 16 + x, y, currentChunkZ * 16 + z)
                                        val blockState = world.getBlockState(blockPos)

                                        if (!blockState.isAir && blockState.isSolidBlock(world, blockPos)) {
                                            var baseBlockColor = blockState.mapColor.color

                                            // Solidモードの高低差強調を適用
                                            if (useShading.value) {
                                                baseBlockColor =
                                                    applyShadingByHeight(
                                                        baseBlockColor,
                                                        blockPos.y,
                                                        globalScanMinY,
                                                        globalScanMaxY,
                                                        // Solidモードのシェーディングを強制
                                                    )
                                            }

                                            val blockColor = baseBlockColor.transparent(255)

                                            sectionDataList.add(
                                                ChunkBlockData(
                                                    blockPos.x,
                                                    blockPos.y,
                                                    blockPos.z,
                                                    blockColor,
                                                ),
                                            )
                                            break
                                        }
                                    }
                                }
                            }

                            MapTextureManager.saveAndRegisterTexture(
                                currentChunkX,
                                currentChunkZ,
                                dimensionKey,
                                sectionFileName,
                                sectionDataList,
                            )

                            // 5. メタデータ (Info.json) の保存
                            val chunkInfo = ChunkInfo(maxBlockY)
                            MapTextureManager.saveChunkInfo(currentChunkX, currentChunkZ, dimensionKey, chunkInfo)

                            updatedChunks[chunkKey] = System.currentTimeMillis()
                        }
                    }
                }
            }

            // ------------------------------------------------
            // C. 範囲外のテクスチャの解放（メモリ削減）
            // ------------------------------------------------
            if (tickCounter % textureUnloadInterval == 0) {
                val currentLoadedKeys = loadedChunkKeys.keys().toList()
                currentLoadedKeys.forEach { cacheKey ->
                    if (cacheKey !in texturesInRenderRange) {
                        val parts = cacheKey.split("_")
                        if (parts.size >= 5) {
                            val dimension = parts[0]
                            val chunkX = parts[1].toInt()
                            val chunkZ = parts[2].toInt()
                            val fileName = parts.subList(3, parts.size).joinToString("_")
                            MapTextureManager.unloadTexture(chunkX, chunkZ, dimension, fileName)
                        }
                    }
                }
            }
            loadedChunkKeys.clear()
            texturesInRenderRange.forEach { loadedChunkKeys[it] = true }
        } else {
            tickCounter = 0
        }
    }

    // ------------------------------------------------------------------------------------------------
    // render2d() / disabled() / getDimensionKey()
    // ------------------------------------------------------------------------------------------------

    /**
     * GUI描画を実行します。Graphics2Dヘルパーを使用します。
     */
    override fun render2d(graphics2D: Graphics2D) {
        val client = MinecraftClient.getInstance()
        val player = client.player
        client.world

        val isUnderground =
            player?.let {
                isUnderground(it.blockY)
            } ?: (mode.value == Mode.Solid)

        val actualMode = if (isUnderground) Mode.Solid else Mode.Flat
        HyperMapRenderer.render(graphics2D, this, actualMode)
    }

    override fun disabled() {
        super.disabled()
        MapTextureManager.clearCache()
    }

    private fun getDimensionKey(): String {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return "minecraft_overworld"
        val dimensionId = world.registryKey.value
        return dimensionId?.toString()?.replace(":", "_") ?: "minecraft_overworld"
    }
}
