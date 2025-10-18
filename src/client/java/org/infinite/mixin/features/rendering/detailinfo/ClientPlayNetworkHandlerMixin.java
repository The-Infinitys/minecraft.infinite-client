package org.infinite.mixin.features.rendering.detailinfo;

import java.util.Objects;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerPropertyUpdateS2CPacket;
import org.infinite.InfiniteClient;
import org.infinite.features.rendering.detailinfo.DetailInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

  @Inject(method = "onOpenScreen", at = @At("HEAD"), cancellable = true)
  private void DetailInfo$cancelOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
    DetailInfo detailInfo = InfiniteClient.INSTANCE.getFeature(DetailInfo.class);
    if (detailInfo != null && detailInfo.getShouldCancelScanScreen()) {
      if (packet.getScreenHandlerType() == detailInfo.getExpectedScreenType()) {
        detailInfo.setShouldCancelScanScreen(false);
        ci.cancel();
      } else {
        detailInfo.setShouldCancelScanScreen(false);
        detailInfo.setScanTargetBlockEntity(null);
        detailInfo.setExpectedScreenType(null);
        // Do not cancel, allow normal screen opening
      }
    }
  }

  @Inject(method = "onInventory", at = @At("HEAD"))
  private void DetailInfo$processInventory(InventoryS2CPacket packet, CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isSettingEnabled(DetailInfo.class, "InnerChest")) {
      var items = packet.contents();
      int syncId = packet.syncId();
      DetailInfo detailInfo = InfiniteClient.INSTANCE.getFeature(DetailInfo.class);
      if (detailInfo != null) {
        detailInfo.handleChestContents(syncId, items);
      }
    }
  }

  @Inject(method = "onScreenHandlerPropertyUpdate", at = @At("HEAD"))
  private void DetailInfo$processFurnaceProgress(
      ScreenHandlerPropertyUpdateS2CPacket packet, CallbackInfo ci) {
    if (InfiniteClient.INSTANCE.isSettingEnabled(DetailInfo.class, "InnerChest")) {
      DetailInfo detailInfo = InfiniteClient.INSTANCE.getFeature(DetailInfo.class);
      var targetDetail = Objects.requireNonNull(detailInfo).getTargetDetail();
      if (targetDetail != null
          && targetDetail.getPos() != null
          && InfiniteClient.INSTANCE.isSettingEnabled(DetailInfo.class, "InnerChest")) {
        var pos = targetDetail.getPos();
        var syncId = packet.getSyncId();
        var propertyId = packet.getPropertyId();
        var value = packet.getValue();
        var client = MinecraftClient.getInstance();
        var world = client.world;
        if (world != null) {
          var blockState = world.getBlockState(targetDetail.getPos());
          if (blockState != null)
            if (blockState.getBlock() == Blocks.BREWING_STAND) {
              detailInfo.handleBrewingProgress(
                  syncId, Objects.requireNonNull(pos), propertyId, value);
            } else if (blockState.getBlock() == Blocks.FURNACE
                || blockState.getBlock() == Blocks.SMOKER
                || blockState.getBlock() == Blocks.BLAST_FURNACE) {
              detailInfo.handleFurnaceProgress(
                  syncId, Objects.requireNonNull(pos), propertyId, value);
            }
        }
      }
    }
  }
}
