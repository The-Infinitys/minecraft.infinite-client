package org.infinite.mixin.features.rendering.hyperui;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.ui.HyperUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class CancelRenderWorldMixin {
  @Unique
  private boolean shouldCancel() {
    // nullチェックは省略していますが、必要に応じてInfiniteClient.INSTANCEがnullでないことを確認してください
    return InfiniteClient.INSTANCE.isFeatureEnabled(HyperUi.class);
  }

  @WrapOperation(
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/render/WorldRenderer;drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDLnet/minecraft/client/render/state/OutlineRenderState;I)V"),
      method =
          "renderTargetBlockOutline(Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/util/math/MatrixStack;ZLnet/minecraft/client/render/state/WorldRenderState;)V")
  private void cancelBlockOutline(
      WorldRenderer instance,
      MatrixStack matrices,
      VertexConsumer vertexConsumer,
      double x,
      double y,
      double z,
      OutlineRenderState state,
      int i,
      Operation<Void> original) {
    if (shouldCancel()) {
      InfiniteClient inf = InfiniteClient.INSTANCE;
      int modifyColor = inf.theme(inf.getCurrentTheme()).getColors().getPrimaryColor();
      original.call(instance, matrices, vertexConsumer, x, y, z, state, modifyColor);
    } else {
      original.call(instance, matrices, vertexConsumer, x, y, z, state, i);
    }
  }

  @Inject(method = "fillBlockBreakingProgressRenderState", at = @At("HEAD"), cancellable = true)
  private void cancelBlockBreakingProgress(CallbackInfo ci) {
    if (shouldCancel()) {
      ci.cancel(); // 描画メソッドの実行をキャンセル
    }
  }
}
