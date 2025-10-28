package org.infinite.features.utils.map

import net.minecraft.block.Blocks
import net.minecraft.block.LeavesBlock
import net.minecraft.client.MinecraftClient
import net.minecraft.client.color.world.BiomeColors
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.world.Heightmap
import net.minecraft.world.World
import org.infinite.ConfigurableFeature
import org.infinite.libs.graphics.Graphics2D
import org.infinite.settings.FeatureSetting
import org.infinite.utils.rendering.transparent
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

// MapTextureManagerが使用するデータクラス（仮定：ローカル座標と色を保持）
// ※このクラスの実装が不明ですが、ここではローカル座標(0-15)を受け取ることを前提とします。
// data class ChunkBlockData(val x: Int, val y: Int, val z: Int, val color: Int)

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

    // =================================================================================================
    // 2. カラー/シェーディング ヘルパー
    // =================================================================================================

    /**
     * バイオーム依存のブロックのレンダリング色（ARGB整数値、アルファ値は255）を取得します。
     * MapColorではなく、バイオームの色補正を適用します。
     * @return ARGB形式の色
     */
    private fun getActualBlockColor(pos: BlockPos): Int {
        val world = world ?: return 0
        val state = world.getBlockState(pos)
        val block = state.block
        val biome = world.getBiome(pos)?.value() ?: return 0x00000000
        val x = pos.x.toDouble()
        val z = pos.z.toDouble()
        val color: Int =
            when (block) {
                Blocks.WATER, Blocks.WATER_CAULDRON -> {
                    Blocks.LAVA.defaultMapColor.color
                        .transparent(255)
                }
                Blocks.LAVA, Blocks.LAVA_CAULDRON ->
                    Blocks.WATER.defaultMapColor.color
                        .transparent(255)

                Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE -> {
                    0xFF00FFFF.toInt()
                }
                // 草ブロックや草 (Grass)
                Blocks.GRASS_BLOCK, Blocks.SHORT_GRASS, Blocks.TALL_GRASS -> {
                    val biomeColor = BiomeColors.GRASS_COLOR.getColor(biome, x, z)
                    0xFF000000.toInt() or (biomeColor and 0x00FFFFFF)
                }
                // 葉ブロック (LeavesBlock)
                is LeavesBlock -> {
                    val biomeColor = BiomeColors.FOLIAGE_COLOR.getColor(biome, x, z)
                    0xFF000000.toInt() or (biomeColor and 0x00FFFFFF)
                }
                // 海草、シダ
                Blocks.SEAGRASS, Blocks.TALL_SEAGRASS -> {
                    state.getMapColor(world, pos).color.transparent(255)
                }

                else -> {
                    state.getMapColor(world, pos).color.transparent(255)
                }
            }
        return color
    }

    /**
     * RGBカラー値を明るさレベルに基づいて調整します。
     * ARGB形式で分解・再構築します。
     */
    private fun applyLighting(
        color: Int,
        brightnessFactor: Float,
    ): Int {
        val alpha = color ushr 24 and 0xFF
        var red = color ushr 16 and 0xFF
        var green = color ushr 8 and 0xFF
        var blue = color and 0xFF // ARGB形式

        // 調整範囲: [0.75, 1.25]
        val minFactor = 0.75f
        val maxFactor = 1.25f

        // brightnessFactor (0.0 to 1.0) を [minFactor, maxFactor] に線形マッピング
        val finalFactor = minFactor + (maxFactor - minFactor) * brightnessFactor

        red = (red * finalFactor).toInt().coerceIn(0, 255)
        green = (green * finalFactor).toInt().coerceIn(0, 255)
        blue = (blue * finalFactor).toInt().coerceIn(0, 255)

        // ARGB形式で再構築
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
        val red = color ushr 16 and 0xFF
        val green = color ushr 8 and 0xFF
        val blue = color and 0xFF

        val hsb = Color.RGBtoHSB(red, green, blue, null)
        val hue = hsb[0]
        var saturation = hsb[1]
        val brightness = hsb[2]

        // 彩度を最大50%増加 (saturation * (1 + 0.5 * factor))
        val maxIncrease = 0.5f // 最大50%増
        saturation *= (1.0f + maxIncrease * saturationFactor)
        saturation = saturation.coerceIn(0.0f, 1.0f) // 1.0fにクランプ

        val newRgb = Color.HSBtoRGB(hue, saturation, brightness)
        val newColor = newRgb and 0xFFFFFF

        // ARGB形式で再構築
        return (alpha shl 24) or newColor
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
        val red = color ushr 16 and 0xFF
        val green = color ushr 8 and 0xFF
        val blue = color and 0xFF

        val hsb = Color.RGBtoHSB(red, green, blue, null)
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
        val newColor = newRgb and 0xFFFFFF

        // ARGB形式で再構築
        return (alpha shl 24) or newColor
    }

    /**
     * 周囲との高低差（勾配）に基づき明るさを調整します。
     */
    private fun applySlopeShading(
        x: Int,
        z: Int,
        chunkX: Int,
        chunkZ: Int,
        heightMap: Array<IntArray>,
        color: Int,
        world: World,
    ): Int {
        val currentY = heightMap[x][z]
        if (currentY <= world.bottomY) return color

        val alpha = color ushr 24 and 0xFF
        val red = color ushr 16 and 0xFF
        val green = color ushr 8 and 0xFF
        val blue = color and 0xFF

        val hsb = Color.RGBtoHSB(red, green, blue, null)
        val hue = hsb[0]
        val saturation = hsb[1]
        var brightness = hsb[2]

        // 周囲との高低差を取得 (チャンク境界では隣接チャンクのデータを使用)
        val northY =
            if (z > 0) {
                heightMap[x][z - 1]
            } else {
                world
                    .getChunk(chunkX, chunkZ - 1)
                    ?.getHeightmap(Heightmap.Type.MOTION_BLOCKING)
                    ?.get(x, 15) ?: currentY
            }
        val diffNorth = northY - currentY

        val eastY =
            if (x < 15) {
                heightMap[x + 1][z]
            } else {
                world
                    .getChunk(chunkX + 1, chunkZ)
                    ?.getHeightmap(Heightmap.Type.MOTION_BLOCKING)
                    ?.get(0, z) ?: currentY
            }
        val diffEast = eastY - currentY

        val westY =
            if (x > 0) {
                heightMap[x - 1][z]
            } else {
                world
                    .getChunk(chunkX - 1, chunkZ)
                    ?.getHeightmap(Heightmap.Type.MOTION_BLOCKING)
                    ?.get(15, z) ?: currentY
            }
        val diffWest = westY - currentY

        val southY =
            if (z < 15) {
                heightMap[x][z + 1]
            } else {
                world
                    .getChunk(chunkX, chunkZ + 1)
                    ?.getHeightmap(Heightmap.Type.MOTION_BLOCKING)
                    ?.get(x, 0) ?: currentY
            }
        val diffSouth = currentY - southY

        // ------------------------------------------------
        // 明るさ調整のルール: 光源は北西から
        // ------------------------------------------------

        var brightnessAdjustment = 0.0f
        val maxShade = 0.15f // 最大15%の明るさ調整の変動幅
        val slopeScale = 5.0f // 5ブロック差で最大の調整がかかるようにする

        // 影の処理 (北側、西側が高い場合)
        if (diffNorth > 0) {
            brightnessAdjustment -= min(maxShade, diffNorth / slopeScale * maxShade)
        }
        if (diffWest > 0) {
            brightnessAdjustment -= min(maxShade, diffWest / slopeScale * maxShade)
        }

        // 光の処理 (東側、南側が低い場合)
        if (diffEast < 0) {
            brightnessAdjustment += min(maxShade, (currentY - eastY) / slopeScale * maxShade)
        }
        if (diffSouth > 0) {
            brightnessAdjustment += min(maxShade, diffSouth / slopeScale * maxShade)
        }

        // 最終的な明るさを適用
        brightness += brightnessAdjustment
        brightness = brightness.coerceIn(0.0f, 1.0f)

        val newRgb = Color.HSBtoRGB(hue, saturation, brightness)
        val newColor = newRgb and 0xFFFFFF

        // ARGB形式で再構築
        return (alpha shl 24) or newColor
    }

    /**
     * 2つの色をアルファ値(液体の透過度)に基づいてブレンドします。
     * @param frontColor ARGB形式の前面の色 (液体)
     * @param backColor ARGB形式の背景の色 (下のブロック)
     * @param progress 0.0 (透明) から 1.0 (不透明) の値 (液体の不透明度)
     * @return ARGB形式のブレンドされた色
     */
    private fun blendColors(
        frontColor: Int,
        backColor: Int,
        progress: Float,
    ): Int {
        // ARGBを分解
        val frontR = frontColor ushr 16 and 0xFF
        val frontG = frontColor ushr 8 and 0xFF
        val frontB = frontColor and 0xFF
        val backR = backColor ushr 16 and 0xFF
        val backG = backColor ushr 8 and 0xFF
        val backB = backColor and 0xFF
        val progress = progress.coerceIn(0f, 1f)
        // 線形補間
        val finalR = (progress * frontR + (1f - progress) * backR).toInt().coerceIn(0, 255)
        val finalG = (progress * frontG + (1f - progress) * backG).toInt().coerceIn(0, 255)
        val finalB = (progress * frontB + (1f - progress) * backB).toInt().coerceIn(0, 255)
        // アルファ値を255 (不透明) として再構築
        return (255 shl 24) or (finalR shl 16) or (finalG shl 8) or finalB
    }

