package org.infinite.features.utils.map

// チャンク内の各ブロックの位置と色のデータを保持するデータクラス
data class ChunkBlockData(
    val x: Int,
    val y: Int,
    val z: Int,
    val color: Int,
)

/**
 * チャンクの情報を保持するメタデータJSON用データクラス
 * @param maxBlockY チャンク内の最も高いブロックのY座標
 */
data class ChunkInfo(
    val maxBlockY: Int,
)
