package org.theinfinitys.mixin.features.rendering.hypertag;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.tag.HyperTag;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<S extends EntityRenderState> {

  @Inject(method = "renderLabelIfPresent", at = @At(value = "HEAD"), cancellable = true)
  private void cancelLabelRendering(
      S renderState,
      MatrixStack matrices,
      OrderedRenderCommandQueue queue,
      CameraRenderState cameraRenderState,
      CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(HyperTag.class)) {
      ci.cancel();
    }
  }
}
