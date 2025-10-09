package org.theinfinitys.features.movement

import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.util.math.Box
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting // 基底クラスからインポート

class SafeWalk : ConfigurableFeature(initialEnabled = false) {
    private val client: MinecraftClient
        get() = MinecraftClient.getInstance()

    // --- 設定（Placeholderとして内部に保持） ---
    // ユーザー設定の実装はスキップし、固定値として扱う
    private val sneakAtEdges: Boolean = false // Wurst Clientの "Sneak at edges" に相当
    private val edgeDistance: Double = 0.05 // Wurst Clientの "Sneak edge distance" に相当 (0.05m)

    override val settings: List<InfiniteSetting<*>> = emptyList() // 設定は追加しない

    // --- 内部状態 ---
    private var sneaking = false // 現在、SafeWalkによってスニーク状態にあるか

    // Wurst Clientの onEnable に相当する処理
    override fun enabled() {
        // Wurst Clientと同様に、他のMod（例: ParkourHack）を無効化する処理があればここに追加
        sneaking = false
    }

    // Wurst Clientの onDisable に相当する処理
    override fun disabled() {
        if (sneaking) {
            setSneaking(false)
        }
    }

    // --- メインロジックメソッド ---

    /**
     * プレイヤーの移動が計算される直前にMixinsから呼び出すためのメソッド。
     * Wurst Clientの onClipAtLedge に相当します。
     */
    fun onPreMotion() {
        // ConfigurableFeatureのenabledプロパティを使用
        if (!isEnabled()) return

        val player = client.player ?: return

        // 必須チェック: スニーク設定が有効か、地面にいるか
        if (!sneakAtEdges || !player.isOnGround) {
            if (sneaking) setSneaking(false)
            return
        }

        // --- SafeWalkのメイン判定ロジック ---

        val box: Box = player.boundingBox
        val adjustedBox: Box =
            box
                // 足元（ステップ高さ分下）まで伸ばす
                .stretch(0.0, (-player.stepHeight).toDouble(), 0.0)
                // 縁からedgeDistanceの分だけ内側に縮小する
                .expand(-edgeDistance, 0.0, -edgeDistance)

        var shouldClip = false

        // 調整されたボックスの範囲にブロックがない場合（＝崖っぷちの場合）
        if (client.world?.isSpaceEmpty(player, adjustedBox) == true) {
            shouldClip = true
        }

        // スニーク状態を更新
        setSneaking(shouldClip)
    }

    // --- ヘルパーメソッド ---

    /**
     * プレイヤーのスニークキーの押下状態を強制的に設定する。
     */
    private fun setSneaking(sneaking: Boolean) {
        val sneakKey: KeyBinding = client.options.sneakKey

        // KeyBindingの押下状態を強制的に設定/解除
        sneakKey.isPressed = sneaking

        this.sneaking = sneaking
    }
}
