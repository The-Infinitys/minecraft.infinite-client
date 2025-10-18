package org.theinfinitys.features.movement.braek

import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.settings.InfiniteSetting

class FastBreak : ConfigurableFeature(initialEnabled = false) {
    override val level: FeatureLevel = FeatureLevel.UTILS

    override val settings: List<InfiniteSetting<*>> = emptyList()
}
