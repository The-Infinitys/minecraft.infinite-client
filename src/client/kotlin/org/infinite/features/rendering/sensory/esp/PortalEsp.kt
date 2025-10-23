package org.infinite.features.rendering.sensory.esp

import net.minecraft.client.MinecraftClient
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.world.chunk.ChunkSection
import net.minecraft.world.dimension.DimensionType
import org.infinite.InfiniteClient
import org.infinite.features.rendering.sensory.ExtraSensory
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.libs.world.WorldManager
import org.infinite.utils.rendering.transparent

object PortalEsp {
    // データ構造をListからMap<BlockPos, Int>に変更し、高速な追加/削除/ルックアップを可能にする
    private val portalPositions = mutableMapOf<BlockPos, Int>()

    // ARGB形式で色を定義
    private val NETHER_PORTAL_COLOR
        get() =
            InfiniteClient
                .theme()
                .colors.redAccentColor
                .transparent(64)
    private val END_GATEWAY_COLOR
        get() =
            InfiniteClient
                .theme()
                .colors.yellowAccentColor
                .transparent(64)
    private val END_PORTAL_FRAME_COLOR
        get() =
            InfiniteClient
                .theme()
                .colors.greenAccentColor
                .transparent(64)
    private val END_PORTAL_COLOR
        get() =
            InfiniteClient
                .theme()
                .colors.blueAccentColor
                .transparent(64)

    // ティックベースのスキャン状態を管理
    private const val SCAN_RADIUS_CHUNKS = 8 // プレイヤーを中心とする8チャンクの半径 (合計17x17チャンク)
    private val TOTAL_CHUNKS = (2 * SCAN_RADIUS_CHUNKS + 1).let { it * it }
    private var currentScanIndex = 0 // 現在走査中のチャンクのインデックス (0からTOTAL_CHUNKS-1)

    private fun getColorForBlock(blockId: String): Int? =
        when (blockId) {
            "minecraft:nether_portal" -> NETHER_PORTAL_COLOR
            "minecraft:end_portal_frame" -> END_PORTAL_FRAME_COLOR
            "minecraft:end_portal" -> END_PORTAL_COLOR
            "minecraft:end_gateway" -> END_GATEWAY_COLOR
            else -> null
        }

    // パケットによる即時更新ロジック (Mapを使用するように修正)
    fun handleChunk(chunk: WorldManager.Chunk) {
        when (chunk) {
            is WorldManager.Chunk.Data -> {
                // チャンクロードパケットが来た場合、そのチャンクを即座にスキャン
                scanChunk(chunk.x, chunk.z)
            }

            is WorldManager.Chunk.BlockUpdate -> {
                val pos = chunk.packet.pos
                // world.getBlockState(pos)はメインスレッドで安全に呼び出せる
                val blockState = MinecraftClient.getInstance().world?.getBlockState(pos)
                val blockId = blockState?.block?.let { Registries.BLOCK.getId(it).toString() }
                val color = blockId?.let { getColorForBlock(it) }

                if (color != null) {
                    // ポータルまたはフレームであれば Mapに追加/更新
                    portalPositions[pos] = color
                } else {
                    // 対象外のブロックであれば Mapから削除
                    portalPositions.remove(pos)
                }
            }

            is WorldManager.Chunk.DeltaUpdate -> {
                chunk.packet.visitUpdates { pos, state ->
                    val blockId = Registries.BLOCK.getId(state.block).toString()
                    val color = getColorForBlock(blockId)

                    if (color != null) {
                        portalPositions[pos] = color
                    } else {
                        portalPositions.remove(pos)
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
        // (X, Z)オフセットは [-SCAN_RADIUS_CHUNKS, SCAN_RADIUS_CHUNKS] の範囲になる
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
     * 指定されたチャンク内のポータルを走査し、結果を Map に追加/更新する。
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
                        portalPositions[pos] = color
                    }
                }
            }
        }
    }

    fun clear() {
        portalPositions.clear()
        currentScanIndex = 0 // スキャンインデックスもリセット
    }

    fun render(
        graphics3D: Graphics3D,
        value: ExtraSensory.Method,
    ) {
        // Mapのエントリをイテレート
        val boxes =
            portalPositions.map { (pos, color) ->
                RenderUtils.ColorBox(
                    color, // ポータル情報から色を使用
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
        if (value == ExtraSensory.Method.HitBox) {
            graphics3D.renderSolidColorBoxes(boxes, true)
        }
        graphics3D.renderLinedColorBoxes(boxes, true)
    }
}
