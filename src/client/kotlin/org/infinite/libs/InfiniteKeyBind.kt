package org.infinite.libs

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.infinite.ConfigurableFeature
import org.infinite.featureCategories
import org.infinite.gui.screen.InfiniteScreen
import org.lwjgl.glfw.GLFW

data class ToggleKeyBindingHandler(
    val keyBinding: KeyBinding,
    val feature: ConfigurableFeature,
)

object InfiniteKeyBind {
    private var menuKeyBinding: KeyBinding? = null

    // 初期化時にリストを空にする必要はないので、valでもOKですが、MutableListであることは維持します
    private val toggleKeyBindings: MutableList<ToggleKeyBindingHandler> = mutableListOf()
    private val actionKeyBindings: MutableList<ConfigurableFeature.ActionKeybind> = mutableListOf()

    fun registerKeybindings() {
        val keyBindingCategory = KeyBinding.Category.create(Identifier.of("gameplay", "infinite"))
        menuKeyBinding =
            KeyBindingHelper.registerKeyBinding(
                KeyBinding(
                    "key.infinite-client.open_menu",
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_RIGHT_SHIFT,
                    keyBindingCategory,
                ),
            )

        for (category in featureCategories) {
            for (feature in category.features) {
                val configurableFeature = feature.instance
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
                actionKeyBindings += configurableFeature.registerKeybinds(category.name, feature.name, keyBindingCategory)
            }
        }

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            // メニューキーバインドの処理
            while (menuKeyBinding!!.wasPressed()) {
                client.setScreen(InfiniteScreen(Text.literal("Infinite Client Menu")))
            }
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
            for (actionKeyBind in actionKeyBindings) {
                while (actionKeyBind.keyBinding.wasPressed()) {
                    actionKeyBind.action()
                }
            }

            // Tick all enabled features
            for (category in featureCategories) {
                for (feature in category.features) {
                    if (feature.instance.isEnabled()) {
                        feature.instance.tick()
                    }
                }
            }
        }
    }
}
