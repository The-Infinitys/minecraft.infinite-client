package org.infinite.features.rendering.sensory.esp

import net.minecraft.client.MinecraftClient
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.chunk.ChunkSection
import net.minecraft.world.dimension.DimensionType
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.libs.world.WorldManager

object ContainerEsp {
    // データ構造: ブロック位置とその色 (ARGB)
    private val containerPositions = mutableMapOf<BlockPos, Int>()

    // ARGB形式でコンテナの色を定義
    private val TRAP_CHEST_COLOR =
        InfiniteClient
            .theme()
            .colors.orangeAccentColor
            .transparent(256)
    private val CHEST_COLOR = InfiniteClient.theme().colors.yellowAccentColor
    private val ENDER_CHEST_COLOR = InfiniteClient.theme().colors.magentaAccentColor
    private val FURNACE_COLOR = InfiniteClient.theme().colors.secondaryColor
    private val HOPPER_COLOR = InfiniteClient.theme().colors.greenAccentColor
    private val BARREL_COLOR = InfiniteClient.theme().colors.yellowAccentColor
    private val SHULKER_BOX_COLOR = InfiniteClient.theme().colors.aquaAccentColor
    private val DISPENSER_DROPPER_COLOR = InfiniteClient.theme().colors.redAccentColor
    private val BREWING_STAND_COLOR = InfiniteClient.theme().colors.blueAccentColor

    // ティックベースのスキャン状態を管理 (PortalEspと同様)
    private const val SCAN_RADIUS_CHUNKS = 8 // プレイヤーを中心とする8チャンクの半径 (合計17x17チャンク)
    private val TOTAL_CHUNKS = (2 * SCAN_RADIUS_CHUNKS + 1).let { it * it }
    private var currentScanIndex = 0 // 現在走査中のチャンクのインデックス

    /**
     * ブロックIDに基づいて対応する色を返す。
     * シュルカーボックスは全色をサポートするため、IDのプレフィックスでチェックする。
     */
    private fun getColorForBlock(blockId: String): Int? =
        when {
            blockId.endsWith("trapped_chest") -> TRAP_CHEST_COLOR
            blockId.endsWith("chest") -> CHEST_COLOR
            blockId.endsWith("ender_chest") -> ENDER_CHEST_COLOR
            blockId.endsWith("furnace") || blockId.endsWith("blast_furnace") || blockId.endsWith("smoker") -> FURNACE_COLOR
            blockId.endsWith("hopper") -> HOPPER_COLOR
            blockId.endsWith("barrel") -> BARREL_COLOR
            blockId.contains("shulker_box") -> SHULKER_BOX_COLOR // 全ての色のシュルカーボックスを検出
            blockId.endsWith("dispenser") || blockId.endsWith("dropper") -> DISPENSER_DROPPER_COLOR
            blockId.endsWith("brewing_stand") -> BREWING_STAND_COLOR
            else -> null
        }

    // パケットによる即時更新ロジック
    fun handleChunk(chunk: WorldManager.Chunk) {
        when (chunk) {
            is WorldManager.Chunk.Data -> {
                // チャンクロードパケットが来た場合、そのチャンクを即座にスキャン
                scanChunk(chunk.x, chunk.z)
            }

            is WorldManager.Chunk.BlockUpdate -> {
                val pos = chunk.packet.pos
                val blockState = MinecraftClient.getInstance().world?.getBlockState(pos)
                val blockId = blockState?.block?.let { Registries.BLOCK.getId(it).toString() }
                val color = blockId?.let { getColorForBlock(it) }

                if (color != null) {
                    // コンテナであれば Mapに追加/更新
                    containerPositions[pos] = color
                } else {
                    // 対象外のブロックであれば Mapから削除
                    containerPositions.remove(pos)
                }
            }

            is WorldManager.Chunk.DeltaUpdate -> {
                chunk.packet.visitUpdates { pos, state ->
                    val blockId = Registries.BLOCK.getId(state.block).toString()
                    val color = getColorForBlock(blockId)

                    if (color != null) {
                        containerPositions[pos] = color
                    } else {
                        containerPositions.remove(pos)
                    }
                }
            }
        }
    }

