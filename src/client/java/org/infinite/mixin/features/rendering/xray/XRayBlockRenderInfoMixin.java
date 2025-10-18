package org.infinite.mixin.features.rendering.xray;

import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.xray.XRay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(value = BlockRenderInfo.class, remap = false)
public abstract class XRayBlockRenderInfoMixin {
  @Shadow public BlockPos blockPos;
  @Shadow public BlockState blockState;

  /**
   * This mixin hides and shows regular blocks when using X-Ray, if Indigo is running and Sodium is
   * not installed.
   */
  @Inject(at = @At("HEAD"), method = "shouldDrawSide", cancellable = true)
  private void onShouldDrawSide(Direction face, CallbackInfoReturnable<Boolean> cir) {
    XRay xray = InfiniteClient.INSTANCE.getFeature(XRay.class);

    // XRayが無効、または取得できない場合は、オリジナルのメソッドに処理を委ねる
    if (xray == null || !InfiniteClient.INSTANCE.isFeatureEnabled(XRay.class)) {
      return;
    }

    // ★ 修正点: face (Direction) が null の場合は描画を強制的にスキップ（falseを返す）してクラッシュを防ぐ
    if (face == null) {
      // null Directionは通常、ブロック内部の面や、特定のレンダリングパスでのみ発生し、
      // XRayとしては表示すべき面ではないため、描画をスキップ(false)して終了します。
      cir.setReturnValue(false);
      return; // 処理を終了
    }

    // World（BlockRenderView）と隣接ブロックの状態を取得する
    BlockRenderView world = MinecraftClient.getInstance().world;
    if (world == null) {
      return;
    }

    // 隣接ブロックの座標を計算
    BlockPos neighborPos = blockPos.offset(face);

    // 隣接ブロックの状態を取得
    BlockState neighborState = world.getBlockState(neighborPos);

    // 新しいシグネチャで shouldDrawSide を呼び出す
    Boolean shouldDraw = xray.shouldDrawSide(blockState, blockPos, face, neighborState);

    // XRay機能が描画ロジックをオーバーライドする場合
    if (shouldDraw != null) {
      cir.setReturnValue(shouldDraw);
    }
    // shouldDraw == null の場合は、オリジナルのメソッドに処理を委ねるため return;
  }
}
