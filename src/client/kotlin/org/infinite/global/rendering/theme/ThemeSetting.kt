package org.infinite.global.rendering.theme

import org.infinite.InfiniteClient
import org.infinite.global.ConfigurableGlobalFeature
import org.infinite.settings.FeatureSetting

class ThemeSetting : ConfigurableGlobalFeature() {
    val themeSetting = FeatureSetting.StringListSetting("Theme", "infinite", mutableListOf())
    override val settings: List<FeatureSetting<*>> = listOf(themeSetting)

    override fun onTick() {
        themeSetting.options.clear()
        InfiniteClient.currentTheme = themeSetting.value
        InfiniteClient.themes.map { it.name }.forEach { themeSetting.options += it }
    }
}