    private var currentDimension: DimensionType? = null

    /**
     * 毎ティック呼ばれる。プレイヤーを中心に、チャンクを順番に走査する (インクリメンタルスキャン)。
     */
    fun tick() {
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return

        // プレイヤーの現在地を中心とするチャンク座標
        val centerChunkX = player.chunkPos.x
        val centerChunkZ = player.chunkPos.z

        // 走査すべきチャンクの相対座標をインデックスから計算
        val relativeX = (currentScanIndex % (2 * SCAN_RADIUS_CHUNKS + 1)) - SCAN_RADIUS_CHUNKS
        val relativeZ = (currentScanIndex / (2 * SCAN_RADIUS_CHUNKS + 1)) - SCAN_RADIUS_CHUNKS

        // グローバルチャンク座標
        val targetChunkX = centerChunkX + relativeX
        val targetChunkZ = centerChunkZ + relativeZ

        // ターゲットチャンクをスキャン (メインスレッドで実行)
        scanChunk(targetChunkX, targetChunkZ)

        // 次のティックで次のチャンクを走査するようにインデックスを更新
        currentScanIndex = (currentScanIndex + 1) % TOTAL_CHUNKS
    }

    /**
     * 指定されたチャンク内のコンテナを走査し、結果を Map に追加/更新する。
     */
    private fun scanChunk(
        chunkX: Int,
        chunkZ: Int,
    ) {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return
        if (world.dimension != currentDimension) {
            currentDimension = world.dimension
            clear()
            currentScanIndex = 0
            return
        }
        // チャンクがロードされているかを確認し、取得
        val chunk: net.minecraft.world.chunk.Chunk? = world.getChunk(chunkX, chunkZ)

        if (chunk != null) {
            // チャンク内のすべてのセクションを走査
            for (chunkY in 0 until chunk.sectionArray.size) {
                val section = chunk.sectionArray[chunkY]
                if (section != null && !section.isEmpty) {
                    scanChunkSection(chunkX, chunkY, chunkZ, section, chunk.bottomY)
                }
            }
        }
    }

    /**
     * チャンクセクション内のブロックを走査し、Mapを更新するヘルパー関数
     */
    private fun scanChunkSection(
        chunkX: Int,
        chunkY: Int,
        chunkZ: Int,
        section: ChunkSection,
        minY: Int,
    ) {
        val chunkLength = 16
        // セクション内のローカル座標 (0-15) を走査
        for (y in 0 until chunkLength) {
            for (z in 0 until chunkLength) {
                for (x in 0 until chunkLength) {
                    val blockState = section.getBlockState(x, y, z)
                    val blockId = Registries.BLOCK.getId(blockState.block).toString()
                    getColorForBlock(blockId)?.let { color ->
                        val blockX = (chunkX * chunkLength) + x
                        val blockY = (chunkY * chunkLength + minY) + y
                        val blockZ = (chunkZ * chunkLength) + z
                        val pos = BlockPos(blockX, blockY, blockZ)
                        containerPositions[pos] = color
                    }
                }
            }
        }
    }

    fun clear() {
        containerPositions.clear()
        currentScanIndex = 0 // スキャンインデックスもリセット
    }

    fun render(graphics3D: Graphics3D) {
        // Mapのエントリをイテレート
        val boxes =
            containerPositions.map { (pos, color) ->
                RenderUtils.ColorBox(
                    color, // コンテナ情報から色を使用
                    Box(
                        pos.x.toDouble(),
                        pos.y.toDouble(),
                        pos.z.toDouble(),
                        // コンテナの中には1ブロック未満のサイズのものがあるが、
                        // 汎用性を高めるため、ここではPortalEspと同様に1x1x1のBoxを描画する。
                        pos.x + 1.0,
                        pos.y + 1.0,
                        pos.z + 1.0,
                    ),
                )
            }
        // 実線と枠線の両方を描画
        graphics3D.renderSolidColorBoxes(boxes, true)
        graphics3D.renderLinedColorBoxes(boxes, true)
    }
}
