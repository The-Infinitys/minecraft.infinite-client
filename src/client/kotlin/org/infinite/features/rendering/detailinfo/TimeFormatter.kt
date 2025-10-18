package org.infinite.features.rendering.detailinfo

import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text

object TimeFormatter {
    fun formatTime(seconds: Double): String =
        if (seconds >= 60) {
            val minutes = (seconds / 60).toInt()
            val remainingSeconds = seconds % 60
            if (remainingSeconds >= 1) {
                "%dmin %.1fs".format(minutes, remainingSeconds)
            } else {
                "%dmin".format(minutes)
            }
        } else {
            "%.1fs".format(seconds)
        }

    fun getBreakingTimeText(
        progress: Float,
        client: MinecraftClient,
    ): Text {
        val player = client.player ?: return Text.empty()
        val world = client.world ?: return Text.empty()
        val interactionManager = client.interactionManager ?: return Text.empty()

        val blockPos = interactionManager.currentBreakingPos
        val blockState = client.world?.getBlockState(blockPos)
        val destroySpeed = blockState?.calcBlockBreakingDelta(player, world, blockPos) ?: 0.0f

        if (destroySpeed <= 0.0001f) return Text.literal("Indestructible")

        val totalTicks = 1.0f / destroySpeed
        val remainingTicks = (1.0f - progress) * totalTicks
        val totalSeconds = totalTicks / 20.0
        val remainingSeconds = remainingTicks / 20.0

        return Text.literal(
            "Time: ${
                formatTime(remainingSeconds)
            } / ${
                formatTime(totalSeconds)
            }",
        )
    }
}
