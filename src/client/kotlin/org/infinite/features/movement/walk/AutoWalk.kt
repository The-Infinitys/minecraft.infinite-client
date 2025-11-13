package org.infinite.features.movement.walk

import net.minecraft.client.MinecraftClient // MinecraftClient のインポートを追加
import net.minecraft.client.option.KeyBinding // KeyBinding のインポートを追加
import org.infinite.ConfigurableFeature
import org.infinite.libs.client.control.ControllerInterface
import org.infinite.settings.FeatureSetting

class AutoWalk : ConfigurableFeature(initialEnabled = false) {
    // 八方向と停止を定義
    enum class Way {
        Forward,
        Back,
        Left,
        Right,
        ForwardLeft,
        ForwardRight,
        BackLeft,
        BackRight,
    }

    private val waySetting = FeatureSetting.EnumSetting("MovementWay", Way.Forward, Way.entries)
    override val settings: List<FeatureSetting<*>> = listOf(waySetting)

    // 機能が無効化されたときに全ての移動キーを離すロジックを追加
    override fun disabled() {
        val options = MinecraftClient.getInstance().options ?: return
        ControllerInterface.release(options.forwardKey)
        ControllerInterface.release(options.backKey)
        ControllerInterface.release(options.leftKey)
        ControllerInterface.release(options.rightKey)
    }

    override fun tick() {
        val keysToPress: List<KeyBinding> =
            when (waySetting.value) {
                Way.Forward -> listOf(options.forwardKey)
                Way.Back -> listOf(options.backKey)
                Way.Left -> listOf(options.leftKey)
                Way.Right -> listOf(options.rightKey)
                // 斜め方向は2つのキーを同時に押す
                Way.ForwardLeft -> listOf(options.forwardKey, options.leftKey)
                Way.ForwardRight -> listOf(options.forwardKey, options.rightKey)
                Way.BackLeft -> listOf(options.backKey, options.leftKey)
                Way.BackRight -> listOf(options.backKey, options.rightKey)
            }

        // 決定したキーをControllerInterface経由で押す
        keysToPress.forEach { key ->
            ControllerInterface.press(key)
        }
    }
}
