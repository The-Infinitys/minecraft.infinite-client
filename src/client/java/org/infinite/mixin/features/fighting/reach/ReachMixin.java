package org.infinite.mixin.features.fighting.reach;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.infinite.InfiniteClient;
import org.infinite.features.fighting.reach.Reach;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientPlayerEntity.class)
public abstract class ReachMixin extends AbstractClientPlayerEntity {

  public ReachMixin(ClientWorld world, GameProfile profile) {
    super(world, profile);
  }

  public double getBlockInteractionRange() {
    // Feature: Reach
    if (InfiniteClient.INSTANCE.isFeatureEnabled(Reach.class))
      return InfiniteClient.INSTANCE.getSettingFloat(Reach.class, "ReachDistance", 4.5F);

    // super.getBlockInteractionRange()
    return 4.5;
  }

  public double getEntityInteractionRange() {
    // Feature: Reach
    if (InfiniteClient.INSTANCE.isFeatureEnabled(Reach.class))
      return InfiniteClient.INSTANCE.getSettingFloat(Reach.class, "ReachDistance", 3.0F);

    // super.getEntityInteractionRange()
    return 3.0;
  }
}
