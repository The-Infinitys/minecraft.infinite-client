package org.infinite.features.rendering.ui

import org.infinite.settings.FeatureSetting

data class UiRenderConfig(
    val heightSetting: FeatureSetting.IntSetting,
    val paddingSetting: FeatureSetting.IntSetting,
    val alphaSetting: FeatureSetting.DoubleSetting,
) {
    val height: Int get() = heightSetting.value
    val padding: Int get() = paddingSetting.value
    val alpha: Double get() = alphaSetting.value
}
