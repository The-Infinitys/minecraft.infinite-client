package org.infinite.mixin.features.rendering.freecamera;

import net.minecraft.client.network.ClientPlayerEntity;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.camera.FreeCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMovementMixin {

  @Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
  private void onSendMovementPackets(CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(FreeCamera.class)) {
      ci.cancel();
    }
  }
}
