package org.infinite.features.rendering.tag

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.ColorHelper
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.rendering.sensory.esp.ItemEsp
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.settings.FeatureSetting
import org.infinite.utils.rendering.getRainbowColor
import org.infinite.utils.rendering.transparent
import kotlin.math.sqrt

class HyperTag : ConfigurableFeature(initialEnabled = false) {
    override val level = FeatureLevel.Utils
    private val mobs = FeatureSetting.BooleanSetting("Mobs", true)
    private val players =
        FeatureSetting.BooleanSetting("Players", true)
    private val distance =
        FeatureSetting.IntSetting("Distance", 64, 0, 256)
    private val always = FeatureSetting.BooleanSetting("Always", false)
    private val showItems =
        FeatureSetting.BooleanSetting("ShowItems", false)

    private val minScaleDistance =
        FeatureSetting.IntSetting(
            "MinScaleDistance",
            32,
            1,
            256,
        )

    // 💡 新規設定: 透過度コントロール
    private val fadeStartDistance =
        FeatureSetting.IntSetting(
            "FadeStartDistance",
            30,
            1,
            256,
        )
    private val fadeEndDistance =
        FeatureSetting.IntSetting(
            "FadeEndDistance",
            60,
            1,
            256,
        )
    private val minAlpha =
        FeatureSetting.IntSetting(
            "MinAlphaPercent",
            30,
            0,
            100,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            mobs,
            players,
            distance,
            always,
            showItems,
            minScaleDistance,
            fadeStartDistance, // 設定に追加
            fadeEndDistance, // 設定に追加
            minAlpha, // 設定に追加
        )

    private data class TagRenderInfo(
        val entity: Entity,
        val pos: Graphics2D.DisplayPos,
        val distSq: Double, // 距離の二乗を保存
    )

    private val targetEntities: MutableList<TagRenderInfo> = mutableListOf()

    // アイテム描画用の定数をクラスレベルで定義
    private val itemRenderSize = 16
    private val itemPaddingSize = 2

    override fun render3d(graphics3D: Graphics3D) {
        targetEntities.clear()
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val entities = client.world?.entities ?: return

        val maxDistSq = distance.value * distance.value // 距離の2乗を事前に計算

        val filteredEntities =
            entities
                .filter { it is LivingEntity || (showItems.value && it is ItemEntity) } // ItemEntityを追加
                .filter {
                    val distCheck = player.squaredDistanceTo(it) < maxDistSq || maxDistSq == 0 || always.value
                    if (!distCheck) return@filter false

                    when (it) {
                        is PlayerEntity -> players.value // プレイヤー
                        is MobEntity -> mobs.value && (it.health < it.maxHealth || always.value) // モブ (体力満タン時はスキップ可能)
                        is ItemEntity -> showItems.value // 落ちているアイテム
                        else -> false
                    }
                }

        for (entity in filteredEntities) {
            val aboveHeadPos =
                when (entity) {
                    is LivingEntity ->
                        entity
                            .getLerpedPos(graphics3D.tickCounter.getTickProgress(false))
                            .add(0.0, entity.getEyeHeight(entity.pose) + 1.5, 0.0)

                    is ItemEntity ->
                        entity
                            .getLerpedPos(graphics3D.tickCounter.getTickProgress(false))
                            .add(0.0, 0.5, 0.0) // 落ちているアイテムの中心あたり
                    else -> continue
                }
            val pos2d = graphics3D.toDisplayPos(aboveHeadPos)
            if (pos2d != null) {
                targetEntities.add(TagRenderInfo(entity, pos2d, player.squaredDistanceTo(entity)))
            }
        }
    }

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
        // 💡 透過度 alpha を barBackgroundColor の計算に反映
        val barBackgroundColor =
            ColorHelper.getArgb(
                (128 * alpha).toInt(),
                ColorHelper.getRed(InfiniteClient.theme().colors.backgroundColor),
                ColorHelper.getGreen(InfiniteClient.theme().colors.backgroundColor),
                ColorHelper.getBlue(InfiniteClient.theme().colors.backgroundColor),
            )
        graphics2d.fill(x, y, width, height, barBackgroundColor)

