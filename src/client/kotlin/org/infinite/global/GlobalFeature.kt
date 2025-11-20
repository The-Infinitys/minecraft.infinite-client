package org.infinite.global

import org.infinite.features.Feature

class GlobalFeature<T : ConfigurableGlobalFeature>(
    override val instance: T,
) : Feature<T>(instance)
