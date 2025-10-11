package org.theinfinitys.features.automatic.aimode

import net.minecraft.client.MinecraftClient
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.InfiniteClient
import org.theinfinitys.features.automatic.woodcutter.WoodCutter
import org.theinfinitys.settings.InfiniteSetting

/**
 * クライアントプレイヤーのAI制御を管理するフィーチャー。
 */
class AIMode : ConfigurableFeature(initialEnabled = false) {
    // WoodCutterがこのAIを利用することを示唆
    private val aiFeatureClasses: List<Class<out ConfigurableFeature>> = listOf(WoodCutter::class.java)
    override val available: Boolean = false
    private var lastKnownHealth: Float = -1.0f

    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.BooleanSetting(
                "AllowPlayerInput",
                "AIモード中でもプレイヤーの入力を許可します。",
                false,
            ),
            // 新しい設定: ダメージを受けた際にAIを強制中断する
            InfiniteSetting.BooleanSetting(
                "CancelOnDamaged",
                "プレイヤーがダメージを受けた際にAIを強制中断します。",
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
