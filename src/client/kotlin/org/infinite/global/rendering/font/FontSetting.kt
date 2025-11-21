package org.infinite.global.rendering.font

import net.minecraft.util.Identifier
import org.infinite.features.rendering.font.HyperTextRenderer
import org.infinite.global.ConfigurableGlobalFeature
import org.infinite.settings.FeatureSetting

class FontSetting : ConfigurableGlobalFeature() {
    override val settings: List<FeatureSetting<*>> = listOf()
    private val hyperTextRenderer: HyperTextRenderer?
        get() = client.textRenderer as? HyperTextRenderer

    override fun onEnabled() {
        hyperTextRenderer?.enable()
    }

    override fun onInit() {
        hyperTextRenderer?.defineFont(
            HyperTextRenderer.HyperFonts(
                Identifier.of("minecraft", "infinite_regular"),
                Identifier.of("minecraft", "infinite_italic"),
                Identifier.of("minecraft", "infinite_bold"),
                Identifier.of("minecraft", "infinite_bolditalic"),
            ),
        )
        if (isEnabled()) {
            onEnabled()
        } else {
            onDisabled()
        }
    }

    override fun onDisabled() {
        hyperTextRenderer?.disable()
    }
}
