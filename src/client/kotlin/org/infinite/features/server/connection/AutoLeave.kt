package org.infinite.features.server.connection

import net.minecraft.client.network.ClientPlayNetworkHandler
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.text.Text
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.settings.FeatureSetting

class AutoLeave : ConfigurableFeature(initialEnabled = false) {
    private enum class Method {
        Normal,
        InvalidChat,
        SelfHurt,
    }

    override val level: FeatureLevel = FeatureLevel.Utils
    private val hpThreshold =
        FeatureSetting.IntSetting(
            "HpThreshold",
            6,
            0,
            20,
        )
    private val totemThreshold =
        FeatureSetting.IntSetting(
            "TotemThreshold",
            0,
            0,
            11,
        )
    private val method =
        FeatureSetting.EnumSetting<Method>(
            "Method",
            Method.Normal,
            Method.entries,
        )

    override val settings: List<FeatureSetting<*>> =
        listOf(
            hpThreshold,
            totemThreshold, // TotemThresholdを追加
            method, // Method設定を追加
        )

    override fun onTick() {
        val player = client.player ?: return
        val handler = client.networkHandler ?: return
        val isLowHp = player.health <= hpThreshold.value

        if (isLowHp) {
            val reasonKey =
                when {
//                isLowHp && isLowTotems -> "lowhp_and_lowtotems"
                    isLowHp -> "lowhp"
                    else -> "lowtotems"
                }
            // サーバーを抜ける理由メッセージを作成
            val reason = Text.translatable("feature.server.autoleave.reason.$reasonKey").string
            leaveServer(reason, handler, player)
        }
    }

    /**
     * 他のFeatureからサーバーを抜けるための公開メソッド
     * @param reason サーバーを抜ける理由
     * @param handler ネットワークハンドラー
     * @param player プレイヤーエンティティ
     */
    fun leaveServer(
        reason: String,
        handler: ClientPlayNetworkHandler,
        player: ClientPlayerEntity,
    ) {
        if (client.world != null) {
            disable() // ハックを無効化
            InfiniteClient.getFeature(AutoConnect::class.java)?.disable()
            InfiniteClient.warn(Text.translatable("feature.server.autoleave.leaving", reason).string)

            when (method.value) {
                Method.Normal -> {
                    // 1. Normal: 通常の切断
                    client.disconnect(Text.literal(reason))
                }

                Method.InvalidChat -> {
                    // 2. InvalidChat: 不正なチャット（\u00a7 セクション記号）を送信
                    // サーバーが不正なチャットとしてプレイヤーをキックすることを期待
                    handler.sendChatMessage("\u00a7")
                }
                Method.SelfHurt -> {
                    // 4. SelfHurt: 自身を対象とした攻撃パケットを送信
                    // サーバーが不正なエンティティ操作としてプレイヤーをキックすることを期待
                    handler.sendPacket(PlayerInteractEntityC2SPacket.attack(player, player.isSneaking))
                }
            }
        }
    }
}
