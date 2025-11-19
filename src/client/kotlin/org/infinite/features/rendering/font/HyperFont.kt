package org.infinite.features.rendering.font

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class HyperFont : ConfigurableFeature() {
    override val settings: List<FeatureSetting<*>> = emptyList()

    override fun start() {
        if (isEnabled()) {
            enabled()
        } else {
            disabled()
        }
    }

    override fun enabled() {
        (client.textRenderer as? HyperTextRenderer)?.enable()
    }

    override fun disabled() {
        (client.textRenderer as? HyperTextRenderer)?.disable()
    }

    override fun stop() {
        disabled()
    }
}
