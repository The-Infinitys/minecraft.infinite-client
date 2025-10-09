package org.theinfinitys.mixin.client;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket; // ⭐ 追加
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.InnerChest;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

  // 既に実装済み: GUIが開くパケットをキャンセルする
  @Inject(method = "onOpenScreen", at = @At("HEAD"), cancellable = true)
  private void innerChest$cancelOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
    // InnerChestが有効な場合のみキャンセル
    InnerChest innerChest = InfiniteClient.INSTANCE.getFeature(InnerChest.class);
    if (innerChest != null && innerChest.isEnabled() && innerChest.getShouldCancelScreen()) {
      innerChest.setShouldCancelScreen(false);
      ci.cancel();
    }
  }

  @Inject(method = "onInventory", at = @At("HEAD"))
  private void innerChest$processInventory(InventoryS2CPacket packet, CallbackInfo ci) {

    if (InfiniteClient.INSTANCE.isFeatureEnabled(InnerChest.class)) {
      var items = packet.contents();
      int syncId = packet.syncId();
      InnerChest innerChest = InfiniteClient.INSTANCE.getFeature(InnerChest.class);
      if (innerChest != null) {
        innerChest.handleChestContents(syncId, items);
      }
    }
  }
}
