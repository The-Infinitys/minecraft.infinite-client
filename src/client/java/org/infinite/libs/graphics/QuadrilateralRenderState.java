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
public record QuadrilateralRenderState(
    RenderPipeline pipeline,
    TextureSetup textureSetup,
    Matrix3x2f pose,
    float x1,
    float y1,
    float x2,
    float y2,
    float x3,
    float y3,
    float x4,
    float y4,
    int color,
    @Nullable ScreenRect scissorArea,
    @Nullable ScreenRect bounds)
    implements SimpleGuiElementRenderState {
  /** カスタムコンストラクタ。boundsフィールドを自動的に計算します。 */
  public QuadrilateralRenderState(
      RenderPipeline pipeline,
      TextureSetup textureSetup,
      Matrix3x2f pose,
      float x1,
      float y1,
      float x2,
      float y2,
      float x3,
      float y3,
      float x4,
      float y4,
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
        x4,
        y4,
        color,
        scissorArea,
        createBounds(x1, y1, x2, y2, x3, y3, x4, y4, pose, scissorArea));
  }

  public void setupVertices(VertexConsumer vertices) {
    // 4つの頂点を指定。多くの場合、四角形は2つの三角形として描画されます。
    // ここでは、一般的なQuad（四角形）またはトライアングルストリップの描画フォーマットとして、6つの頂点を出力します。
    // (x1, y1) -> (x2, y2) -> (x3, y3) [Triangle 1]
    // (x3, y3) -> (x4, y4) -> (x1, y1) [Triangle 2]

    // 頂点 1
    vertices.vertex(this.pose(), this.x1(), this.y1()).color(this.color());
    // 頂点 2
    vertices.vertex(this.pose(), this.x2(), this.y2()).color(this.color());
    // 頂点 3
    vertices.vertex(this.pose(), this.x3(), this.y3()).color(this.color());
    // 頂点 4
    vertices.vertex(this.pose(), this.x4(), this.y4()).color(this.color());
  }

  @Nullable
  private static ScreenRect createBounds(
      float x1,
      float y1,
      float x2,
      float y2,
      float x3,
      float y3,
      float x4,
      float y4,
      Matrix3x2f pose,
      @Nullable ScreenRect scissorArea) {
    var startX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
    var startY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
    var endX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
    var endY = Math.max(Math.max(y1, y2), Math.max(y3, y4));

    // 変換前の座標で矩形を作成
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
