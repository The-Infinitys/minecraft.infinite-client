package org.theinfinitys.mixin.features.fighting.superattack;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.PositionAndOnGround;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.fighting.superattack.SuperAttack;
import org.theinfinitys.features.fighting.superattack.SuperAttack.AttackMethod;
import org.theinfinitys.settings.InfiniteSetting;

@Mixin(ClientPlayerInteractionManager.class)
public class SuperAttackMixin {

  @Inject(method = "attackEntity", at = @At("HEAD"))
  private void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
    SuperAttack superAttackFeature = InfiniteClient.INSTANCE.getFeature(SuperAttack.class);

    if (superAttackFeature != null && superAttackFeature.isEnabled()) {
      InfiniteSetting.EnumSetting<AttackMethod> methodSetting =
          (InfiniteSetting.EnumSetting<AttackMethod>) superAttackFeature.getSetting("Method");
      if (methodSetting != null) {
        AttackMethod method = methodSetting.getValue();
        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player != null && client.player.equals(player)) {
          // Conditions from CriticalsHack
          if (!(target instanceof LivingEntity)) return;
          if (!player.isOnGround()) return;
          if (player.isTouchingWater() || player.isInLava()) return;

          if (method == AttackMethod.FULL_JUMP) {
            // Full Jump (equivalent to CriticalsHack's FULL_JUMP)
            player.jump();
          } else if (method == AttackMethod.MINI_JUMP) {
            // Mini Jump (equivalent to CriticalsHack's MINI_JUMP)
            player.addVelocity(0.0, 0.1, 0.0);
            player.fallDistance = 0.1F;
            player.setOnGround(false);
          } else if (method == AttackMethod.PACKET) {
            // Packet method (equivalent to CriticalsHack's PACKET)
            sendFakeY(player, 0.0625, true);
            sendFakeY(player, 0, false);
            sendFakeY(player, 1.1e-5, false);
            sendFakeY(player, 0, false);
          }
        }
      }
    }
  }

  @Unique
  private void sendFakeY(PlayerEntity player, double offset, boolean onGround) {
    MinecraftClient.getInstance()
        .getNetworkHandler()
        .sendPacket(
            new PositionAndOnGround(
                player.getX(),
                player.getY() + offset,
                player.getZ(),
                onGround,
                player.isOnGround())); // Use player.isOnGround() for moving argument
  }
}
