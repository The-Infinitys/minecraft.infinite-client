package org.infinite.libs.mods.modmenu

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import com.terraformersmc.modmenu.api.UpdateChecker
import net.minecraft.client.gui.screen.Screen
import org.infinite.gui.screen.GlobalSettingsScreen

// Kotlinでは、クラス名の末尾に「Api」を付けず、実装するインターフェイスを
// クラス名に含めることが推奨される場合もありますが、このままでも問題ありません。
class InfiniteModMenuApi : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> =
        ConfigScreenFactory { screen: Screen? ->
            GlobalSettingsScreen(screen)
        }

    override fun getProvidedConfigScreenFactories(): Map<String, ConfigScreenFactory<*>> = emptyMap()

    override fun getProvidedUpdateCheckers(): Map<String, UpdateChecker> = mapOf("Infinite" to InfiniteUpdateChecker())
}
