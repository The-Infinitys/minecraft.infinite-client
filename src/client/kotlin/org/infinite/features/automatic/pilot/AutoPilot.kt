package org.infinite.features.automatic.pilot

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class AutoPilot : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> = listOf()
}
