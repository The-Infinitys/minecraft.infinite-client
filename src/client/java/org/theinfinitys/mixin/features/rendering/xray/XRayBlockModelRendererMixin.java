package org.theinfinitys.mixin.features.rendering.xray;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.item.ItemConvertible;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.xray.XRay;

@Mixin(BlockModelRenderer.class)
public abstract class XRayBlockModelRendererMixin implements ItemConvertible {

  /**
   * Makes X-Ray work when neither Sodium nor Indigo are running. Also gets called while Indigo is
   * running when breaking a block in survival mode or seeing a piston retract.
   */
  @WrapOperation(
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/block/Block;shouldDrawSide(Lnet/minecraft/block/BlockState;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/Direction;)Z"),
      method =
          "shouldDrawFace(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;ZLnet/minecraft/util/math/Direction;Lnet/minecraft/util/math/BlockPos;)Z")
  private static boolean onRenderSmoothOrFlat(
      BlockState state, // 現在のブロックの状態 (blockState)
      BlockState otherState, // 隣接ブロックの状態 (neighborState)
      Direction side, // チェックしている面 (side)
      Operation<Boolean> original,
      BlockRenderView world,
      BlockState stateButFromTheOtherMethod,
      boolean cull,
      Direction sideButFromTheOtherMethod,
      BlockPos pos // 現在のブロックの座標 (blockPos)
      ) {
    XRay xray = InfiniteClient.INSTANCE.getFeature(XRay.class);

    // XRayが無効、または取得できない場合は、オリジナルのメソッドを呼び出して終了
    if (xray == null || !InfiniteClient.INSTANCE.isFeatureEnabled(XRay.class)) {
      return original.call(state, otherState, side);
    }

    // shouldDrawSideを新しいシグネチャで呼び出す
    // shouldDrawSide(現在のブロックの状態, 現在のブロックの座標, チェックしている面, 隣接ブロックの状態)
    // このフックの引数をそのまま新しい shouldDrawSide メソッドに渡すことができます。
    Boolean shouldDraw = xray.shouldDrawSide(state, pos, side, otherState);

    // XRay機能が描画ロジックをオーバーライドする場合
    if (shouldDraw != null) {
      // shouldDrawSideは「描画すべきかどうか」を返すので、そのまま返す
      return shouldDraw;
    }

    // XRay機能が判断しなかった場合は、オリジナルのメソッドを呼び出す
    return original.call(state, otherState, side);
  }
}
