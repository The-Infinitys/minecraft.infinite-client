package org.infinite.global

import org.infinite.feature.ConfigurableFeature

abstract class ConfigurableGlobalFeature : ConfigurableFeature(true) {
    open fun onInit() {}

    open fun onShutdown() {}
}
