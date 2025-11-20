package org.infinite.gui.screen

import net.minecraft.client.gui.screen.Screen
import net.minecraft.client.gui.screen.option.OptionsScreen
import net.minecraft.client.input.KeyInput
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW

class GlobalSettingsScreen(
    optionsScreen: OptionsScreen,
) : Screen(Text.literal("Infinite Client General Settings")) {
    private val parent: Screen = optionsScreen

    // キーが押されたときに呼び出されるメソッド
    override fun keyPressed(input: KeyInput): Boolean {
        // Escキーが押されたかどうかをチェック
        val keyCode = input.key
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // Escキーが押されたら
            // 1. 現在のスクリーンを閉じる
            this.close()

            // 2. 処理済みとしてtrueを返す
            return true
        }

        // それ以外のキーの場合は、親クラスの処理を実行
        return super.keyPressed(input)
    }

    override fun close() {
        this.client?.setScreen(this.parent)
    }
}
