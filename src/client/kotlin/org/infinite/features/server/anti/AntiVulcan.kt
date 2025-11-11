package org.infinite.features.server.anti

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class AntiVulcan : ConfigurableFeature(initialEnabled = true) {
    override val settings: List<FeatureSetting<*>> = listOf()
}
