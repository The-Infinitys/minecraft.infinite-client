package org.infinite.features.movement.braek

import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.settings.FeatureSetting
import org.infinite.utils.block.BlockUtils

class LinearBreak : ConfigurableFeature() {
    override val level: FeatureLevel = FeatureLevel.Cheat

    // --- 設定項目 ---
    private val breakRange =
        FeatureSetting.DoubleSetting(
            name = "Range",
            defaultValue = 5.0,
            min = 1.0,
            max = 6.0,
        )
    private val maxBlocks =
        FeatureSetting.DoubleSetting(
            name = "MaxBlocks",
            defaultValue = 64.0,
            min = 1.0,
            max = 500.0,
        )
    private val swingHand =
        FeatureSetting.BooleanSetting(
            name = "SwingHand",
            defaultValue = true,
        )

    // 修正点: AutoToolなどによるホットバー変更を無視するための設定
    private val ignoreHotbarChange =
        FeatureSetting.BooleanSetting(
            name = "IgnoreHotbarChange",
            defaultValue = false, // デフォルトは安全のため無効
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(breakRange, maxBlocks, swingHand, ignoreHotbarChange) // 設定に追加

    // 破壊対象のブロックリスト（LinkedHashSetで処理順を維持）
    private val blocksToMine = LinkedHashSet<BlockPos>()

    // 現在破壊中のブロックの位置と、そのブロックを叩いている面を保持
    var currentBreakingPos: BlockPos? = null
    private var currentBreakingSide: Direction? = null

    // 破壊進行度 (0.0 から 1.0) を保持
    private var currentBreakingProgress: Float = 0.0f

    // 破壊開始時のホットバーのスロットを保持
    private var startHotbarSlot: Int = -1

    override fun tick() {
        rayCast()
        mine()
    }

    fun add(pos: BlockPos) = blocksToMine.add(pos)

    /**
     * 射線追跡を行い、新しいブロックを追加し、範囲外のブロックを削除する。
     */
    private fun rayCast() {
        val range = breakRange.value
        val playerPos = playerPos ?: return
        // 2. 範囲外のブロックを安全に削除 (retainAllを使用)
        blocksToMine.retainAll { it.isWithinDistance(playerPos, range) }
    }

    /**
     * 破壊リスト内のブロックをサーバーに破壊させる。
     */
    private fun mine() {
        val interactionManager = client.interactionManager ?: return
        val player = player ?: return

        // 修正点: 設定が無効な場合のみ、ホットバー切り替え時のキャンセルチェックを行う
        if (!ignoreHotbarChange.value) {
            if (currentBreakingPos != null && startHotbarSlot != -1 && player.inventory.selectedSlot != startHotbarSlot) {
                if (autoToolCallBack == player.inventory.selectedSlot) {
                    startHotbarSlot = autoToolCallBack
                } else {
                    // ホットバーが切り替わった場合、破壊をキャンセルしリストから削除
                    interactionManager.cancelBlockBreaking()
                    blocksToMine.remove(currentBreakingPos)
                    // 状態をリセット
                    currentBreakingPos = null
                    currentBreakingSide = null
                    currentBreakingProgress = 0.0f
                    startHotbarSlot = -1
                    return
                }
            }
            // ----------------------------------------------------
        }

        // 1. 破壊対象のブロックがない場合、現在の破壊を中止
        if (blocksToMine.isEmpty()) {
            if (currentBreakingPos != null) {
                interactionManager.cancelBlockBreaking()
                currentBreakingPos = null
                currentBreakingSide = null
                currentBreakingProgress = 0.0f
            }
            return
        }

        // 2. ターゲットの決定とブロック状態のチェック
        val targetPos = blocksToMine.first()
        val blockState = client.world?.getBlockState(targetPos) ?: return

        // ターゲットブロックが流体なら、破壊リストから削除
        if (blockState.isAir || blockState.isOpaque) {
            blocksToMine.remove(targetPos)
            currentBreakingPos = null
            currentBreakingProgress = 0.0f
            return
        }

        // 3. 破壊パラメータの取得と有効性のチェック
        val params = BlockUtils.getBlockBreakingParams(targetPos)

        // パラメータが取得できない、または距離が離れすぎた場合
        if (params == null || params.distanceSq > breakRange.value * breakRange.value) {
            blocksToMine.remove(targetPos)
            currentBreakingPos = null
            currentBreakingProgress = 0.0f
            return
        }

        // 4. 破壊開始またはターゲット変更
        if (currentBreakingPos == null || currentBreakingPos != params.pos) {
            // 前の破壊を中止
            interactionManager.cancelBlockBreaking()

            // サーバーに視点変更パケットを送信
            BlockUtils.faceVectorPacket(params.hitVec)
            // 破壊開始

            // 状態の更新
            currentBreakingPos = params.pos
            currentBreakingSide = params.side
            currentBreakingProgress =
                0.0f // 新しいブロックなので0か                                                              ら開始
            interactionManager.attackBlock(params.pos, params.side)
            interactionManager.updateBlockBreakingProgress(params.pos, params.side)

            // IgnoreHotbarChangeが有効でない場合にのみスロットを保存
            startHotbarSlot =
                if (!ignoreHotbarChange.value) {
                    player.inventory.selectedSlot // ホットバーの状態を保存
                } else {
                    -1 // 無視する場合は状態を保存しない
                }
        } else {
            // 5. 進行中: 進行パケットを送信し、進行度を更新
            val pos = currentBreakingPos!!
            val side = currentBreakingSide!!
            interactionManager.updateBlockBreakingProgress(pos, side)
            currentBreakingProgress = interactionManager.currentBreakingProgress

            val fastBreak = InfiniteClient.getFeature(FastBreak::class.java) ?: return
            if (fastBreak.isEnabled() && !fastBreak.safeMode.value) {
                fastBreak.handle(pos)
            }
            // 破壊が完了したと見なせる場合（次のtickでブロックが消えるはずだが、念のため）
            if (currentBreakingProgress >= 1.0f) {
                currentBreakingPos = null
                currentBreakingProgress = 0.0f
            }
        }

        // 6. 手を振る
        if (swingHand.value) {
            player.swingHand(Hand.MAIN_HAND)
        }
    }

    override fun render3d(graphics3D: Graphics3D) {
        val color = InfiniteClient.theme().colors.primaryColor

        // 破壊対象ブロックのアウトライン描画
        val boxes =
            blocksToMine.map { pos ->
                val box = Box(pos)
                RenderUtils.ColorBox(color, box)
            }
        graphics3D.renderLinedColorBoxes(boxes, true)

        // 破壊中のブロックに進行度に応じたハイライトを追加
        currentBreakingPos?.let { pos ->
            val progress = currentBreakingProgress.coerceIn(0.0f, 1.0f)
            val scale = progress // 0.0fから1.0fまでスケール
            val offset = (1.0 - scale) * 0.5 // ブロックの中心から外側へ拡大するためのオフセット
            val minX = pos.x + offset
            val minY = pos.y + offset
            val minZ = pos.z + offset
            val maxX = pos.x + 1.0 - offset
            val maxY = pos.y + 1.0 - offset
            val maxZ = pos.z + 1.0 - offset

            // 進行度に応じて縮小/拡大するボックス
            val dynamicBox = Box(minX, minY, minZ, maxX, maxY, maxZ).contract(0.005)

            val boxes = listOf(RenderUtils.ColorBox(color, dynamicBox))
            graphics3D.renderSolidColorBoxes(boxes, true)
        }
    }

    val isWorking: Boolean
        get() = isEnabled() && !blocksToMine.isEmpty()
    var autoToolCallBack: Int = -1
}
