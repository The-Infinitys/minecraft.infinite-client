package org.infinite.mixin.features.movement.freeze;

import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.infinite.InfiniteClient;
import org.infinite.features.movement.freeze.Freeze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public class FreezeNetworkHandlerMixin {

  /**
   * クライアントがパケットを送信する直前にフックし、Freezeが有効な場合は処理をブロックする。
   *
   * @param packet 送信されようとしているパケット
   * @param ci CallbackInfo
   */
  @Inject(
      method = "sendPacket(Lnet/minecraft/network/packet/Packet;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
    Freeze freezeFeature = InfiniteClient.INSTANCE.getFeature(Freeze.class);
    if (!InfiniteClient.INSTANCE.isFeatureEnabled(Freeze.class)
        || !(packet instanceof PlayerMoveC2SPacket)
        || freezeFeature == null) {
      return;
    }
    freezeFeature.processMovePacket((PlayerMoveC2SPacket) packet);
    ci.cancel();
  }
}
