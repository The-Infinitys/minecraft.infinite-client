package org.infinite.features.rendering.portalgui

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class PortalGui : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> = listOf()
}
