package org.theinfinitys.rendering.detailinfo;

import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.theinfinitys.InfiniteClient;
import org.theinfinitys.features.rendering.gui.DetailInfo;

@Mixin(ShulkerBoxBlockEntity.class)
public abstract class ShulkerBoxMixin {
  @Unique
  private boolean shouldCancel() {
    // 設定チェックのロジックを統合
    DetailInfo detailInfo = InfiniteClient.INSTANCE.getFeature(DetailInfo.class);
    return detailInfo != null
        && InfiniteClient.INSTANCE.isSettingEnabled(DetailInfo.class, "InnerChest")
        && detailInfo.getShouldCancelScanScreen();
  }

  @Unique private boolean cancelFlag = false;

  // アニメーションの停止 (getAnimationProgress)
  @Inject(
      method = "getAnimationProgress",
      at =
          @At(
              value = "HEAD",
              target =
                  "Lnet/minecraft/block/entity/ShulkerBoxBlockEntity;getAnimationProgress(F)F"),
      cancellable = true)
  private void infiniteClient$forceZeroShulkerAnimation(
      float tickProgress, CallbackInfoReturnable<Float> cir) {
    if (shouldCancel()) {
      cir.setReturnValue(0.0F);
      cir.cancel();
    }
  }

  // サウンドの停止 (onOpen)
  @Inject(
      method = "onOpen",
      at =
          @At(
              value = "HEAD",
              target =
                  "Lnet/minecraft/block/entity/ShulkerBoxBlockEntity;onOpen(Lnet/minecraft/entity/ContainerUser;)V"),
      cancellable = true)
  public void infiniteClient$cancelShulkerOpenSound(CallbackInfo ci) {
    if (shouldCancel()) {
      ci.cancel();
      cancelFlag = true;
    }
  }

  // サウンドの停止 (onClose)
  @Inject(
      method = "onClose",
      at =
          @At(
              value = "HEAD",
              target =
                  "Lnet/minecraft/block/entity/ShulkerBoxBlockEntity;onClose(Lnet/minecraft/entity/ContainerUser;)V"),
      cancellable = true)
  public void infiniteClient$cancelShulkerCloseSound(CallbackInfo ci) {
    if (cancelFlag) {
      ci.cancel();
      cancelFlag = false;
    }
  }
}
