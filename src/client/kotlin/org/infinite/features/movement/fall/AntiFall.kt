package org.infinite.features.movement.fall

import org.infinite.feature.ConfigurableFeature
import org.infinite.features.movement.hunger.AntiHunger
import org.infinite.settings.FeatureSetting

class AntiFall : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> = listOf()
    override val conflicts: List<Class<out ConfigurableFeature>> = listOf(AntiHunger::class.java)
}
