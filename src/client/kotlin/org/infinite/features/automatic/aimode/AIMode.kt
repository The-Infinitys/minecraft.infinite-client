package org.infinite.features.automatic.aimode

import net.minecraft.client.MinecraftClient
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.automatic.woodcutter.WoodCutter
import org.infinite.settings.FeatureSetting

/**
 * クライアントプレイヤーのAI制御を管理するフィーチャー。
 */
class AIMode : ConfigurableFeature(initialEnabled = false) {
    // WoodCutterがこのAIを利用することを示唆
    private val aiFeatureClasses: List<Class<out ConfigurableFeature>> = listOf(WoodCutter::class.java)
    override val togglable: Boolean = false
    private var lastKnownHealth: Float = -1.0f

    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.BooleanSetting(
                "AllowPlayerInput",
                "feature.automatic.aimode.allowplayerinput.description",
                false,
            ),
            // 新しい設定: ダメージを受けた際にAIを強制中断する
            FeatureSetting.BooleanSetting(
                "CancelOnDamaged",
                "feature.automatic.aimode.cancelondamaged.description",
                true, // デフォルトをtrueに設定してテストしやすく
            ),
        )

    override fun enabled() {
        lastKnownHealth = MinecraftClient.getInstance().player?.health ?: -1.0f
    }

    override fun disabled() {
        lastKnownHealth = -1.0f
    }

    override fun tick() {
        // AIを利用するフィーチャーがない場合、AIModeを無効化
        if (!aiFeatureClasses.any { aiFeature -> InfiniteClient.isFeatureEnabled(aiFeature) }) {
            disable()
            return
        }

        // ダメージ検知ロジック
        val player = MinecraftClient.getInstance().player
        val cancelOnDamaged = settings.find { it.name == "CancelOnDamaged" }?.value as? Boolean ?: false

        if (player != null && cancelOnDamaged) {
            val currentHealth = player.health

            // プレイヤーがダメージを受けたか（Healthが減ったか）を検知
            if (lastKnownHealth > 0.0f && currentHealth < lastKnownHealth) {
                InfiniteClient.log("Player damaged! Cancelling AI operation.")
                disable() // AIMode全体を無効化
                return
            }
            lastKnownHealth = currentHealth
        }
    }
}
