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
    // --- Line Rendering Resources (Existing) ---

    val lineSnippet: RenderPipeline.Snippet =
        RenderPipeline
            .builder(
                RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET,
                RenderPipelines.GLOBALS_SNIPPET,
            ).withVertexShader(Identifier.of("infinite:core/lines"))
            .withFragmentShader(Identifier.of("infinite:core/lines"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false)
            // Lined rendering often uses POSITION_COLOR_NORMAL to get proper MatrixStack transformation
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

    /**
     * Returns either {@link #normalLayer} (depth test) or {@link #espLayer} (no depth test).
     */
    fun renderLinedLayer(isOverDraw: Boolean): RenderLayer.MultiPhase =
        if (isOverDraw) {
            espLayer
        } else {
            normalLayer
        }

    // --- Solid (Quad) Rendering Resources (New) ---

    val solidSnippet: RenderPipeline.Snippet =
        RenderPipeline
            .builder(
                RenderPipelines.TRANSFORMS_PROJECTION_FOG_SNIPPET,
                RenderPipelines.GLOBALS_SNIPPET,
            ).withVertexShader(Identifier.of("infinite:core/solid")) // Assuming a solid shader is available
            .withFragmentShader(Identifier.of("infinite:core/solid")) // Assuming a solid shader is available
            .withBlend(BlendFunction.TRANSLUCENT)
            .withCull(false) // Solid faces usually cull the back face
            // Using POSITION_COLOR_NORMAL and TRIANGLES to render quads, resolving the missing Normal error.
            .withVertexFormat(VertexFormats.POSITION_COLOR_NORMAL, VertexFormat.DrawMode.TRIANGLES)
            .buildSnippet()

    // 1. Depth-tested Solid Layer (Similar to WurstRenderLayers.QUADS)
    val quadsPipeline: RenderPipeline =
        RenderPipelines.register(
            RenderPipeline
                .builder(solidSnippet)
                .withLocation(Identifier.of("infinite:pipeline/normal_quads"))
                // Depth Test is implicitly enabled by default
                .build(),
        )
    val quadsLayer: RenderLayer.MultiPhase =
        RenderLayer.of(
            "infinite:quads",
            1536,
            quadsPipeline,
            RenderLayer.MultiPhaseParameters
                .builder()
                .layering(RenderLayer.VIEW_OFFSET_Z_LAYERING)
                .target(RenderLayer.ITEM_ENTITY_TARGET)
                .build(false),
        )

    // 2. No-depth-tested Solid Layer (Similar to WurstRenderLayers.ESP_QUADS)
    val espQuadsPipeline: RenderPipeline =
        RenderPipelines.register(
            RenderPipeline
                .builder(solidSnippet)
                .withLocation(Identifier.of("infinite:pipeline/esp_quads"))
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .build(),
        )
    val espQuadsLayer: RenderLayer.MultiPhase =
        RenderLayer.of(
            "infinite:esp_quads",
            1536,
            espQuadsPipeline,
            RenderLayer.MultiPhaseParameters
                .builder()
                .layering(RenderLayer.VIEW_OFFSET_Z_LAYERING)
                .target(RenderLayer.ITEM_ENTITY_TARGET)
                .build(false),
        )

    /**
     * Returns either {@link #quadsLayer} (depth test) or {@link #espQuadsLayer} (no depth test).
     */
    fun renderSolidLayer(isOverDraw: Boolean): RenderLayer.MultiPhase =
        if (isOverDraw) {
            espQuadsLayer
        } else {
            quadsLayer
        }
}
