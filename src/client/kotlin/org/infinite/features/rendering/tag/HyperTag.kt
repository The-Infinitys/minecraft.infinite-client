package org.infinite.features.rendering.tag

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.Entity
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.ItemEntity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.MobEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.ColorHelper
import org.infinite.InfiniteClient
import org.infinite.feature.ConfigurableFeature
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

    // 💡 新規設定: ステータスオーバーレイとパーティクル
    private val showStatusEffects =
        FeatureSetting.BooleanSetting("ShowStatusEffects", true)
    private val showHealthRegen =
        FeatureSetting.BooleanSetting("ShowHealthRegen", true)
    private val showHunger =
        FeatureSetting.BooleanSetting("ShowHunger", true)

    override val settings: List<FeatureSetting<*>> =
        listOf(
            mobs,
            players,
            distance,
            always,
            showItems,
            minScaleDistance,
            fadeStartDistance,
            fadeEndDistance,
            minAlpha,
            showStatusEffects,
            showHealthRegen,
            showHunger,
        )

    private data class TagRenderInfo(
        val entity: Entity,
        val pos: Graphics2D.DisplayPos,
        val distSq: Double, // 距離の二乗を保存
    )

    // 💡 2Dタグパーティクルデータクラス
    private data class TagParticle(
        var x: Float, // 画面上のX座標 (エンティティタグの中心からの相対座標)
        var y: Float, // 画面上のY座標 (エンティティタグの中心からの相対座標)
        var entityId: Int, // 関連エンティティのID
        var color: Int, // ARGBカラー
        var size: Float, // パーティクルのサイズ
        var lifetime: Int, // 残り寿命 (ティック)
        val maxLifetime: Int, // 最大寿命 (不透明度計算用)
        var velX: Float, // X方向の速度
        var velY: Float, // Y方向の速度
        val gravity: Float, // 重力の模倣 (Y方向の加速度)
    )

    private val targetEntities: MutableList<TagRenderInfo> = mutableListOf()

    // 💡 パーティクルリスト
    private val activeParticles: MutableList<TagParticle> = mutableListOf()

    private val itemRenderSize = 16
    private val itemPaddingSize = 2

    // ----------------------------------------------------------------------
    // 3Dレンダリングフック (主に更新処理とパーティクル生成に使用)
    // ----------------------------------------------------------------------
    override fun render3d(graphics3D: Graphics3D) {
        targetEntities.clear()
        val client = MinecraftClient.getInstance()
        val player = client.player ?: return
        val entities = client.world?.entities ?: return
        val worldRandom = client.world?.random

        val maxDistSq = distance.value * distance.value

        // 💡 1. 既存パーティクルの更新とフィルタリング
        activeParticles.removeIf { particle ->
            particle.lifetime--
            if (particle.lifetime <= 0) return@removeIf true

            // 位置と速度の更新 (2D座標系内)
            particle.velY += particle.gravity
            particle.x += particle.velX
            particle.y += particle.velY

            return@removeIf false
        }

        // 💡 2. エンティティフィルタリング、2D座標計算、新規パーティクル生成
        val filteredEntities =
            entities
                .filter { it is LivingEntity || (showItems.value && it is ItemEntity) }
                .filter {
                    val distCheck = player.squaredDistanceTo(it) < maxDistSq || maxDistSq == 0 || always.value
                    if (!distCheck) return@filter false

                    when (it) {
                        is PlayerEntity -> players.value
                        is MobEntity -> mobs.value && (it.health < it.maxHealth || always.value)
                        is ItemEntity -> showItems.value
                        else -> false
                    }
                }

        for (entity in filteredEntities) {
            val aboveHeadPos =
                when (entity) {
                    is LivingEntity -> {
                        entity
                            .getLerpedPos(graphics3D.tickCounter.getTickProgress(false))
                            .add(0.0, entity.height.toDouble(), 0.0)
                    }

                    is ItemEntity -> {
                        entity
                            .getLerpedPos(graphics3D.tickCounter.getTickProgress(false))
                            .add(0.0, 0.5, 0.0)
                    }

                    else -> {
                        continue
                    }
                }
            val pos2d = graphics3D.toDisplayPos(aboveHeadPos)

            if (pos2d != null) {
                targetEntities.add(TagRenderInfo(entity, pos2d, player.squaredDistanceTo(entity)))

                // 💡 新規パーティクル生成フック (20ティックごとに1/20の確率でスポーン)
                if (entity is LivingEntity && showStatusEffects.value && entity.age % 20 == 0 && worldRandom?.nextInt(20) == 0) {
                    generate2dTagParticles(entity, entity.id)
                }
            }
        }
    }

    // ----------------------------------------------------------------------
    // 💡 2Dパーティクル生成ロジック
    // ----------------------------------------------------------------------

    private fun generate2dTagParticles(
        entity: LivingEntity,
        entityId: Int,
    ) {
        val (particleColor, _) = getStatusOverlay(entity) // オーバーレイの色を取得

        if (particleColor != null) {
            val random = world?.random ?: return

            val lifetime = 25 // ティック
            val size = random.nextFloat() * 1.5f + 2.0f // 2.0から3.5

            // パーティクルの初期位置と速度をランダムに設定 (タグの中心基準)
            val initialX = (random.nextFloat() - 0.5f) * 10f
            val initialY = (random.nextFloat() * 5f) - 40f // タグの中心付近 (HPバーあたり)

            val velX = (random.nextFloat() - 0.5f) * 0.3f
            val velY = random.nextFloat() * -0.5f - 0.5f // 上向き
            val gravity = 0.03f

            val particle =
                TagParticle(
                    x = initialX,
                    y = initialY,
                    entityId = entityId,
                    color = particleColor,
                    size = size,
                    lifetime = lifetime,
                    maxLifetime = lifetime,
                    velX = velX,
                    velY = velY,
                    gravity = gravity,
                )
            activeParticles.add(particle)
        }
    }

    // ----------------------------------------------------------------------
    // HPバー描画ヘルパー
    // ----------------------------------------------------------------------
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
                ColorHelper.getRed(InfiniteClient.theme().colors.backgroundColor),
                ColorHelper.getGreen(InfiniteClient.theme().colors.backgroundColor),
                ColorHelper.getBlue(InfiniteClient.theme().colors.backgroundColor),
            )
        graphics2d.fill(x, y, width, height, barBackgroundColor)

        val fillWidth = (width * clampedProgress).toInt()
        if (fillWidth > 0) {
            val healthColor = getRainbowColor(progress * 0.4f).transparent((alpha * 255).toInt())
            graphics2d.fill(x, y, fillWidth, height, healthColor)
        }
    }

    // ----------------------------------------------------------------------
    // 透過度 (アルファ値) 計算ヘルパー関数
    // ----------------------------------------------------------------------
    private fun calculateAlpha(distance: Double): Float {
        val start = fadeStartDistance.value.toDouble()
        val end = fadeEndDistance.value.toDouble()
        val min = minAlpha.value.toFloat() / 100.0f

        if (distance <= start) {
            return 1.0f
        }
        if (distance >= end) {
            return min
        }

        val progress = ((distance - start) / (end - start)).toFloat().coerceIn(0.0f, 1.0f)

        return 1.0f + (min - 1.0f) * progress
    }

    private fun calculateAlpha(entityId: Int): Float {
        val distSq = targetEntities.find { it.entity.id == entityId }?.distSq ?: 0.0
        return calculateAlpha(sqrt(distSq))
    }

    // ----------------------------------------------------------------------
    // ステータスオーバーレイの色を取得するヘルパー関数
    // ----------------------------------------------------------------------

    private fun getStatusOverlay(entity: LivingEntity): Pair<Int?, Float> {
        val theme = InfiniteClient.theme().colors

        // 1. デバフ (優先度高)
        if (entity.isOnFire) {
            // 火: 赤/オレンジ
            return Pair(ColorHelper.getArgb(0, 255, 127, 0), 1.0f)
        }
        if (entity.hasStatusEffect(StatusEffects.POISON)) {
            // 毒: 緑
            return Pair(theme.greenAccentColor.transparent(0), 1.0f)
        }
        if (entity.hasStatusEffect(StatusEffects.WITHER)) {
            // 衰弱: 暗い灰色/黒
            return Pair(ColorHelper.getArgb(0, 50, 50, 50), 1.0f)
        }
        if (entity.hasStatusEffect(StatusEffects.WEAKNESS)) {
            // 弱体化: 淡い紫
            return Pair(ColorHelper.getArgb(0, 150, 150, 200), 1.0f)
        }
        if (entity.hasStatusEffect(StatusEffects.BLINDNESS)) {
            // 盲目: 黒
            return Pair(ColorHelper.getArgb(0, 0, 0, 0), 1.0f)
        }

        // 2. バフ/特殊状態
        if (showHealthRegen.value && entity.hasStatusEffect(StatusEffects.REGENERATION)) {
            // 再生: 明るい緑
            return Pair(ColorHelper.getArgb(0, 0, 255, 0), 1.0f)
        }

        // 3. プレイヤー固有の状態
        if (showHunger.value && entity is PlayerEntity) {
            val hungerLevel = entity.hungerManager.foodLevel
            if (hungerLevel <= 6) { // 空腹エフェクトが始まるレベル (1-6)
                // 空腹: 黄色/茶色
                return Pair(ColorHelper.getArgb(0, 200, 150, 50), 1.0f)
            }
        }

        return Pair(null, 0.0f) // 該当なし
    }

    // ----------------------------------------------------------------------
    // 2Dレンダリングフック (メイン描画)
    // ----------------------------------------------------------------------
    override fun render2d(graphics2D: Graphics2D) {
        val minScaleDist = minScaleDistance.value.toDouble()
        val maxDist = distance.value.toDouble()

        // 描画順序を変更: 遠いものから順に描画
        targetEntities.sortByDescending { it.distSq }

        // 💡 描画済みのエンティティのIDとスケール、座標をマップに保存 (パーティクル描画用)
        val renderedTags = mutableMapOf<Int, Triple<Float, Float, Float>>() // ID -> (scale, screenX, screenY)

        for (renderInfo in targetEntities) {
            val entity = renderInfo.entity
            val pos = renderInfo.pos
            val distSq = renderInfo.distSq
            val distance = sqrt(distSq)

            val alpha = calculateAlpha(distance)
            if (alpha < 0.01f) continue

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

            // 描画情報を保存
            renderedTags[entity.id] = Triple(scale, pos.x.toFloat(), pos.y.toFloat())

            graphics2D.pushState()
            graphics2D.translate(pos.x.toFloat(), pos.y.toFloat())
            graphics2D.scale(scale, scale)

            when (entity) {
                is LivingEntity -> renderLivingEntityTag(graphics2D, entity, alpha)
                is ItemEntity -> renderItemEntityTag(graphics2D, entity, alpha)
            }

            graphics2D.popState()
        }

        // --------------------------------------------------
        // 💡 2Dパーティクルの描画
        // --------------------------------------------------
        for (particle in activeParticles) {
            val tagInfo = renderedTags[particle.entityId] ?: continue // タグが描画されていなければスキップ

            val (scale, tagScreenX, tagScreenY) = tagInfo

            // 描画状態をプッシュ
            graphics2D.pushState()

            // 1. タグの中心位置に移動
            graphics2D.translate(tagScreenX, tagScreenY)

            // 2. タグのスケールを適用
            graphics2D.scale(scale, scale)

            // 3. パーティクルの座標に移動 (X, Yはタグの中心からの相対座標)
            graphics2D.translate(particle.x, particle.y)

            // 4. 不透明度とサイズを計算
            val lifeRatio = particle.lifetime.toFloat() / particle.maxLifetime.toFloat()
            // 距離によるアルファ値とライフタイムによるアルファ値を乗算し、最大80%の不透明度を適用
            val distAlpha = calculateAlpha(particle.entityId)
            val currentAlpha = (lifeRatio * distAlpha * 255 * 0.8f).toInt()

            // 色に不透明度を適用
            val particleColor =
                ColorHelper.getArgb(
                    currentAlpha.coerceIn(0, 255),
                    ColorHelper.getRed(particle.color),
                    ColorHelper.getGreen(particle.color),
                    ColorHelper.getBlue(particle.color),
                )

            // サイズもライフタイムで少し縮小
            val currentSize = particle.size * lifeRatio.coerceAtLeast(0.2f)

            // 5. パーティクルの描画 (円で模倣)
            graphics2D.fillCircle(0f, 0f, currentSize, particleColor)

            graphics2D.popState()
        }
    }

    // ----------------------------------------------------------------------
    // 落ちているアイテムのタグ描画
    // ----------------------------------------------------------------------

    private fun renderItemEntityTag(
        graphics2D: Graphics2D,
        itemEntity: ItemEntity,
        alpha: Float,
    ) {
        val stack = itemEntity.stack
        val x = -(itemRenderSize / 2)
        val y = -(itemRenderSize / 2) - 32

        graphics2D.drawItem(stack, x, y, alpha)
    }

    // ----------------------------------------------------------------------
    // 生存エンティティのタグ描画 (オーバーレイロジックを含む)
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

        // 1. タグ本体（名前とHPバー）のサイズ計算
        val tagWidth = contentWidth.coerceAtLeast(minWidth) + padding * 2
        val tagHeight = nameHeight + barHeight + padding * 2

        val tagStartX = -(tagWidth / 2)
        val tagStartY = -tagHeight

        val healthPer = entity.health / entity.maxHealth

        // タグの色にアルファ値を適用
        val alphaInt = (alpha * 255).toInt()
        val tagColor =
            when (entity) {
                is PlayerEntity -> {
                    InfiniteClient
                        .theme()
                        .colors.infoColor
                        .transparent(alphaInt)
                }

                is HostileEntity -> {
                    InfiniteClient
                        .theme()
                        .colors.errorColor
                        .transparent(alphaInt)
                }

                is PassiveEntity -> {
                    InfiniteClient
                        .theme()
                        .colors.greenAccentColor
                        .transparent(alphaInt)
                }

                else -> {
                    InfiniteClient
                        .theme()
                        .colors.foregroundColor
                        .transparent(alphaInt)
                }
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
        val barX = tagStartX + padding
        val barY = tagStartY + nameHeight + padding
        val barW = tagWidth - padding * 2

        drawBar(graphics2D, barX, barY, barW, barHeight, healthPer, alpha)

        // 💡 状態オーバーレイの描画 (HPバーの上)
        if (showStatusEffects.value) {
            val (overlayColor, progressRatio) = getStatusOverlay(entity)

            if (overlayColor != null && progressRatio > 0.001f) {
                // オーバーレイのアルファ値を計算 (最大50%の透過度)
                val overlayAlpha = (0.5f * alpha).coerceIn(0.0f, 1.0f)

                // オーバーレイの塗りつぶし色
                val overlayArgb = overlayColor.transparent((overlayAlpha * 255).toInt())

                // オーバーレイの幅 (HPバー全体を覆う)
                val overlayWidth = (barW * progressRatio).toInt().coerceAtLeast(barW)

                graphics2D.fill(
                    barX,
                    barY,
                    overlayWidth,
                    barHeight,
                    overlayArgb,
                )
            }
        }

        // 2. 装備品の描画
        if (!showItems.value) return

        // 2-1. 防具スロットの描画
        val armorSlots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
        val armorSlotCount = 4
        val armorAreaWidth = armorSlotCount * itemRenderSize + (armorSlotCount - 1) * itemPaddingSize

        val armorY = tagStartY + tagHeight + itemPaddingSize * 2
        var currentX = -(armorAreaWidth / 2)

        for (slot in armorSlots) {
            val itemStack = entity.getEquippedStack(slot)
            val renderStack = if (itemStack.isEmpty) ItemStack(Items.AIR) else itemStack

            graphics2D.drawItem(renderStack, currentX, armorY, alpha)

            currentX += itemRenderSize + itemPaddingSize
        }

        // 2-2. 手持ちアイテムの描画
        val mainHandStack = entity.getEquippedStack(EquipmentSlot.MAINHAND)
        val offHandStack = entity.getEquippedStack(EquipmentSlot.OFFHAND)

        val handY = tagStartY + tagHeight / 2 - itemRenderSize / 2

        // メインハンド (タグの右端外側)
        val mainHandX = tagStartX + tagWidth + itemPaddingSize
        graphics2D.drawItem(mainHandStack, mainHandX, handY, alpha)

        // オフハンド (タグの左端外側)
        val offHandX = tagStartX - itemRenderSize - itemPaddingSize
        graphics2D.drawItem(offHandStack, offHandX, handY, alpha)
    }
}
