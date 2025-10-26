package org.infinite.mixin.features.rendering.extrasensory;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.sensory.ExtraSensory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {
  @Inject(
      method = "updateRenderState",
      at =
          @At(
              value = "FIELD",
              target = "Lnet/minecraft/client/render/entity/state/EntityRenderState;outlineColor:I",
              shift = At.Shift.AFTER))
  private void onUpdateRenderState(T entity, S state, float tickProgress, CallbackInfo ci) {
    ExtraSensory extraSensory = InfiniteClient.INSTANCE.getFeature(ExtraSensory.class);
    if (extraSensory != null
        && extraSensory.getMethod().getValue() == ExtraSensory.Method.OutLine
        && extraSensory.isEnabled()) {
      extraSensory.handleRenderState(entity, state, tickProgress, ci);
    }
  }
}
