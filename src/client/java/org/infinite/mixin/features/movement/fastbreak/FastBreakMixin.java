package org.infinite.mixin.features.movement.fastbreak;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.infinite.InfiniteClient;
import org.infinite.features.movement.braek.FastBreak;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class FastBreakMixin {

  @Inject(method = "tick", at = @At("TAIL"))
  private void resetBlockBreakingCooldown(CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(FastBreak.class)) {
      ((ClientPlayerInteractionManagerAccessor) this).setBlockBreakingCooldown(0);
    }
  }
}
