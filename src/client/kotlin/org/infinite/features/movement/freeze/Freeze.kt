package org.infinite.features.movement.freeze

import net.minecraft.client.MinecraftClient
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.rendering.camera.FreeCamera
import org.infinite.settings.FeatureSetting
import org.infinite.utils.FakePlayerEntity

/**
 * Freeze Feature (Blink Hack implementation)
 * Mixinを使用してPlayerMoveC2SPacketの送信を停止し、蓄積することで瞬間移動を可能にする。
 */
class Freeze : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Utils

    // 蓄積された移動パケットを保持するキュー
    val packets = ArrayDeque<PlayerMoveC2SPacket>() // Mixinからアクセスするためvalにしておく

    // 瞬間移動中にクライアント側での表示を維持するための偽プレイヤー
    private var fakePlayer: FakePlayerEntity? = null

    private var freezeStartTime: Long = 0L

    private val mc: MinecraftClient = MinecraftClient.getInstance()

    // --- 設定 ---
    private val durationSetting =
        FeatureSetting.FloatSetting(
            "Duration",
            0.0f,
            0.0f,
            600.0f,
        )

    private val packetLimitSetting =
        FeatureSetting.IntSetting(
            "PacketLimit",
            0,
            0,
            500,
        )

    override val settings: List<FeatureSetting<*>> = listOf(durationSetting, packetLimitSetting)

    // --- Featureライフサイクル ---

    override fun enabled() {
        freezeStartTime = System.currentTimeMillis()
        cleanup()
        // 偽プレイヤーを作成し、本物のプレイヤーの位置を維持
        fakePlayer = FakePlayerEntity()

        // 注: Mixinがすでにパケット送信をフックしているため、ここではイベントリスナーの追加は不要
    }

    override fun disabled() {
        // 1. 偽プレイヤーを削除
        fakePlayer?.despawn()
        fakePlayer = null
        // 2. 蓄積された全てのパケットをサーバーに送信（瞬間移動の実行）
        sendQueuedPackets()
        cleanup()
    }

    override fun tick() {
        // Durationによる自動無効化チェック
        val duration = durationSetting.value
        if (duration > 0.0f && !InfiniteClient.isFeatureEnabled(FreeCamera::class.java)) {
            val elapsedSeconds = (System.currentTimeMillis() - freezeStartTime) / 1000.0f
            if (elapsedSeconds >= duration) {
                disable()
                return
            }
        }

        // Packet Limitによる自動再起動チェック
        val limit = packetLimitSetting.value
        if (limit > 0) {
            if (packets.size >= limit) {
                // 自動再起動: disable() -> enable() でキューを送信・クリアしてから再開
                disable()
                enable()
            }
        }
    }

    /**
     * 蓄積された全てのパケットを順番に送信し、瞬間移動を実行する
     */
    private fun sendQueuedPackets() {
        val networkHandler = mc.player?.networkHandler ?: return
        // キューから全てのパケットを取り出し、送信
        packets.forEach { networkHandler.sendPacket(it) }
    }

    /**
     * 蓄積されたパケットをクリアし、プレイヤーの位置をリセットして機能を停止する
     * 蓄積を破棄して、元の位置に戻るために使用
     */
    fun forceCancel() {
        // 蓄積されたパケットは送信せずに破棄
        packets.clear()

        // 偽プレイヤーがいた位置に本物のプレイヤーを戻す
        fakePlayer?.resetPlayerPosition()

        // Featureを無効化
        disable()
    }

    /**
     * Mixinから呼び出され、パケットを処理（キューに追加）する
     */
    fun processMovePacket(packet: PlayerMoveC2SPacket) {
        if (packets.isEmpty()) {
            packets.addLast(packet)
            return
        }

        val prevPacket = packets.last()

        // パケットの内容が前のパケットと全て同一であれば、冗長なパケットとして無視
        // BlinkHackの冗長パケットチェックロジックを再現
        if (packet.isOnGround == prevPacket.isOnGround && packet.getYaw(-1f) ==
            prevPacket.getYaw(
                -1f,
            ) && packet.getPitch(-1f) == prevPacket.getPitch(-1f) && packet.getX(-1.0) == prevPacket.getX(-1.0) && packet.getY(
                -1.0,
            ) == prevPacket.getY(-1.0) && packet.getZ(-1.0) == prevPacket.getZ(-1.0)
        ) {
            return
        }

        // 有効な移動パケットをキューに追加
        packets.addLast(packet)
    }

    /**
     * ダミープレイヤーの削除とキューのクリアを行う内部クリーンアップ処理
     */
    private fun cleanup() {
        fakePlayer?.despawn()
        fakePlayer = null
        packets.clear() // disabled()でも呼ばれているが、念のためここに集約
    }

    override fun start() {
        disable()
    }

    override fun stop() {
        cleanup()
    }
}
