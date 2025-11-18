package org.infinite.features.rendering.font

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.text.OrderedText
import org.infinite.ConfigurableFeature
import org.infinite.InfiniteClient
import org.infinite.settings.FeatureSetting
import org.joml.Matrix4f

class HyperFont : ConfigurableFeature() {
    override val settings: List<FeatureSetting<*>> = emptyList()
}
