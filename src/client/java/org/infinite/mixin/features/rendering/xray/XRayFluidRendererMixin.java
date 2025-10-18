package org.infinite.mixin.features.rendering.xray;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.FluidRenderer;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.xray.XRay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FluidRenderer.class)
public class XRayFluidRendererMixin {

  /** Hides and shows fluids when using X-Ray without Sodium installed. */
  @WrapOperation(
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/render/block/FluidRenderer;shouldSkipRendering(Lnet/minecraft/util/math/Direction;FLnet/minecraft/block/BlockState;)Z"),
      method =
          "render(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/block/BlockState;Lnet/minecraft/fluid/FluidState;)V")
  private boolean modifyShouldSkipRendering(
      Direction side, // shouldSkipRenderingの引数1: チェックしている面
      float height, // shouldSkipRenderingの引数2
      BlockState neighborState, // shouldSkipRenderingの引数3: 隣接ブロックの状態
      Operation<Boolean> original,
      BlockRenderView world,
      BlockPos pos, // レンダリング対象の流体ブロックの座標
      VertexConsumer vertexConsumer,
      BlockState blockState, // レンダリング対象の流体ブロックの状態
      FluidState fluidState) {
    XRay xray = InfiniteClient.INSTANCE.getFeature(XRay.class);

    // XRayが無効、または取得できない場合は、オリジナルのメソッドを呼び出して終了
    if (xray == null || !InfiniteClient.INSTANCE.isFeatureEnabled(XRay.class)) {
      return original.call(side, height, neighborState);
    }

    // shouldDrawSideを新しいシグネチャで呼び出す
    // shouldDrawSide(現在のブロックの状態, 現在のブロックの座標, チェックしている面, 隣接ブロックの状態)
    // FluidRenderer.shouldSkipRenderingは、レンダリング対象の流体ブロックに対して、
    // 特定の方向(side)の面を描画するかどうかを判断するために隣接ブロック(neighborState)をチェックする。
    Boolean shouldDraw = xray.shouldDrawSide(blockState, pos, side, neighborState);

    // XRay機能が描画ロジックをオーバーライドする場合
    if (shouldDraw != null) {
      // shouldDrawSideは「描画すべきかどうか」を返す。
      // shouldSkipRenderingは「描画をスキップすべきかどうか」を返すので、論理を反転させる。
      return !shouldDraw;
    }

    // XRay機能が判断しなかった場合は、オリジナルのメソッドを呼び出す
    return original.call(side, height, neighborState);
  }
}
