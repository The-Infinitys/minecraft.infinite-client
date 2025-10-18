package org.infinite.features.server

import org.infinite.ConfigurableFeature
import org.infinite.FeatureLevel
import org.infinite.InfiniteClient
import org.infinite.Translation
import org.infinite.featureCategories
import org.infinite.settings.FeatureSetting

class DetectServer : ConfigurableFeature(initialEnabled = true) {
    override val level: FeatureLevel = FeatureLevel.UTILS
    override val settings: List<FeatureSetting<*>> =
        listOf(
            FeatureSetting.EnumSetting(
                "FeatureLevel",
                "Set Feature Features",
                FeatureLevel.EXTEND,
                FeatureLevel.entries.toList(),
            ),
        )

    override fun tick() {
        val maxAllowedLevel = getSetting("FeatureLevel")?.value as? FeatureLevel ?: FeatureLevel.EXTEND
        featureCategories.forEach { category ->
            category.features.forEach { feature ->
                val configurableFeature = feature.instance as? ConfigurableFeature
                if (configurableFeature != null &&
                    configurableFeature.isEnabled() &&
                    configurableFeature.level.ordinal > maxAllowedLevel.ordinal
                ) {
                    configurableFeature.disable()
                    InfiniteClient.warn(
                        "DetectServer: Disabled feature ${Translation.t(feature.nameKey)} (Level: ${configurableFeature.level}) because it exceeds the allowed level ($maxAllowedLevel).",
                    )
                }
            }
        }
    }
}
