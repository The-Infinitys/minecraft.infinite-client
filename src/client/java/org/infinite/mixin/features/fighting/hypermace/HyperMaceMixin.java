package org.infinite.mixin.features.fighting.hypermace;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.HitResult;
import org.infinite.InfiniteClient;
import org.infinite.features.fighting.mace.HyperMace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
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

    MinecraftClient client = MinecraftClient.getInstance();

    if (client.crosshairTarget == null
        || client.crosshairTarget.getType() != HitResult.Type.ENTITY) {
      return;
    }

    if (!player.getMainHandStack().isOf(Items.MACE)) {
      return;
    }

    // Get damageBoost setting from HyperMace feature
    int damageBoost = hyperMaceFeature.getDamageBoost().getValue();
    // Get fallDistance setting from HyperMace feature
    double fallDistanceToSend = getFallDistanceToSend(player, hyperMaceFeature);

    // MaceDmgHack.java のロジックを移植
    for (int i = 0; i < damageBoost; i++) {
      sendFakeY(player, 0.0);
    }
    sendFakeY(player, fallDistanceToSend);
    sendFakeY(player, 0.0);
  }

  @Unique
  private static double getFallDistanceToSend(PlayerEntity player, HyperMace hyperMaceFeature) {
    double fallDistance = hyperMaceFeature.getFallDistance().getValue();
    double fallMultiply = hyperMaceFeature.getFallMultiply().getValue();
    double actualFall = player.fallDistance;
    double fallDistanceToSend;
    if (actualFall <= fallDistance) {
      fallDistanceToSend = fallDistance;
    }
    // 2. 実際の落下距離が設定値（maxFallDistance）を上回る場合
    else {
      // 上回った分
      double excessFall = actualFall - fallDistance;
      // 上回った分に倍率を適用した追加ブースト
      double multipliedBoost = excessFall * fallMultiply;
      // 擬似落下距離 = 設定値（保証ブースト） + 倍率による追加ブースト
      fallDistanceToSend = fallDistance + multipliedBoost;
    }
    return fallDistanceToSend;
  }

  @Unique
  private void sendFakeY(PlayerEntity player, double offset) {
    if (MinecraftClient.getInstance().getNetworkHandler() == null) return;
    MinecraftClient.getInstance()
        .getNetworkHandler()
        .sendPacket(
            new PlayerMoveC2SPacket.PositionAndOnGround(
                player.getX(),
                player.getY() + offset,
                player.getZ(),
                false,
                player.horizontalCollision));
  }
}
