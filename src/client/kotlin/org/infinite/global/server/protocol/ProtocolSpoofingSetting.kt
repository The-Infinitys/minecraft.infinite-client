package org.infinite.global.server.protocol

import org.infinite.global.ConfigurableGlobalFeature
import org.infinite.settings.FeatureSetting

class ProtocolSpoofingSetting : ConfigurableGlobalFeature() {
    override val settings: List<FeatureSetting<*>> = listOf()

    override fun onInit() {
        // Mixins handle the actual spoofing, this feature just represents it.
        // No specific initialization needed here for the spoofing itself.
    }

    override fun onEnabled() {
        // The spoofing is always active due to Mixins, no action needed here.
    }

    override fun onDisabled() {
        // The spoofing is always active due to Mixins, no action needed here.
    }
}
