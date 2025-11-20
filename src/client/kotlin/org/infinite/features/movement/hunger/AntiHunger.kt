package org.infinite.features.movement.hunger

import org.infinite.feature.ConfigurableFeature
import org.infinite.features.movement.fall.AntiFall
import org.infinite.settings.FeatureSetting

class AntiHunger : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> = listOf()
    override val conflicts: List<Class<out ConfigurableFeature>> = listOf(AntiFall::class.java)
}
