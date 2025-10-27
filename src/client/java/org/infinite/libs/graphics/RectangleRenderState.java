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
public record RectangleRenderState(
    RenderPipeline pipeline,
    TextureSetup textureSetup,
    Matrix3x2f pose,
    float x1,
    float y1,
    float x2,
    float y2,
    int color,
    @Nullable ScreenRect scissorArea,
    @Nullable ScreenRect bounds)
    implements SimpleGuiElementRenderState {
  /** カスタムコンストラクタ。boundsフィールドを自動的に計算します。 x1, y1は左上隅、x2, y2は右下隅を想定しています。 */
  public RectangleRenderState(
      RenderPipeline pipeline,
      TextureSetup textureSetup,
      Matrix3x2f pose,
      float x1,
      float y1,
      float x2,
      float y2,
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
        color,
        scissorArea,
        createBounds(x1, y1, x2, y2, pose, scissorArea));
  }

  public void setupVertices(VertexConsumer vertices) {
    // 長方形の4つの頂点座標を計算
    float left = Math.min(this.x1(), this.x2());
    float top = Math.min(this.y1(), this.y2());
    float right = Math.max(this.x1(), this.x2());
    float bottom = Math.max(this.y1(), this.y2());

    // 描画プリミティブとして2つの三角形（Quad）を出力します:
    // 頂点: (left, top), (left, bottom), (right, bottom), (right, top)

    // 頂点 1 (左上)
    vertices.vertex(this.pose(), left, top).color(this.color());
    // 頂点 2 (左下)
    vertices.vertex(this.pose(), left, bottom).color(this.color());
    // 頂点 3 (右下)
    vertices.vertex(this.pose(), right, bottom).color(this.color());

    // 頂点 4 (右上)
    vertices.vertex(this.pose(), right, top).color(this.color());
  }

  @Nullable
  private static ScreenRect createBounds(
      float x1, float y1, float x2, float y2, Matrix3x2f pose, @Nullable ScreenRect scissorArea) {
    var startX = Math.min(x1, x2);
    var startY = Math.min(y1, y2);
    var endX = Math.max(x1, x2);
    var endY = Math.max(y1, y2);

    // 変換前の座標で矩形を作成 (長方形は常に軸並行であるため、min/maxでそのままバウンディングボックスになります)
    ScreenRect screenRect =
        new ScreenRect(
            (int) Math.floor(startX),
            (int) Math.floor(startY),
            ((int) Math.ceil(endX - startX)),
            ((int) Math.ceil(endY - startY)));

    // 変換行列を適用して最小AABBを計算
    ScreenRect transformedRect = screenRect.transformEachVertex(pose);

    // スクリムエリアとの交差を計算
    return scissorArea != null ? scissorArea.intersection(transformedRect) : transformedRect;
  }
}
