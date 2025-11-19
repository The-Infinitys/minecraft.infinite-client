package org.infinite.features.server.meta

import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.featureCategories
import org.infinite.settings.FeatureSetting

class DetectServer : ConfigurableFeature(initialEnabled = true) {
    override val level: FeatureLevel = FeatureLevel.Utils
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.EnumSetting(
                "FeatureLevel",
                FeatureLevel.Extend,
                FeatureLevel.entries.toList(),
            ),
        )

    override fun onTick() {
        val maxAllowedLevel = getSetting("FeatureLevel")?.value as? FeatureLevel ?: FeatureLevel.Extend
        featureCategories.forEach { category ->
            category.features.forEach { feature ->
                val configurableFeature = feature.instance
                if (configurableFeature.isEnabled() && configurableFeature.level.ordinal > maxAllowedLevel.ordinal) {
                    configurableFeature.disable()
                    InfiniteClient.warn(
                        "DetectServer: Disabled feature ${
                            feature.name
                        } (Level: ${configurableFeature.level}) because it exceeds the allowed level ($maxAllowedLevel).",
                    )
                }
            }
        }
    }
}
