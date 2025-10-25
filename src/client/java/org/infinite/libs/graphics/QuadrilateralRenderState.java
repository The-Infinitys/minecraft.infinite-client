package org.infinite.libs.graphics;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.Arrays;
import java.util.List;
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
    // 頂点をPointFのリストとしてまとめます
    List<PointF> points =
        Arrays.asList(
            new PointF(this.x1(), this.y1()),
            new PointF(this.x2(), this.y2()),
            new PointF(this.x3(), this.y3()),
            new PointF(this.x4(), this.y4()));

    // 頂点を時計回りにソートします
    List<PointF> sortedPoints = sortPointsClockwise(points);

    // ソートされた頂点をVertexConsumerに追加します
    for (PointF point : sortedPoints) {
      vertices.vertex(this.pose(), point.x, point.y).color(this.color());
    }
  }

  /**
   * 頂点リストを時計回りにソートします。 画面座標系（Y軸下向きが正）を想定しています。
   *
   * @param points ソートされていない頂点リスト
   * @return 時計回りにソートされた頂点リスト
   */
  private static List<PointF> sortPointsClockwise(List<PointF> points) {
    if (points.size() != 4) {
      // 四角形でない場合はそのまま返します (または例外をスローします)
      return points;
    }

    // 1. 重心 (中心点) を計算
    float averageX = 0;
    float averageY = 0;
    for (PointF point : points) {
      averageX += point.x;
      averageY += point.y;
    }
    final float centerX = averageX / points.size();
    final float centerY = averageY / points.size();

    // 2. 偏角に基づいてソート
    // 画面座標系 (Y軸下向きが正) では、標準の数学関数 (atan2) は反時計回りになります。
    // 時計回りにするには、比較時に角度を反転するか、atan2の引数を (dx, -dy) のように調整します。
    // 今回は、標準のソート順 (反時計回り) に逆順のコンパレータを適用します。
    points.sort(
        (p1, p2) -> {
          // 重心からの相対座標
          double angle1 = Math.atan2(p1.y - centerY, p1.x - centerX);
          double angle2 = Math.atan2(p2.y - centerY, p2.x - centerX);

          // 角度が小さい順 (反時計回り) になるように比較し、
          // 逆順 (時計回り) にするために符号を反転します。
          // Math.atan2の結果は -PI から PI の範囲です。
          return Double.compare(
              angle2, angle1); // angle2 < angle1 の場合、負の値 (p2, p1の順) になり、時計回りソートになります
        });

    return points;
  }

  // PointFはJavaの標準ライブラリには含まれないことが多いので、仮のレコードとして定義します
  private record PointF(float x, float y) {}

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
