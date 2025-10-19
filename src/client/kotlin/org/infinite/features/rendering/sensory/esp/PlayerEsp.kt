package org.infinite.features.rendering.sensory.esp

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.Box
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.Vec3d
import org.infinite.libs.graphics.Graphics3D // Graphics3D をインポート
import org.infinite.libs.graphics.render.RenderUtils
import kotlin.math.sqrt

object PlayerEsp {
    // 枠の色は水色 (0xFF00FFFF) に固定
    private val BOX_COLOR =
        org.infinite.InfiniteClient
            .theme()
            .colors.aquaAccentColor
    private const val EXPAND = 0.05

    private fun otherPlayers(): List<PlayerEntity> {
        val client = MinecraftClient.getInstance()
        val world = client.world
        val self = client.player

        return world
            ?.entities
            ?.filter {
                // PlayerEntity かつ 自分自身ではない
                it is PlayerEntity && it != self && it.isAlive
            }?.map {
                it as PlayerEntity
            }
            ?: return emptyList()
    }

    /**
     * Graphics3D を利用して他のプレイヤーエンティティのアウトラインとコネクションラインを描画します。
     *
     * @param graphics3d 描画コンテキスト
     */
    fun render(
        graphics3d: Graphics3D, // Graphics3D を引数として受け取る
    ) {
        val client = MinecraftClient.getInstance()
        val self = client.player ?: return

        // Graphics3D から tickProgress を取得
        val tickProgress = graphics3d.tickProgress

        val players = otherPlayers()

        // 1. プレイヤーの枠線 (水色) を描画
        val renderBoxes =
            players.map { player ->
                RenderUtils.LinedColorBox(
                    BOX_COLOR,
                    playerBox(player, tickProgress).expand(EXPAND),
                )
            }
        // Graphics3D のメソッドを呼び出す
        graphics3d.renderLinedColorBoxes(renderBoxes, true)

        // 2. 自分とプレイヤーを結ぶ直線を描画
        val selfPos = client.player?.getLerpedPos(graphics3d.tickCounter.getTickProgress(true)) ?: return
        for (player in players) {
            val playerPos = playerPos(player, tickProgress)
            // プレイヤーの足元ではなく、目の高さ（中間点）を使用
            val playerLineTarget = playerPos.add(0.0, player.height / 2.0, 0.0)

            // 距離を計算 (X, Y, Zの距離)
            val dx = selfPos.x - playerLineTarget.x
            val dy = selfPos.y - playerLineTarget.y
            val dz = selfPos.z - playerLineTarget.z
            val distance = sqrt(dx * dx + dy * dy + dz * dz)

            // 距離に基づいて色を決定
            val lineColor = RenderUtils.distColor(distance)
            // Graphics3D のメソッドを利用して直線を描画
            graphics3d.renderLine(playerLineTarget, selfPos, lineColor, true)
        }
    }

    /**
     * プレイヤーの現在の描画位置に基づいてBoxを取得する
     */
    private fun playerBox(
        entity: PlayerEntity,
        tickProgress: Float,
    ): Box {
        if (entity.isRemoved) return entity.boundingBox
        val offset: Vec3d =
            playerPos(
                entity,
                tickProgress,
            ).subtract(entity.entityPos)
        return entity.boundingBox.offset(offset)
    }

    /**
     * tickProgress (partialTicks) を使用して、プレイヤーの補間された位置を計算する
     */
    private fun playerPos(
        entity: PlayerEntity,
        partialTicks: Float,
    ): Vec3d {
        if (entity.isRemoved) return entity.entityPos

        val x: Double = MathHelper.lerp(partialTicks.toDouble(), entity.lastRenderX, entity.x)
        val y: Double = MathHelper.lerp(partialTicks.toDouble(), entity.lastRenderY, entity.y)
        val z: Double = MathHelper.lerp(partialTicks.toDouble(), entity.lastRenderZ, entity.z)
        return Vec3d(x, y, z)
    }
}