        val fillWidth = (width * clampedProgress).toInt()
        if (fillWidth > 0) {
            val healthColor = getRainbowColor(progress * 0.4f).transparent((alpha * 255).toInt()) // 塗りつぶしの色にもアルファ値を適用
            graphics2d.fill(x, y, fillWidth, height, healthColor)
        }
    }

    // ----------------------------------------------------------------------
    // 💡 アルファ値を計算するヘルパー関数
    // ----------------------------------------------------------------------
    private fun calculateAlpha(distance: Double): Float {
        val start = fadeStartDistance.value.toDouble()
        val end = fadeEndDistance.value.toDouble()
        val min = minAlpha.value.toFloat() / 100.0f

        if (distance <= start) {
            return 1.0f // 透過開始距離内は完全に不透明
        }
        if (distance >= end) {
            return min // 透過終了距離外は最小アルファ値
        }

        // 線形補間 (lerp): start (1.0) から end (min) へ
        // progress: 0.0 (start) から 1.0 (end) へ
        val progress = ((distance - start) / (end - start)).toFloat().coerceIn(0.0f, 1.0f)

        return 1.0f + (min - 1.0f) * progress // 1.0からminまで減少
    }

    override fun render2d(graphics2D: Graphics2D) {
        val minScaleDist = minScaleDistance.value.toDouble()
        val maxDist = distance.value.toDouble()

        // 💡 描画順序を変更: 遠いものから順に描画
        targetEntities.sortByDescending { it.distSq }

        for (renderInfo in targetEntities) {
            val entity = renderInfo.entity
            val pos = renderInfo.pos
            val distSq = renderInfo.distSq
            val distance = sqrt(distSq)

            // 💡 透過度 (アルファ値) の計算
            val alpha = calculateAlpha(distance)
            if (alpha < 0.01f) continue // ほぼ透明ならスキップ

            // ----------------------------------------------------------------------
            // スケール計算
            val scale =
                if (distance <= minScaleDist) {
                    1.0f
                } else if (distance >= maxDist) {
                    0.5f
                } else {
                    val scaleRange = 1.0f - 0.5f
                    val distRange = maxDist - minScaleDist

                    if (distRange <= 0.0) {
                        0.5f
                    } else {
                        val progress = ((distance - minScaleDist) / distRange).toFloat().coerceIn(0.0f, 1.0f)
                        1.0f - scaleRange * progress
                    }
                }

            graphics2D.pushState()
            graphics2D.translate(pos.x.toFloat(), pos.y.toFloat())
            graphics2D.scale(scale, scale)

            // スケール適用後の描画開始座標 (中央揃えのため pos.x, pos.y は (0, 0) に移動済み)

            // ----------------------------------------------------------------------

            when (entity) {
                is LivingEntity -> renderLivingEntityTag(graphics2D, entity, alpha)
                is ItemEntity -> renderItemEntityTag(graphics2D, entity, alpha)
            }

            graphics2D.popState()
        }
    }

    // ----------------------------------------------------------------------
    // 💡 落ちているアイテムのタグ描画 (alpha引数を追加)
    // ----------------------------------------------------------------------

    private fun renderItemEntityTag(
        graphics2D: Graphics2D,
        itemEntity: ItemEntity,
        alpha: Float,
    ) {
        val stack = itemEntity.stack

        val name = stack.name.string
        val nameWidth = graphics2D.textWidth(name)

        val itemText =
            if (stack.damage > 0) {
                "Dur: ${stack.maxDamage - stack.damage}/${stack.maxDamage}"
            } else if (stack.count > 1) {
                "Count: ${stack.count}"
            } else {
                null
            }
        val itemTextWidth = itemText?.let { graphics2D.textWidth(it) } ?: 0

        val contentWidth = nameWidth.coerceAtLeast(itemTextWidth).coerceAtLeast(itemRenderSize)

        // サイズ計算
        val padding = 1
        val width = contentWidth + itemRenderSize + padding * 4 // 名前/テキスト + アイコン + パディング
        val height = graphics2D.fontHeight() * 2 + padding * 2 // 名前 + 情報 + パディング

        val startX = -(width / 2)
        val startY = -height

        // 💡 タグの色にアルファ値を適用
        val alphaInt = (alpha * 255.0)
        val tagColor = ItemEsp.rarityColor(itemEntity).transparent(alphaInt)
        val bgColor =
            InfiniteClient
                .theme()
                .colors.backgroundColor
                .transparent(136.0 * alpha)

        // 背景と枠
        graphics2D.fill(startX, startY, width, height, bgColor)
        graphics2D.drawBorder(startX, startY, width, height, tagColor, padding)

        // アイコンの描画
        val iconX = startX + padding
        val iconY = startY + padding + (height - itemRenderSize) / 2
        graphics2D.drawItem(stack, iconX, iconY - 4) // 💡 drawItemにアルファ値を渡す（対応している場合）

        // 名前の描画
        val textX = startX + itemRenderSize + padding * 2
        graphics2D.drawText(name, textX, startY + padding, tagColor, true)

        // 詳細テキストの描画
        if (itemText != null) {
            graphics2D.drawText(
                itemText,
                textX,
                startY + padding + graphics2D.fontHeight(),
                tagColor.transparent((180 * alpha).toInt()), // 💡 アルファ値を適用
                true,
            )
        }
    }

    // ----------------------------------------------------------------------
    // 💡 生存エンティティのタグ描画 (alpha引数を追加)
    // ----------------------------------------------------------------------

    private fun renderLivingEntityTag(
        graphics2D: Graphics2D,
        entity: LivingEntity,
        alpha: Float,
    ) {
        val isPlayer = entity is PlayerEntity
        val name = entity.name
        val displayName: String? = if (isPlayer) name.string else null
        val hasName = !displayName.isNullOrEmpty()

        val nameHeight = if (hasName) graphics2D.fontHeight() else 0
        val barHeight = graphics2D.fontHeight()
        val padding = 1
        val minWidth = graphics2D.textWidth("defaultNameText")
        val contentWidth = if (hasName) graphics2D.textWidth(displayName) else minWidth

        // --------------------------------------------------
        // 1. タグ本体（名前とHPバー）のサイズ計算
        // --------------------------------------------------
        val tagWidth = contentWidth.coerceAtLeast(minWidth) + padding * 2
        val tagHeight = nameHeight + barHeight + padding * 2

        // タグ本体の描画開始座標
        val tagStartX = -(tagWidth / 2)
        val tagStartY = -tagHeight

        val healthPer = entity.health / entity.maxHealth

        // 💡 タグの色にアルファ値を適用
        val alphaInt = (alpha * 255).toInt()
        val tagColor =
            when (entity) {
                is PlayerEntity ->
                    InfiniteClient
                        .theme()
                        .colors.infoColor
                        .transparent(alphaInt)
                is HostileEntity ->
                    InfiniteClient
                        .theme()
                        .colors.errorColor
                        .transparent(alphaInt)
                is PassiveEntity ->
                    InfiniteClient
                        .theme()
                        .colors.greenAccentColor
                        .transparent(alphaInt)
                else ->
                    InfiniteClient
                        .theme()
                        .colors.foregroundColor
                        .transparent(alphaInt)
            }
        val bgColor =
            InfiniteClient
                .theme()
                .colors.backgroundColor
                .transparent((136 * alpha).toInt())

        // render background
        graphics2D.fill(tagStartX, tagStartY, tagWidth, tagHeight, bgColor)
        graphics2D.drawBorder(tagStartX, tagStartY, tagWidth, tagHeight, tagColor, padding)

        // 名前の描画
        if (hasName) {
            graphics2D.drawText(displayName, tagStartX + padding, tagStartY + padding, tagColor, true)
        }

        // 体力バーの描画
        val barY = tagStartY + nameHeight + padding
        drawBar(
            graphics2D,
            tagStartX + padding,
            barY,
            tagWidth - padding * 2,
            barHeight,
            healthPer,
            alpha, // 💡 drawBarにアルファ値を渡す
        )
        // --------------------------------------------------
        // 2. 装備品の描画 (タグ本体から分離)
        // --------------------------------------------------
        if (!showItems.value) return

        // 2-1. 防具スロットの描画 (タグの下に配置)
        val armorSlots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
        val armorSlotCount = 4
        val armorAreaWidth = armorSlotCount * itemRenderSize + (armorSlotCount - 1) * itemPaddingSize

        // 防具描画のY座標 (タグ本体の最下部から少し下にオフセット)
        val armorY = tagStartY + tagHeight + itemPaddingSize * 2

        // 防具描画のX座標 (タグの中心に合わせて中央揃え)
        var currentX = -(armorAreaWidth / 2)

        for (slot in armorSlots) {
            val itemStack = entity.getEquippedStack(slot)
            val renderStack = if (itemStack.isEmpty) ItemStack(Items.AIR) else itemStack

            renderEquipmentStack(graphics2D, renderStack, currentX, armorY, alpha) // 💡 alpha引数を追加

            currentX += itemRenderSize + itemPaddingSize
        }

        // 2-2. 手持ちアイテムの描画 (タグの左右に配置し、防具とは別のY座標か、防具に干渉しない位置にする)

        val mainHandStack = entity.getEquippedStack(EquipmentSlot.MAINHAND)
        val offHandStack = entity.getEquippedStack(EquipmentSlot.OFFHAND)

        // 手持ちアイテム描画のY座標 (タグのY座標の中心付近に配置)
        val handY = tagStartY + tagHeight / 2 - itemRenderSize / 2

        // メインハンド (タグの右端外側)
        val mainHandX = tagStartX + tagWidth + itemPaddingSize
        renderEquipmentStack(graphics2D, mainHandStack, mainHandX, handY, alpha) // 💡 alpha引数を追加

        // オフハンド (タグの左端外側)
        val offHandX = tagStartX - itemRenderSize - itemPaddingSize
        renderEquipmentStack(graphics2D, offHandStack, offHandX, handY, alpha) // 💡 alpha引数を追加
    }

    // ----------------------------------------------------------------------
    // 💡 アイテムアイコン、個数、耐久値を描画するヘルパー (alpha引数を追加)
    // ----------------------------------------------------------------------

    private fun renderEquipmentStack(
        graphics2D: Graphics2D,
        stack: ItemStack,
        x: Int,
        y: Int,
        alpha: Float = 1.0f,
    ) {
        if (stack.isEmpty && stack.item != Items.AIR) return
        val size = itemRenderSize

        // 💡 アイコンの描画にアルファ値を渡す（対応している場合）
        graphics2D.drawItem(stack, x, y)

        // 個数の描画
        if (stack.count > 1) {
            val text = stack.count.toString()
            val textColor = ColorHelper.getArgb((alpha * 255).toInt(), 255, 255, 255) // 💡 色にアルファ値を適用
            graphics2D.drawText(
                text,
                x + size - graphics2D.textWidth(text),
                y + size - graphics2D.fontHeight(),
                textColor,
                true,
            )
        }

        // 耐久値の描画 (耐久値を持つアイテムかつダメージを受けている場合)
        if (stack.isDamageable && stack.damage > 0) {
            val progress = (stack.maxDamage - stack.damage).toFloat() / stack.maxDamage.toFloat()
            val barHeight = 2
            val barY = y + size - barHeight
            val alphaInt = (alpha * 255).toInt()

            // 耐久値バーの背景
            graphics2D.fill(x, barY, size, barHeight, ColorHelper.getArgb(alphaInt, 0, 0, 0)) // 💡 アルファ値を適用

            // 耐久値の進捗バー
            val fillWidth = (size * progress).toInt()
            if (fillWidth > 0) {
                val color = getRainbowColor(progress * 0.3f).transparent(alphaInt) // 💡 色にアルファ値を適用
                // 💡 修正: 前回の fill 関数呼び出しの高さが 0 になっていたため修正
                graphics2D.fill(x + 1, barY + 1, fillWidth - 2, 0, color)
            }
        }
    }
}
