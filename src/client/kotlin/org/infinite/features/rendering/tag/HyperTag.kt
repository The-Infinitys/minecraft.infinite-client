package org.infinite.features.rendering.tag

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.math.ColorHelper
import org.infinite.ConfigurableFeature
import org.infinite.ConfigurableFeature.FeatureLevel
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.settings.FeatureSetting
import org.infinite.utils.rendering.getRainbowColor
import org.infinite.utils.rendering.transparent
import kotlin.math.sqrt // 平方根を計算するためにインポート

class HyperTag : ConfigurableFeature(initialEnabled = false) {
    override val level = FeatureLevel.UTILS
    private val mobs = FeatureSetting.BooleanSetting("Mobs", "feature.rendering.hypertag.mobs.description", true)
    private val players =
        FeatureSetting.BooleanSetting("Players", "feature.rendering.hypertag.players.description", true)
    private val distance =
        FeatureSetting.IntSetting("Distance", "feature.rendering.hypertag.distance.description", 64, 0, 256)
    private val always = FeatureSetting.BooleanSetting("Always", "feature.rendering.hypertag.always.description", false)
    private val showItems =
        FeatureSetting.BooleanSetting("ShowItems", "feature.rendering.hypertag.showitems.description", false)

    // 🚀 新規追加: 最小スケールになる距離の閾値
    private val minScaleDistance =
        FeatureSetting.IntSetting(
            "MinScaleDistance",
            "feature.rendering.hypertag.min_scale_distance.description",
            32,
            1,
            256,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            mobs,
            players,
            distance,
            always,
            showItems,
            minScaleDistance, // 設定に追加
        )

    private data class TagRenderInfo(
        val entity: LivingEntity,
        val pos: Graphics2D.DisplayPos,
        val distSq: Double, // 距離の二乗を保存
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
                    (players.value && it is PlayerEntity) || (mobs.value && it is MobEntity)
                }.map { it as LivingEntity }
                // 🚀 最適化: 距離フィルタリング
                .filter { player.squaredDistanceTo(it) < maxDistSq || maxDistSq == 0 || always.value }
                // 🚀 最適化: 体力満タンのモブの描画をスキップ (プレイヤーは常に表示)
                .filter { it is PlayerEntity || it.health < it.maxHealth || always.value }

