package org.infinite.features.movement.water

import org.infinite.feature.ConfigurableFeature
import org.infinite.libs.client.control.ControllerInterface
import org.infinite.settings.FeatureSetting

enum class WaterHoverMethod {
    Jump,
    Velocity,
}

class WaterHover : ConfigurableFeature(initialEnabled = false) {
    private val method =
        FeatureSetting.EnumSetting(
            "Method",
            WaterHoverMethod.Jump,
            WaterHoverMethod.entries.toList(),
        )

    override val settings: List<FeatureSetting<*>> = listOf(method)

    override fun onTick() {
        val player = client.player ?: return
        when (method.value) {
            WaterHoverMethod.Jump -> {
                val waterAttenuationFactor = 0.8
                val waterGravity = -0.025
                val waterGravityVelocity = waterGravity * waterAttenuationFactor
                // プレイヤーが水中にいて、泳いでいない、かつスニークキーを押していない場合に処理を行う
                val shouldHover =
                    player.isTouchingWater &&
                        !player.isSwimming &&
                        !options.sneakKey.isPressed &&
                        player.velocity.y < waterGravityVelocity
                // 浮遊状態であれば、ジャンプキーの状態を「押されている」に設定する
                // これにより、ゲームの水中のジャンプロジック（+0.04のmotY追加）が呼び出され、浮力を得る
                if (shouldHover) {
                    ControllerInterface.press(options.jumpKey, tick = 0)
                }
            }

            WaterHoverMethod.Velocity -> {
                val shouldHover =
                    player.isTouchingWater && !player.isSwimming
                if (shouldHover) {
                    player.setVelocity(player.velocity.x, player.velocity.y + 0.005, player.velocity.z)
                }
            }
        }
    }
}
