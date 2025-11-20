package org.infinite.features.rendering.ui

import net.minecraft.util.hit.HitResult
import org.infinite.InfiniteClient
import org.infinite.features.rendering.detailinfo.DetailInfo
import org.infinite.libs.client.player.ClientInterface
import org.infinite.libs.graphics.Graphics2D
import org.infinite.utils.rendering.transparent
import org.infinite.utils.toRadians
import kotlin.math.PI

class CrosshairRenderer : ClientInterface() {
    fun render(graphics2D: Graphics2D) {
        val accentColor = InfiniteClient.theme().colors.primaryColor
        val scaledWidth = graphics2D.width
        val scaledHeight = graphics2D.height
        val boxSize = 16.0
        val boxHalfSize = boxSize / 2
        val targetHit = findCrossHairTarget() ?: return

        // 既存のクロスヘア描画ロジック (省略) ...
        when (targetHit.type) {
            HitResult.Type.ENTITY -> {
                graphics2D.drawBorder(
                    scaledWidth / 2.0 - boxHalfSize,
                    scaledHeight / 2.0 - boxHalfSize,
                    boxSize,
                    boxSize,
                    accentColor,
                )
                graphics2D.drawLine(
                    scaledWidth / 2.0 - boxHalfSize * 1.5,
                    scaledHeight / 2.0,
                    scaledWidth / 2.0 + boxHalfSize * 1.5,
                    scaledHeight / 2.0,
                    accentColor,
                    2,
                )
                graphics2D.drawLine(
                    scaledWidth / 2.0,
                    scaledHeight / 2.0 - boxHalfSize * 1.5,
                    scaledWidth / 2.0,
                    scaledHeight / 2.0 + boxHalfSize * 1.5,
                    accentColor,
                    2,
                )
            }

            HitResult.Type.BLOCK -> {
                graphics2D.drawBorder(
                    scaledWidth / 2.0 - boxHalfSize,
                    scaledHeight / 2.0 - boxHalfSize,
                    boxSize,
                    boxSize,
                    accentColor,
                )
            }

            HitResult.Type.MISS -> {
                graphics2D.drawLine(
                    scaledWidth / 2.0 - boxHalfSize,
                    scaledHeight / 2.0,
                    scaledWidth / 2.0 + boxHalfSize,
                    scaledHeight / 2.0,
                    0xFF888888.toInt(),
                    2,
                )
                graphics2D.drawLine(
                    scaledWidth / 2.0,
                    scaledHeight / 2.0 - boxHalfSize,
                    scaledWidth / 2.0,
                    scaledHeight / 2.0 + boxHalfSize,
                    0xFF888888.toInt(),
                    2,
                )
            }
        }
        val player = player ?: return
        val cooldownProgress = player.getAttackCooldownProgress(graphics2D.tickProgress)
        if (cooldownProgress < 1.0f) {
            val centerX = scaledWidth / 2.0f
            val centerY = scaledHeight / 2.0f

            // クロスヘアのボックスサイズから少し外側に描画する半径を設定
            val radius = (boxHalfSize + 4).toFloat()
            val lineThickness = 3 // 描画する円弧の太さ
            val arcAngle = cooldownProgress * (2 * PI).toFloat()
            // 円弧描画ロジック
            graphics2D.drawArc(
                centerX,
                centerY,
                radius,
                toRadians(90f),
                arcAngle,
                lineThickness,
                accentColor.transparent(128),
            )
        }
    }

    fun findCrossHairTarget(): HitResult? {
        val player = player ?: return null
        val camera = client.cameraEntity ?: return null
        val blockInteractionRange = player.blockInteractionRange
        val entityInteractionRange = player.entityInteractionRange
        return InfiniteClient
            .getFeature(DetailInfo::class.java)
            ?.findCrosshairTarget(camera, blockInteractionRange, entityInteractionRange)
    }
}