        for (entity in livingEntities) {
            val aboveHeadPos =
                entity
                    .getLerpedPos(graphics3D.tickCounter.getTickProgress(false))
                    .add(0.0, entity.getEyeHeight(entity.pose) + 1.5, 0.0)
            val pos2d = graphics3D.toDisplayPos(aboveHeadPos)
            if (pos2d != null) {
                targetEntities.add(TagRenderInfo(entity, pos2d, player.squaredDistanceTo(entity))) // distSqを保存
            }
        }
    }

    /**
     * プログレスバーを描画します。
     * ... (変更なし)
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
        val barBackgroundColor =
            ColorHelper.getArgb(
                (128 * alpha).toInt(),
                ColorHelper.getRed(
                    org.infinite.InfiniteClient
                        .theme()
                        .colors.backgroundColor,
                ),
                ColorHelper.getGreen(
                    org.infinite.InfiniteClient
                        .theme()
                        .colors.backgroundColor,
                ),
                ColorHelper.getBlue(
                    org.infinite.InfiniteClient
                        .theme()
                        .colors.backgroundColor,
                ),
            ) // 背景色を塗りつぶし
        graphics2d.fill(x, y, width, height, barBackgroundColor)

        val fillWidth = (width * clampedProgress).toInt()
        if (fillWidth > 0) {
            val healthColor = getRainbowColor(progress * 0.4f)
            // 🚀 最適化: 単一の描画コールでバーの進捗部分を塗りつぶし
            graphics2d.fill(x, y, fillWidth, height, healthColor)
        }
    }

    override fun render2d(graphics2D: Graphics2D) {
        // アイテムの描画順序を定義
        val equipmentSlots =
            listOf(
                EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND,
            )
        val itemRenderSize = 16 // アイテム描画サイズ (通常16x16)
        val itemPadding = 2
        val minScaleDist = minScaleDistance.value.toDouble()
        val maxDist = distance.value.toDouble()

        for (renderInfo in targetEntities) {
            val entity = renderInfo.entity
            val pos = renderInfo.pos
            val distSq = renderInfo.distSq
            val distance = sqrt(distSq) // 距離の計算
            val isPlayer = entity is PlayerEntity

            // ----------------------------------------------------------------------
            // 🚀 距離によるスケール計算
            // 距離が minScaleDistance までの場合はスケール 1.0
            // minScaleDistance から distance.value までの間で 0.5 まで線形補間
            // distance.value を超える場合は描画されないため、ここで maxDist を上限とする
            val scale =
                if (distance <= minScaleDist) {
                    1.0f
                } else if (distance >= maxDist) {
                    0.5f // 理論上 maxDist を超える場合はここに来ないが、安全のために設定
                } else {
                    // 線形補間 (lerp): maxScale + (minScale - maxScale) * progress
                    // progress = (distance - minScaleDist) / (maxDist - minScaleDist)
                    val scaleRange = 1.0f - 0.5f // 0.5
                    val distRange = maxDist - minScaleDist

                    // distRange が 0 になる可能性があるためチェック
                    if (distRange <= 0.0) {
                        0.5f
                    } else {
                        val progress = ((distance - minScaleDist) / distRange).toFloat().coerceIn(0.0f, 1.0f)
                        1.0f - scaleRange * progress // 1.0 から 0.5 へ
                    }
                }
            // スケールを適用 (描画の中心を原点として扱うために pushState を使用)
            graphics2D.pushState()
            graphics2D.translate(pos.x.toFloat(), pos.y.toFloat())
            graphics2D.scale(scale, scale)

            // スケール適用後の座標計算は、(0, 0) を基準に行う

            // ----------------------------------------------------------------------
            // タグのコンテンツ計算 (スケール適用前と同じ値)

            val name = entity.name
            val displayName: String? = if (isPlayer) name.string else null
            val hasName = !displayName.isNullOrEmpty()

            val nameHeight = if (hasName) graphics2D.fontHeight() else 0
            val barHeight = graphics2D.fontHeight()

            val minWidth = graphics2D.textWidth("defaultNameText")

            val padding = 1
            val contentWidth =
                if (hasName) {
                    graphics2D.textWidth(displayName)
                } else {
                    minWidth
                }

            // 🚀 修正: アイテムを持つスロットの数をカウント
            val equippedItems = equipmentSlots.map { entity.getEquippedStack(it) }
            val visibleItemCount =
                if (showItems.value) {
                    equippedItems.count { !it.isEmpty } // 空でないアイテムスタックの数
                } else {
                    0
                }

            // 🚀 修正: アイテムの数に基づいてアイテムエリアの幅を計算
            val itemsAreaWidth =
                if (visibleItemCount > 0) {
                    // アイテムの幅 = アイテム数 * アイテムサイズ + (アイテム数 - 1) * パディング
                    visibleItemCount * itemRenderSize + (visibleItemCount - 1) * itemPadding
                } else {
                    0
                }

            val contentMaxWidth = contentWidth.coerceAtLeast(itemsAreaWidth)
            val width = contentMaxWidth.coerceAtLeast(minWidth) + padding * 2

            // 🚀 修正: アイテムがある場合のみ高さを追加
            val itemsHeight =
                if (visibleItemCount > 0) {
                    itemRenderSize + padding
                } else {
                    0
                }

            val height = nameHeight + barHeight + itemsHeight + padding * 2

            // スケール適用後の描画開始座標 (中央揃えのため pos.x, pos.y は (0, 0) に移動済み)
            val startX = -(width / 2)
            val startY = -height

            val healthPer = entity.health / entity.maxHealth
            val tagColor =
                when (entity) {
                    is PlayerEntity ->
                        org.infinite.InfiniteClient
                            .theme()
                            .colors.infoColor

                    is HostileEntity ->
                        org.infinite.InfiniteClient
                            .theme()
                            .colors.errorColor
                    is PassiveEntity ->
                        org.infinite.InfiniteClient
                            .theme()
                            .colors.greenAccentColor
                    else ->
                        org.infinite.InfiniteClient
                            .theme()
                            .colors.foregroundColor
                }
            val bgColor =
                org.infinite.InfiniteClient
                    .theme()
                    .colors.backgroundColor
                    .transparent(136)

            // render background
            graphics2D.fill(startX, startY, width, height, bgColor)
            graphics2D.drawBorder(startX, startY, width, height, tagColor, padding)

            // 名前の描画
            if (hasName) {
                graphics2D.drawText(displayName, startX + padding, startY + padding, tagColor, true)
            }

            // 体力バーの描画
            val barY = startY + nameHeight + padding
            drawBar(
                graphics2D,
                startX + padding,
                barY,
                width - padding * 2,
                barHeight,
                healthPer,
            )

            // 装備品の描画
            if (visibleItemCount > 0) {
                val itemsY = barY + barHeight + padding

                // アイテムエリアの左端を計算 (タグの幅に合わせて中央揃え)
                val itemsStartX = startX + (width - itemsAreaWidth) / 2

                var currentX = itemsStartX

                for (itemStack in equippedItems) {
                    if (!itemStack.isEmpty) {
                        // アイテムを16x16で描画
                        graphics2D.drawItem(itemStack, currentX, itemsY)
                    }

                    // 次のアイテム描画位置を更新 (アイテムがあるかどうかにかかわらず、スロットの順序で進む)
                    // ただし、幅の計算は visibleItemCount に基づいているため、
                    // ここではアイテムがあった場合にのみ currentX を進めることで、
                    // スキップされたアイテムのスペースを詰め、中央揃えを維持する必要があります。
                    if (!itemStack.isEmpty) {
                        currentX += itemRenderSize + itemPadding
                    }
                }
            }

            // スケールを元に戻す
            graphics2D.popState()
        }
    }
}
