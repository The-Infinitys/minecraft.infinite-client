package org.infinite.features.automatic.pilot

import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.features.movement.fly.SuperFly
import org.infinite.settings.FeatureSetting

class AutoPilot : ConfigurableFeature(initialEnabled = false) {
    override val settings: List<FeatureSetting<*>> = listOf()
    private val isSuperFlyEnabled: Boolean
        get() = InfiniteClient.isFeatureEnabled(SuperFly::class.java)
}
