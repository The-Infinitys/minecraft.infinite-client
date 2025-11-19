package org.infinite.features.rendering.ui

import net.minecraft.client.MinecraftClient
import net.minecraft.client.network.ClientPlayerEntity
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.gui.theme.ThemeColors
import org.infinite.libs.client.player.PlayerStatsManager
import org.infinite.libs.graphics.Graphics2D
import org.infinite.libs.graphics.Graphics3D
import org.infinite.settings.FeatureSetting
import org.infinite.utils.rendering.transparent
import kotlin.math.min

// EasingManager, DamageCalculator, RayCastRenderer, ElytraFlightUiRenderer は既存のものを使用

class HyperUi : ConfigurableFeature() {
    override val render2DTiming: Timing =
        Timing.Start
    private val easeSpeedSetting = FeatureSetting.DoubleSetting("EasingSpeed", 0.25, 0.0, 1.0)
    private val alphaSetting = FeatureSetting.DoubleSetting("Transparency", 0.5, 0.0, 1.0)
    val heightSetting = FeatureSetting.IntSetting("Height", 24, 8, 32)
    private val paddingSetting = FeatureSetting.IntSetting("Padding", 4, 0, 16)
    override val settings: List<FeatureSetting<*>> =
        listOf(easeSpeedSetting, alphaSetting, heightSetting, paddingSetting)

    // --- 依存オブジェクト/サブシステム ---
    private val statsManager = PlayerStatsManager
    private val damageCalculator = DamageCalculator()
    private val rayCastRenderer = RayCastRenderer()
    private val flightUiRenderer = ElytraFlightUiRenderer()
    private val crosshairRenderer = CrosshairRenderer()

    // 描画に必要な設定値をまとめる
    private val renderConfig = UiRenderConfig(heightSetting, paddingSetting, alphaSetting)

    // 新しいデータモデルとレンダラー
    private val statsModel = PlayerStatsModel(statsManager, damageCalculator, easeSpeedSetting)
    private val barRenderer = UiBarRenderer(renderConfig)
    private val compassRenderer = CompassRenderer(renderConfig)
    private val hotbarRenderer = HotbarRenderer(renderConfig) // HotbarRendererのインスタンスを追加

    // --- ライフサイクル/Tick処理 ---

    override fun tick() {
        val player = MinecraftClient.getInstance().player ?: return
        // データモデルにTick処理を委譲
        statsModel.tick(player)
    }

    override fun respawn() {
        // データモデルにリスポーン処理を委譲
        statsModel.reset()
    }

    // --- 描画処理 ---

    override fun render2d(graphics2D: Graphics2D) {
        val s = statsModel.currentStats ?: return
        val e = statsModel.easingManager // EasingManagerをモデルから取得
        val colors = InfiniteClient.theme().colors
        val player = MinecraftClient.getInstance().player ?: return

        // Easing値の計算とデータ準備
        statsModel.ease()
        val estimatedTotalDamage = statsModel.estimatedTotalDamage(player)
        val (hungerProgress, saturationProgress) = statsModel.getFoodProgress(player)
        val drawnHpProgress = statsModel.calculateDrawnHpProgress(estimatedTotalDamage)

        // 描画コンポーネントにディスパッチ
        barRenderer.renderBars(
            graphics2D,
            s,
            e,
            colors,
            player,
            drawnHpProgress,
            hungerProgress,
            saturationProgress,
        )

        // ホットバーとアイテム情報の描画
        hotbarRenderer.render(graphics2D, colors)

        // 飛行UIレンダリング (変更なし)
        if (player.isGliding) {
            flightUiRenderer.render(graphics2D)
        }

        // 方位計の描画
        compassRenderer.render(graphics2D, player.getLerpedYaw(graphics2D.tickProgress), colors)

        // クロスヘアの描画
        crosshairRenderer.render(graphics2D)

        // スプリント/潜水時間テキスト (barRendererに移譲しても良いが、ここではシンプルにHyperUiに残す)
        renderStatsText(graphics2D, player, colors)
    }

    override fun render3d(graphics3D: Graphics3D) {
        rayCastRenderer.render(graphics3D)
    }

    // 省略: renderStatsTextの実装 (元のコードから必要な部分を抽出・調整)
    private fun renderStatsText(
        graphics2D: Graphics2D,
        player: ClientPlayerEntity,
        colors: ThemeColors,
    ) {
        val statsManager = PlayerStatsManager // 再度参照
        val diveTime = statsManager.diveSeconds(player)
        val transparentOfAirProgress = min(1.0, (1.0 - (statsModel.currentStats?.airProgress ?: 0.0)) * 10)
        val isSubmerged = if (player.isSubmergedInWater) 1.0 else 0.0

        val diveTimeString = "${diveTime}s"
        if (diveTime > 0) {
            graphics2D.drawText(
                diveTimeString,
                graphics2D.width - graphics2D.textWidth(diveTimeString),
                graphics2D.height - graphics2D.fontHeight(),
                colors.foregroundColor.transparent(255 * isSubmerged * (1 + transparentOfAirProgress) / 2.0),
            )
        }
        val swimming = player.isSwimming && player.isSprinting
        if (swimming) {
            val swimTime = statsManager.swimMeters(player)
            val spaceString = " "
            val swimmableLength = "${swimTime}m"
            graphics2D.drawText(
                swimmableLength,
                graphics2D.width - graphics2D.textWidth(swimmableLength + spaceString + diveTimeString),
                graphics2D.height - graphics2D.fontHeight(),
                colors.foregroundColor.transparent(255),
            )
        }
        val sprinting = player.isSprinting && !player.isSwimming && !player.isGliding
        if (sprinting) {
            val sprintTime = statsManager.sprintMeters(player)
            val sprintableLength = "${sprintTime}m"
            graphics2D.drawText(
                sprintableLength,
                graphics2D.width - graphics2D.textWidth(sprintableLength),
                graphics2D.height - graphics2D.fontHeight(),
                colors.foregroundColor.transparent(255),
            )
        }
    }
}
