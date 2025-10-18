package org.theinfinitys.infinite

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.lwjgl.glfw.GLFW
import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.featureCategories
import org.theinfinitys.gui.screen.InfiniteScreen

data class ToggleKeyBindingHandler(
    val keyBinding: KeyBinding,
    val feature: ConfigurableFeature,
)

object InfiniteKeyBind {
    private var menuKeyBinding: KeyBinding? = null

    // 初期化時にリストを空にする必要はないので、valでもOKですが、MutableListであることは維持します
    private val toggleKeyBindings: MutableList<ToggleKeyBindingHandler> = mutableListOf()

    fun registerKeybindings() {
        val keyBindingCategory = KeyBinding.Category.create(Identifier.of("gameplay", "infinite"))
        menuKeyBinding =
            KeyBindingHelper.registerKeyBinding(
                KeyBinding(
                    "key.infinite-client.open_menu",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_LEFT_CONTROL,
                    keyBindingCategory,
                ),
            )

        for (category in featureCategories) {
            for (feature in category.features) {
                if (feature.instance is ConfigurableFeature) {
                    val configurableFeature = feature.instance
                    // 修正 1: plus()を+=に変更して、リストに要素を追加します
                    toggleKeyBindings +=
                        ToggleKeyBindingHandler(
                            KeyBindingHelper.registerKeyBinding(
                                KeyBinding(
                                    "key.infinite-client.toggle.${category.name}.${feature.name}",
                                    InputUtil.Type.KEYSYM,
                                    configurableFeature.toggleKeyBind.value,
                                    keyBindingCategory,
                                ),
                            ),
                            configurableFeature,
                        )
                }
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            // メニューキーバインドの処理
            while (menuKeyBinding!!.wasPressed()) {
                client.setScreen(InfiniteScreen(Text.literal("Infinite Client Menu")))
            }

            // トグルキーバインドの処理
            for (toggleKeyBind in toggleKeyBindings) {
                while (toggleKeyBind.keyBinding.wasPressed()) {
                    // 修正 2: enabled.valueをトグル（否定を代入）します
                    if (toggleKeyBind.feature.isEnabled()) {
                        toggleKeyBind.feature.disable()
                    } else {
                        toggleKeyBind.feature.enable()
                    }
                }
            }

            // Tick all enabled features
            for (category in featureCategories) {
                for (feature in category.features) {
                    if (feature.instance is ConfigurableFeature && feature.instance.isEnabled()) {
                        feature.instance.tick()
                    }
                }
            }
        }
    }
}
