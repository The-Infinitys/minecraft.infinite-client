package org.infinite.libs.graphics.render

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.DepthTestFunction
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.gl.RenderPipelines
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderPhase
import net.minecraft.client.render.VertexFormats
import net.minecraft.util.Identifier
import java.util.OptionalDouble

object RenderResources {
    val lineSnippet: RenderPipeline.Snippet =
        RenderPipeline
            .builder(
                RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET,
                RenderPipelines.GLOBALS_SNIPPET,
            ).withVertexShader(Identifier.of("infinite:core/lines"))
            .withFragmentShader(Identifier.of("infinite:core/lines"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.LINES)
            .buildSnippet()

    val depthTestPipeline: RenderPipeline =
        RenderPipelines.register(
            RenderPipeline
                .builder(lineSnippet)
                .withLocation(
                    Identifier.of("infinite:pipeline/normal_lines"),
                ).build(),
        )
    val normalLayer: RenderLayer.MultiPhase =
        RenderLayer.of(
            "infinite:lines",
            1536,
            depthTestPipeline,
            RenderLayer.MultiPhaseParameters
                .builder()
                .lineWidth(RenderPhase.LineWidth(OptionalDouble.of(2.0)))
                .layering(RenderLayer.VIEW_OFFSET_Z_LAYERING)
                .target(RenderLayer.ITEM_ENTITY_TARGET)
                .build(false),
        )
    val espLines: RenderPipeline =
        RenderPipelines.register(
            RenderPipeline
                .builder(lineSnippet)
                .withLocation(Identifier.of("infinite:pipeline/esp_lines"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build(),
        )
    val espLayer: RenderLayer.MultiPhase =
        RenderLayer.of(
            "infinite:esp_lines",
            1536,
            espLines,
            RenderLayer.MultiPhaseParameters
                .builder()
                .lineWidth(RenderPhase.LineWidth(OptionalDouble.of(2.0)))
                .layering(RenderLayer.VIEW_OFFSET_Z_LAYERING)
                .target(RenderLayer.ITEM_ENTITY_TARGET)
                .build(false),
        )

    fun renderLayer(isOverDraw: Boolean): RenderLayer.MultiPhase =
        if (isOverDraw) {
            espLayer
        } else {
            normalLayer
        }
}
