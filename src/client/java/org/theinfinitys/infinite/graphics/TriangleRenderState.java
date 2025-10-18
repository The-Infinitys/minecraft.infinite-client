package org.theinfinitys.infinite.graphics;

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
public record TriangleRenderState(
    RenderPipeline pipeline,
    TextureSetup textureSetup,
    Matrix3x2f pose,
    float x1,
    float y1,
    float x2,
    float y2,
    float x3,
    float y3,
    int color,
    @Nullable ScreenRect scissorArea,
    @Nullable ScreenRect bounds)
    implements SimpleGuiElementRenderState {
  /**
   * カスタムコンストラクタ。boundsフィールドを自動的に計算します。 正規コンストラクタ (Canonical Constructor) を呼び出すことでレコードのフィールドを初期化します。
   */
  public TriangleRenderState(
      RenderPipeline pipeline,
      TextureSetup textureSetup,
      Matrix3x2f pose,
      float x1,
      float y1,
      float x2,
      float y2,
      float x3,
      float y3,
      int color,
      @Nullable ScreenRect scissorArea) {
    // フィールドを初期化する正規コンストラクタを呼び出し、boundsを計算して渡します。
    this(
        pipeline,
        textureSetup,
        pose,
        x1,
        y1,
        x2,
        y2,
        x3,
        y3,
        color,
        scissorArea,
        createBounds(x1, y1, x2, y2, x3, y3, pose, scissorArea));
  }

  public void setupVertices(VertexConsumer vertices) {
    vertices.vertex(this.pose(), this.x1(), this.y1()).color(this.color());
    vertices.vertex(this.pose(), this.x2(), this.y2()).color(this.color());
    vertices.vertex(this.pose(), this.x3(), this.y3()).color(this.color());
    vertices.vertex(this.pose(), this.x2(), this.y2()).color(this.color());
  }

  @Nullable
  private static ScreenRect createBounds(
      float x1,
      float y1,
      float x2,
      float y2,
      float x3,
      float y3,
      Matrix3x2f pose,
      @Nullable ScreenRect scissorArea) {
    var startX = Math.min(Math.min(x1, x2), x3);
    var startY = Math.min(Math.min(y1, y2), y3);
    var endX = Math.max(Math.max(x1, x2), x3);
    var endY = Math.max(Math.max(y1, y2), y3);

    // まず、変換前の座標で矩形を作成
    ScreenRect screenRect =
        new ScreenRect(
            (int) Math.floor(startX),
            (int) Math.floor(startY),
            ((int) Math.ceil(endX - startX)),
            ((int) Math.ceil(endY - startY)));

    // ScreenRectのtransformEachVertexメソッドは、通常、矩形の頂点を変換し、
    // それらを含む新しい最小の軸並行バウンディングボックス（AABB）を計算します。
    ScreenRect transformedRect = screenRect.transformEachVertex(pose);

    // スクリムエリアとの交差を計算
    return scissorArea != null ? scissorArea.intersection(transformedRect) : transformedRect;
  }
}
