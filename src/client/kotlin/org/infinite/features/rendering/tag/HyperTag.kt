package org.infinite.features.rendering.tag

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.ColorHelper
import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.settings.FeatureSetting

class HyperTag : ConfigurableFeature(initialEnabled = false) {
    override val level = FeatureLevel.UTILS
    private val mobs = FeatureSetting.BooleanSetting("Mobs", "Show Mob Info", true)
    private val players = FeatureSetting.BooleanSetting("Players", "Show Player Info", true)
    private val distance = FeatureSetting.IntSetting("Distance", "Display Distance (0 to Unlimited)", 64, 0, 256)
    override val settings: List<FeatureSetting<*>> =
        listOf(
            mobs,
            players,
            distance,
        )

    private data class TagRenderInfo(
        val entity: LivingEntity,
        val pos: Graphics2D.DisplayPos,
    )

    private val targetEntities: MutableList<TagRenderInfo> = mutableListOf()

    override fun render3d(graphics3D: Graphics3D) {
        targetEntities.clear()
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val entities = client.world?.entities ?: return

        val maxDistSq = distance.value * distance.value // 距離の2乗を事前に計算

        val livingEntities =
            entities
                .filter { it is LivingEntity }
                .filter {
                    (players.value && it is PlayerEntity) ||
                        (mobs.value && it is MobEntity)
                }.map { it as LivingEntity }
                // 🚀 最適化: 距離フィルタリング
                .filter { player.squaredDistanceTo(it) < maxDistSq || maxDistSq == 0 }
                // 🚀 最適化: 体力満タンのモブの描画をスキップ (プレイヤーは常に表示)
                .filter { it is PlayerEntity || it.health < it.maxHealth }

        for (entity in livingEntities) {
            val aboveHeadPos = entity.eyePos.add(0.0, 1.5, 0.0)
            val pos2d = graphics3D.toDisplayPos(aboveHeadPos)
            if (pos2d != null) {
                targetEntities.add(TagRenderInfo(entity, pos2d))
            }
        }
    }

    /**
     * プログレスバーを描画します。
     * * 🚀 最適化: 1ピクセルごとのループをやめ、fillでまとめて描画します。
     *
     * @param graphics2d 描画に使用する Graphics2D オブジェクト。
     * @param x バーの左上隅の X 座標。
     * @param y バーの左上隅の Y 座標。
     * @param width バーの全体の幅。
     * @param height バーの全体の高さ。
     * @param progress バーの進捗 (0.0 から 1.0 の浮動小数点数)。
     */
    private fun drawBar(
        graphics2d: Graphics2D,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        progress: Float,
        alpha: Float = 1.0f,
    ) {
        val clampedProgress = progress.coerceIn(0.0f, 1.0f)
        val barBackgroundColor = ColorHelper.getArgb((128 * alpha).toInt(), 50, 50, 50)
        // 背景色を塗りつぶし
        graphics2d.fill(x, y, width, height, barBackgroundColor)

        val fillWidth = (width * clampedProgress).toInt()
        if (fillWidth > 0) {
            // グラデーションの代わりに、体力進捗に応じて色を変える (緑 -> 黄 -> 赤)
            val progressColor = clampedProgress // 0.0 (低い) から 1.0 (高い)
            val r = (255 * (1 - progressColor)).toInt().coerceIn(0, 255) // progressが低いとRが増加
            val g = (255 * progressColor).toInt().coerceIn(0, 255) // progressが高いとGが増加
            val healthColor = ColorHelper.getArgb((255 * alpha).toInt(), r, g, 0)

            // 🚀 最適化: 単一の描画コールでバーの進捗部分を塗りつぶし
            graphics2d.fill(x, y, fillWidth, height, healthColor)

            // 注: もし元の ColorUtils.getGradientColor が単一の色ではなく、
            // 複雑なグラデーションを描画する実装だった場合、この代替はグラデーションではなくなります。
            // 複雑なグラデーションが必要な場合は、Graphics2Dに新しい効率的なグラデーション描画メソッドを実装する必要があります。
        }
    }

    override fun render2d(graphics2D: Graphics2D) {
        for (renderInfo in targetEntities) {
            val entity = renderInfo.entity
            val pos = renderInfo.pos
            val isPlayer = entity is PlayerEntity // プレイヤーかどうかのフラグ

            val name = entity.name // プレイヤーの場合に使用する名前

            // プレイヤーではない場合は名前を表示しない
            val displayName: String? = if (isPlayer) name.string else null

            // プレイヤーではないモブの場合、名前の表示スペースを考慮する必要がない
            val hasName = !displayName.isNullOrEmpty()

            // 表示される要素に応じてタグの高さを決定
            val nameHeight = if (hasName) graphics2D.fontHeight() else 0
            val barHeight = graphics2D.fontHeight() // 体力バーの高さ

            // タグの最小幅を定義
            val minWidth = graphics2D.textWidth("defaultNameText")

            val padding = 1
            val contentWidth =
                if (hasName) {
                    graphics2D.textWidth(displayName)
                } else {
                    minWidth
                }

            val width = contentWidth.coerceAtLeast(minWidth) + padding * 2
            val height = nameHeight + barHeight + padding * 2 // 名前と体力バーの合計

            val x = pos.x.toInt()
            val y = pos.y.toInt()
            val startX = x - width / 2
            val startY = y - height

            val healthPer = entity.health / entity.maxHealth
            val tagColor =
                when (entity) {
                    is PlayerEntity -> 0xFF00FFFF
                    is HostileEntity -> 0xFFFF0000 // 敵対モブ: 赤色
                    is PassiveEntity -> 0xFF00FF00 // 友好モブ: 緑色
                    else -> 0xFFFFFFFF
                }.toInt()
            val bgColor = 0x88000000.toInt()

            // render background
            graphics2D.fill(startX, startY, width, height, bgColor)
            graphics2D.drawBorder(startX, startY, width, height, tagColor, padding)

            // 体力バーの描画
            val barY = startY + nameHeight + padding // 名前の下にバーを配置
            drawBar(
                graphics2D,
                startX + padding,
                barY,
                width - padding * 2,
                barHeight, // バーの高さ
                healthPer,
            )

            // 名前の描画 (プレイヤーの場合のみ)
            if (hasName) {
                graphics2D.drawText(displayName, startX + padding, startY + padding, tagColor, true)
            }
        }
    }
}
