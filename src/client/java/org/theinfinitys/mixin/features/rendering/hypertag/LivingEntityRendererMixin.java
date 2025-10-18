package org.theinfinitys.mixin.features.rendering.hypertag;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.tag.HyperTag;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
  /** Forces the nametag to be rendered if configured in NameTags. */
  @Inject(
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/MinecraftClient;getInstance()Lnet/minecraft/client/MinecraftClient;",
              ordinal = 0),
      method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z",
      cancellable = true)
  private void shouldForceLabel(
      LivingEntity entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
    if (InfiniteClient.INSTANCE.isSettingEnabled(HyperTag.class, "AlwaysLabeling"))
      cir.setReturnValue(true);
  }
}
