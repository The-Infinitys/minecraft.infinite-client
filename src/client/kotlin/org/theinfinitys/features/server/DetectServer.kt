package org.theinfinitys.features.server

import org.theinfinitys.ConfigurableFeature
import org.theinfinitys.FeatureLevel
import org.theinfinitys.InfiniteClient
import org.theinfinitys.featureCategories
import org.theinfinitys.settings.InfiniteSetting

class DetectServer : ConfigurableFeature(initialEnabled = true) {
    override val level: FeatureLevel = FeatureLevel.UTILS
    override val settings: List<InfiniteSetting<*>> =
        listOf(
            InfiniteSetting.EnumSetting(
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
                        "DetectServer: Disabled feature ${feature.name} (Level: ${configurableFeature.level}) because it exceeds the allowed level ($maxAllowedLevel).",
                    )
                }
            }
        }
    }
}
