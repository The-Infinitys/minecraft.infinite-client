package org.theinfinitys.mixin.client;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket; // ⭐ 追加
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.DetailInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onOpenScreen", at = @At("HEAD"), cancellable = true)
    private void DetailInfo$cancelOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        DetailInfo detailInfo = InfiniteClient.INSTANCE.getFeature(DetailInfo.class);
        if (detailInfo != null
                && InfiniteClient.INSTANCE.isSettingEnabled(DetailInfo.class, "InnerChest")
                && detailInfo.getShouldCancelScanScreen()) {
            detailInfo.setShouldCancelScanScreen(false);
            ci.cancel();
        }
    }

    @Inject(method = "onInventory", at = @At("HEAD"))
    private void DetailInfo$processInventory(InventoryS2CPacket packet, CallbackInfo ci) {

        if (InfiniteClient.INSTANCE.isSettingEnabled(DetailInfo.class, "InnerChest")) {
            var items = packet.contents();
            int syncId = packet.syncId();
            DetailInfo DetailInfo = InfiniteClient.INSTANCE.getFeature(DetailInfo.class);
            if (DetailInfo != null) {
                DetailInfo.handleChestContents(syncId, items);
            }
        }
    }
}
