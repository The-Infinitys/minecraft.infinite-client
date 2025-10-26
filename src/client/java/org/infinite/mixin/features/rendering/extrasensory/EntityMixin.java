package org.infinite.mixin.features.rendering.extrasensory;

import net.minecraft.entity.Entity;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.sensory.ExtraSensory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin {

  @Inject(method = "isGlowing", at = @At("HEAD"), cancellable = true)
  private void isGlowing(CallbackInfoReturnable<Boolean> cir) {
    ExtraSensory extraSensory = InfiniteClient.INSTANCE.getFeature(ExtraSensory.class);
    if (extraSensory != null
        && extraSensory.getMethod().getValue() == ExtraSensory.Method.OutLine
        && extraSensory.isEnabled()) {
      extraSensory.handleIsGlowing((Entity) (Object) this, cir);
    }
  }
}
