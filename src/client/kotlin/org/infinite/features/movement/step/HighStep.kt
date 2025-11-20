package org.infinite.features.movement.step

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.Vec3d
import org.infinite.feature.ConfigurableFeature
import org.infinite.settings.FeatureSetting
import kotlin.math.floor

/**
 * Step機能をパケット分割による急な階段シミュレーションに一本化する。
 * stepHeightの直接変更は行わない。
 *
 * 変更点:
 * - 現在のベロシティに基づき、次のティックで衝突する段差を予測する。
 * - 予測衝突時に、移動の過程をパケットでシミュレートする。
 */
class HighStep : ConfigurableFeature() {
    // 既存のMaxStepHeight設定
    private val maxStepHeight =
        FeatureSetting.DoubleSetting(
            "MaxStepHeight",
            1.0,
            1.0,
            10.0,
        )
    override val settings: List<FeatureSetting<*>> = listOf(maxStepHeight)

    override val level: FeatureLevel = FeatureLevel.Extend

    override fun onTick() {
        val player = this.player ?: return
        val world = this.world ?: return

        // 1. 基本的な前提条件チェック (既存と同様)
        if (!player.isOnGround || player.isClimbing || player.isTouchingWater || player.isInLava) return
        // 水平方向の衝突のチェックは、ここではベロシティ予測に置き換えるため、一旦削除（または次のステップで再評価）
        // if (!player.horizontalCollision) return // これを削除

        // 2. 移動入力とジャンプチェック (既存と同様)
        if (player.input.movementInput.length() <= 1e-5f) return
        if (options.jumpKey.isPressed) return

        // 3. 衝突の予測
        // 次のティックで到達する予測位置を計算
        val velocity = player.velocity
        val nextPredictedDiffX = velocity.x
        val nextPredictedDiffZ = velocity.z

        // 水平移動のみを考慮した予測バウンディングボックス
        // Yは現在の位置のまま、水平方向を予測位置に移動させる
        val currentBox = player.boundingBox.offset(0.0, 0.05, 0.0).expand(0.05) // わずかに調整
        val predictedCollisionBox = currentBox.offset(velocity.x, 0.0, velocity.z)

        // 予測位置で水平方向に衝突があるかをチェック
        val predictedCollisions = world.getCollisions(player, predictedCollisionBox)

        // 衝突がない場合、処理を終了
        if (predictedCollisions.toList().isEmpty()) return

        // 4. 段差の高さ検出と空間チェック
        // 衝突しているブロックの最大Y座標を検出
        val maxBlockY = predictedCollisions.maxOfOrNull { it.boundingBox.maxY } ?: Double.NEGATIVE_INFINITY

        // 予測位置での段差の高さ
        val step = maxBlockY - player.y

        // 許容範囲の段差かチェック
        if (step <= 0.0 || step > maxStepHeight.value) return

        // 上部空間のチェック (最大段差の上は空いているか)
        // 予測されたステップ量分上に移動しても衝突がないこと
        if (!world.isSpaceEmpty(player, currentBox.offset(0.0, step, 0.0))) return
        val stepIncrement = 0.5 // 階段の動作をシミュレートする
        val numSegments = step / stepIncrement
        val stepCount = floor(numSegments).toInt()
        // パケットでシミュレートする移動の過程
        for (i in 1..stepCount) {
            val currentStepHeight = stepIncrement * i // 0.5 * step から変更し、等間隔に分割
            // クライアント側では移動させず、パケットのみを送信してサーバーに移動を主張する
            // XとZは予測衝突地点の移動ベクトルを含めない（サーバー側で通常移動は処理される）
            // Y座標を現在のYからステップ分上昇させる主張をする
            player.networkHandler.sendPacket(
                PlayerMoveC2SPacket.PositionAndOnGround(
                    Vec3d(
                        player.x + nextPredictedDiffX * (i / stepCount.toDouble()),
                        player.y + currentStepHeight,
                        player.z + nextPredictedDiffZ * (i / stepCount.toDouble()),
                    ),
                    true,
                    player.horizontalCollision,
                ),
            )
        }
        player.setPos(player.x + nextPredictedDiffX, player.y + step, player.z + nextPredictedDiffZ)
    }
}
