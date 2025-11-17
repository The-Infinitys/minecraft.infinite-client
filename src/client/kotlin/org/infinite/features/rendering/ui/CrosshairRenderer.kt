package org.infinite.features.rendering.ui

import net.minecraft.util.hit.HitResult
import org.infinite.InfiniteClient
import org.infinite.features.rendering.detailinfo.DetailInfo
import org.infinite.libs.client.player.ClientInterface
import org.infinite.libs.graphics.Graphics2D

class CrosshairRenderer : ClientInterface() {
    fun render(graphics2D: Graphics2D) {
        val accentColor = InfiniteClient.theme().colors.primaryColor
        val scaledWidth = graphics2D.width
        val scaledHeight = graphics2D.height
        val boxSize = 16.0
        val boxHalfSize = boxSize / 2
        val targetHit = findCrossHairTarget() ?: return
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
