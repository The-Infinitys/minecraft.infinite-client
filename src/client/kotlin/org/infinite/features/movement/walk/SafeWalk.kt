package org.infinite.features.movement.walk

import net.minecraft.client.option.KeyBinding
import net.minecraft.util.math.Box
import org.infinite.feature.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class SafeWalk : ConfigurableFeature(initialEnabled = false) {
    private val sneakAtEdges: Boolean = false
    private val edgeDistance: Double = 0.05

    override val settings: List<FeatureSetting<*>> = emptyList() // 設定は追加しない

    // --- 内部状態 ---
    private var sneaking = false

    override fun onEnabled() {
        sneaking = false
    }

    // Wurst Clientの onDisable に相当する処理
    override fun onDisabled() {
        if (sneaking) {
            setSneaking(false)
        }
    }

    fun onPreMotion() {
        if (!isEnabled()) return
        val player = client.player ?: return
        if (!sneakAtEdges || !player.isOnGround) {
            if (sneaking) setSneaking(false)
            return
        }
        val box: Box = player.boundingBox
        val adjustedBox: Box =
            box
                .stretch(0.0, (-player.stepHeight).toDouble(), 0.0)
                // 縁からedgeDistanceの分だけ内側に縮小する
                .expand(-edgeDistance, 0.0, -edgeDistance)

        var shouldClip = false
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

        sneakKey.isPressed = sneaking

        this.sneaking = sneaking
    }
}
