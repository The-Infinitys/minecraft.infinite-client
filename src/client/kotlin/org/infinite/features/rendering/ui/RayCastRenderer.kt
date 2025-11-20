package org.infinite.features.rendering.ui

import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import org.infinite.InfiniteClient
import org.infinite.libs.client.player.ClientInterface
import org.infinite.libs.graphics.Graphics3D
import org.infinite.libs.graphics.render.RenderUtils
import org.infinite.utils.rendering.transparent // 透明度ユーティリティのインポート

/**
 * プレイヤーの視線が捉えているエンティティまたはブロックを3D空間にハイライト描画するクラス。
 */
class RayCastRenderer : ClientInterface() {
    /**
     * メインの描画関数。レイトレース結果に応じて適切なハイライト関数を呼び出します。
     * @param graphics3D 3D描画コンテキスト
     */
    fun render(graphics3D: Graphics3D) {
        val player = player ?: return

        // プレイヤーのブロックとのインタラクション範囲を取得
        val blockReach = player.blockInteractionRange

        // レイトレースを実行
        val rayCastResult = player.raycast(blockReach, graphics3D.tickProgress, false)

        when (rayCastResult.type) {
            HitResult.Type.MISS -> return

            // EntityHitResult の場合はエンティティをハイライト
            HitResult.Type.ENTITY -> renderEntityHighLight(graphics3D, rayCastResult as EntityHitResult)

            // BlockHitResult の場合はブロックをハイライト
            HitResult.Type.BLOCK -> renderBlockHighLight(graphics3D, rayCastResult as BlockHitResult)
        }
    }

    /**
     * エンティティの周囲に輪郭線と半透明の塗りつぶしを描画します。
     * LinearBreakの描画スタイルに合わせて、塗りつぶしを追加しました。
     */
    fun renderEntityHighLight(
        graphics3D: Graphics3D,
        rayCast: EntityHitResult,
    ) {
        val entity = rayCast.entity
        // 補間された位置と境界ボックス
        val pos = entity.getLerpedPos(graphics3D.tickProgress)
        val boundingBox = entity.boundingBox
        val box = boundingBox.offset(pos)

        // 色の設定
        val primaryColor = InfiniteClient.theme().colors.primaryColor
        // 塗りつぶし用に透明度を調整
        val solidColor = primaryColor.transparent(70)

        // 1. 塗りつぶしボックスを描画
        val solidBoxes = listOf(RenderUtils.ColorBox(solidColor, box.contract(0.002))) // わずかに縮小
        graphics3D.renderSolidColorBoxes(solidBoxes, true)

        // 2. 輪郭ボックスを描画
        graphics3D.renderLinedBox(box, primaryColor, true)
    }

    /**
     * ブロックの周囲に輪郭線と、より強調された塗りつぶしを描画します。
     * LinearBreakの描画を参考に、固定の進行度(1.0)として強調描画を行います。
     */
    fun renderBlockHighLight(
        graphics3D: Graphics3D,
        rayCast: BlockHitResult,
    ) {
        val blockPos = rayCast.blockPos
        val world = world ?: return
        val interactionManager = interactionManager ?: return
        val blockState =
            world.getBlockState(blockPos)
        val progress = interactionManager.currentBreakingProgress
        val boundingBox =
            blockState
                .getOutlineShape(world, blockPos)
                .boundingBox
        val dynamicBox =
            boundingBox
                .contract((1 - progress) / 2.0)
                .offset(blockPos)
        val accentColor = InfiniteClient.theme().colors.primaryColor
        val solidColor = accentColor.transparent(90) // 塗りつぶし用に透明度を調整 (強めに)

        // 1. 強調された塗りつぶしボックスを描画
        if (interactionManager.isBreakingBlock) {
            val solidBoxes = listOf(RenderUtils.ColorBox(solidColor, dynamicBox))
            graphics3D.renderSolidColorBoxes(solidBoxes, true)
        }
    }
}
