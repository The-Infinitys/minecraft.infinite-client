package org.infinite.features.movement.hunger

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class AntiHunger : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> = listOf()
}
