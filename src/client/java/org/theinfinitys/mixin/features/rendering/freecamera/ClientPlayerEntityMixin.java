package org.theinfinitys.mixin.features.rendering.freecamera;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.camera.FreeCamera;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {
  public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
    super(world, profile);
  }

  @Override
  public boolean isSpectator() {
    if (InfiniteClient.INSTANCE.isFeatureEnabled(FreeCamera.class)) {
      return true;
    } else {
      return super.isSpectator();
    }
  }
}
