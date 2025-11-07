package org.infinite.libs.infinite

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient.log
import org.infinite.InfiniteClient.warn
import org.infinite.featureCategories
import org.infinite.gui.screen.InfiniteScreen
import org.infinite.utils.toSnakeCase
import org.lwjgl.glfw.GLFW

object InfiniteKeyBind {
    private var menuKeyBinding: KeyBinding? = null

    data class ToggleKeyBindingHandler(
        val keyBinding: KeyBinding,
        val feature: ConfigurableFeature,
    )

    var translationKeyList: MutableList<String> = mutableListOf()

    // 初期化時にリストを空にする必要はないので、valでもOKですが、MutableListであることは維持します
    private val toggleKeyBindings: MutableList<ToggleKeyBindingHandler> = mutableListOf()
    private val actionKeyBindings: MutableList<Pair<ConfigurableFeature, List<ConfigurableFeature.ActionKeybind>>> =
        mutableListOf()

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
                val translationKey =
                    "key.infinite-client.toggle.${toSnakeCase(category.name)}.${toSnakeCase(feature.name)}"
                translationKeyList += translationKey
                toggleKeyBindings +=
                    ToggleKeyBindingHandler(
                        KeyBindingHelper.registerKeyBinding(
                            KeyBinding(
                                translationKey,
                                InputUtil.Type.KEYSYM,
                                configurableFeature.toggleKeyBind.value,
                                keyBindingCategory,
                            ),
                        ),
                        configurableFeature,
                    )
                actionKeyBindings +=
                    configurableFeature to
                    configurableFeature.registerKeybinds(
                        category.name,
                        feature.name,
                        keyBindingCategory,
                    )
                translationKeyList += configurableFeature.registeredTranslations()
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
            for ((feature, actionKeyBindList) in actionKeyBindings) {
                if (feature.isEnabled()) {
                    for (actionKeyBind in actionKeyBindList) {
                        while (actionKeyBind.keyBinding.wasPressed()) {
                            actionKeyBind.action()
                        }
                    }
                }
            }
        }
    }

    fun checkTranslations(): List<String> {
        val result = mutableListOf<String>()
        for (key in translationKeyList) {
            if (Text.translatable(key).string == key) {
                result.add(key)
            }
        }
        return result
    }
}
