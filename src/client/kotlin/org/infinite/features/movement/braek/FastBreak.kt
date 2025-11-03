package org.infinite.features.movement.braek

import org.infinite.ConfigurableFeature
import org.infinite.settings.FeatureSetting

class FastBreak : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.Utils

    override val settings: List<FeatureSetting<*>> = emptyList()
}
