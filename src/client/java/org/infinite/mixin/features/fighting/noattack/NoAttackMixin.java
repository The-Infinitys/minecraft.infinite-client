package org.infinite.mixin.features.fighting.noattack;

import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import org.infinite.InfiniteClient;
import org.infinite.features.utils.noattack.NoAttack;
import org.infinite.features.utils.playermanager.PlayerManager;
import org.infinite.settings.FeatureSetting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class NoAttackMixin {

  @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
  private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
    NoAttack noAttackFeature = InfiniteClient.INSTANCE.getFeature(NoAttack.class);
    PlayerManager playerManagerFeature = InfiniteClient.INSTANCE.getFeature(PlayerManager.class);

    if (noAttackFeature != null && noAttackFeature.isEnabled()) {
      // Check for protected entities (villagers, pets, etc.)
      FeatureSetting.EntityListSetting protectedEntitiesSetting =
          (FeatureSetting.EntityListSetting) noAttackFeature.getSetting("ProtectedEntities");
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
        FeatureSetting.PlayerListSetting friendsSetting =
            (FeatureSetting.PlayerListSetting) playerManagerFeature.getSetting("Friends");
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
