package org.infinite.mixin.features.fighting.hypermace;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.infinite.InfiniteClient;
import org.infinite.features.fighting.mace.HyperMace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class HyperMaceMixin {

  @Inject(method = "attackEntity", at = @At("HEAD"))
  private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
    HyperMace hyperMaceFeature = InfiniteClient.INSTANCE.getFeature(HyperMace.class);

    if (hyperMaceFeature == null || !hyperMaceFeature.isEnabled()) {
      return;
    }
    hyperMaceFeature.execute(player, target);
  }
}
