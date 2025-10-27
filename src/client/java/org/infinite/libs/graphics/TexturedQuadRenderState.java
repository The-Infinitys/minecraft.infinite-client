package org.infinite.libs.graphics;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.texture.TextureSetup;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

@Environment(EnvType.CLIENT)
public record TexturedQuadRenderState(
    RenderPipeline pipeline,
    TextureSetup textureSetup,
    Matrix3x2f pose,
    float x1,
    float y1,
    float x2,
    float y2,
    float u1,
    float u2,
    float v1,
    float v2,
    int color,
    @Nullable ScreenRect scissorArea,
    @Nullable ScreenRect bounds)
    implements SimpleGuiElementRenderState {
  public TexturedQuadRenderState(
      RenderPipeline pipeline,
      TextureSetup textureSetup,
      Matrix3x2f pose,
      float x1,
      float y1,
      float x2,
      float y2,
      float u1,
      float u2,
      float v1,
      float v2,
      int color,
      @Nullable ScreenRect scissorArea) {
    this(
        pipeline,
        textureSetup,
        pose,
        x1,
        y1,
        x2,
        y2,
        u1,
        u2,
        v1,
        v2,
        color,
        scissorArea,
        createBounds(x1, y1, x2, y2, pose, scissorArea));
  }

  public void setupVertices(VertexConsumer vertices) {
    vertices
        .vertex(this.pose(), (float) this.x1(), (float) this.y1())
        .texture(this.u1(), this.v1())
        .color(this.color());
    vertices
        .vertex(this.pose(), (float) this.x1(), (float) this.y2())
        .texture(this.u1(), this.v2())
        .color(this.color());
    vertices
        .vertex(this.pose(), (float) this.x2(), (float) this.y2())
        .texture(this.u2(), this.v2())
        .color(this.color());
    vertices
        .vertex(this.pose(), (float) this.x2(), (float) this.y1())
        .texture(this.u2(), this.v1())
        .color(this.color());
  }

  @Nullable
  private static ScreenRect createBounds(
      float x1, float y1, float x2, float y2, Matrix3x2f pose, @Nullable ScreenRect scissorArea) {
    ScreenRect screenRect =
        (new ScreenRect((int) x1, (int) y1, (int) (x2 - x1), (int) (y2 - y1)))
            .transformEachVertex(pose);
    return scissorArea != null ? scissorArea.intersection(screenRect) : screenRect;
  }
}
