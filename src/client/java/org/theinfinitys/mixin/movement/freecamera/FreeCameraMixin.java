package org.theinfinitys.mixin.movement.freecamera;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.movement.FreeCamera;

@Mixin(ClientPlayerEntity.class)
public abstract class FreeCameraMixin extends AbstractClientPlayerEntity {

  public FreeCameraMixin(ClientWorld world, GameProfile profile) {
    super(world, profile);
  }

  @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
  private void onTickMovement(CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(FreeCamera.class)) {
      ci.cancel();
    }
  }

  /** FreeCamera: Player appears as a spectator when FreeCamera is enabled. */
  public boolean isSpectator() {
    // Feature: FreeCamera の有効性チェック
    return super.isSpectator() || InfiniteClient.INSTANCE.isFeatureEnabled(FreeCamera.class);
  }
}
