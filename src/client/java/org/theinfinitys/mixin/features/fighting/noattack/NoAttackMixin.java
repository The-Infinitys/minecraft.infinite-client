package org.theinfinitys.mixin.features.fighting.noattack;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.fighting.noattack.NoAttack;
import org.theinfinitys.features.fighting.playermanager.PlayerManager;
import org.theinfinitys.settings.InfiniteSetting;

@Mixin(ClientPlayerInteractionManager.class)
public class NoAttackMixin {

  @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
  private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
    NoAttack noAttackFeature = InfiniteClient.INSTANCE.getFeature(NoAttack.class);
    PlayerManager playerManagerFeature = InfiniteClient.INSTANCE.getFeature(PlayerManager.class);

    if (noAttackFeature != null && noAttackFeature.isEnabled()) {
      // Check for protected entities (villagers, pets, etc.)
      InfiniteSetting.EntityListSetting protectedEntitiesSetting =
          (InfiniteSetting.EntityListSetting) noAttackFeature.getSetting("ProtectedEntities");
      if (protectedEntitiesSetting != null) {
        String targetEntityId = Registries.ENTITY_TYPE.getId(target.getType()).toString();
        if (protectedEntitiesSetting.getValue().contains(targetEntityId)) {
          ci.cancel(); // Cancel the attack
          return;
        }
      }
    }

    if (playerManagerFeature != null && playerManagerFeature.isEnabled()) {
      // Check for friendly players
      if (target instanceof PlayerEntity) {
        InfiniteSetting.PlayerListSetting friendsSetting =
            (InfiniteSetting.PlayerListSetting) playerManagerFeature.getSetting("Friends");
        if (friendsSetting != null) {
          String targetPlayerName = target.getName().getString();
          if (friendsSetting.getValue().contains(targetPlayerName)) {
            ci.cancel(); // Cancel the attack
          }
        }
      }
    }
  }
}