// =================================================================================================
// 3. メインロジック
// =================================================================================================

    // 地下判定ヘルパー
    private fun isUnderground(playerY: Int): Boolean {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return false
        val player = client.player ?: return false
        val isForceSolid = mode.value == Mode.Solid
        val isBelowThreshold = playerY < undergroundYThreshold
        val isSkyObscured = !world.isSkyVisible(player.blockPos)
        return isForceSolid || isBelowThreshold || isSkyObscured
    }

    override fun tick() {
        nearbyMobs = findTargetMobs()
        if (renderTerrain.value) {
            tickCounter++
            val world = world ?: return
            val player = player ?: return
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
                    // A. テクスチャの読み込みチェック (ファイルからメモリへ) (変更なし)
                    // ------------------------------------------------
                    if (MapTextureManager.getChunkTextureIdentifier(
                            currentChunkX,
                            currentChunkZ,
                            dimensionKey,
                            surfaceFileName,
                        ) == null
                    ) {
                        MapTextureManager.loadAndRegisterTextureFromFile(
                            currentChunkX,
                            currentChunkZ,
                            dimensionKey,
                            surfaceFileName,
                        )
                    }
                    texturesInRenderRange.add(surfaceCacheKey)

                    if (MapTextureManager.getChunkTextureIdentifier(
                            currentChunkX,
                            currentChunkZ,
                            dimensionKey,
                            sectionFileName,
                        ) == null
                    ) {
                        MapTextureManager.loadAndRegisterTextureFromFile(
                            currentChunkX,
                            currentChunkZ,
                            dimensionKey,
                            sectionFileName,
                        )
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

                            val heightMap = Array(16) { IntArray(16) { scanMinY - 1 } }
                            val rawBlockDataMap =
                                mutableMapOf<Pair<Int, Int>, Pair<BlockPos, Int>>()
                            var maxBlockY = world.bottomY

                            // 1. チャンクデータ収集 (パス1: Y座標とベースカラーの取得)
                            for (x in 0 until 16) {
                                for (z in 0 until 16) {
                                    var closestBlockPos: BlockPos?

                                    // スキャンロジックの実行
                                    for (y in scanMaxY downTo scanMinY) {
                                        val blockPos = BlockPos(currentChunkX * 16 + x, y, currentChunkZ * 16 + z)
                                        val blockState = world.getBlockState(blockPos)
                                        val block = blockState.block
                                        if (!blockState.isAir) {
                                            val isLiquidBlock =
                                                block == Blocks.WATER || block == Blocks.LAVA
                                            if (isLiquidBlock) {
                                                // 液体の場合、下のブロックをスキャンして色を混ぜる
                                                var underBlockY = y - 1
                                                var underBlockPos: BlockPos? = null
                                                var underBlockBaseColor: Int? = null

                                                // 液体ではないブロックを探す
                                                while (underBlockY >= scanMinY) {
                                                    val checkPos =
                                                        BlockPos(
                                                            currentChunkX * 16 + x,
                                                            underBlockY,
                                                            currentChunkZ * 16 + z,
                                                        )
                                                    val checkState = world.getBlockState(checkPos)

                                                    if (!checkState.isAir) {
                                                        // 空気でも液体でもないブロック
                                                        if (checkState.block != Blocks.WATER && checkState.block != Blocks.LAVA) {
                                                            underBlockPos = checkPos
                                                            // 下のブロックのベースカラーを取得
                                                            underBlockBaseColor =
                                                                getActualBlockColor(checkPos)
                                                            break
                                                        }
                                                    }
                                                    underBlockY--
                                                }

                                                // Liquidブロックの色を取得
                                                val liquidColor =
                                                    getActualBlockColor(blockPos)

                                                // 下のブロックが見つかった場合、色をブレンド
                                                if (underBlockPos != null && underBlockBaseColor != null) {
                                                    val blendedColor =
                                                        blendColors(
                                                            liquidColor,
                                                            underBlockBaseColor,
                                                            // Liquidの深さに基づいて不透明度を調整 (適当な値を設定)
                                                            (y - underBlockY).toFloat() / 10f,
                                                        )
                                                    closestBlockPos = blockPos // 描画座標は液体ブロックのY座標
                                                    heightMap[x][z] = y
                                                    maxBlockY = maxBlockY.coerceAtLeast(y)

                                                    // rawBlockDataMapにはブレンドされた色と液体の位置を格納
                                                    rawBlockDataMap[x to z] = Pair(closestBlockPos, blendedColor)
                                                    break
                                                }
                                                // 下のブロックが見つからなかった場合は、Liquidブロックの色をそのまま描画
                                                closestBlockPos = blockPos
                                                heightMap[x][z] = y
                                                maxBlockY = maxBlockY.coerceAtLeast(y)
                                                rawBlockDataMap[x to z] = Pair(closestBlockPos, liquidColor)
                                                break
                                            } else {
                                                // 液体ではない有効なブロック
                                                closestBlockPos = blockPos
                                                heightMap[x][z] = y
                                                maxBlockY = maxBlockY.coerceAtLeast(y)

                                                // getActualBlockColor() を使用して、バイオーム依存の色を取得
                                                val baseBlockColor = getActualBlockColor(blockPos)

                                                rawBlockDataMap[x to z] = Pair(closestBlockPos, baseBlockColor)
                                                break
                                            }
                                        }
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
                                        var finalColor = initialColor
                                        if (useShading.value) {
                                            if (currentMode == Mode.Flat) {
                                                // Flat/Netherモードでは勾配シェーディングを適用
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

                                                // ライティング補正
                                                val lightLevel = world.getLightLevel(closestBlockPos) // 0 to 15
                                                val brightnessFactor = lightLevel / 15.0f
                                                finalColor = applyLighting(finalColor, brightnessFactor)

                                                // 彩度補正
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
                                        val blockColor = finalColor

                                        // *** [クラッシュ修正] ChunkBlockDataにローカル座標 (0-15) を渡す ***
                                        val data =
                                            ChunkBlockData(
                                                x, // ローカルX座標 (0-15)
                                                closestBlockPos.y, // ワールドY座標 (高さ情報)
                                                z, // ローカルZ座標 (0-15)
                                                blockColor,
                                            )
                                        chunkBlockDataMap[x to z] = data
                                        surfaceDataList.add(data)
                                    }
                                }
                            }

                            MapTextureManager.saveAndRegisterTexture(
                                currentChunkX,
                                currentChunkZ,
                                dimensionKey,
                                surfaceFileName,
                                surfaceDataList,
                            )

                            // Sectionテクスチャの生成・保存
                            val sectionStartY = currentSectionY.coerceIn(scanMinY, scanMaxY - 16)
                            val sectionDataList = mutableListOf<ChunkBlockData>()
                            for (x in 0 until 16) {
                                for (z in 0 until 16) {
                                    for (y in (sectionStartY + 15) downTo sectionStartY) {
                                        val blockPos = BlockPos(currentChunkX * 16 + x, y, currentChunkZ * 16 + z)
                                        val blockState = world.getBlockState(blockPos)
                                        if (!blockState.isAir && blockState.isSolidBlock(world, blockPos)) {
                                            // getActualBlockColor() を使用して、ベースカラーを取得
                                            var baseBlockColor = getActualBlockColor(blockPos)

                                            if (useShading.value) {
                                                baseBlockColor =
                                                    applyShadingByHeight(
                                                        baseBlockColor,
                                                        blockPos.y,
                                                        globalScanMinY,
                                                        globalScanMaxY,
                                                    )
                                            }
                                            val blockColor = baseBlockColor
                                            sectionDataList.add(
                                                ChunkBlockData(
                                                    x, // ローカルX座標 (0-15)
                                                    blockPos.y, // ワールドY座標 (高さ情報)
                                                    z, // ローカルZ座標 (0-15)
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
                            val chunkInfo = ChunkInfo(maxBlockY)
                            MapTextureManager.saveChunkInfo(currentChunkX, currentChunkZ, dimensionKey, chunkInfo)
                            updatedChunks[chunkKey] = System.currentTimeMillis()
                        }
                    }
                }
            }
            if (tickCounter % textureUnloadInterval == 0) {
                val currentLoadedKeys = loadedChunkKeys.keys().toList()
                currentLoadedKeys.forEach { cacheKey ->
                    val parts = cacheKey.split("_")
                    if (parts.size >= 5) {
                        val dimension = parts[1]
                        val chunkX = parts[2].toInt()
                        val chunkZ = parts[3].toInt()
                        val fileName = parts.subList(4, parts.size).joinToString("_")
                        MapTextureManager.unloadTexture(chunkX, chunkZ, dimension, fileName)
                    }
                }
            }
            loadedChunkKeys.clear()
            texturesInRenderRange.forEach { loadedChunkKeys[it] = true }
        } else {
            tickCounter = 0
        }
    }

    override fun render2d(graphics2D: Graphics2D) {
        val client = MinecraftClient.getInstance()
        val player = client.player
        client.world

        val isUnderground =
            player?.let {
                isUnderground(it.blockY)
            } ?: (mode.value == Mode.Solid)

        // 描画モードを決定
        val actualMode =
            if (isUnderground) {
                Mode.Solid
            } else {
                Mode.Flat
            }

        HyperMapRenderer.render(graphics2D, this, actualMode)
    }

    override fun disabled() {
        MapTextureManager.clearCache()
    }

    private fun getDimensionKey(): String {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return "minecraft_overworld"
        val dimensionId = world.registryKey.value
        return dimensionId?.toString()?.replace(":", "_") ?: "minecraft_overworld"
    }
}
