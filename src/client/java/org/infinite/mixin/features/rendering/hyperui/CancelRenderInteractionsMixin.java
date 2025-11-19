package org.infinite.mixin.features.rendering.hyperui;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.ui.HyperUi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class CancelRenderInteractionsMixin {
  @Unique
  private static boolean shouldCancel() {
    return InfiniteClient.INSTANCE.isFeatureEnabled(HyperUi.class);
  }

  @Inject(method = "hasExperienceBar", at = @At("HEAD"), cancellable = true)
  private void cancelExperienceBar(CallbackInfoReturnable<Boolean> cir) {
    if (shouldCancel()) {
      cir.setReturnValue(false);
    }
  }
}
