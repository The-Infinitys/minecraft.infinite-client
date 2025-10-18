package org.theinfinitys.features.automatic.woodcutter

import net.minecraft.client.MinecraftClient
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting

class WoodCutter : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<InfiniteSetting<*>> = listOf()
    override val available: Boolean = false

    /**
     * MinecraftのブロックIDが「*_log」のパターンに一致するかどうかを判定します。
     */
    fun isLogBlock(id: String): Boolean {
        // パターン: "minecraft:" で始まり、間に任意の1文字以上があり、"_log" で終わる
        val pattern = Regex("minecraft:.+_log")
        return pattern.matches(id)
    }

    /**
     * 探索範囲内の木を探します。
     * * @return 見つかった木のリスト。プレイヤーからの距離でソートされます。
     */
    private fun searchTrees(
        range: Int,
        height: Int,
    ): List<Tree> {
        val client = MinecraftClient.getInstance()
        val world = client.world ?: return emptyList()
        val player = client.player ?: return emptyList()

        val playerPos = player.blockPos
        val foundTrees = mutableListOf<Tree>()
        val searchedLogRoots = mutableSetOf<BlockPos>()

        // 探索範囲の計算
        val minX = playerPos.x - range
        val maxX = playerPos.x + range
        val minY = playerPos.y - height
        val maxY = playerPos.y + height
        val minZ = playerPos.z - range
        val maxZ = playerPos.z + range

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val currentPos = BlockPos(x, y, z)
                    if (searchedLogRoots.contains(currentPos)) continue

                    val blockState = world.getBlockState(currentPos)
                    val blockId = Registries.BLOCK.getId(blockState.block).toString()

                    if (isLogBlock(blockId)) {
                        val blockUnderPos = currentPos.down()
                        val blockUnderState = world.getBlockState(blockUnderPos)
                        val blockUnderId = Registries.BLOCK.getId(blockUnderState.block).toString()

                        // 根元判定: 現在のブロックがログであり、その下のブロックのIDが現在のログと同じでない
                        // (つまり、土や石など、ログでないブロックの上にあるログブロックを探す)
                        if (blockUnderId != blockId) {
                            val tree =
                                Tree(
                                    rootPos = currentPos,
                                    id = blockId,
                                    client = client,
                                )

                            // Treeのインスタンス化時に calculateLogBlocks() が呼ばれる
                            // ログの座標を事前に取得し、ログが存在するかチェックする
                            if (tree.logCount > 0) {
                                foundTrees.add(tree)
                                searchedLogRoots.add(currentPos)
                            }
                        }
                    }
                }
            }
        }
        // プレイヤーからの距離が近い順にソート
        return foundTrees.sortedBy { it.rootPos.getSquaredDistance(playerPos) }
    }
}

/**
 * 木のデータクラス。
 * 根元の座標から再帰的に接続されたすべての丸太ブロックを探索し、保持します。
 */
data class Tree(
    val rootPos: BlockPos,
    val id: String, // 丸太ブロックのID (例: "minecraft:oak_log")
    private val client: MinecraftClient,
) {
    val logBlocks: MutableList<BlockPos> = mutableListOf()
    val logCount: Int
        get() = logBlocks.size

    /**
     * 根元の座標から開始し、同じIDを持つ隣接ブロックを再帰的に探索してログブロックのリストを構築します。
     * * @return 見つかったログブロックの数
     */
    fun calculateLogBlocks(): Int {
        val world = client.world ?: return 0
        logBlocks.clear()

        val countedLogs: MutableSet<BlockPos> = mutableSetOf()
        val toSearch: MutableList<BlockPos> = mutableListOf(rootPos) // 探索キュー

        while (toSearch.isNotEmpty()) {
            val currentPos = toSearch.removeAt(0)

            if (countedLogs.contains(currentPos)) continue

            val state = world.getBlockState(currentPos)
            val currentBlockId = Registries.BLOCK.getId(state.block).toString()

            // IDが一致しない場合はスキップ
            if (currentBlockId != id) continue

            logBlocks.add(currentPos)
            countedLogs.add(currentPos)

            // 隣接する6方向を探索
            val searchDirections =
                listOf(
                    currentPos.up(),
                    currentPos.down(),
                    currentPos.north(),
                    currentPos.south(),
                    currentPos.east(),
                    currentPos.west(),
                )

            for (neighborPos in searchDirections) {
                if (!countedLogs.contains(neighborPos)) { // まだカウントされていないかチェック
                    val neighborState = world.getBlockState(neighborPos)
                    val neighborBlockId = Registries.BLOCK.getId(neighborState.block).toString()

                    // 隣接ブロックが同じ種類のログであれば、探索キューに追加
                    if (neighborBlockId == id) {
                        toSearch.add(neighborPos)
                    }
                }
            }
        }
        return logCount
    }

    init {
        // インスタンス作成時にログブロックを計算
        calculateLogBlocks()
    }

    override fun toString(): String = "$id Tree at (${rootPos.x}, ${rootPos.y}, ${rootPos.z}) with $logCount logs"
}
