package org.infinite.features.rendering.font

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class HyperFont : ConfigurableFeature() {
    override val settings: List<FeatureSetting<*>> = emptyList()

    override fun onStart() {
        if (isEnabled()) {
            onEnabled()
        } else {
            onDisabled()
        }
    }

    override fun onEnabled() {
        (client.textRenderer as? HyperTextRenderer)?.enable()
    }

    override fun onDisabled() {
        (client.textRenderer as? HyperTextRenderer)?.disable()
    }

    override fun stop() {
        onDisabled()
    }
}
