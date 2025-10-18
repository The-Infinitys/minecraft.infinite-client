package org.theinfinitys.mixin.features.movement.safewalk;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.movement.walk.SafeWalk;

@Mixin(ClientPlayerEntity.class)
public abstract class SafeWalkMixin extends AbstractClientPlayerEntity {

  public SafeWalkMixin(ClientWorld world, GameProfile profile) {
    super(world, profile);
  }

  /** SafeWalk: This is the part that makes SafeWalk work. */
  protected boolean clipAtLedge() {
    return super.clipAtLedge() || InfiniteClient.INSTANCE.isFeatureEnabled(SafeWalk.class);
  }

  /** SafeWalk: Allows SafeWalk to sneak visibly when the player is near a ledge. */
  // NOTE: adjustMovementForSneakingはClientPlayerEntityの親クラスのメソッドをオーバーライドしていると想定
  protected Vec3d adjustMovementForSneaking(Vec3d movement, MovementType type) {

    Vec3d vec3d = super.adjustMovementForSneaking(movement, type);
    SafeWalk safeWalk = InfiniteClient.INSTANCE.getFeature(SafeWalk.class);
    if (movement != null
        && InfiniteClient.INSTANCE.isFeatureEnabled(SafeWalk.class)
        && safeWalk != null) {
      safeWalk.onPreMotion();
    }
    return vec3d;
  }
}
