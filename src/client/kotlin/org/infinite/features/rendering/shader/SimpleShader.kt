package org.infinite.features.rendering.shader

import net.minecraft.client.MinecraftClient
import org.infinite.feature.ConfigurableFeature
import org.infinite.settings.FeatureSetting
import org.infinite.settings.Property
import org.lwjgl.glfw.GLFW

class SimpleShader : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Utils

    // 初期状態ではキーバインドなし
    override val toggleKeyBind: Property<Int> = Property(GLFW.GLFW_KEY_UNKNOWN)

    override val settings: List<FeatureSetting<*>> =
        listOf(
            // 現時点では特別な設定は不要ですが、将来的に追加できます
            // 例: FeatureSetting.BooleanSetting("EnableAdvancedEffect", false)
        )

    override fun onEnabled() {
        // シェーダーを有効化した際にワールドレンダラーをリロードし、変更を反映させる
        MinecraftClient.getInstance().worldRenderer.reload()
        // TODO: ここでカスタムシェーダーをMinecraftのレンダリングパイプラインにフックするMixinのロジックが必要になります。
        // これはMinecraftのレンダリングコードに深く依存するため、Featureクラスの外部で実装されるべきです。
    }

    override fun onDisabled() {
        // シェーダーを無効化した際にワールドレンダラーをリロードし、デフォルトの状態に戻す
        MinecraftClient.getInstance().worldRenderer.reload()
    }

    // 必要に応じて、onTick(), render2d(), render3d() などをオーバーライドできます。
    // 軽量シェーダーの性質上、これらのメソッドで直接何かを行う必要はないかもしれません。
}
