package org.theinfinitys.features.movement.water

import net.minecraft.client.MinecraftClient
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.settings.InfiniteSetting

enum class WaterHoverMethod {
    Jump,
    Velocity,
}

class WaterHover : ConfigurableFeature(initialEnabled = false) {
    private val client: MinecraftClient
        get() = MinecraftClient.getInstance()

    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.EnumSetting(
                "Method",
                "WaterHoverを実現するメソッドを変更します。",
                WaterHoverMethod.Jump,
                WaterHoverMethod.entries.toList(),
            ),
        )
    private var isFloatedBefore = false

    override fun tick() {
        val player = client.player ?: return
        val method = (getSetting("Method") as? InfiniteSetting.EnumSetting)?.value as? WaterHoverMethod ?: return
        when (method) {
            WaterHoverMethod.Jump -> {
                val waterAttenuationFactor = 0.8
                val waterGravityVelocity = -0.02 * waterAttenuationFactor
                // プレイヤーが水中にいて、泳いでいない、かつスニークキーを押していない場合に処理を行う
                val shouldHover =
                    player.isTouchingWater &&
                        !player.isSwimming &&
                        !client.options.sneakKey.isPressed &&
                        player.velocity.y < waterGravityVelocity

                // 浮遊状態であれば、ジャンプキーの状態を「押されている」に設定する
                // これにより、ゲームの水中のジャンプロジック（+0.04のmotY追加）が呼び出され、浮力を得る
                if (shouldHover) {
                    client.options.jumpKey.isPressed = true
                    isFloatedBefore = true
                } else if (isFloatedBefore) {
                    client.options.jumpKey.isPressed = false
                    isFloatedBefore = false
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

    override fun disabled() {
        super.disabled()
        // Featureが無効になったとき、ジャンプキーの状態を元の（押されていない）状態に戻す
        // これを行わないと、プレイヤーはジャンプし続けてしまう
        client.options.jumpKey.isPressed = false
    }
}
